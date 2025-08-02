package com.tracer.plugin.commands;

import com.tracer.plugin.TracerPlugin;
import com.tracer.plugin.analysis.ChunkAnalyzer;
import com.tracer.plugin.analysis.LagLevel;
import com.tracer.plugin.analysis.LagScore;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles the /tracer teleport command for teleporting to high-lag chunks.
 */
public final class TeleportCommand extends TracerCommand.SubCommand {
    
    private final ChunkAnalyzer chunkAnalyzer;
    private static final int ITEMS_PER_PAGE = 10;
    
    public TeleportCommand(final TracerPlugin plugin) {
        super(plugin);
        this.chunkAnalyzer = plugin.getChunkAnalyzer();
    }
    
    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        final Player player = this.getPlayer(sender);
        if (player == null) {
            this.sendError(sender, this.configManager.getMessage("messages.player-only", "This command can only be used by players."));
            return false;
        }
        
        // Check for specific coordinates teleport
        if (args.length >= 3) {
            return this.teleportToCoordinates(player, args);
        }
        
        // Parse page number
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) {
                    page = 1;
                }
            } catch (NumberFormatException e) {
                this.sendError(sender, "Invalid page number. Usage: /tracer teleport [page]");
                return false;
            }
        }
        
        this.showHighLagChunks(player, page);
        return true;
    }
    
    /**
     * Teleport to specific coordinates.
     */
    private boolean teleportToCoordinates(final Player player, final String[] args) {
        try {
            final String worldName = args[0];
            final int chunkX = Integer.parseInt(args[1]);
            final int chunkZ = Integer.parseInt(args[2]);
            
            final World world = Bukkit.getWorld(worldName);
            if (world == null) {
                this.sendError(player, "World '" + worldName + "' not found.");
                return false;
            }
            
            // Calculate block coordinates from chunk coordinates
            final int blockX = chunkX * 16 + 8;
            final int blockZ = chunkZ * 16 + 8;
            final int blockY = world.getHighestBlockYAt(blockX, blockZ) + 1;
            
            final Location teleportLocation = new Location(world, blockX, blockY, blockZ);
            player.teleport(teleportLocation);
            
            this.sendSuccess(player, String.format("Teleported to chunk %d, %d in %s (Block: %d, %d, %d)", 
                chunkX, chunkZ, worldName, blockX, blockY, blockZ));
            
            // Show lag information for this chunk
            final LagScore lagScore = this.plugin.getDataStorage().getLagScore(worldName, chunkX, chunkZ);
            if (lagScore != null) {
                final LagLevel lagLevel = lagScore.getLagLevel();
                this.sendMessage(player, ChatColor.YELLOW + "Chunk Lag Info: " + 
                    lagLevel.getChatColor() + lagLevel.getDisplayName() + ChatColor.YELLOW + " (Score: " + 
                    String.format("%.1f", lagScore.getOverallScore()) + ")");
            } else {
                this.sendMessage(player, ChatColor.GRAY + "No lag data available for this chunk. Run a scan first.");
            }
            
            return true;
        } catch (NumberFormatException e) {
            this.sendError(player, "Invalid coordinates. Usage: /tracer teleport <world> <chunkX> <chunkZ>");
            return false;
        }
    }
    
    /**
     * Show high lag chunks with pagination.
     */
    private void showHighLagChunks(final Player player, final int page) {
        // Get all cached lag scores from DataStorage (includes all scans: player and server-wide)
        // Filter for chunks with significant lag (MEDIUM or higher) and recent data
        final long maxAge = this.configManager.getCacheDuration() * 1000L; // Convert to milliseconds
        final long currentTime = System.currentTimeMillis();
        
        final List<LagScore> highLagChunks = this.plugin.getDataStorage().getAllCachedScores().values().stream()
            .filter(score -> score.getLagLevel().ordinal() >= LagLevel.MEDIUM.ordinal())
            .filter(score -> (currentTime - score.getTimestamp()) <= maxAge) // Only show recent scans
            .sorted((a, b) -> Double.compare(b.getOverallScore(), a.getOverallScore()))
            .collect(Collectors.toList());
        
        if (highLagChunks.isEmpty()) {
            this.sendMessage(player, ChatColor.GREEN + "No high-lag chunks found. This could mean:");
            this.sendMessage(player, ChatColor.GRAY + "  • No scans have been performed recently");
            this.sendMessage(player, ChatColor.GRAY + "  • All previously lagging chunks have been fixed");
            this.sendMessage(player, ChatColor.GRAY + "  • Run '/tracer scan' or '/tracer scan server' to update data");
            return;
        }
        
        // Calculate pagination
        final int totalPages = (int) Math.ceil((double) highLagChunks.size() / ITEMS_PER_PAGE);
        final int startIndex = (page - 1) * ITEMS_PER_PAGE;
        final int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, highLagChunks.size());
        
        if (startIndex >= highLagChunks.size()) {
            this.sendError(player, "Page " + page + " does not exist. Total pages: " + totalPages);
            return;
        }
        
        // Header
        this.sendMessage(player, ChatColor.GOLD + "=== High Lag Chunks (Page " + page + "/" + totalPages + ") ===");
        this.sendMessage(player, ChatColor.GRAY + "Click on coordinates to teleport!");
        
        // Show chunks for this page
        for (int i = startIndex; i < endIndex; i++) {
            final LagScore score = highLagChunks.get(i);
            this.sendClickableChunkInfo(player, score, i + 1);
        }
        
        // Footer with navigation
        this.sendNavigationFooter(player, page, totalPages);
    }
    
    /**
     * Send clickable chunk information.
     */
    private void sendClickableChunkInfo(final Player player, final LagScore score, final int rank) {
        final String coordinates = score.getChunkX() + ", " + score.getChunkZ();
        final String teleportCommand = "/tracer teleport " + score.getWorldName() + " " + 
            score.getChunkX() + " " + score.getChunkZ();
        
        // Create clickable coordinates
        final TextComponent coordComponent = new TextComponent("[" + coordinates + "]");
        coordComponent.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        coordComponent.setBold(true);
        coordComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, teleportCommand));
        coordComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new ComponentBuilder("Click to teleport to chunk " + coordinates + " in " + score.getWorldName())
                .color(net.md_5.bungee.api.ChatColor.YELLOW).create()));
        
        // Create the full message with proper color formatting
        final LagLevel lagLevel = score.getLagLevel();
        final TextComponent message = new TextComponent(String.format("%d. ", rank));
        
        // Add colored lag level
        final TextComponent lagLevelComponent = new TextComponent(lagLevel.getDisplayName());
        lagLevelComponent.setColor(net.md_5.bungee.api.ChatColor.valueOf(lagLevel.getChatColor().name()));
        lagLevelComponent.setBold(true);
        
        // Add the rest of the message
        final TextComponent restMessage = new TextComponent(String.format(" in %s - Score: %.1f", 
            score.getWorldName(),
            score.getOverallScore()));
        
        message.addExtra(lagLevelComponent);
        message.addExtra(restMessage);
        
        // Add lag sources
        final String lagSources = score.getLagSources().isEmpty() ? "Unknown" : 
            String.join(", ", score.getLagSources());
        final TextComponent sourcesComponent = new TextComponent(" (" + lagSources + ")");
        sourcesComponent.setColor(net.md_5.bungee.api.ChatColor.GRAY);
        
        // Combine components
        final TextComponent fullMessage = new TextComponent("");
        fullMessage.addExtra(message);
        fullMessage.addExtra(" ");
        fullMessage.addExtra(coordComponent);
        fullMessage.addExtra(sourcesComponent);
        
        player.spigot().sendMessage(fullMessage);
    }
    
    /**
     * Send navigation footer.
     */
    private void sendNavigationFooter(final Player player, final int currentPage, final int totalPages) {
        final TextComponent footer = new TextComponent("");
        
        // Previous page
        if (currentPage > 1) {
            final TextComponent prevButton = new TextComponent("[◀ Previous]");
            prevButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            prevButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/tracer teleport " + (currentPage - 1)));
            prevButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("Go to page " + (currentPage - 1))
                    .color(net.md_5.bungee.api.ChatColor.YELLOW).create()));
            footer.addExtra(prevButton);
        }
        
        // Page info
        final TextComponent pageInfo = new TextComponent(" Page " + currentPage + "/" + totalPages + " ");
        pageInfo.setColor(net.md_5.bungee.api.ChatColor.GRAY);
        footer.addExtra(pageInfo);
        
        // Next page
        if (currentPage < totalPages) {
            final TextComponent nextButton = new TextComponent("[Next ▶]");
            nextButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            nextButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/tracer teleport " + (currentPage + 1)));
            nextButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("Go to page " + (currentPage + 1))
                    .color(net.md_5.bungee.api.ChatColor.YELLOW).create()));
            footer.addExtra(nextButton);
        }
        
        player.spigot().sendMessage(footer);
    }
    
    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            // Page numbers or world names
            final List<String> completions = new ArrayList<>();
            completions.add("1");
            completions.add("2");
            completions.add("3");
            
            // Add world names
            for (World world : Bukkit.getWorlds()) {
                completions.add(world.getName());
            }
            
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("tracer.teleport");
    }
    
    @Override
    public String getDescription() {
        return "Teleport to high-lag chunks";
    }
    
    @Override
    public String getUsage() {
        return "/tracer teleport [page] or /tracer teleport <world> <chunkX> <chunkZ>";
    }
}