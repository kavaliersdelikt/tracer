package com.tracer.plugin.visualization;

import com.tracer.plugin.TracerPlugin;
import com.tracer.plugin.config.ConfigManager;
import com.tracer.plugin.analysis.LagLevel;
import com.tracer.plugin.analysis.LagScore;
import lombok.Getter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages visual feedback for lag analysis results.
 * Handles particles, floating text, action bar messages, and chunk highlighting.
 */
public final class VisualizationManager {
    
    private final TracerPlugin plugin;
    private final ConfigManager configManager;
    
    /**
     * Players who have visualization enabled
     */
    @Getter
    private final Set<UUID> visualizationEnabled;
    
    /**
     * Active visualization tasks for cleanup
     */
    private final Map<UUID, BukkitTask> activeTasks;
    
    /**
     * Current visualization data for each player
     */
    private final Map<UUID, List<LagScore>> playerVisualizationData;
    
    /**
     * Particle update task
     */
    private BukkitTask particleTask;
    
    /**
     * Action bar update task
     */
    private BukkitTask actionBarTask;
    
    public VisualizationManager(final TracerPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.visualizationEnabled = ConcurrentHashMap.newKeySet();
        this.activeTasks = new ConcurrentHashMap<>();
        this.playerVisualizationData = new ConcurrentHashMap<>();
        
        this.startVisualizationTasks();
    }
    
    /**
     * Toggle visualization for a player.
     * 
     * @param player The player
     * @return true if visualization is now enabled, false if disabled
     */
    public boolean toggleVisualization(final Player player) {
        if (player == null) {
            return false;
        }
        
        final UUID playerId = player.getUniqueId();
        
        if (this.visualizationEnabled.contains(playerId)) {
            this.disableVisualization(player);
            return false;
        } else {
            this.enableVisualization(player);
            return true;
        }
    }
    
    /**
     * Enable visualization for a player.
     * 
     * @param player The player
     */
    public void enableVisualization(final Player player) {
        if (player == null) {
            return;
        }
        
        final UUID playerId = player.getUniqueId();
        this.visualizationEnabled.add(playerId);
        
        // Send enable message
        final String message = this.configManager.getMessage("messages.visualization.enabled", "Visualization enabled");
        player.sendMessage(ChatColor.GREEN + message);
        
        this.plugin.debugLog("Enabled visualization for player: " + player.getName());
    }
    
    /**
     * Disable visualization for a player.
     * 
     * @param player The player
     */
    public void disableVisualization(final Player player) {
        if (player == null) {
            return;
        }
        
        final UUID playerId = player.getUniqueId();
        this.visualizationEnabled.remove(playerId);
        this.playerVisualizationData.remove(playerId);
        
        // Cancel any active tasks for this player
        final BukkitTask task = this.activeTasks.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        
        // Send disable message
        final String message = this.configManager.getMessage("messages.visualization.disabled", "Visualization disabled");
        player.sendMessage(ChatColor.RED + message);
        
        this.plugin.debugLog("Disabled visualization for player: " + player.getName());
    }
    
    /**
     * Check if a player has visualization enabled.
     * 
     * @param player The player
     * @return true if visualization is enabled
     */
    public boolean isVisualizationEnabled(final Player player) {
        return player != null && this.visualizationEnabled.contains(player.getUniqueId());
    }
    
    /**
     * Update visualization data for a player.
     * 
     * @param player The player
     * @param lagScores The lag scores to visualize
     */
    public void updateVisualization(final Player player, final List<LagScore> lagScores) {
        if (player == null || lagScores == null || !this.isVisualizationEnabled(player)) {
            return;
        }
        
        final UUID playerId = player.getUniqueId();
        this.playerVisualizationData.put(playerId, new ArrayList<>(lagScores));
        
        // Filter scores that should be visualized
        final List<LagScore> visibleScores = lagScores.stream()
            .filter(score -> score.getLagLevel().ordinal() >= this.getMinimumVisualizationLevel().ordinal())
            .toList();
        
        if (visibleScores.isEmpty()) {
            this.sendActionBarMessage(player, this.configManager.getMessage("messages.visualization.no-lag-detected", "No lag detected"));
            return;
        }
        
        // Show particles for high-lag chunks
        if (this.configManager.getBoolean("visualization.particles.enabled", true)) {
            this.showParticlesForScores(player, visibleScores);
        }
        
        // Show floating text
        if (this.configManager.getBoolean("visualization.floating-text.enabled", true)) {
            this.showFloatingTextForScores(player, visibleScores);
        }
        
        // Update action bar
        if (this.configManager.getBoolean("visualization.action-bar.enabled", true)) {
            this.updateActionBarForPlayer(player, visibleScores);
        }
        
        // Highlight chunk borders
        if (this.configManager.getBoolean("visualization.chunk-borders.enabled", false)) {
            this.highlightChunkBorders(player, visibleScores);
        }
    }
    
