package com.tracer.plugin.config;

import com.tracer.plugin.TracerPlugin;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages plugin configuration including loading, saving, and providing
 * convenient access to configuration values.
 */
public final class ConfigManager {
    
    private final TracerPlugin plugin;
    private FileConfiguration config;
    
    @Getter
    private boolean debugMode;
    
    @Getter
    private boolean scanningEnabled;
    
    @Getter
    private int scanInterval;
    
    @Getter
    private int scanRadius;
    
    @Getter
    private int maxChunksPerTick;
    
    @Getter
    private boolean asyncScanning;
    
    @Getter
    private int chunkCooldown;
    
    // Threshold values
    @Getter
    private int entityWarningThreshold;
    
    @Getter
    private int entityCriticalThreshold;
    
    @Getter
    private int tileEntityWarningThreshold;
    
    @Getter
    private int tileEntityCriticalThreshold;
    
    @Getter
    private int redstoneWarningThreshold;
    
    @Getter
    private int redstoneCriticalThreshold;
    
    @Getter
    private int overallWarningThreshold;
    
    @Getter
    private int overallCriticalThreshold;
    
    @Getter
    private double tpsWarningThreshold;
    
    @Getter
    private double tpsCriticalThreshold;
    
    // Visualization settings
    @Getter
    private boolean particlesEnabled;
    
    @Getter
    private boolean floatingTextEnabled;
    
    @Getter
    private boolean actionBarEnabled;
    
    @Getter
    private int particleDensity;
    
    @Getter
    private int floatingTextDuration;
    
    // Performance settings
    @Getter
    private int maxMemoryUsage;
    
    @Getter
    private int cacheDuration;
    
    @Getter
    private boolean adaptiveScanning;
    
    @Getter
    private double adaptiveTpsThreshold;
    
    @Getter
    private int maxAsyncTasks;
    
    // Storage settings
    @Getter
    private boolean storageEnabled;
    
    @Getter
    private int retentionDays;
    
    @Getter
    private boolean autoCleanup;
    
    public ConfigManager(final TracerPlugin plugin) {
        this.plugin = plugin;
        this.loadConfig();
    }
    
    /**
     * Load the configuration from file.
     */
    public void loadConfig() {
        try {
            // Save default config if it doesn't exist
            this.plugin.saveDefaultConfig();
            
            // Load the configuration
            this.config = this.plugin.getConfig();
            
            // Load all configuration values
            this.loadConfigValues();
            
            this.plugin.getLogger().info("Configuration loaded successfully.");
            
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to load configuration", e);
        }
    }
    
    /**
     * Reload the configuration from file.
     */
    public void reloadConfig() {
        try {
            this.plugin.reloadConfig();
            this.config = this.plugin.getConfig();
            this.loadConfigValues();
            
            this.plugin.getLogger().info("Configuration reloaded successfully.");
            
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to reload configuration", e);
        }
    }
    
    /**
     * Save the current configuration to file.
     */
    public void saveConfig() {
        try {
            this.plugin.saveConfig();
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to save configuration", e);
        }
    }
    
    /**
     * Load all configuration values into instance variables.
     */
    private void loadConfigValues() {
        // General settings
        this.debugMode = this.config.getBoolean("general.debug", false);
        
        // Scanning settings
        this.scanningEnabled = this.config.getBoolean("scanning.enabled", true);
        this.scanInterval = this.config.getInt("scanning.interval", 30);
        this.scanRadius = this.config.getInt("scanning.radius", 5);
        this.maxChunksPerTick = this.config.getInt("scanning.max-chunks-per-tick", 10);
        this.asyncScanning = this.config.getBoolean("scanning.async-scanning", true);
        this.chunkCooldown = this.config.getInt("scanning.chunk-cooldown", 60);
        
        // Threshold settings
        this.entityWarningThreshold = this.config.getInt("thresholds.entities.warning", 50);
        this.entityCriticalThreshold = this.config.getInt("thresholds.entities.critical", 100);
        this.tileEntityWarningThreshold = this.config.getInt("thresholds.tile-entities.warning", 20);
        this.tileEntityCriticalThreshold = this.config.getInt("thresholds.tile-entities.critical", 40);
        this.redstoneWarningThreshold = this.config.getInt("thresholds.redstone.warning", 10);
        this.redstoneCriticalThreshold = this.config.getInt("thresholds.redstone.critical", 25);
        this.overallWarningThreshold = this.config.getInt("thresholds.overall-score.warning", 70);
        this.overallCriticalThreshold = this.config.getInt("thresholds.overall-score.critical", 90);
        this.tpsWarningThreshold = this.config.getDouble("thresholds.tps.warning", 18.0);
        this.tpsCriticalThreshold = this.config.getDouble("thresholds.tps.critical", 15.0);
        
        // Visualization settings
        this.particlesEnabled = this.config.getBoolean("visualization.particles.enabled", true);
        this.floatingTextEnabled = this.config.getBoolean("visualization.floating-text.enabled", true);
        this.actionBarEnabled = this.config.getBoolean("visualization.action-bar.enabled", true);
        this.particleDensity = this.config.getInt("visualization.particles.density", 5);
        this.floatingTextDuration = this.config.getInt("visualization.floating-text.duration", 10);
        
        // Performance settings
        this.maxMemoryUsage = this.config.getInt("performance.max-memory-usage", 50);
        this.cacheDuration = this.config.getInt("performance.cache-duration", 300);
        this.adaptiveScanning = this.config.getBoolean("performance.adaptive-scanning", true);
        this.adaptiveTpsThreshold = this.config.getDouble("performance.adaptive-tps-threshold", 17.0);
        this.maxAsyncTasks = this.config.getInt("performance.max-async-tasks", 4);
        
        // Storage settings
        this.storageEnabled = this.config.getBoolean("storage.enabled", true);
        this.retentionDays = this.config.getInt("storage.retention-days", 7);
        this.autoCleanup = this.config.getBoolean("storage.auto-cleanup", true);
    }
    
