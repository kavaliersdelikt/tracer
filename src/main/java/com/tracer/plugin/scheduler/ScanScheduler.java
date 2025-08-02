package com.tracer.plugin.scheduler;

import com.tracer.plugin.TracerPlugin;
import com.tracer.plugin.analysis.ChunkAnalyzer;
import com.tracer.plugin.config.ConfigManager;
import com.tracer.plugin.analysis.LagScore;
import com.tracer.plugin.visualization.VisualizationManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages automatic scanning of chunks at configured intervals.
 * Handles scheduling, player-based scanning, and performance optimization.
 */
public final class ScanScheduler {
    
    private final TracerPlugin plugin;
    private final ConfigManager configManager;
    private final ChunkAnalyzer chunkAnalyzer;
    private final VisualizationManager visualizationManager;
    
    /**
     * Main scanning task
     */
    @Getter
    private BukkitTask scanTask;
    
    /**
     * Adaptive scanning task for performance optimization
     */
    private BukkitTask adaptiveTask;
    
    /**
     * Server-wide scanning task
     */
    private BukkitTask serverScanTask;
    
    /**
     * Whether automatic scanning is enabled
     */
    @Getter
    private boolean scanningEnabled;
    
    /**
     * Current scan interval in ticks
     */
    @Getter
    private int currentScanInterval;
    
    /**
     * Chunks that are currently on cooldown
     */
    private final Map<String, Long> chunkCooldowns;
    
    /**
     * Performance metrics for adaptive scanning
     */
    private final Map<String, Double> performanceMetrics;
    
    /**
     * Last scan times for worlds
     */
    private final Map<String, Long> worldScanTimes;
    
    /**
     * Scan statistics
     */
    @Getter
    private long totalScansPerformed;
    
    @Getter
    private long totalChunksAnalyzed;
    
    @Getter
    private double averageScanTime;
    
    public ScanScheduler(final TracerPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.chunkAnalyzer = plugin.getChunkAnalyzer();
        this.visualizationManager = plugin.getVisualizationManager();
        
        this.scanningEnabled = false;
        this.currentScanInterval = this.configManager.getScanInterval() * 20; // Convert to ticks
        this.chunkCooldowns = new ConcurrentHashMap<>();
        this.performanceMetrics = new ConcurrentHashMap<>();
        this.worldScanTimes = new ConcurrentHashMap<>();
        
        this.totalScansPerformed = 0;
        this.totalChunksAnalyzed = 0;
        this.averageScanTime = 0.0;
    }
    
    /**
     * Start automatic scanning.
     */
    public void startScanning() {
        if (this.scanningEnabled) {
            return;
        }
        
        this.scanningEnabled = true;
        this.currentScanInterval = this.configManager.getScanInterval() * 20;
        
        // Start main scanning task
        this.scanTask = new BukkitRunnable() {
            @Override
            public void run() {
                performAutomaticScan();
            }
        }.runTaskTimer(this.plugin, 20, this.currentScanInterval);
        
        // Start adaptive scanning task if enabled
        if (this.configManager.getBoolean("performance.adaptive-scanning.enabled", true)) {
            this.startAdaptiveScanning();
        }
        
        this.plugin.getLogger().info("Automatic scanning started with interval: " + (this.currentScanInterval / 20) + " seconds");
        this.plugin.debugLog("Scan scheduler started successfully.");
    }
    
    /**
     * Stop automatic scanning.
     */
    public void stopScanning() {
        if (!this.scanningEnabled) {
            return;
        }
        
        this.scanningEnabled = false;
        
        // Cancel scanning task
        if (this.scanTask != null && !this.scanTask.isCancelled()) {
            this.scanTask.cancel();
            this.scanTask = null;
        }
        
        // Cancel adaptive task
        if (this.adaptiveTask != null && !this.adaptiveTask.isCancelled()) {
            this.adaptiveTask.cancel();
            this.adaptiveTask = null;
        }
        
        this.plugin.getLogger().info("Automatic scanning stopped");
        this.plugin.debugLog("Scan scheduler stopped successfully.");
    }
    
    /**
     * Restart scanning with updated configuration.
     */
    public void restartScanning() {
        this.stopScanning();
        this.startScanning();
    }
    
