package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import com.tracer.plugin.scheduler.ScanScheduler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles the /tracer autoscan command for managing automatic scanning settings.
 */
public final class AutoScanCommand extends TracerCommand.SubCommand {
    
    private final ScanScheduler scanScheduler;
    
    public AutoScanCommand(final TracerPlugin plugin) {
        super(plugin);
        this.scanScheduler = plugin.getScanScheduler();
    }
    
    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            this.showAutoScanStatus(sender);
            return true;
        }
        
        final String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "enable":
                return this.enableAutoScan(sender);
            case "disable":
                return this.disableAutoScan(sender);
            case "interval":
                return this.setInterval(sender, args);
            case "server":
                return this.handleServerScan(sender, args);
            case "status":
                return this.showDetailedStatus(sender);
            default:
                this.sendError(sender, "Unknown sub-command. Use: enable, disable, interval, server, status");
                return false;
        }
    }
    
    /**
     * Show basic auto-scan status.
     */
    private void showAutoScanStatus(final CommandSender sender) {
        final boolean enabled = this.scanScheduler.isScanningEnabled();
        final int interval = this.scanScheduler.getCurrentScanInterval() / 20; // Convert ticks to seconds
        final boolean serverScanEnabled = this.configManager.getBoolean("scanning.server-wide.enabled", false);
        final int serverScanInterval = this.configManager.getInt("scanning.server-wide.interval", 3600);
        
        this.sendMessage(sender, ChatColor.GOLD + "=== Auto-Scan Status ===");
        this.sendMessage(sender, ChatColor.YELLOW + "Player-based scanning: " + 
            (enabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        
        if (enabled) {
            this.sendMessage(sender, ChatColor.GRAY + "  Interval: " + interval + " seconds");
        }
        
        this.sendMessage(sender, ChatColor.YELLOW + "Server-wide scanning: " + 
            (serverScanEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        
        if (serverScanEnabled) {
            this.sendMessage(sender, ChatColor.GRAY + "  Interval: " + (serverScanInterval / 60) + " minutes");
        }
    }
    
    /**
     * Show detailed status information.
     */
    private boolean showDetailedStatus(final CommandSender sender) {
        this.showAutoScanStatus(sender);
        
        // Show statistics
        final String stats = this.scanScheduler.getStatistics();
        this.sendMessage(sender, ChatColor.YELLOW + "Statistics: " + ChatColor.WHITE + stats);
        
        // Show next scan times if applicable
        final boolean enabled = this.scanScheduler.isScanningEnabled();
        if (enabled) {
            final int interval = this.scanScheduler.getCurrentScanInterval() / 20;
            this.sendMessage(sender, ChatColor.GRAY + "Next player scan: ~" + interval + " seconds");
        }
        
        final boolean serverScanEnabled = this.configManager.getBoolean("scanning.server-wide.enabled", false);
        if (serverScanEnabled) {
            final long lastServerScan = this.configManager.getInt("scanning.server-wide.last-scan", 0);
            final int serverScanInterval = this.configManager.getInt("scanning.server-wide.interval", 3600);
            final long nextServerScan = lastServerScan + (serverScanInterval * 1000L);
            final long timeUntilNext = Math.max(0, nextServerScan - System.currentTimeMillis());
            
            this.sendMessage(sender, ChatColor.GRAY + "Next server scan: " + 
                (timeUntilNext / 60000) + " minutes");
        }
        
        return true;
    }
    
    /**
     * Enable auto-scanning.
     */
    private boolean enableAutoScan(final CommandSender sender) {
        if (this.scanScheduler.isScanningEnabled()) {
            this.sendMessage(sender, ChatColor.YELLOW + "Auto-scanning is already enabled.");
            return true;
        }
        
        this.scanScheduler.startScanning();
        this.sendSuccess(sender, "Auto-scanning enabled.");
        return true;
    }
    
    /**
     * Disable auto-scanning.
     */
    private boolean disableAutoScan(final CommandSender sender) {
        if (!this.scanScheduler.isScanningEnabled()) {
            this.sendMessage(sender, ChatColor.YELLOW + "Auto-scanning is already disabled.");
            return true;
        }
        
        this.scanScheduler.stopScanning();
        this.sendSuccess(sender, "Auto-scanning disabled.");
        return true;
    }
    
    /**
     * Set the auto-scan interval.
     */
    private boolean setInterval(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            this.sendError(sender, "Usage: /tracer autoscan interval <seconds>");
            return false;
        }
        
        try {
            final int seconds = Integer.parseInt(args[1]);
            
            if (seconds < 10) {
                this.sendError(sender, "Interval must be at least 10 seconds.");
                return false;
            }
            
            if (seconds > 3600) {
                this.sendError(sender, "Interval cannot exceed 3600 seconds (1 hour).");
                return false;
            }
            
            // Update configuration
            this.configManager.set("scanning.interval", seconds);
            this.configManager.saveConfig();
            
            // Restart scanning with new interval
            if (this.scanScheduler.isScanningEnabled()) {
                this.scanScheduler.restartScanning();
            }
            
            this.sendSuccess(sender, "Auto-scan interval set to " + seconds + " seconds.");
            return true;
            
        } catch (NumberFormatException e) {
            this.sendError(sender, "Invalid number format. Please enter a valid number of seconds.");
            return false;
        }
    }
    
    /**
     * Handle server-wide scan commands.
     */
    private boolean handleServerScan(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            this.sendError(sender, "Usage: /tracer autoscan server <enable|disable|interval|trigger>");
            return false;
        }
        
        final String action = args[1].toLowerCase();
        
        switch (action) {
            case "enable":
                return this.enableServerScan(sender);
            case "disable":
                return this.disableServerScan(sender);
            case "interval":
                return this.setServerScanInterval(sender, args);
            case "trigger":
                return this.triggerServerScan(sender);
            default:
                this.sendError(sender, "Unknown action. Use: enable, disable, interval, trigger");
                return false;
        }
    }
    
    /**
     * Enable server-wide scanning.
     */
    private boolean enableServerScan(final CommandSender sender) {
        this.configManager.set("scanning.server-wide.enabled", true);
        this.configManager.saveConfig();
        
        this.sendSuccess(sender, "Server-wide scanning enabled.");
        return true;
    }
    
    /**
     * Disable server-wide scanning.
     */
    private boolean disableServerScan(final CommandSender sender) {
        this.configManager.set("scanning.server-wide.enabled", false);
        this.configManager.saveConfig();
        
        this.sendSuccess(sender, "Server-wide scanning disabled.");
        return true;
    }
    
    /**
     * Set server-wide scan interval.
     */
    private boolean setServerScanInterval(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            this.sendError(sender, "Usage: /tracer autoscan server interval <minutes>");
            return false;
        }
        
        try {
            final int minutes = Integer.parseInt(args[2]);
            
            if (minutes < 5) {
                this.sendError(sender, "Server scan interval must be at least 5 minutes.");
                return false;
            }
            
            if (minutes > 1440) { // 24 hours
                this.sendError(sender, "Server scan interval cannot exceed 1440 minutes (24 hours).");
                return false;
            }
            
            final int seconds = minutes * 60;
            this.configManager.set("scanning.server-wide.interval", seconds);
            this.configManager.saveConfig();
            
            this.sendSuccess(sender, "Server-wide scan interval set to " + minutes + " minutes.");
            return true;
            
        } catch (NumberFormatException e) {
            this.sendError(sender, "Invalid number format. Please enter a valid number of minutes.");
            return false;
        }
    }
    
    /**
     * Manually trigger a server-wide scan.
     */
    private boolean triggerServerScan(final CommandSender sender) {
        this.sendMessage(sender, ChatColor.YELLOW + "Triggering server-wide scan...");
        
        final long startTime = System.currentTimeMillis();
        
        this.scanScheduler.triggerServerWideScan()
            .thenAccept(lagScores -> {
                final long endTime = System.currentTimeMillis();
                final double scanTime = (endTime - startTime) / 1000.0;
                
                // Update last scan time
                this.configManager.set("scanning.server-wide.last-scan", System.currentTimeMillis());
                this.configManager.saveConfig();
                
                // Send results summary
                final long significantChunks = lagScores.stream()
                    .filter(score -> score.getLagLevel().ordinal() >= 1) // Medium or higher
                    .count();
                
                this.sendSuccess(sender, String.format(
                    "Server-wide scan completed in %.2f seconds. Analyzed %d chunks, found %d with significant lag.",
                    scanTime, lagScores.size(), significantChunks));
                
                if (significantChunks > 0) {
                    this.sendMessage(sender, ChatColor.AQUA + "Use '/tracer teleport' to navigate to high-lag chunks.");
                }
            })
            .exceptionally(throwable -> {
                this.sendError(sender, "Error during server-wide scan: " + throwable.getMessage());
                this.plugin.getLogger().warning("Error during manual server-wide scan: " + throwable.getMessage());
                return null;
            });
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            return Arrays.asList("enable", "disable", "interval", "server", "status")
                .stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("interval")) {
                return Arrays.asList("30", "60", "120", "300", "600");
            }
            
            if (args[0].equalsIgnoreCase("server")) {
                return Arrays.asList("enable", "disable", "interval", "trigger")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("server") && args[1].equalsIgnoreCase("interval")) {
            return Arrays.asList("5", "10", "15", "30", "60", "120");
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("tracer.autoscan");
    }
    
    @Override
    public String getDescription() {
        return "Manage automatic scanning settings";
    }
    
    @Override
    public String getUsage() {
        return "/tracer autoscan <enable|disable|interval|server|status>";
    }
}