package com.tracer.plugin.analysis;

import com.tracer.plugin.TracerPlugin;
import com.tracer.plugin.config.ConfigManager;
import com.tracer.plugin.analysis.LagLevel;
import com.tracer.plugin.analysis.LagScore;
import lombok.Getter;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Analyzes chunks for performance-intensive elements and calculates lag scores.
 * This class is responsible for the core analysis logic of the plugin.
 */
public final class ChunkAnalyzer {
    
    private final TracerPlugin plugin;
    private final ConfigManager configManager;
    
    /**
     * Cache for storing recent analysis results
     */
    @Getter
    private final Map<String, LagScore> analysisCache;
    
    /**
     * Set of chunks currently being analyzed to prevent duplicate analysis
     */
    private final Set<String> analyzingChunks;
    
    /**
     * Materials that are considered redstone components
     */
    private static final Set<Material> REDSTONE_MATERIALS = EnumSet.of(
        Material.REDSTONE_WIRE,
        Material.REDSTONE_TORCH,
        Material.REDSTONE_WALL_TORCH,
        Material.REDSTONE_BLOCK,
        Material.REPEATER,
        Material.COMPARATOR,
        Material.PISTON,
        Material.STICKY_PISTON,
        Material.DISPENSER,
        Material.DROPPER,
        Material.OBSERVER,
        Material.HOPPER,
        Material.POWERED_RAIL,
        Material.DETECTOR_RAIL,
        Material.ACTIVATOR_RAIL
    );
    
    /**
     * Materials that are considered tile entities (block entities)
     */
    private static final Set<Material> TILE_ENTITY_MATERIALS = EnumSet.of(
        Material.CHEST,
        Material.TRAPPED_CHEST,
        Material.FURNACE,
        Material.BLAST_FURNACE,
        Material.SMOKER,
        Material.HOPPER,
        Material.DISPENSER,
        Material.DROPPER,
        Material.BREWING_STAND,
        Material.ENCHANTING_TABLE,
        Material.BEACON,
        Material.SPAWNER,
        Material.SHULKER_BOX,
        Material.BARREL,
        Material.LECTERN,
        Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE
    );
    
    /**
     * Entity types that are considered performance-intensive
     */
    private static final Set<EntityType> INTENSIVE_ENTITIES = EnumSet.of(
        EntityType.ITEM,
        EntityType.EXPERIENCE_ORB,
        EntityType.ARROW,
        EntityType.FIREWORK_ROCKET,
        EntityType.MINECART
    );
    
    public ChunkAnalyzer(final TracerPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.analysisCache = new ConcurrentHashMap<>();
        this.analyzingChunks = ConcurrentHashMap.newKeySet();
        
        // Start cache cleanup task
        this.startCacheCleanupTask();
    }
    
    /**
     * Analyze a single chunk and return its lag score.
     * 
     * @param chunk The chunk to analyze
     * @return CompletableFuture containing the lag score
     */
    public CompletableFuture<LagScore> analyzeChunk(final Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) {
            return CompletableFuture.completedFuture(null);
        }
        
        final String chunkKey = this.getChunkKey(chunk);
        
        // Check if chunk is already being analyzed
        if (this.analyzingChunks.contains(chunkKey)) {
            return CompletableFuture.completedFuture(this.plugin.getDataStorage().getLagScore(
                chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
        }
        
        // Check cache first
        final LagScore cachedScore = this.plugin.getDataStorage().getLagScore(
            chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (cachedScore != null && cachedScore.isFresh(this.configManager.getCacheDuration() * 1000L)) {
            return CompletableFuture.completedFuture(cachedScore);
        }
        
        // Mark chunk as being analyzed
        this.analyzingChunks.add(chunkKey);
        
        final CompletableFuture<LagScore> future = new CompletableFuture<>();
        
        if (this.configManager.isAsyncScanning()) {
            // Perform analysis asynchronously
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        final LagScore score = performAnalysis(chunk);
                        plugin.getDataStorage().storeLagScore(score);
                        analyzingChunks.remove(chunkKey);
                        future.complete(score);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Error analyzing chunk " + chunkKey, e);
                        analyzingChunks.remove(chunkKey);
                        future.completeExceptionally(e);
                    }
                }
            }.runTaskAsynchronously(this.plugin);
        } else {
            // Perform analysis synchronously
            try {
                final LagScore score = this.performAnalysis(chunk);
                this.plugin.getDataStorage().storeLagScore(score);
                this.analyzingChunks.remove(chunkKey);
                future.complete(score);
            } catch (Exception e) {
                this.plugin.getLogger().log(Level.WARNING, "Error analyzing chunk " + chunkKey, e);
                this.analyzingChunks.remove(chunkKey);
                future.completeExceptionally(e);
            }
        }
        
