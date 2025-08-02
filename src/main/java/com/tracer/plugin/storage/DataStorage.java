package com.tracer.plugin.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tracer.plugin.TracerPlugin;
import com.tracer.plugin.analysis.LagScore;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Handles data storage and persistence for lag analysis results.
 */
public final class DataStorage {
    
    private final TracerPlugin plugin;
    private final Gson gson;
    private final Path dataDirectory;
    private final Path historyFile;
    private final Path cacheFile;
    
    // Thread-safe storage
    private final Map<String, LagScore> analysisCache;
    private final List<LagScore> analysisHistory;
    private final ReentrantReadWriteLock lock;
    
    // Auto-save task
    private BukkitTask autoSaveTask;
    
    @Getter
    private boolean persistenceEnabled;
    
    @Getter
    private int retentionDays;
    
    public DataStorage(final TracerPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();
        
        this.dataDirectory = Paths.get(plugin.getDataFolder().getAbsolutePath(), "data");
        this.historyFile = this.dataDirectory.resolve("analysis_history.json");
        this.cacheFile = this.dataDirectory.resolve("analysis_cache.json");
        
        this.analysisCache = new ConcurrentHashMap<>();
        this.analysisHistory = Collections.synchronizedList(new ArrayList<>());
        this.lock = new ReentrantReadWriteLock();
        
        this.loadConfiguration();
        this.initializeStorage();
        this.startAutoSave();
    }
    
    /**
     * Load configuration settings.
     */
    private void loadConfiguration() {
        this.persistenceEnabled = this.plugin.getConfigManager().getBoolean("data.persistence.enabled", true);
        this.retentionDays = this.plugin.getConfigManager().getInt("data.persistence.retention_days", 7);
    }
    
    /**
     * Initialize storage directories and load existing data.
     */
    private void initializeStorage() {
        try {
            // Create data directory if it doesn't exist
            if (!Files.exists(this.dataDirectory)) {
                Files.createDirectories(this.dataDirectory);
                this.plugin.getLogger().info("Created data directory: " + this.dataDirectory);
            }
            
            // Load existing data if persistence is enabled
            if (this.persistenceEnabled) {
                this.loadAnalysisCache();
                this.loadAnalysisHistory();
                this.cleanupOldData();
            }
            
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to initialize data storage", e);
        }
    }
    
    /**
     * Start auto-save task.
     */
    private void startAutoSave() {
        if (!this.persistenceEnabled) {
            return;
        }
        
        final int saveInterval = this.plugin.getConfigManager().getInt("data.persistence.auto_save_interval", 300); // 5 minutes
        
        this.autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            this.plugin,
            this::saveAllData,
            saveInterval * 20L, // Convert to ticks
            saveInterval * 20L
        );
        