    /**
     * Show particles for lag scores.
     * 
     * @param player The player to show particles to
     * @param lagScores The lag scores
     */
    private void showParticlesForScores(final Player player, final List<LagScore> lagScores) {
        for (LagScore score : lagScores) {
            this.showParticlesForChunk(player, score);
        }
    }
    
    /**
     * Show particles for a specific chunk.
     * 
     * @param player The player
     * @param score The lag score
     */
    private void showParticlesForChunk(final Player player, final LagScore score) {
        if (score == null || score.getLocation() == null) {
            return;
        }
        
        final Location chunkCenter = score.getLocation().clone().add(8, 64, 8);
        final Particle particle = score.getLagLevel().getParticle();
        final int particleCount = this.getParticleCount(score.getLagLevel());
        
        try {
            // Show particles in a circle around the chunk center
            for (int i = 0; i < particleCount; i++) {
                final double angle = (2 * Math.PI * i) / particleCount;
                final double x = chunkCenter.getX() + Math.cos(angle) * 8;
                final double z = chunkCenter.getZ() + Math.sin(angle) * 8;
                final Location particleLocation = new Location(chunkCenter.getWorld(), x, chunkCenter.getY(), z);
                
                player.spawnParticle(particle, particleLocation, 1, 0, 0, 0, 0);
            }
            
            // Add some particles above the chunk for visibility
            player.spawnParticle(particle, chunkCenter.clone().add(0, 10, 0), 5, 4, 2, 4, 0);
            
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Error showing particles for chunk", e);
        }
    }
    
    /**
     * Show floating text for lag scores.
     * 
     * @param player The player
     * @param lagScores The lag scores
     */
    private void showFloatingTextForScores(final Player player, final List<LagScore> lagScores) {
        // Note: Floating text requires either holograms plugin or armor stands
        // For now, we'll use title/subtitle as an alternative
        
        if (lagScores.isEmpty()) {
            return;
        }
        
        // Find the highest lag score
        final LagScore highestScore = lagScores.stream()
            .max(Comparator.comparingDouble(LagScore::getOverallScore))
            .orElse(null);
        
        if (highestScore != null && highestScore.getLagLevel() != LagLevel.LOW) {
            final String title = this.configManager.getMessage("messages.visualization.floating-text.title", "Lag Level: {level} (Score: {score})")
                .replace("{level}", highestScore.getLagLevel().getDisplayName())
                .replace("{score}", String.format("%.1f", highestScore.getOverallScore()));
            
            final String subtitle = this.configManager.getMessage("messages.visualization.floating-text.subtitle", "Chunk: {x}, {z} in {world}")
                .replace("{x}", String.valueOf(highestScore.getChunkX()))
                .replace("{z}", String.valueOf(highestScore.getChunkZ()))
                .replace("{world}", highestScore.getWorldName());
            
            player.sendTitle(title, subtitle, 10, 40, 10);
        }
    }
    
