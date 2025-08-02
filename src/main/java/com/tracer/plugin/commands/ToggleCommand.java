package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import com.tracer.plugin.visualization.VisualizationManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Handles the /tracer toggle command for enabling/disabling visualization.
 */
public final class ToggleCommand extends TracerCommand.SubCommand {
    
    private final VisualizationManager visualizationManager;
    
    public ToggleCommand(final TracerPlugin plugin) {
        super(plugin);
        this.visualizationManager = plugin.getVisualizationManager();
    }
    
    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        final Player player = this.getPlayer(sender);
        if (player == null) {
            this.sendError(sender, this.configManager.getMessage("messages.player-only", "This command can only be used by players"));
            return false;
        }
        
        final boolean enabled = this.visualizationManager.toggleVisualization(player);
        
        if (enabled) {
            this.sendSuccess(sender, this.configManager.getMessage("messages.visualization.enabled", "Visualization enabled"));
        } else {
            this.sendMessage(sender, ChatColor.RED + this.configManager.getMessage("messages.visualization.disabled", "Visualization disabled"));
        }
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        return Collections.emptyList();
    }
    
    @Override
    public boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("tracer.toggle");
    }
    
    @Override
    public String getDescription() {
        return "Toggle lag visualization on/off";
    }
    
    @Override
    public String getUsage() {
        return "/tracer toggle";
    }
}