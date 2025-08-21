package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import com.tracer.plugin.config.ConfigManager;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main command handler for the Tracer plugin.
 * Routes commands to appropriate sub-command handlers.
 */
public final class TracerCommand implements CommandExecutor, TabCompleter {
    
    private final TracerPlugin plugin;
    private final ConfigManager configManager;
    
    /**
     * Map of sub-commands to their handlers
     */
    @Getter
    private final Map<String, SubCommand> subCommands;
    
    /**
     * Command cooldowns for players
     */
    private final Map<UUID, Long> commandCooldowns;
    
    public TracerCommand(final TracerPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.subCommands = new HashMap<>();
        this.commandCooldowns = new HashMap<>();
        
        this.initializeSubCommands();
    }
    
    /**
     * Initialize sub-commands.
     */
    private void initializeSubCommands() {
        this.subCommands.put("scan", new ScanCommand(this.plugin));
        this.subCommands.put("toggle", new ToggleCommand(this.plugin));
        this.subCommands.put("info", new InfoCommand(this.plugin));
        this.subCommands.put("stats", new StatsCommand(this.plugin));
        this.subCommands.put("reload", new ReloadCommand(this.plugin));
        this.subCommands.put("clear", new ClearCommand(this.plugin));
        this.subCommands.put("debug", new DebugCommand(this.plugin));
        this.subCommands.put("teleport", new TeleportCommand(this.plugin));
        this.subCommands.put("autoscan", new AutoScanCommand(this.plugin));
        this.subCommands.put("version", new VersionCommand(this.plugin));
        this.subCommands.put("help", new HelpCommand(this.plugin, this.subCommands));
    }
    
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        // Check basic permission
        if (!sender.hasPermission("tracer.use")) {
            sender.sendMessage(ChatColor.RED + this.configManager.getMessage("messages.no-permission", "You don't have permission to use this command"));
            return true;
        }
        
        // Check command cooldown for players
        if (sender instanceof Player player) {
            if (this.isOnCooldown(player)) {
                final long remainingTime = this.getRemainingCooldown(player);
                final String message = this.configManager.getMessage("messages.command-cooldown", "Please wait before using this command again")
                    .replace("{time}", String.valueOf(remainingTime));
                player.sendMessage(ChatColor.RED + message);
                return true;
            }
        }
        
        // Handle no arguments - show help
        if (args.length == 0) {
            this.showMainHelp(sender);
            return true;
        }
        
        // Get sub-command
        final String subCommandName = args[0].toLowerCase();
        final SubCommand subCommand = this.subCommands.get(subCommandName);
        
        if (subCommand == null) {
            sender.sendMessage(ChatColor.RED + this.configManager.getMessage("messages.unknown-command", "Unknown command")
                .replace("{command}", subCommandName));
            this.showMainHelp(sender);
            return true;
        }
        
