package com.tracer.plugin;

import com.tracer.plugin.analysis.ChunkAnalyzer;
import com.tracer.plugin.commands.TracerCommand;
import com.tracer.plugin.config.ConfigManager;
import com.tracer.plugin.scheduler.ScanScheduler;
import com.tracer.plugin.storage.DataStorage;
import com.tracer.plugin.visualization.VisualizationManager;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for Tracer - A comprehensive Minecraft plugin for analyzing
 * and visualizing server performance issues.
 * 
 * This plugin scans chunks for performance-intensive elements and provides
 * visual feedback to help administrators identify lag sources.
 * 
 * @author Kavaliersdelikt
 * @version 1.0.0
 */
public final class TracerPlugin extends JavaPlugin {
    
    @Getter
    private static TracerPlugin instance;
    
    @Getter
    private ConfigManager configManager;
    
    @Getter
    private VisualizationManager visualizationManager;
    
    @Getter
    private ScanScheduler scanScheduler;
    
    @Getter
    private DataStorage dataStorage;
    
    @Getter
    private ChunkAnalyzer chunkAnalyzer;
    
    @Override
    public void onEnable() {
        // Set static instance
        instance = this;
        
        // Initialize plugin components
        this.initializeComponents();
        
        // Register commands
        this.registerCommands();
        
        // Start background tasks
        this.startBackgroundTasks();
        
        // Log successful startup
        this.getLogger().info("Tracer plugin has been enabled successfully!");
        this.getLogger().info("Version: " + this.getDescription().getVersion());
        this.getLogger().info("Ready to analyze server performance.");
    }
    
    @Override
    public void onDisable() {
        // Stop background tasks
        if (this.scanScheduler != null) {
            this.scanScheduler.shutdown();
        }
        
        // Clear all visualizations
        if (this.visualizationManager != null) {
            this.visualizationManager.clearAllVisualizations();
        }
        
        // Shutdown data storage
        if (this.dataStorage != null) {
            this.dataStorage.shutdown();
        }
        
        // Save any pending data
        if (this.configManager != null) {
            this.configManager.saveConfig();
        }
        
        // Clear static instance
        instance = null;
        
        this.getLogger().info("Tracer plugin has been disabled.");
    }
    
    /**
     * Initialize all plugin components in the correct order.
     */
    private void initializeComponents() {
        try {
            // Initialize components
            this.configManager = new ConfigManager(this);
            this.dataStorage = new DataStorage(this);
            this.chunkAnalyzer = new ChunkAnalyzer(this);
            this.visualizationManager = new VisualizationManager(this);
            this.scanScheduler = new ScanScheduler(this);
            
        } catch (Exception e) {
            this.getLogger().severe("Failed to initialize plugin components: " + e.getMessage());
            e.printStackTrace();
            this.getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    /**
     * Register all plugin commands.
     */
    private void registerCommands() {
        try {
            // Register main command
            final TracerCommand mainCommand = new TracerCommand(this);
            this.getCommand("tracer").setExecutor(mainCommand);
            this.getCommand("tracer").setTabCompleter(mainCommand);
            
            this.getLogger().info("Commands registered successfully.");
            
        } catch (Exception e) {
            this.getLogger().severe("Failed to register commands: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Start background tasks and schedulers.
     */
    private void startBackgroundTasks() {
        try {
            // Start automatic scanning if enabled
            if (this.configManager.isScanningEnabled()) {
                this.scanScheduler.startAutomaticScanning();
                this.getLogger().info("Automatic scanning started.");
            }
            
        } catch (Exception e) {
            this.getLogger().severe("Failed to start background tasks: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Reload the plugin configuration and restart necessary components.
     */
    public void reloadPlugin() {
        try {
            this.getLogger().info("Reloading Tracer configuration...");
            
            // Stop current tasks
            this.scanScheduler.stopAutomaticScanning();
            this.visualizationManager.clearAllVisualizations();
            
            // Reload components
            this.configManager.reloadConfig();
            this.dataStorage.reloadConfiguration();
            this.chunkAnalyzer.reloadConfiguration();
            this.visualizationManager.reloadConfiguration();
            this.scanScheduler.reloadConfiguration();
            
            // Restart tasks if needed
            if (this.configManager.isScanningEnabled()) {
                this.scanScheduler.startAutomaticScanning();
            }
            
            this.getLogger().info("Tracer configuration reloaded successfully.");
            
        } catch (Exception e) {
            this.getLogger().severe("Failed to reload plugin: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get the plugin version.
     * 
     * @return The plugin version string
     */
    public String getPluginVersion() {
        return this.getDescription().getVersion();
    }
    
    /**
     * Check if the plugin is in debug mode.
     * 
     * @return true if debug mode is enabled
     */
    public boolean isDebugMode() {
        return this.configManager != null && this.configManager.isDebugMode();
    }
    
    /**
     * Log a debug message if debug mode is enabled.
     * 
     * @param message The debug message to log
     */
    public void debugLog(final String message) {
        if (this.isDebugMode()) {
            this.getLogger().info("[DEBUG] " + message);
        }
    }
}