    /**
     * Perform an automatic scan of all relevant chunks.
     */
    private void performAutomaticScan() {
        if (!this.scanningEnabled) {
            return;
        }
        
        final long startTime = System.currentTimeMillis();
        
        try {
            // Get all online players
            final Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            
            if (onlinePlayers.isEmpty()) {
                this.plugin.debugLog("No online players, skipping automatic scan.");
                return;
            }
            
            // Scan chunks around each player
            final List<CompletableFuture<Void>> scanFutures = new ArrayList<>();
            
            for (Player player : onlinePlayers) {
                if (player.isOnline() && this.shouldScanForPlayer(player)) {
                    final CompletableFuture<Void> future = this.scanAroundPlayer(player);
                    scanFutures.add(future);
                }
            }
            
            // Wait for all scans to complete (with timeout)
            CompletableFuture.allOf(scanFutures.toArray(new CompletableFuture[0]))
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .whenComplete((result, throwable) -> {
                    final long endTime = System.currentTimeMillis();
                    final double scanTime = (endTime - startTime) / 1000.0;
                    
                    this.updateScanStatistics(scanTime);
                    
                    if (throwable != null) {
                        this.plugin.getLogger().log(Level.WARNING, "Error during automatic scan", throwable);
                    } else {
                        this.plugin.debugLog("Automatic scan completed in " + String.format("%.2f", scanTime) + " seconds");
                    }
                });
            
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Critical error during automatic scan", e);
        }
    }
    
    /**
     * Scan chunks around a specific player.
     * 
     * @param player The player
     * @return CompletableFuture for the scan operation
     */
    private CompletableFuture<Void> scanAroundPlayer(final Player player) {
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(null);
        }
        
        final Chunk playerChunk = player.getLocation().getChunk();
        final int scanRadius = this.configManager.getDefaultScanRadius();
        