        this.plugin.debugLog("Started auto-save task with interval: " + saveInterval + " seconds");
    }
    
    /**
     * Store a lag score in the cache.
     * 
     * @param lagScore The lag score to store
     */
    public void storeLagScore(final LagScore lagScore) {
        if (lagScore == null) {
            return;
        }
        
        final String key = this.generateCacheKey(lagScore);
        
        this.lock.writeLock().lock();
        try {
            // Store in cache
            this.analysisCache.put(key, lagScore);
            
            // Add to history if persistence is enabled
            if (this.persistenceEnabled) {
                this.analysisHistory.add(lagScore);
            }
            
        } finally {
            this.lock.writeLock().unlock();
        }
        
        this.plugin.debugLog("Stored lag score for chunk: " + lagScore.getChunkX() + ", " + lagScore.getChunkZ());
    }
    
    /**
     * Retrieve a lag score from the cache.
     * 
     * @param world The world name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return The cached lag score, or null if not found
     */
    public LagScore getLagScore(final String world, final int chunkX, final int chunkZ) {
        final String key = this.generateCacheKey(world, chunkX, chunkZ);
        
        this.lock.readLock().lock();
        try {
            return this.analysisCache.get(key);
        } finally {
            this.lock.readLock().unlock();
        }
    }
    
    /**
     * Get all cached lag scores.
     * 
     * @return A copy of all cached lag scores
     */
    public Map<String, LagScore> getAllCachedScores() {
        this.lock.readLock().lock();
        try {
            return new HashMap<>(this.analysisCache);
        } finally {
            this.lock.readLock().unlock();
        }
    }
    
    /**
     * Get analysis history for a specific time period.
     * 
     * @param hours The number of hours to look back
     * @return List of lag scores from the specified time period
     */
    public List<LagScore> getAnalysisHistory(final int hours) {
        if (!this.persistenceEnabled) {
            return Collections.emptyList();
        }
        
        final long cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);
        
        this.lock.readLock().lock();
        try {
            return this.analysisHistory.stream()
                .filter(score -> score.getTimestamp() >= cutoffTime)
                .collect(Collectors.toList());
        } finally {
            this.lock.readLock().unlock();
        }
    }
    
    /**
     * Clear the analysis cache.
     */
    public void clearCache() {
        this.lock.writeLock().lock();
        try {
            final int size = this.analysisCache.size();
            this.analysisCache.clear();
            this.plugin.getLogger().info("Cleared analysis cache (" + size + " entries)");
        } finally {
            this.lock.writeLock().unlock();
        }
    }
    
    /**
     * Clear the analysis history.
     */
    public void clearHistory() {
        this.lock.writeLock().lock();
        try {
            final int size = this.analysisHistory.size();
            this.analysisHistory.clear();
            this.plugin.getLogger().info("Cleared analysis history (" + size + " entries)");
        } finally {
            this.lock.writeLock().unlock();
        }
    }
    
    /**
     * Clear all stored data.
     */
    public void clearAll() {
        this.clearCache();
        this.clearHistory();
    }
    
    /**
     * Get cache statistics.
     * 
     * @return Map containing cache statistics
     */
    public Map<String, Object> getCacheStats() {
        this.lock.readLock().lock();
        try {
            final Map<String, Object> stats = new HashMap<>();
            stats.put("cache_size", this.analysisCache.size());
            stats.put("history_size", this.analysisHistory.size());
            stats.put("persistence_enabled", this.persistenceEnabled);
            stats.put("retention_days", this.retentionDays);
            
            // Calculate memory usage estimate
            final long estimatedMemory = (this.analysisCache.size() + this.analysisHistory.size()) * 500L; // Rough estimate
            stats.put("estimated_memory_bytes", estimatedMemory);
            
            return stats;
        } finally {
            this.lock.readLock().unlock();
        }
    }
    
    /**
     * Export analysis data to a file.
     * 
     * @param format The export format ("json" or "csv")
     * @param hours The number of hours of data to export
     * @return The path to the exported file
     * @throws IOException If export fails
     */
    public Path exportData(final String format, final int hours) throws IOException {
        final List<LagScore> data = this.getAnalysisHistory(hours);
        
        if (data.isEmpty()) {
            throw new IOException("No data available for export");
        }
        
        final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        final String filename = "tracer_export_" + timestamp + "." + format.toLowerCase();
        final Path exportFile = this.dataDirectory.resolve("exports").resolve(filename);
        
        // Create exports directory if it doesn't exist
        Files.createDirectories(exportFile.getParent());
        
        if ("json".equalsIgnoreCase(format)) {
            this.exportToJson(data, exportFile);
        } else if ("csv".equalsIgnoreCase(format)) {
            this.exportToCsv(data, exportFile);
        } else {
            throw new IllegalArgumentException("Unsupported export format: " + format);
        }
        
        this.plugin.getLogger().info("Exported " + data.size() + " records to: " + exportFile);
        return exportFile;
    }
    
    /**
     * Save all data to disk.
     */
    public void saveAllData() {
        if (!this.persistenceEnabled) {
            return;
        }
        
        try {
            this.saveAnalysisCache();
            this.saveAnalysisHistory();
            this.plugin.debugLog("Saved all data to disk");
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to save data", e);
        }
    }
    
    /**
     * Reload configuration and update settings.
     */
    public void reloadConfiguration() {
        this.loadConfiguration();
        
        // Restart auto-save if needed
        if (this.autoSaveTask != null) {
            this.autoSaveTask.cancel();
        }
        this.startAutoSave();
        
        this.plugin.debugLog("Reloaded data storage configuration");
    }
    
    /**
     * Shutdown data storage and save all data.
     */
    public void shutdown() {
        if (this.autoSaveTask != null) {
            this.autoSaveTask.cancel();
        }
        
        this.saveAllData();
        this.plugin.getLogger().info("Data storage shutdown complete");
    }
    
    // Private helper methods
    
    private String generateCacheKey(final LagScore lagScore) {
        return this.generateCacheKey(lagScore.getWorldName(), lagScore.getChunkX(), lagScore.getChunkZ());
    }
    
    private String generateCacheKey(final String world, final int chunkX, final int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }
    
    private void loadAnalysisCache() throws IOException {
        if (!Files.exists(this.cacheFile)) {
            return;
        }
        
        try (final Reader reader = Files.newBufferedReader(this.cacheFile)) {
            final Type type = new TypeToken<Map<String, LagScore>>(){}.getType();
            final Map<String, LagScore> loadedCache = this.gson.fromJson(reader, type);
            
            if (loadedCache != null) {
                this.analysisCache.putAll(loadedCache);
                this.plugin.getLogger().info("Loaded " + loadedCache.size() + " cached analysis results");
            }
        }
    }
    
    private void saveAnalysisCache() throws IOException {
        this.lock.readLock().lock();
        try (final Writer writer = Files.newBufferedWriter(this.cacheFile)) {
            this.gson.toJson(this.analysisCache, writer);
        } finally {
            this.lock.readLock().unlock();
        }
    }
    
    private void loadAnalysisHistory() throws IOException {
        if (!Files.exists(this.historyFile)) {
            return;
        }
        
        try (final Reader reader = Files.newBufferedReader(this.historyFile)) {
            final Type type = new TypeToken<List<LagScore>>(){}.getType();
            final List<LagScore> loadedHistory = this.gson.fromJson(reader, type);
            
            if (loadedHistory != null) {
                this.analysisHistory.addAll(loadedHistory);
                this.plugin.getLogger().info("Loaded " + loadedHistory.size() + " historical analysis results");
            }
        }
    }
    
    private void saveAnalysisHistory() throws IOException {
        this.lock.readLock().lock();
        try (final Writer writer = Files.newBufferedWriter(this.historyFile)) {
            this.gson.toJson(this.analysisHistory, writer);
        } finally {
            this.lock.readLock().unlock();
        }
    }
    
    private void cleanupOldData() {
        if (this.retentionDays <= 0) {
            return;
        }
        
        final long cutoffTime = System.currentTimeMillis() - (this.retentionDays * 24 * 60 * 60 * 1000L);
        
        this.lock.writeLock().lock();
        try {
            final int sizeBefore = this.analysisHistory.size();
            this.analysisHistory.removeIf(score -> score.getTimestamp() < cutoffTime);
            final int sizeAfter = this.analysisHistory.size();
            
            if (sizeBefore > sizeAfter) {
                this.plugin.getLogger().info("Cleaned up " + (sizeBefore - sizeAfter) + " old analysis records");
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }
    
    private void exportToJson(final List<LagScore> data, final Path file) throws IOException {
        try (final Writer writer = Files.newBufferedWriter(file)) {
            this.gson.toJson(data, writer);
        }
    }
    
    private void exportToCsv(final List<LagScore> data, final Path file) throws IOException {
        try (final PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file))) {
            // Write CSV header
            writer.println("timestamp,world,chunk_x,chunk_z,entities,tile_entities,redstone,lag_score,lag_level,tps");
            
            // Write data rows
            for (final LagScore score : data) {
                writer.printf("%d,%s,%d,%d,%d,%d,%d,%.2f,%s,%.2f%n",
                    score.getTimestamp(),
                    score.getWorldName(),
                    score.getChunkX(),
                    score.getChunkZ(),
                    score.getEntityCount(),
                    score.getTileEntityCount(),
                    score.getRedstoneCount(),
                    score.getOverallScore(),
                    score.getLagLevel().name(),
                    score.getCurrentTps()
                );
            }
        }
    }
}