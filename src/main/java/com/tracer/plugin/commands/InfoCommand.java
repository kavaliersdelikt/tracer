package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Handles the /tracer info command for displaying plugin information.
 */
public final class InfoCommand extends TracerCommand.SubCommand {
    
    public InfoCommand(final TracerPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        sender.sendMessage(ChatColor.GOLD + "=== Tracer Plugin Info ===");
        sender.sendMessage(ChatColor.YELLOW + "Version: " + ChatColor.WHITE + this.plugin.getPluginVersion());
        sender.sendMessage(ChatColor.YELLOW + "Author: " + ChatColor.WHITE + "Kavaliersdelikt");
        sender.sendMessage(ChatColor.YELLOW + "Description: " + ChatColor.WHITE + "Analyze and visualize server performance issues");
        sender.sendMessage(ChatColor.YELLOW + "Support: " + ChatColor.WHITE + "https://kavalier.me for my contact methods if you need help regarding this plugin.");
        
        // Show current status
        final boolean scanningEnabled = this.plugin.getScanScheduler().isScanningEnabled();
        sender.sendMessage(ChatColor.YELLOW + "Auto-scanning: " + 
            (scanningEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        
        if (scanningEnabled) {
            final int interval = this.plugin.getScanScheduler().getCurrentScanInterval() / 20;
            sender.sendMessage(ChatColor.YELLOW + "Scan interval: " + ChatColor.WHITE + interval + " seconds");
        }
        
        // Show server-wide scanning status
        final boolean serverWideScanningEnabled = this.plugin.getScanScheduler().isServerWideScanningEnabled();
        final boolean serverWideScanningActive = this.plugin.getScanScheduler().isServerWideScanningActive();
        sender.sendMessage(ChatColor.YELLOW + "Server-wide scanning: " + 
            (serverWideScanningEnabled ? 
                (serverWideScanningActive ? ChatColor.GREEN + "Active" : ChatColor.YELLOW + "Enabled but not running") : 
                ChatColor.RED + "Disabled"));
        
        if (serverWideScanningEnabled) {
            final int serverInterval = this.plugin.getScanScheduler().getServerWideScanInterval();
            final long lastScan = this.plugin.getScanScheduler().getLastServerWideScanTime();
            sender.sendMessage(ChatColor.YELLOW + "Server scan interval: " + ChatColor.WHITE + (serverInterval / 60) + " minutes");
            
            if (lastScan > 0) {
                final long timeSinceLastScan = (System.currentTimeMillis() - lastScan) / 1000 / 60;
                sender.sendMessage(ChatColor.YELLOW + "Last server scan: " + ChatColor.WHITE + timeSinceLastScan + " minutes ago");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Last server scan: " + ChatColor.WHITE + "Never");
            }
        }
        
        // Show visualization status for players
        if (sender instanceof Player player) {
            final boolean visualizationEnabled = this.plugin.getVisualizationManager().isVisualizationEnabled(player);
            sender.sendMessage(ChatColor.YELLOW + "Your visualization: " + 
                (visualizationEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        }
        
        // Show cache info
        final int cacheSize = this.plugin.getChunkAnalyzer().getCacheSize();
        sender.sendMessage(ChatColor.YELLOW + "Analysis cache: " + ChatColor.WHITE + cacheSize + " entries");
        
        // Show debug mode
        final boolean debugMode = this.plugin.isDebugMode();
        sender.sendMessage(ChatColor.YELLOW + "Debug mode: " + 
            (debugMode ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        
        sender.sendMessage(ChatColor.GOLD + "=========================");
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        return Collections.emptyList();
    }
    
    @Override
    public boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("tracer.info");
    }
    
    @Override
    public String getDescription() {
        return "Display plugin information and status";
    }
    
    @Override
    public String getUsage() {
        return "/tracer info";
    }
}