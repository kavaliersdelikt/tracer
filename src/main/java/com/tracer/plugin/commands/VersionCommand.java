package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Handles the /tracer version command for displaying plugin version information.
 */
public final class VersionCommand extends TracerCommand.SubCommand {
    
    public VersionCommand(final TracerPlugin plugin) {
        super(plugin);
    }
    
    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        sender.sendMessage(ChatColor.GOLD + "=== Tracer Version ===");
        sender.sendMessage(ChatColor.YELLOW + "Version: " + ChatColor.WHITE + this.plugin.getPluginVersion());
        sender.sendMessage(ChatColor.YELLOW + "Author: " + ChatColor.WHITE + "Kavaliersdelikt");
        sender.sendMessage(ChatColor.GRAY + "Get support at: https://kavalier.me");
        
        return true;
    }
    
    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        return Collections.emptyList();
    }
    
    @Override
    public boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("tracer.version");
    }
    
    @Override
    public String getDescription() {
        return "Display plugin version information";
    }
    
    @Override
    public String getUsage() {
        return "/tracer version";
    }
}