        return future;
    }
    
    /**
     * Analyze multiple chunks in the specified radius around a center chunk.
     * 
     * @param centerChunk The center chunk
     * @param radius The radius in chunks
     * @return CompletableFuture containing a list of lag scores
     */
    public CompletableFuture<List<LagScore>> analyzeChunksInRadius(final Chunk centerChunk, final int radius) {
        if (centerChunk == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        final List<CompletableFuture<LagScore>> futures = new ArrayList<>();
        final World world = centerChunk.getWorld();
        final int centerX = centerChunk.getX();
        final int centerZ = centerChunk.getZ();
        
        // Collect chunks in radius
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                if (world.isChunkLoaded(x, z)) {
                    final Chunk chunk = world.getChunkAt(x, z);
                    futures.add(this.analyzeChunk(chunk));
                }
            }
        }
        
        // Combine all futures
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                final List<LagScore> results = new ArrayList<>();
                for (CompletableFuture<LagScore> future : futures) {
                    try {
                        final LagScore score = future.get();
                        if (score != null) {
                            results.add(score);
                        }
                    } catch (Exception e) {
                        this.plugin.getLogger().log(Level.WARNING, "Error getting analysis result", e);
                    }
                }
                return results;
            });
    }
    
    /**
     * Analyze a list of chunks asynchronously.
     * 
     * @param chunks The list of chunks to analyze
     * @return A CompletableFuture containing the list of lag scores
     */
    public CompletableFuture<List<LagScore>> analyzeChunks(final List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        final List<CompletableFuture<LagScore>> futures = new ArrayList<>();
        
        for (final Chunk chunk : chunks) {
            futures.add(this.analyzeChunk(chunk));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                final List<LagScore> results = new ArrayList<>();
                for (final CompletableFuture<LagScore> future : futures) {
                    try {
                        final LagScore score = future.get();
                        if (score != null) {
                            results.add(score);
                        }
                    } catch (final Exception e) {
                        this.plugin.getLogger().log(Level.WARNING, "Error getting analysis result", e);
                    }
                }
                return results;
            });
    }
    
    /**
     * Perform the actual analysis of a chunk.
     * 
     * @param chunk The chunk to analyze
     * @return The calculated lag score
     */
    private LagScore performAnalysis(final Chunk chunk) {
        try {
            // Count entities, tile entities, and redstone components
            final int entityCount = this.countEntities(chunk);
            final int tileEntityCount = this.countTileEntities(chunk);
            final int redstoneCount = this.countRedstoneComponents(chunk);
            final double currentTps = this.getCurrentTPS();
            
            // Create LagScore with all the data
            final LagScore score = new LagScore(
                chunk.getWorld().getName(),
                chunk.getX(),
                chunk.getZ(),
                entityCount,
                tileEntityCount,
                redstoneCount,
                currentTps
            );
            
            this.plugin.debugLog("Analyzed chunk " + this.getChunkKey(chunk) + ": " + score.getFormattedString());
            return score;
            
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Error during chunk analysis", e);
            // Return a default score in case of error
            final LagScore errorScore = new LagScore(
                chunk.getWorld().getName(),
                chunk.getX(),
                chunk.getZ(),
                0, 0, 0, 20.0
            );
            errorScore.setDetails("Analysis error: " + e.getMessage());
             return errorScore;
   }
       }
    
    /**
     * Count entities in a chunk.
     * 
     * @param chunk The chunk to analyze
     * @return The number of entities
     */
    private int countEntities(final Chunk chunk) {
        try {
            final Entity[] entities = chunk.getEntities();
            if (entities == null) {
                return 0;
            }
            
            int count = 0;
            for (Entity entity : entities) {
                if (entity != null && this.shouldCountEntity(entity)) {
                    count++;
                    
                    // Give extra weight to performance-intensive entities
                    if (INTENSIVE_ENTITIES.contains(entity.getType())) {
                        count++; // Count twice
                    }
                }
            }
            
            return count;
        } catch (Exception e) {
            this.plugin.debugLog("Error counting entities in chunk: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Count tile entities (block entities) in a chunk.
     * 
     * @param chunk The chunk to analyze
     * @return The number of tile entities
     */
    private int countTileEntities(final Chunk chunk) {
        try {
            final BlockState[] tileEntities = chunk.getTileEntities();
            if (tileEntities == null) {
                return 0;
            }
            
            int count = 0;
            for (BlockState tileEntity : tileEntities) {
                if (tileEntity != null && this.shouldCountTileEntity(tileEntity)) {
                    count++;
                }
            }
            
            return count;
        } catch (Exception e) {
            this.plugin.debugLog("Error counting tile entities in chunk: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Count redstone components in a chunk.
     * 
     * @param chunk The chunk to analyze
     * @return The number of redstone components
     */
    private int countRedstoneComponents(final Chunk chunk) {
        try {
            int count = 0;
            final int chunkX = chunk.getX() << 4;
            final int chunkZ = chunk.getZ() << 4;
            final World world = chunk.getWorld();
            
            // Scan all blocks in the chunk
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                        final Block block = world.getBlockAt(chunkX + x, y, chunkZ + z);
                        if (this.isRedstoneComponent(block)) {
                            count++;
                            
                            // Check if the component is powered (active)
                            if (block.isBlockPowered() || block.isBlockIndirectlyPowered()) {
                                count++; // Count active components twice
                            }
                        }
                    }
                }
            }
            
            return count;
        } catch (Exception e) {
            this.plugin.debugLog("Error counting redstone components in chunk: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Check if an entity should be counted in the analysis.
     * 
     * @param entity The entity to check
     * @return true if the entity should be counted
     */
    private boolean shouldCountEntity(final Entity entity) {
        if (entity == null) {
            return false;
        }
        
        final EntityType type = entity.getType();
        
        // Always count certain types
        if (INTENSIVE_ENTITIES.contains(type)) {
            return true;
        }
        
        // Check configuration for specific entity types
        switch (type.getEntityClass().getSimpleName()) {
            case "Animals":
            case "WaterMob":
                return this.configManager.getBoolean("thresholds.entities.count-passive", true);
            case "Monster":
                return this.configManager.getBoolean("thresholds.entities.count-hostile", true);
            case "Vehicle":
                return this.configManager.getBoolean("thresholds.entities.count-vehicles", true);
            default:
                return true; // Count other entities by default
        }
    }
    
    /**
     * Check if a tile entity should be counted in the analysis.
     * 
     * @param tileEntity The tile entity to check
     * @return true if the tile entity should be counted
     */
    private boolean shouldCountTileEntity(final BlockState tileEntity) {
        if (tileEntity == null) {
            return false;
        }
        
        final Material material = tileEntity.getType();
        
        // Check if it's a monitored tile entity type
        return TILE_ENTITY_MATERIALS.contains(material);
    }
    
    /**
     * Check if a block is a redstone component.
     * 
     * @param block The block to check
     * @return true if the block is a redstone component
     */
    private boolean isRedstoneComponent(final Block block) {
        if (block == null) {
            return false;
        }
        
        return REDSTONE_MATERIALS.contains(block.getType());
    }
    
    /**
     * Get the current server TPS.
     * 
     * @return The current TPS
     */
    private double getCurrentTPS() {
        try {
            // Use reflection to get TPS from the server
            final Object server = this.plugin.getServer().getClass().getMethod("getServer").invoke(this.plugin.getServer());
            final double[] tps = (double[]) server.getClass().getField("recentTps").get(server);
            return Math.min(20.0, tps[0]); // Use 1-minute average, cap at 20
        } catch (Exception e) {
            // Fallback to 20 TPS if reflection fails
            return 20.0;
        }
    }
    
    /**
     * Generate a unique key for a chunk.
     * 
     * @param chunk The chunk
     * @return The chunk key
     */
    private String getChunkKey(final Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + "," + chunk.getZ();
    }
    
    /**
     * Clear the analysis cache.
     */
    public void clearCache() {
        this.plugin.getDataStorage().clearCache();
        this.plugin.debugLog("Analysis cache cleared.");
    }
    
    /**
     * Get the number of cached analysis results.
     * 
     * @return The cache size
     */
    public int getCacheSize() {
        return this.plugin.getDataStorage().getCacheStats().containsKey("cache_size") ? 
            (Integer) this.plugin.getDataStorage().getCacheStats().get("cache_size") : 0;
    }
    
    /**
     * Start the cache cleanup task to remove old entries.
     */
    private void startCacheCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupCache();
            }
        }.runTaskTimerAsynchronously(this.plugin, 20 * 60, 20 * 60); // Run every minute
    }
    
    /**
     * Clean up old entries from the cache.
     */
    private void cleanupCache() {
        // Cache cleanup is now handled by DataStorage
        this.plugin.debugLog("Cache cleanup delegated to DataStorage");
    }
    
    /**
     * Shutdown the analyzer and clean up resources.
     */
    public void reloadConfiguration() {
        // Clear cache when configuration is reloaded
        this.clearCache();
    }
    
    public void shutdown() {
        this.analyzingChunks.clear();
    }
}