        // Check sub-command permission
        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage(ChatColor.RED + this.configManager.getMessage("messages.no-permission", "You don't have permission to use this command"));
            return true;
        }
        
        // Execute sub-command
        try {
            final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            final boolean success = subCommand.execute(sender, subArgs);
            
            // Apply cooldown for players if command was successful
            if (success && sender instanceof Player player) {
                this.applyCooldown(player, subCommand.getCooldown());
            }
            
            return success;
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "An error occurred while executing the command.");
            this.plugin.getLogger().severe("Error executing command '" + subCommandName + "': " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        // Check basic permission
        if (!sender.hasPermission("tracer.use")) {
            return Collections.emptyList();
        }
        
        // First argument - sub-command names
        if (args.length == 1) {
            return this.subCommands.entrySet().stream()
                .filter(entry -> entry.getValue().hasPermission(sender))
                .map(Map.Entry::getKey)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
        }
        
        // Delegate to sub-command tab completion
        if (args.length > 1) {
            final String subCommandName = args[0].toLowerCase();
            final SubCommand subCommand = this.subCommands.get(subCommandName);
            
            if (subCommand != null && subCommand.hasPermission(sender)) {
                final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.tabComplete(sender, subArgs);
            }
        }
        
        return Collections.emptyList();
    }
    
    /**
     * Show the main help message.
     * 
     * @param sender The command sender
     */
    private void showMainHelp(final CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Tracer Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "Use /tracer help for detailed command information.");
        sender.sendMessage(ChatColor.GRAY + "Available commands:");
        
        // Show available commands based on permissions
        this.subCommands.entrySet().stream()
            .filter(entry -> entry.getValue().hasPermission(sender))
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                final SubCommand subCommand = entry.getValue();
                sender.sendMessage(ChatColor.AQUA + "/tracer " + entry.getKey() + 
                    ChatColor.GRAY + " - " + subCommand.getDescription());
            });
    }
    
    /**
     * Check if a player is on command cooldown.
     * 
     * @param player The player
     * @return true if the player is on cooldown
     */
    private boolean isOnCooldown(final Player player) {
        final UUID playerId = player.getUniqueId();
        final Long cooldownEnd = this.commandCooldowns.get(playerId);
        
        if (cooldownEnd == null) {
            return false;
        }
        
        if (System.currentTimeMillis() >= cooldownEnd) {
            this.commandCooldowns.remove(playerId);
            return false;
        }
        
        return true;
    }
    
    /**
     * Get the remaining cooldown time for a player.
     * 
     * @param player The player
     * @return The remaining time in seconds
     */
    private long getRemainingCooldown(final Player player) {
        final UUID playerId = player.getUniqueId();
        final Long cooldownEnd = this.commandCooldowns.get(playerId);
        
        if (cooldownEnd == null) {
            return 0;
        }
        
        final long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }
    
    /**
     * Apply a cooldown to a player.
     * 
     * @param player The player
     * @param cooldownSeconds The cooldown duration in seconds
     */
    private void applyCooldown(final Player player, final int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return;
        }
        
        // Bypass cooldown for players with admin permission
        if (player.hasPermission("tracer.admin")) {
            return;
        }
        
        final UUID playerId = player.getUniqueId();
        final long cooldownEnd = System.currentTimeMillis() + (cooldownSeconds * 1000L);
        this.commandCooldowns.put(playerId, cooldownEnd);
    }
    
    /**
     * Clear cooldowns for a player.
     * 
     * @param player The player
     */
    public void clearCooldown(final Player player) {
        if (player != null) {
            this.commandCooldowns.remove(player.getUniqueId());
        }
    }
    
    /**
     * Clear all cooldowns.
     */
    public void clearAllCooldowns() {
        this.commandCooldowns.clear();
    }
    
    /**
     * Get the number of players with active cooldowns.
     * 
     * @return The count
     */
    public int getActiveCooldownCount() {
        // Clean up expired cooldowns
        final long currentTime = System.currentTimeMillis();
        this.commandCooldowns.entrySet().removeIf(entry -> currentTime >= entry.getValue());
        
        return this.commandCooldowns.size();
    }
    
    /**
     * Abstract base class for sub-commands.
     */
    public abstract static class SubCommand {
        protected final TracerPlugin plugin;
        protected final ConfigManager configManager;
        
        public SubCommand(final TracerPlugin plugin) {
            this.plugin = plugin;
            this.configManager = plugin.getConfigManager();
        }
        
        /**
         * Execute the sub-command.
         * 
         * @param sender The command sender
         * @param args The command arguments
         * @return true if the command was executed successfully
         */
        public abstract boolean execute(final CommandSender sender, final String[] args);
        
        /**
         * Get tab completion suggestions.
         * 
         * @param sender The command sender
         * @param args The command arguments
         * @return List of suggestions
         */
        public List<String> tabComplete(final CommandSender sender, final String[] args) {
            return Collections.emptyList();
        }
        
        /**
         * Check if the sender has permission to use this command.
         * 
         * @param sender The command sender
         * @return true if the sender has permission
         */
        public abstract boolean hasPermission(final CommandSender sender);
        
        /**
         * Get the command description.
         * 
         * @return The description
         */
        public abstract String getDescription();
        
        /**
         * Get the command usage.
         * 
         * @return The usage string
         */
        public abstract String getUsage();
        
        /**
         * Get the command cooldown in seconds.
         * 
         * @return The cooldown duration
         */
        public int getCooldown() {
            return 0; // No cooldown by default
        }
        
        /**
         * Check if the sender is a player.
         * 
         * @param sender The command sender
         * @return true if the sender is a player
         */
        protected boolean isPlayer(final CommandSender sender) {
            return sender instanceof Player;
        }
        
        /**
         * Get the player from the sender, or null if not a player.
         * 
         * @param sender The command sender
         * @return The player, or null
         */
        protected Player getPlayer(final CommandSender sender) {
            return this.isPlayer(sender) ? (Player) sender : null;
        }
        
        /**
         * Send a message with color code translation.
         * 
         * @param sender The command sender
         * @param message The message
         */
        protected void sendMessage(final CommandSender sender, final String message) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
        
        /**
         * Send an error message.
         * 
         * @param sender The command sender
         * @param message The error message
         */
        protected void sendError(final CommandSender sender, final String message) {
            sender.sendMessage(ChatColor.RED + message);
        }
        
        /**
         * Send a success message.
         * 
         * @param sender The command sender
         * @param message The success message
         */
        protected void sendSuccess(final CommandSender sender, final String message) {
            sender.sendMessage(ChatColor.GREEN + message);
        }
    }
}