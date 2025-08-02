package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Handles the /tracer reload command for reloading plugin configuration.
 */
public final class ReloadCommand extends TracerCommand.SubCommand {
    
    public ReloadCommand(final TracerPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        try {
            // Reload the plugin
            this.plugin.reloadPlugin();
            
            this.sendSuccess(sender, this.configManager.getMessage("messages.reload.success", "Plugin configuration reloaded successfully."));
            this.plugin.getLogger().info("Plugin reloaded by " + sender.getName());
            
        } catch (Exception e) {
            this.sendError(sender, this.configManager.getMessage("messages.reload.error", "Failed to reload plugin configuration: {error}")
                .replace("{error}", e.getMessage()));
            this.plugin.getLogger().severe("Error reloading plugin: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        return Collections.emptyList();
    }
    
    @Override
    public boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("tracer.reload");
    }
    
    @Override
    public String getDescription() {
        return "Reload plugin configuration";
    }
    
    @Override
    public String getUsage() {
        return "/tracer reload";
    }
}