        return this.chunkAnalyzer.analyzeChunksInRadius(playerChunk, scanRadius)
            .thenAccept(lagScores -> {
                if (lagScores != null && !lagScores.isEmpty()) {
                    // Update visualization for this player
                    this.visualizationManager.updateVisualization(player, lagScores);
                    
                    // Update chunk cooldowns
                    this.updateChunkCooldowns(lagScores);
                    
                    // Update performance metrics
                    this.updatePerformanceMetrics(player.getWorld().getName(), lagScores);
                    
                    this.totalChunksAnalyzed += lagScores.size();
                }
            })
            .exceptionally(throwable -> {
                this.plugin.getLogger().log(Level.WARNING, "Error scanning around player " + player.getName(), throwable);
                return null;
            });
    }
    
    /**
     * Check if we should scan for a specific player.
     * 
     * @param player The player
     * @return true if we should scan
     */
    private boolean shouldScanForPlayer(final Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        
        // Check if player has permission
        if (!player.hasPermission("tracer.autoscan")) {
            return false;
        }
        
        // Check world scan cooldown
        final String worldName = player.getWorld().getName();
        final long lastScanTime = this.worldScanTimes.getOrDefault(worldName, 0L);
        final long worldCooldown = this.configManager.getInt("scanning.world-cooldown", 30) * 1000L;
        
        if (System.currentTimeMillis() - lastScanTime < worldCooldown) {
            return false;
        }
        
        // Check if player is in a scannable world
        final List<String> excludedWorlds = this.configManager.getStringList("scanning.excluded-worlds");
        if (excludedWorlds.contains(worldName)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Update chunk cooldowns based on scan results.
     * 
     * @param lagScores The lag scores
     */
    private void updateChunkCooldowns(final List<LagScore> lagScores) {
        final long cooldownDuration = this.configManager.getChunkCooldown() * 1000L;
        final long currentTime = System.currentTimeMillis();
        
        for (LagScore score : lagScores) {
            final String chunkKey = score.getWorldName() + ":" + score.getChunkX() + "," + score.getChunkZ();
            this.chunkCooldowns.put(chunkKey, currentTime + cooldownDuration);
        }
    }
    
    /**
     * Update performance metrics for adaptive scanning.
     * 
     * @param worldName The world name
     * @param lagScores The lag scores
     */
    private void updatePerformanceMetrics(final String worldName, final List<LagScore> lagScores) {
        if (lagScores.isEmpty()) {
            return;
        }
        
        // Calculate average lag score for this world
        final double averageLagScore = lagScores.stream()
            .mapToDouble(LagScore::getOverallScore)
            .average()
            .orElse(0.0);
        
        this.performanceMetrics.put(worldName, averageLagScore);
        this.worldScanTimes.put(worldName, System.currentTimeMillis());
    }
    
    /**
     * Update scan statistics.
     * 
     * @param scanTime The time taken for the scan
     */
    private void updateScanStatistics(final double scanTime) {
        this.totalScansPerformed++;
        
        // Calculate running average
        this.averageScanTime = ((this.averageScanTime * (this.totalScansPerformed - 1)) + scanTime) / this.totalScansPerformed;
    }
    
    /**
     * Start adaptive scanning to optimize performance.
     */
    private void startAdaptiveScanning() {
        this.adaptiveTask = new BukkitRunnable() {
            @Override
            public void run() {
                performAdaptiveOptimization();
            }
        }.runTaskTimer(this.plugin, 20 * 60, 20 * 60); // Run every minute
    }
    
    /**
     * Perform adaptive optimization of scan intervals.
     */
    private void performAdaptiveOptimization() {
        try {
            // Get current server TPS
            final double currentTPS = this.getCurrentTPS();
            final double targetTPS = this.configManager.getDouble("performance.adaptive-scanning.target-tps", 19.0);
            
            // Calculate performance factor
            final double performanceFactor = currentTPS / 20.0;
            
            // Adjust scan interval based on performance
            final int baseScanInterval = this.configManager.getScanInterval() * 20;
            int newScanInterval = baseScanInterval;
            
            if (currentTPS < targetTPS) {
                // Server is lagging, increase scan interval
                newScanInterval = (int) (baseScanInterval * (1.0 + (1.0 - performanceFactor)));
            } else if (currentTPS > 19.5 && this.averageScanTime < 1.0) {
                // Server is performing well, decrease scan interval
                newScanInterval = (int) (baseScanInterval * 0.8);
            }
            
            // Apply limits
            final int minInterval = this.configManager.getInt("performance.adaptive-scanning.min-interval", 10) * 20;
            final int maxInterval = this.configManager.getInt("performance.adaptive-scanning.max-interval", 300) * 20;
            
            newScanInterval = Math.max(minInterval, Math.min(maxInterval, newScanInterval));
            
            // Update scan interval if changed significantly
            if (Math.abs(newScanInterval - this.currentScanInterval) > 20) {
                this.updateScanInterval(newScanInterval);
                this.plugin.debugLog("Adaptive scanning: adjusted interval to " + (newScanInterval / 20) + " seconds (TPS: " + String.format("%.2f", currentTPS) + ")");
            }
            
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Error during adaptive optimization", e);
        }
    }
    
    /**
     * Update the scan interval.
     * 
     * @param newInterval The new interval in ticks
     */
    private void updateScanInterval(final int newInterval) {
        this.currentScanInterval = newInterval;
        
        // Restart scanning task with new interval
        if (this.scanTask != null && !this.scanTask.isCancelled()) {
            this.scanTask.cancel();
        }
        
        this.scanTask = new BukkitRunnable() {
            @Override
            public void run() {
                performAutomaticScan();
            }
        }.runTaskTimer(this.plugin, 20, this.currentScanInterval);
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
     * Check if a chunk is on cooldown.
     * 
     * @param chunk The chunk
     * @return true if the chunk is on cooldown
     */
    public boolean isChunkOnCooldown(final Chunk chunk) {
        if (chunk == null) {
            return false;
        }
        
        final String chunkKey = chunk.getWorld().getName() + ":" + chunk.getX() + "," + chunk.getZ();
        final Long cooldownEnd = this.chunkCooldowns.get(chunkKey);
        
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }
    
    /**
     * Manually trigger a scan around a player.
     * 
     * @param player The player
     * @param radius The scan radius
     * @return CompletableFuture for the scan operation
     */
    public CompletableFuture<List<LagScore>> triggerManualScan(final Player player, final int radius) {
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        
        final Chunk playerChunk = player.getLocation().getChunk();
        
        return this.chunkAnalyzer.analyzeChunksInRadius(playerChunk, radius)
            .thenApply(lagScores -> {
                if (lagScores != null && !lagScores.isEmpty()) {
                    // Update visualization for this player
                    this.visualizationManager.updateVisualization(player, lagScores);
                    
                    // Update statistics
                    this.totalChunksAnalyzed += lagScores.size();
                    this.totalScansPerformed++;
                }
                
                return lagScores != null ? lagScores : Collections.emptyList();
            });
    }
    
    /**
     * Manually trigger a server-wide scan of all loaded chunks.
     * 
     * @return CompletableFuture for the scan operation
     */
    public CompletableFuture<List<LagScore>> triggerServerWideScan() {
        final List<String> excludedWorlds = this.configManager.getStringList("scanning.excluded-worlds");
        final List<CompletableFuture<List<LagScore>>> worldScanFutures = new ArrayList<>();
        
        // Scan all worlds
        for (World world : Bukkit.getWorlds()) {
            if (excludedWorlds.contains(world.getName())) {
                continue;
            }
            
            // Get all loaded chunks in this world
            final Chunk[] loadedChunks = world.getLoadedChunks();
            if (loadedChunks.length == 0) {
                continue;
            }
            
            // Analyze chunks in batches to avoid overwhelming the server
            final int batchSize = Math.max(1, Math.min(50, loadedChunks.length / 10));
            final List<CompletableFuture<List<LagScore>>> batchFutures = new ArrayList<>();
            
            for (int i = 0; i < loadedChunks.length; i += batchSize) {
                final int endIndex = Math.min(i + batchSize, loadedChunks.length);
                final List<Chunk> chunkBatch = Arrays.asList(loadedChunks).subList(i, endIndex);
                
                final CompletableFuture<List<LagScore>> batchFuture = this.chunkAnalyzer.analyzeChunks(chunkBatch);
                batchFutures.add(batchFuture);
            }
            
            // Combine all batch results for this world
            final CompletableFuture<List<LagScore>> worldFuture = CompletableFuture.allOf(
                batchFutures.toArray(new CompletableFuture[0])
            ).thenApply(v -> {
                final List<LagScore> worldResults = new ArrayList<>();
                for (CompletableFuture<List<LagScore>> batchFuture : batchFutures) {
                    try {
                        final List<LagScore> batchResults = batchFuture.get();
                        if (batchResults != null) {
                            worldResults.addAll(batchResults);
                        }
                    } catch (Exception e) {
                        this.plugin.getLogger().warning("Error processing batch in world " + world.getName() + ": " + e.getMessage());
                    }
                }
                return worldResults;
            });
            
            worldScanFutures.add(worldFuture);
        }
        
        // Combine all world results
        return CompletableFuture.allOf(
            worldScanFutures.toArray(new CompletableFuture[0])
        ).thenApply(v -> {
            final List<LagScore> allResults = new ArrayList<>();
            for (CompletableFuture<List<LagScore>> worldFuture : worldScanFutures) {
                try {
                    final List<LagScore> worldResults = worldFuture.get();
                    if (worldResults != null) {
                        allResults.addAll(worldResults);
                    }
                } catch (Exception e) {
                    this.plugin.getLogger().warning("Error processing world scan results: " + e.getMessage());
                }
            }
            
            // Update statistics
            if (!allResults.isEmpty()) {
                this.totalChunksAnalyzed += allResults.size();
                this.totalScansPerformed++;
            }
            
            this.plugin.debugLog("Server-wide scan completed. Analyzed " + allResults.size() + " chunks across " + worldScanFutures.size() + " worlds.");
            return allResults;
        });
    }
    
    /**
     * Get scan statistics as a formatted string.
     * 
     * @return The statistics string
     */
    public String getStatistics() {
        return String.format(
            "Scans: %d | Chunks: %d | Avg Time: %.2fs | Interval: %ds | Cache: %d",
            this.totalScansPerformed,
            this.totalChunksAnalyzed,
            this.averageScanTime,
            this.currentScanInterval / 20,
            this.chunkAnalyzer.getCacheSize()
        );
    }
    
    /**
     * Clear all cooldowns and metrics.
     */
    public void clearData() {
        this.chunkCooldowns.clear();
        this.performanceMetrics.clear();
        this.worldScanTimes.clear();
        this.totalScansPerformed = 0;
        this.totalChunksAnalyzed = 0;
        this.averageScanTime = 0.0;
        
        this.plugin.debugLog("Scan scheduler data cleared.");
    }
    
    /**
     * Reload configuration settings.
     */
    public void reloadConfiguration() {
        // Update scan interval from config
        this.currentScanInterval = this.configManager.getScanInterval() * 20;
        
        // Restart scanning with new settings if it was enabled
        if (this.scanningEnabled) {
            this.restartScanning();
        }
    }
    
    /**
     * Start automatic scanning based on configuration.
     */
    public void startAutomaticScanning() {
        if (this.configManager.isAutomaticScanningEnabled()) {
            this.startScanning();
        }
        this.startServerWideScanning();
    }
    
    /**
     * Stop automatic scanning.
     */
    public void stopAutomaticScanning() {
        this.stopScanning();
    }
    
    /**
     * Start server-wide scanning if enabled in configuration.
     */
    public void startServerWideScanning() {
        if (!this.configManager.getBoolean("scanning.server-wide.enabled", false)) {
            return;
        }
        
        if (this.serverScanTask != null && !this.serverScanTask.isCancelled()) {
            return; // Already running
        }
        
        final int intervalSeconds = this.configManager.getInt("scanning.server-wide.interval", 3600);
        final long intervalTicks = intervalSeconds * 20L; // Convert to ticks
        
        this.serverScanTask = new BukkitRunnable() {
            @Override
            public void run() {
                performServerWideScan();
            }
        }.runTaskTimer(this.plugin, intervalTicks, intervalTicks);
        
        this.plugin.getLogger().info("Server-wide scanning started with interval: " + intervalSeconds + " seconds");
    }
    
    /**
     * Stop server-wide scanning.
     */
    public void stopServerWideScanning() {
        if (this.serverScanTask != null && !this.serverScanTask.isCancelled()) {
            this.serverScanTask.cancel();
            this.serverScanTask = null;
            this.plugin.getLogger().info("Server-wide scanning stopped");
        }
    }
    
    /**
     * Restart server-wide scanning with updated configuration.
     */
    public void restartServerWideScanning() {
        this.stopServerWideScanning();
        this.startServerWideScanning();
    }
    
    /**
     * Perform a server-wide scan automatically.
     */
    private void performServerWideScan() {
        if (this.getCurrentTPS() < 15.0) {
            this.plugin.getLogger().warning("Skipping server-wide scan due to low TPS: " + this.getCurrentTPS());
            return;
        }
        
        this.triggerServerWideScan()
            .thenAccept(lagScores -> {
                // Update last scan time
                this.configManager.set("scanning.server-wide.last-scan", System.currentTimeMillis());
                this.configManager.saveConfig();
                
                // Log results
                final long significantChunks = lagScores.stream()
                    .filter(score -> score.getLagLevel().ordinal() >= 2) // High or Critical
                    .count();
                
                if (significantChunks > 0) {
                    this.plugin.getLogger().info(String.format(
                        "Server-wide scan completed. Found %d chunks with high lag out of %d total chunks.",
                        significantChunks, lagScores.size()));
                    
                    // Notify online OPs
                    Bukkit.getOnlinePlayers().stream()
                        .filter(player -> player.hasPermission("tracer.autoscan"))
                        .forEach(player -> {
                            player.sendMessage("§6[Tracer] §eServer-wide scan found " + significantChunks + " high-lag chunks. Use '/tracer teleport' to investigate.");
                        });
                }
            })
            .exceptionally(throwable -> {
                this.plugin.getLogger().warning("Error during automatic server-wide scan: " + throwable.getMessage());
                return null;
            });
    }
    
    /**
     * Check if server-wide scanning is enabled.
     * @return true if server-wide scanning is enabled
     */
    public boolean isServerWideScanningEnabled() {
        return this.configManager.getBoolean("scanning.server-wide.enabled", false);
    }
    
    /**
     * Check if server-wide scanning is currently running.
     * @return true if server-wide scanning task is active
     */
    public boolean isServerWideScanningActive() {
        return this.serverScanTask != null && !this.serverScanTask.isCancelled();
    }
    
    /**
     * Get the server-wide scanning interval in seconds.
     * @return interval in seconds
     */
    public int getServerWideScanInterval() {
        return this.configManager.getInt("scanning.server-wide.interval", 3600);
    }
    
    /**
     * Get the timestamp of the last server-wide scan.
     * @return timestamp in milliseconds, or 0 if never scanned
     */
    public long getLastServerWideScanTime() {
        return (long) this.configManager.getDouble("scanning.server-wide.last-scan", 0.0);
    }
    
    /**
     * Shutdown the scheduler and clean up resources.
     */
    public void shutdown() {
        this.stopScanning();
        this.stopServerWideScanning();
        this.clearData();
    }
}