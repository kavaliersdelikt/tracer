package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /tracer debug command for toggling debug mode.
 */
public final class DebugCommand extends TracerCommand.SubCommand {
    
    public DebugCommand(final TracerPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            // Toggle debug mode
            final boolean newDebugMode = !this.plugin.isDebugMode();
            this.configManager.set("general.debug", newDebugMode);
            this.configManager.saveConfig();
            
            final String message = newDebugMode ? "Debug mode enabled" : "Debug mode disabled";
            this.sendMessage(sender, (newDebugMode ? ChatColor.GREEN : ChatColor.RED) + message);
            
            this.plugin.getLogger().info("Debug mode " + (newDebugMode ? "enabled" : "disabled") + " by " + sender.getName());
            
        } else {
            final String action = args[0].toLowerCase();
            
            switch (action) {
                case "on":
                case "enable":
                case "true":
                    this.setDebugMode(sender, true);
                    break;
                case "off":
                case "disable":
                case "false":
                    this.setDebugMode(sender, false);
                    break;
                case "status":
                    this.showDebugStatus(sender);
                    break;
                default:
                    this.sendError(sender, "Invalid option. Use: on, off, or status");
                    return false;
            }
        }
        
        return true;
    }
    
    /**
     * Set debug mode to a specific state.
     * 
     * @param sender The command sender
     * @param enabled Whether to enable debug mode
     */
    private void setDebugMode(final CommandSender sender, final boolean enabled) {
        this.configManager.set("general.debug", enabled);
        this.configManager.saveConfig();
        
        final String message = enabled ? "Debug mode enabled" : "Debug mode disabled";
        this.sendMessage(sender, (enabled ? ChatColor.GREEN : ChatColor.RED) + message);
        
        this.plugin.getLogger().info("Debug mode " + (enabled ? "enabled" : "disabled") + " by " + sender.getName());
    }
    
    /**
     * Show current debug status.
     * 
     * @param sender The command sender
     */
    private void showDebugStatus(final CommandSender sender) {
        final boolean debugMode = this.plugin.isDebugMode();
        
        sender.sendMessage(ChatColor.YELLOW + "Debug mode: " + 
            (debugMode ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        
        if (debugMode) {
            sender.sendMessage(ChatColor.GRAY + "Debug messages will be logged to console");
            sender.sendMessage(ChatColor.GRAY + "Additional performance information will be collected");
            
            // Show additional debug information
            final boolean scanningEnabled = this.plugin.getScanScheduler().isScanningEnabled();
            final boolean serverWideScanningEnabled = this.plugin.getScanScheduler().isServerWideScanningEnabled();
            final boolean serverWideScanningActive = this.plugin.getScanScheduler().isServerWideScanningActive();
            
            sender.sendMessage(ChatColor.GRAY + "Auto-scanning: " + (scanningEnabled ? "Active" : "Inactive"));
            sender.sendMessage(ChatColor.GRAY + "Server-wide scanning: " + 
                (serverWideScanningEnabled ? 
                    (serverWideScanningActive ? "Active" : "Enabled but not running") : 
                    "Disabled"));
            
            final int cacheSize = this.plugin.getChunkAnalyzer().getCacheSize();
            sender.sendMessage(ChatColor.GRAY + "Cache entries: " + cacheSize);
        }
    }
    
    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            final List<String> options = Arrays.asList("on", "off", "enable", "disable", "status", "true", "false");
            return options.stream()
                .filter(option -> option.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("tracer.debug");
    }
    
    @Override
    public String getDescription() {
        return "Toggle debug mode on/off";
    }
    
    @Override
    public String getUsage() {
        return "/tracer debug [on|off|status]";
    }
}