    /**
     * Get a string value from the configuration.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    public String getString(final String path, final String defaultValue) {
        return this.config.getString(path, defaultValue);
    }
    
    /**
     * Get an integer value from the configuration.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    public int getInt(final String path, final int defaultValue) {
        return this.config.getInt(path, defaultValue);
    }
    
    /**
     * Get a double value from the configuration.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    public double getDouble(final String path, final double defaultValue) {
        return this.config.getDouble(path, defaultValue);
    }
    
    /**
     * Get a boolean value from the configuration.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    public boolean getBoolean(final String path, final boolean defaultValue) {
        return this.config.getBoolean(path, defaultValue);
    }
    
    /**
     * Get a list of strings from the configuration.
     * 
     * @param path The configuration path
     * @return The configuration value
     */
    public List<String> getStringList(final String path) {
        return this.config.getStringList(path);
    }
    
    /**
     * Set a value in the configuration.
     * 
     * @param path The configuration path
     * @param value The value to set
     */
    public void set(final String path, final Object value) {
        this.config.set(path, value);
    }
    
    /**
     * Get a formatted message from the configuration.
     * 
     * @param path The message path
     * @param defaultMessage The default message if not found
     * @return The formatted message
     */
    public String getMessage(final String path, final String defaultMessage) {
        String message = this.getString("messages." + path, defaultMessage);
        
        // Replace color codes
        message = message.replace("&", "§");
        
        // Replace color placeholders
        message = message.replace("{info}", this.getString("visualization.colors.info", "&b").replace("&", "§"));
        message = message.replace("{success}", this.getString("visualization.colors.success", "&a").replace("&", "§"));
        message = message.replace("{warning}", this.getString("visualization.colors.warning", "&6").replace("&", "§"));
        message = message.replace("{error}", this.getString("visualization.colors.error", "&c").replace("&", "§"));
        
        return message;
    }
    
    /**
     * Update a threshold value and save the configuration.
     * 
     * @param thresholdType The type of threshold (entities, tile-entities, redstone, overall-score)
     * @param level The threshold level (warning, critical)
     * @param value The new threshold value
     */
    public void updateThreshold(final String thresholdType, final String level, final int value) {
        final String path = "thresholds." + thresholdType + "." + level;
        this.set(path, value);
        this.saveConfig();
        this.loadConfigValues(); // Reload values
    }
    
    /**
     * Update the scan interval and save the configuration.
     * 
     * @param interval The new scan interval in seconds
     */
    public void updateScanInterval(final int interval) {
        this.set("scanning.interval", interval);
        this.saveConfig();
        this.scanInterval = interval;
    }
    
    /**
     * Check if PlaceholderAPI integration is enabled.
     * 
     * @return true if PlaceholderAPI integration is enabled
     */
    public boolean isPlaceholderAPIEnabled() {
        return this.getBoolean("integrations.placeholderapi.enabled", true);
    }
    
    /**
     * Check if WorldGuard integration is enabled.
     * 
     * @return true if WorldGuard integration is enabled
     */
    public boolean isWorldGuardEnabled() {
        return this.getBoolean("integrations.worldguard.enabled", true);
    }
    
    /**
     * Check if metrics collection is enabled.
     * 
     * @return true if metrics collection is enabled
     */
    public boolean isMetricsEnabled() {
        return this.getBoolean("metrics.enabled", true);
    }
    
    /**
     * Gets the default scan radius from configuration
     * @return The default scan radius
     */
    public int getDefaultScanRadius() {
        return this.getInt("scanning.default-radius", 5);
    }
    
    /**
     * Gets the maximum allowed scan radius from configuration
     * @return The maximum scan radius
     */
    public int getMaxScanRadius() {
        return this.getInt("scanning.max-radius", 10);
    }
    
    /**
     * Checks if automatic scanning is enabled
     * @return True if automatic scanning is enabled
     */
    public boolean isAutomaticScanningEnabled() {
        return this.getBoolean("scanning.automatic", true);
    }
}