    /**
     * Update action bar for a player.
     * 
     * @param player The player
     * @param lagScores The lag scores
     */
    private void updateActionBarForPlayer(final Player player, final List<LagScore> lagScores) {
        if (lagScores.isEmpty()) {
            this.sendActionBarMessage(player, this.configManager.getMessage("messages.visualization.action-bar.no-lag", "No lag detected"));
            return;
        }
        
        // Count chunks by lag level
        final Map<LagLevel, Long> levelCounts = lagScores.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                LagScore::getLagLevel,
                java.util.stream.Collectors.counting()
            ));
        
        // Build action bar message
        final StringBuilder message = new StringBuilder();
        message.append(ChatColor.GRAY).append("Lag: ");
        
        for (LagLevel level : LagLevel.values()) {
            final long count = levelCounts.getOrDefault(level, 0L);
            if (count > 0) {
                message.append(level.getChatColor())
                    .append(level.getDisplayName())
                    .append(": ")
                    .append(count)
                    .append(" ");
            }
        }
        
        this.sendActionBarMessage(player, message.toString());
    }
    
    /**
     * Highlight chunk borders for lag scores.
     * 
     * @param player The player
     * @param lagScores The lag scores
     */
    private void highlightChunkBorders(final Player player, final List<LagScore> lagScores) {
        for (LagScore score : lagScores) {
            if (score.getLagLevel() == LagLevel.LOW) {
                continue;
            }
            
            this.highlightChunkBorder(player, score);
        }
    }
    
    /**
     * Highlight the border of a specific chunk.
     * 
     * @param player The player
     * @param score The lag score
     */
    private void highlightChunkBorder(final Player player, final LagScore score) {
        if (score.getLocation() == null) {
            return;
        }
        
        final World world = score.getLocation().getWorld();
        if (world == null) {
            return;
        }
        
        final int chunkX = score.getChunkX() << 4;
        final int chunkZ = score.getChunkZ() << 4;
        final Particle particle = score.getLagLevel().getParticle();
        
        try {
            // Draw border lines with particles
            for (int i = 0; i <= 16; i++) {
                // North and South borders
                final Location north = new Location(world, chunkX + i, player.getLocation().getY() + 1, chunkZ);
                final Location south = new Location(world, chunkX + i, player.getLocation().getY() + 1, chunkZ + 16);
                
                player.spawnParticle(particle, north, 1, 0, 0, 0, 0);
                player.spawnParticle(particle, south, 1, 0, 0, 0, 0);
                
                // East and West borders
                final Location east = new Location(world, chunkX, player.getLocation().getY() + 1, chunkZ + i);
                final Location west = new Location(world, chunkX + 16, player.getLocation().getY() + 1, chunkZ + i);
                
                player.spawnParticle(particle, east, 1, 0, 0, 0, 0);
                player.spawnParticle(particle, west, 1, 0, 0, 0, 0);
            }
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Error highlighting chunk border", e);
        }
    }
    
    /**
     * Send an action bar message to a player.
     * 
     * @param player The player
     * @param message The message
     */
    private void sendActionBarMessage(final Player player, final String message) {
        if (player == null || message == null) {
            return;
        }
        
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
        } catch (Exception e) {
            // Fallback to regular message if action bar fails
            player.sendMessage(message);
        }
    }
    
    /**
     * Get the particle count for a lag level.
     * 
     * @param level The lag level
     * @return The particle count
     */
    private int getParticleCount(final LagLevel level) {
        return switch (level) {
            case LOW -> 8;
            case MEDIUM -> 12;
            case HIGH -> 16;
            case CRITICAL -> 24;
        };
    }
    
    /**
     * Get the minimum lag level that should be visualized.
     * 
     * @return The minimum lag level
     */
    private LagLevel getMinimumVisualizationLevel() {
        final String levelName = this.configManager.getString("visualization.minimum-level", "MEDIUM");
        try {
            return LagLevel.valueOf(levelName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return LagLevel.MEDIUM;
        }
    }
    
    /**
     * Start the visualization tasks.
     */
    private void startVisualizationTasks() {
        // Particle update task
        this.particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateParticlesForAllPlayers();
            }
        }.runTaskTimer(this.plugin, 20, this.configManager.getInt("visualization.particles.update-interval", 20));
        
        // Action bar update task
        this.actionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateActionBarForAllPlayers();
            }
        }.runTaskTimer(this.plugin, 20, this.configManager.getInt("visualization.action-bar.update-interval", 40));
    }
    
    /**
     * Update particles for all players with visualization enabled.
     */
    private void updateParticlesForAllPlayers() {
        for (UUID playerId : this.visualizationEnabled) {
            final Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                final List<LagScore> scores = this.playerVisualizationData.get(playerId);
                if (scores != null && !scores.isEmpty()) {
                    this.showParticlesForScores(player, scores);
                }
            }
        }
    }
    
    /**
     * Update action bar for all players with visualization enabled.
     */
    private void updateActionBarForAllPlayers() {
        for (UUID playerId : this.visualizationEnabled) {
            final Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                final List<LagScore> scores = this.playerVisualizationData.get(playerId);
                if (scores != null) {
                    this.updateActionBarForPlayer(player, scores);
                }
            }
        }
    }
    
    /**
     * Clean up visualization data for a player.
     * 
     * @param player The player
     */
    public void cleanupPlayer(final Player player) {
        if (player == null) {
            return;
        }
        
        final UUID playerId = player.getUniqueId();
        this.visualizationEnabled.remove(playerId);
        this.playerVisualizationData.remove(playerId);
        
        final BukkitTask task = this.activeTasks.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
    
    /**
     * Get the number of players with visualization enabled.
     * 
     * @return The count
     */
    public int getVisualizationPlayerCount() {
        return this.visualizationEnabled.size();
    }
    
    /**
     * Clear all visualizations for all players.
     */
    public void clearAllVisualizations() {
        this.visualizationEnabled.clear();
        this.playerVisualizationData.clear();
        
        // Cancel all active tasks
        for (BukkitTask task : this.activeTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        this.activeTasks.clear();
    }
    
    /**
     * Reload configuration settings.
     */
    public void reloadConfiguration() {
        // Clear all visualization data when configuration is reloaded
        this.playerVisualizationData.clear();
    }
    
    /**
     * Shutdown the visualization manager and clean up resources.
     */
    public void shutdown() {
        // Cancel all tasks
        if (this.particleTask != null && !this.particleTask.isCancelled()) {
            this.particleTask.cancel();
        }
        
        if (this.actionBarTask != null && !this.actionBarTask.isCancelled()) {
            this.actionBarTask.cancel();
        }
        
        for (BukkitTask task : this.activeTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        
        // Clear data
        this.visualizationEnabled.clear();
        this.activeTasks.clear();
        this.playerVisualizationData.clear();
    }
}