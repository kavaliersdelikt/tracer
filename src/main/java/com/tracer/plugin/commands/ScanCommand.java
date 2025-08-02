package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import com.tracer.plugin.analysis.ChunkAnalyzer;
import com.tracer.plugin.analysis.LagLevel;
import com.tracer.plugin.analysis.LagScore;
import com.tracer.plugin.scheduler.ScanScheduler;
import com.tracer.plugin.visualization.VisualizationManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles the /tracer scan command for manual chunk analysis.
 */
public final class ScanCommand extends TracerCommand.SubCommand {
    
    private final ChunkAnalyzer chunkAnalyzer;
    private final ScanScheduler scanScheduler;
    private final VisualizationManager visualizationManager;
    
    public ScanCommand(final TracerPlugin plugin) {
        super(plugin);
        this.chunkAnalyzer = plugin.getChunkAnalyzer();
        this.scanScheduler = plugin.getScanScheduler();
        this.visualizationManager = plugin.getVisualizationManager();
    }
    
    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        final Player player = this.getPlayer(sender);
        if (player == null) {
            this.sendError(sender, this.configManager.getMessage("messages.player-only", "This command can only be used by players."));
            return false;
        }
        
        // Check for server-wide scan
        boolean serverWide = false;
        int scanRadius = this.configManager.getDefaultScanRadius();
        
        if (args.length > 0) {
            // Check if first argument is "server" or "all"
            if (args[0].equalsIgnoreCase("server") || args[0].equalsIgnoreCase("all")) {
                if (!sender.hasPermission("tracer.scan.server")) {
                    this.sendError(sender, this.configManager.getMessage("messages.no-permission", "You don't have permission to perform server-wide scans."));
                    return false;
                }
                serverWide = true;
            } else {
                // Parse scan radius
                try {
                    scanRadius = Integer.parseInt(args[0]);
                    
                    // Validate radius
                    final int maxRadius = this.configManager.getMaxScanRadius();
                    if (scanRadius < 1) {
                        this.sendError(sender, this.configManager.getMessage("messages.scan.invalid-radius-min", "Scan radius must be at least 1."));
                        return false;
                    }
                    if (scanRadius > maxRadius) {
                        this.sendError(sender, this.configManager.getMessage("messages.scan.invalid-radius-max", "Scan radius too large. Maximum allowed: {max}")
                            .replace("{max}", String.valueOf(maxRadius)));
                        return false;
                    }
                    
                } catch (NumberFormatException e) {
                    this.sendError(sender, this.configManager.getMessage("messages.scan.invalid-radius-format", "Invalid radius format. Please enter a number or 'server' for server-wide scan."));
                    return false;
                }
            }
        }
        
        // Check if player is in a scannable world (only for radius-based scans)
        if (!serverWide) {
            final List<String> excludedWorlds = this.configManager.getStringList("scanning.excluded-worlds");
            if (excludedWorlds.contains(player.getWorld().getName())) {
                this.sendError(sender, this.configManager.getMessage("messages.scan.world-excluded", "Scanning is not allowed in this world."));
                return false;
            }
        }
        
        // Send scan start message
        final String startMessage;
        if (serverWide) {
            startMessage = this.configManager.getMessage("messages.scan.started-server", "Starting server-wide scan...");
        } else {
            startMessage = this.configManager.getMessage("messages.scan.started", "Starting scan with radius {radius}...")
                .replace("{radius}", String.valueOf(scanRadius));
        }
        this.sendMessage(sender, ChatColor.YELLOW + startMessage);
        
        // Perform the scan
        final long startTime = System.currentTimeMillis();
        final int finalScanRadius = scanRadius;
        final boolean finalServerWide = serverWide;
        
