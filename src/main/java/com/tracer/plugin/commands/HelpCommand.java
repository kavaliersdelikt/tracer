package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles the /tracer help command for displaying command help.
 */
public final class HelpCommand extends TracerCommand.SubCommand {
    
    private final Map<String, TracerCommand.SubCommand> subCommands;
    
    public HelpCommand(final TracerPlugin plugin, final Map<String, TracerCommand.SubCommand> subCommands) {
        super(plugin);
        this.subCommands = subCommands;
    }
    
    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            this.showGeneralHelp(sender);
        } else {
            final String commandName = args[0].toLowerCase();
            this.showSpecificHelp(sender, commandName);
        }
        
        return true;
    }
    
    /**
     * Show general help with all available commands.
     * 
     * @param sender The command sender
     */
    private void showGeneralHelp(final CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Tracer Help ===");
        sender.sendMessage(ChatColor.YELLOW + "Tracer analyzes and visualizes server performance issues.");
        sender.sendMessage(ChatColor.GRAY + "Use '/tracer help <command>' for detailed information.");
        sender.sendMessage("");
        
        // Group commands by category
        final Map<String, List<Map.Entry<String, TracerCommand.SubCommand>>> categories = new LinkedHashMap<>();
        categories.put("Core Commands", new ArrayList<>());
        categories.put("Admin Commands", new ArrayList<>());
        
        // Categorize commands
        for (Map.Entry<String, TracerCommand.SubCommand> entry : this.subCommands.entrySet()) {
            if (!entry.getValue().hasPermission(sender)) {
                continue;
            }
            
            final String commandName = entry.getKey();
            if (this.isAdminCommand(commandName)) {
                categories.get("Admin Commands").add(entry);
            } else {
                categories.get("Core Commands").add(entry);
            }
        }
        
        // Display categorized commands
        for (Map.Entry<String, List<Map.Entry<String, TracerCommand.SubCommand>>> category : categories.entrySet()) {
            final List<Map.Entry<String, TracerCommand.SubCommand>> commands = category.getValue();
            if (commands.isEmpty()) {
                continue;
            }
            
            sender.sendMessage(ChatColor.AQUA + category.getKey() + ":");
            
            commands.stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    final String commandName = entry.getKey();
                    final TracerCommand.SubCommand subCommand = entry.getValue();
                    
                    sender.sendMessage(ChatColor.WHITE + "  /tracer " + commandName + 
                        ChatColor.GRAY + " - " + subCommand.getDescription());
                });
            
            sender.sendMessage("");
        }
        
        // Show additional information
        sender.sendMessage(ChatColor.YELLOW + "Quick Start:");
        sender.sendMessage(ChatColor.GRAY + "  1. Use '/tracer scan' to analyze chunks around you");
        sender.sendMessage(ChatColor.GRAY + "  2. Use '/tracer toggle' to enable visual indicators");
        sender.sendMessage(ChatColor.GRAY + "  3. Use '/tracer info' to check plugin status");
        
        sender.sendMessage(ChatColor.GOLD + "===================");
    }
    
    /**
     * Show help for a specific command.
     * 
     * @param sender The command sender
     * @param commandName The command name
     */
    private void showSpecificHelp(final CommandSender sender, final String commandName) {
        final TracerCommand.SubCommand subCommand = this.subCommands.get(commandName);
        
        if (subCommand == null) {
            this.sendError(sender, "Unknown command: " + commandName);
            sender.sendMessage(ChatColor.GRAY + "Use '/tracer help' to see all available commands.");
            return;
        }
        
        if (!subCommand.hasPermission(sender)) {
            this.sendError(sender, "You don't have permission to use this command.");
            return;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== Command Help: " + commandName + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Description: " + ChatColor.WHITE + subCommand.getDescription());
        sender.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.WHITE + subCommand.getUsage());
        
        // Show cooldown if applicable
        final int cooldown = subCommand.getCooldown();
        if (cooldown > 0) {
            sender.sendMessage(ChatColor.YELLOW + "Cooldown: " + ChatColor.WHITE + cooldown + " seconds");
        }
        
        // Show specific help for each command
        switch (commandName) {
            case "scan":
                this.showScanHelp(sender);
                break;
            case "toggle":
                this.showToggleHelp(sender);
                break;
            case "clear":
                this.showClearHelp(sender);
                break;
            case "debug":
                this.showDebugHelp(sender);
                break;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=========================");
    }
    
    /**
     * Show detailed help for the scan command.
     * 
     * @param sender The command sender
     */
    private void showScanHelp(final CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Examples:");
        sender.sendMessage(ChatColor.WHITE + "  /tracer scan" + ChatColor.GRAY + " - Scan with default radius");
        sender.sendMessage(ChatColor.WHITE + "  /tracer scan 5" + ChatColor.GRAY + " - Scan 5 chunks in each direction");
        sender.sendMessage(ChatColor.WHITE + "  /tracer scan 1" + ChatColor.GRAY + " - Scan only the current chunk");
        
        sender.sendMessage(ChatColor.YELLOW + "Notes:");
        sender.sendMessage(ChatColor.GRAY + "  • Larger radius = more chunks analyzed = longer scan time");
        sender.sendMessage(ChatColor.GRAY + "  • Results show chunks with significant lag sources");
        sender.sendMessage(ChatColor.GRAY + "  • Use '/tracer toggle' to see visual indicators");
    }
    
    /**
     * Show detailed help for the toggle command.
     * 
     * @param sender The command sender
     */
    private void showToggleHelp(final CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Visual Indicators:");
        sender.sendMessage(ChatColor.GRAY + "  • Particles around high-lag chunks");
        sender.sendMessage(ChatColor.GRAY + "  • Action bar messages with lag summary");
        sender.sendMessage(ChatColor.GRAY + "  • Title/subtitle notifications for critical lag");
        sender.sendMessage(ChatColor.GRAY + "  • Optional chunk border highlighting");
    }
    
    /**
     * Show detailed help for the clear command.
     * 
     * @param sender The command sender
     */
    private void showClearHelp(final CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Clear Options:");
        sender.sendMessage(ChatColor.WHITE + "  cache" + ChatColor.GRAY + " - Clear analysis cache (forces re-scan)");
        sender.sendMessage(ChatColor.WHITE + "  stats" + ChatColor.GRAY + " - Clear scan statistics and performance data");
        sender.sendMessage(ChatColor.WHITE + "  all" + ChatColor.GRAY + " - Clear everything (cache + stats)");
    }
    
    /**
     * Show detailed help for the debug command.
     * 
     * @param sender The command sender
     */
    private void showDebugHelp(final CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Debug Options:");
        sender.sendMessage(ChatColor.WHITE + "  on/enable" + ChatColor.GRAY + " - Enable debug mode");
        sender.sendMessage(ChatColor.WHITE + "  off/disable" + ChatColor.GRAY + " - Disable debug mode");
        sender.sendMessage(ChatColor.WHITE + "  status" + ChatColor.GRAY + " - Show current debug status");
        
        sender.sendMessage(ChatColor.YELLOW + "Debug Mode:");
        sender.sendMessage(ChatColor.GRAY + "  • Logs detailed analysis information");
        sender.sendMessage(ChatColor.GRAY + "  • Shows performance metrics");
        sender.sendMessage(ChatColor.GRAY + "  • Useful for troubleshooting issues");
    }
    
    /**
     * Check if a command is an admin command.
     * 
     * @param commandName The command name
     * @return true if it's an admin command
     */
    private boolean isAdminCommand(final String commandName) {
        return Arrays.asList("reload", "clear", "debug").contains(commandName);
    }
    
    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            return this.subCommands.entrySet().stream()
                .filter(entry -> entry.getValue().hasPermission(sender))
                .map(Map.Entry::getKey)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("tracer.help");
    }
    
    @Override
    public String getDescription() {
        return "Show help information for commands";
    }
    
    @Override
    public String getUsage() {
        return "/tracer help [command]";
    }
}