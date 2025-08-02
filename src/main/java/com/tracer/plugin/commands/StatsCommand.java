package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import com.tracer.plugin.scheduler.ScanScheduler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Handles the /tracer stats command for displaying scan statistics.
 */
public final class StatsCommand extends TracerCommand.SubCommand {
    
    private final ScanScheduler scanScheduler;
    
    public StatsCommand(final TracerPlugin plugin) {
        super(plugin);
        this.scanScheduler = plugin.getScanScheduler();
    }
    
    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        sender.sendMessage(ChatColor.GOLD + "=== Tracer Statistics ===");
        
        // Scan statistics
        sender.sendMessage(ChatColor.YELLOW + "Total scans performed: " + ChatColor.WHITE + this.scanScheduler.getTotalScansPerformed());
        sender.sendMessage(ChatColor.YELLOW + "Total chunks analyzed: " + ChatColor.WHITE + this.scanScheduler.getTotalChunksAnalyzed());
        sender.sendMessage(ChatColor.YELLOW + "Average scan time: " + ChatColor.WHITE + String.format("%.2f", this.scanScheduler.getAverageScanTime()) + "s");
        
        // Current status
        final boolean scanningEnabled = this.scanScheduler.isScanningEnabled();
        sender.sendMessage(ChatColor.YELLOW + "Auto-scanning: " + 
            (scanningEnabled ? ChatColor.GREEN + "Active" : ChatColor.RED + "Inactive"));
        
        if (scanningEnabled) {
            final int currentInterval = this.scanScheduler.getCurrentScanInterval() / 20;
            sender.sendMessage(ChatColor.YELLOW + "Current scan interval: " + ChatColor.WHITE + currentInterval + "s");
        }
        
        // Server-wide scanning status
        final boolean serverWideScanningEnabled = this.scanScheduler.isServerWideScanningEnabled();
        final boolean serverWideScanningActive = this.scanScheduler.isServerWideScanningActive();
        sender.sendMessage(ChatColor.YELLOW + "Server-wide scanning: " + 
            (serverWideScanningEnabled ? 
                (serverWideScanningActive ? ChatColor.GREEN + "Active" : ChatColor.YELLOW + "Enabled") : 
                ChatColor.RED + "Disabled"));
        
        if (serverWideScanningEnabled) {
            final int serverInterval = this.scanScheduler.getServerWideScanInterval();
            final long lastScan = this.scanScheduler.getLastServerWideScanTime();
            sender.sendMessage(ChatColor.YELLOW + "Server scan interval: " + ChatColor.WHITE + (serverInterval / 60) + " minutes");
            
            if (lastScan > 0) {
                final long timeSinceLastScan = (System.currentTimeMillis() - lastScan) / 1000 / 60;
                sender.sendMessage(ChatColor.YELLOW + "Last server scan: " + ChatColor.WHITE + timeSinceLastScan + " minutes ago");
                
                // Calculate next scan time
                final long nextScanIn = (serverInterval - ((System.currentTimeMillis() - lastScan) / 1000)) / 60;
                if (nextScanIn > 0 && serverWideScanningActive) {
                    sender.sendMessage(ChatColor.YELLOW + "Next server scan: " + ChatColor.WHITE + nextScanIn + " minutes");
                }
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Last server scan: " + ChatColor.WHITE + "Never");
            }
        }
        
        // Cache statistics
        final int cacheSize = this.plugin.getChunkAnalyzer().getCacheSize();
        sender.sendMessage(ChatColor.YELLOW + "Analysis cache size: " + ChatColor.WHITE + cacheSize + " entries");
        
        // Visualization statistics
        final int visualizationPlayers = this.plugin.getVisualizationManager().getVisualizationPlayerCount();
        sender.sendMessage(ChatColor.YELLOW + "Players with visualization: " + ChatColor.WHITE + visualizationPlayers);
        
        // Performance info
        final Runtime runtime = Runtime.getRuntime();
        final long totalMemory = runtime.totalMemory() / 1024 / 1024;
        final long freeMemory = runtime.freeMemory() / 1024 / 1024;
        final long usedMemory = totalMemory - freeMemory;
        
        sender.sendMessage(ChatColor.YELLOW + "Memory usage: " + ChatColor.WHITE + usedMemory + "MB / " + totalMemory + "MB");
        
        // Detailed statistics
        sender.sendMessage(ChatColor.GRAY + this.scanScheduler.getStatistics());
        
        sender.sendMessage(ChatColor.GOLD + "=========================");
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        return Collections.emptyList();
    }
    
    @Override
    public boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("tracer.stats");
    }
    
    @Override
    public String getDescription() {
        return "Display scan statistics and performance info";
    }
    
    @Override
    public String getUsage() {
        return "/tracer stats";
    }
}