        if (serverWide) {
            this.scanScheduler.triggerServerWideScan()
                .thenAccept(lagScores -> {
                    final long endTime = System.currentTimeMillis();
                    final double scanTime = (endTime - startTime) / 1000.0;
                    
                    // Process and display results
                    this.displayServerWideScanResults(player, lagScores, scanTime);
                })
                .exceptionally(throwable -> {
                    this.sendError(sender, this.configManager.getMessage("messages.scan.error", "An error occurred during scan: {error}")
                        .replace("{error}", throwable.getMessage()));
                    this.plugin.getLogger().warning("Error during server-wide scan: " + throwable.getMessage());
                    return null;
                });
        } else {
            this.scanScheduler.triggerManualScan(player, scanRadius)
                .thenAccept(lagScores -> {
                    final long endTime = System.currentTimeMillis();
                    final double scanTime = (endTime - startTime) / 1000.0;
                    
                    // Process and display results
                    this.displayScanResults(player, lagScores, finalScanRadius, scanTime);
                })
                .exceptionally(throwable -> {
                    this.sendError(sender, this.configManager.getMessage("messages.scan.error", "An error occurred during scan: {error}")
                        .replace("{error}", throwable.getMessage()));
                    this.plugin.getLogger().warning("Error during manual scan: " + throwable.getMessage());
                    return null;
                });
        }
        
