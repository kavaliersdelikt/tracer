package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /tracer clear command for clearing caches and data.
 */
public final class ClearCommand extends TracerCommand.SubCommand {
    
    public ClearCommand(final TracerPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            this.sendError(sender, "Usage: " + this.getUsage());
            return false;
        }
        
        final String target = args[0].toLowerCase();
        
        switch (target) {
            case "cache":
                this.clearCache(sender);
                break;
            case "stats":
                this.clearStats(sender);
                break;
            case "all":
                this.clearAll(sender);
                break;
            default:
                this.sendError(sender, "Invalid target. Use: cache, stats, or all");
                return false;
        }
        
        return true;
    }
    
    /**
     * Clear the analysis cache.
     * 
     * @param sender The command sender
     */
    private void clearCache(final CommandSender sender) {
        final int cacheSize = this.plugin.getChunkAnalyzer().getCacheSize();
        this.plugin.getChunkAnalyzer().clearCache();
        
        this.sendSuccess(sender, "Cleared analysis cache (" + cacheSize + " entries)");
        this.plugin.getLogger().info("Analysis cache cleared by " + sender.getName());
    }
    
    /**
     * Clear scan statistics.
     * 
     * @param sender The command sender
     */
    private void clearStats(final CommandSender sender) {
        this.plugin.getScanScheduler().clearData();
        
        this.sendSuccess(sender, "Cleared scan statistics and performance data");
        this.plugin.getLogger().info("Scan statistics cleared by " + sender.getName());
    }
    
    /**
     * Clear all data.
     * 
     * @param sender The command sender
     */
    private void clearAll(final CommandSender sender) {
        final int cacheSize = this.plugin.getChunkAnalyzer().getCacheSize();
        
        this.plugin.getChunkAnalyzer().clearCache();
        this.plugin.getScanScheduler().clearData();
        
        this.sendSuccess(sender, "Cleared all plugin data (cache: " + cacheSize + " entries, statistics, performance data)");
        this.plugin.getLogger().info("All plugin data cleared by " + sender.getName());
    }
    
    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            final List<String> options = Arrays.asList("cache", "stats", "all");
            return options.stream()
                .filter(option -> option.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("tracer.clear");
    }
    
    @Override
    public String getDescription() {
        return "Clear plugin caches and data";
    }
    
    @Override
    public String getUsage() {
        return "/tracer clear <cache|stats|all>";
    }
}