        return true;
    }
    
    /**
     * Display the scan results to the player.
     * 
     * @param player The player
     * @param lagScores The scan results
     * @param scanRadius The scan radius
     * @param scanTime The time taken for the scan
     */
    private void displayScanResults(final Player player, final List<LagScore> lagScores, final int scanRadius, final double scanTime) {
        if (lagScores == null || lagScores.isEmpty()) {
            this.sendMessage(player, ChatColor.GREEN + this.configManager.getMessage("messages.scan.no-chunks", "No chunks found to analyze."));
            return;
        }
        
        // Filter out low-lag chunks for display
        final List<LagScore> significantLagScores = lagScores.stream()
            .filter(score -> score.getLagLevel() != LagLevel.LOW)
            .sorted((a, b) -> Double.compare(b.getOverallScore(), a.getOverallScore()))
            .toList();
        
        // Send header
        player.sendMessage(ChatColor.GOLD + "=== Lag Analysis Results ===");
        player.sendMessage(ChatColor.GRAY + "Scanned " + lagScores.size() + " chunks in " + 
            String.format("%.2f", scanTime) + " seconds (radius: " + scanRadius + ")");
        
        if (significantLagScores.isEmpty()) {
            player.sendMessage(ChatColor.GREEN + this.configManager.getMessage("messages.scan.no-lag-detected", "No significant lag detected in scanned chunks."));
        } else {
            // Count chunks by lag level
            final Map<LagLevel, Long> levelCounts = significantLagScores.stream()
                .collect(Collectors.groupingBy(LagScore::getLagLevel, Collectors.counting()));
            
            // Display summary
            player.sendMessage(ChatColor.YELLOW + "Lag detected in " + significantLagScores.size() + " chunks:");
            for (LagLevel level : LagLevel.values()) {
                final long count = levelCounts.getOrDefault(level, 0L);
                if (count > 0) {
                    player.sendMessage(level.getChatColor() + "  " + level.getDisplayName() + ": " + count + " chunks");
                }
            }
            
            // Display top lag sources
            player.sendMessage(ChatColor.YELLOW + "Top lag sources:");
            final int maxDisplay = Math.min(5, significantLagScores.size());
            
            for (int i = 0; i < maxDisplay; i++) {
                final LagScore score = significantLagScores.get(i);
                this.displayLagScore(player, score, i + 1);
            }
            
            if (significantLagScores.size() > maxDisplay) {
                player.sendMessage(ChatColor.GRAY + "... and " + (significantLagScores.size() - maxDisplay) + " more");
            }
        }
        
        // Display overall statistics
        this.displayOverallStatistics(player, lagScores);
        
        // Suggest actions
        this.suggestActions(player, significantLagScores);
        
        player.sendMessage(ChatColor.GOLD + "=========================");
    }
    
    /**
     * Display server-wide scan results.
     * 
     * @param player The player
     * @param lagScores The scan results
     * @param scanTime The time taken for the scan
     */
    private void displayServerWideScanResults(final Player player, final List<LagScore> lagScores, final double scanTime) {
        if (lagScores == null || lagScores.isEmpty()) {
            this.sendMessage(player, ChatColor.GREEN + this.configManager.getMessage("messages.scan.no-chunks", "No chunks found to analyze."));
            return;
        }
        
        // Filter out low-lag chunks for display
        final List<LagScore> significantLagScores = lagScores.stream()
            .filter(score -> score.getLagLevel() != LagLevel.LOW)
            .sorted((a, b) -> Double.compare(b.getOverallScore(), a.getOverallScore()))
            .toList();
        
        // Send header
        player.sendMessage(ChatColor.GOLD + "=== Server-Wide Lag Analysis Results ===");
        player.sendMessage(ChatColor.GRAY + "Scanned " + lagScores.size() + " chunks across all worlds in " + 
            String.format("%.2f", scanTime) + " seconds");
        
        if (significantLagScores.isEmpty()) {
            player.sendMessage(ChatColor.GREEN + this.configManager.getMessage("messages.scan.no-lag-detected", "No significant lag detected in any chunks."));
        } else {
            // Count chunks by lag level
            final Map<LagLevel, Long> levelCounts = significantLagScores.stream()
                .collect(Collectors.groupingBy(LagScore::getLagLevel, Collectors.counting()));
            
            // Count chunks by world
            final Map<String, Long> worldCounts = significantLagScores.stream()
                .collect(Collectors.groupingBy(LagScore::getWorldName, Collectors.counting()));
            
            // Display summary
            player.sendMessage(ChatColor.YELLOW + "Lag detected in " + significantLagScores.size() + " chunks:");
            for (LagLevel level : LagLevel.values()) {
                final long count = levelCounts.getOrDefault(level, 0L);
                if (count > 0) {
                    player.sendMessage(level.getChatColor() + "  " + level.getDisplayName() + ": " + count + " chunks");
                }
            }
            
            // Display world distribution
            player.sendMessage(ChatColor.YELLOW + "Distribution by world:");
            worldCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10) // Show top 10 worlds
                .forEach(entry -> {
                    player.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " + entry.getValue() + " chunks");
                });
            
            // Display top lag sources
            player.sendMessage(ChatColor.YELLOW + "Top lag sources:");
            final int maxDisplay = Math.min(10, significantLagScores.size()); // Show more for server-wide
            
            for (int i = 0; i < maxDisplay; i++) {
                final LagScore score = significantLagScores.get(i);
                this.displayLagScore(player, score, i + 1);
            }
            
            if (significantLagScores.size() > maxDisplay) {
                player.sendMessage(ChatColor.GRAY + "... and " + (significantLagScores.size() - maxDisplay) + " more");
                player.sendMessage(ChatColor.AQUA + "Use '/tracer teleport' to navigate to high-lag chunks");
            }
        }
        
        // Display overall statistics
        this.displayOverallStatistics(player, lagScores);
        
        // Suggest actions
        this.suggestActions(player, significantLagScores);
        
        player.sendMessage(ChatColor.GOLD + "======================================");
    }
    
    /**
     * Display information about a specific lag score.
     * 
     * @param player The player
     * @param score The lag score
     * @param rank The rank of this score
     */
    private void displayLagScore(final Player player, final LagScore score, final int rank) {
        final String rankStr = ChatColor.WHITE + "#" + rank + ". ";
        final String location = ChatColor.AQUA + "(" + score.getChunkX() + ", " + score.getChunkZ() + ")";
        final String lagLevel = score.getLagLevel().getChatColor() + score.getLagLevel().getDisplayName();
        final String lagScore = ChatColor.YELLOW + String.format("%.1f", score.getOverallScore());
        
        player.sendMessage(rankStr + location + " " + lagLevel + " " + ChatColor.GRAY + "(" + lagScore + ")");
        
        // Show details if available
        if (score.getLagSources() != null && !score.getLagSources().isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "    " + String.join(", ", score.getLagSources()));
        }
    }
    
    /**
     * Display overall statistics.
     * 
     * @param player The player
     * @param lagScores All lag scores
     */
    private void displayOverallStatistics(final Player player, final List<LagScore> lagScores) {
        // Calculate statistics
        final double averageScore = lagScores.stream()
            .mapToDouble(LagScore::getOverallScore)
            .average()
            .orElse(0.0);
        
        final double maxScore = lagScores.stream()
            .mapToDouble(LagScore::getOverallScore)
            .max()
            .orElse(0.0);
        
        final int totalEntities = lagScores.stream()
            .mapToInt(LagScore::getEntityCount)
            .sum();
        
        final int totalTileEntities = lagScores.stream()
            .mapToInt(LagScore::getTileEntityCount)
            .sum();
        
        final int totalRedstone = lagScores.stream()
            .mapToInt(LagScore::getRedstoneCount)
            .sum();
        
        // Display statistics
        player.sendMessage(ChatColor.GRAY + "Statistics:");
        player.sendMessage(ChatColor.GRAY + "  Average lag score: " + ChatColor.WHITE + String.format("%.2f", averageScore));
        player.sendMessage(ChatColor.GRAY + "  Highest lag score: " + ChatColor.WHITE + String.format("%.2f", maxScore));
        player.sendMessage(ChatColor.GRAY + "  Total entities: " + ChatColor.WHITE + totalEntities);
        player.sendMessage(ChatColor.GRAY + "  Total tile entities: " + ChatColor.WHITE + totalTileEntities);
        player.sendMessage(ChatColor.GRAY + "  Total redstone: " + ChatColor.WHITE + totalRedstone);
    }
    
    /**
     * Suggest actions based on scan results.
     * 
     * @param player The player
     * @param significantLagScores Lag scores with significant lag
     */
    private void suggestActions(final Player player, final List<LagScore> significantLagScores) {
        if (significantLagScores.isEmpty()) {
            return;
        }
        
        player.sendMessage(ChatColor.YELLOW + "Suggestions:");
        
        // Check for high entity counts
        final boolean hasHighEntities = significantLagScores.stream()
            .anyMatch(score -> score.getEntityCount() > this.configManager.getEntityWarningThreshold());
        
        if (hasHighEntities) {
            player.sendMessage(ChatColor.GRAY + "  • Consider reducing entity farms or using entity limits");
        }
        
        // Check for high tile entity counts
        final boolean hasHighTileEntities = significantLagScores.stream()
            .anyMatch(score -> score.getTileEntityCount() > this.configManager.getTileEntityWarningThreshold());
        
        if (hasHighTileEntities) {
            player.sendMessage(ChatColor.GRAY + "  • Optimize storage systems and reduce hopper chains");
        }
        
        // Check for high redstone counts
        final boolean hasHighRedstone = significantLagScores.stream()
            .anyMatch(score -> score.getRedstoneCount() > this.configManager.getRedstoneWarningThreshold());
        
        if (hasHighRedstone) {
            player.sendMessage(ChatColor.GRAY + "  • Simplify redstone contraptions and reduce clock circuits");
        }
        
        // Suggest visualization
        if (!this.visualizationManager.isVisualizationEnabled(player)) {
            player.sendMessage(ChatColor.GRAY + "  • Use '/tracer toggle' to enable visual indicators");
        }
    }
    
    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            final List<String> completions = new ArrayList<>(Arrays.asList("1", "2", "3", "5", "10", "15", "20"));
            
            // Add server-wide options if player has permission
            if (sender.hasPermission("tracer.scan.server")) {
                completions.add("server");
                completions.add("all");
            }
            
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("tracer.scan");
    }
    
    @Override
    public String getDescription() {
        return "Scan chunks around you for lag sources";
    }
    
    @Override
    public String getUsage() {
        return "/tracer scan [radius|server|all]";
    }
    
    @Override
    public int getCooldown() {
        return this.configManager.getInt("commands.scan.cooldown", 10);
    }
}