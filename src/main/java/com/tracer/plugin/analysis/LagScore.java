package com.tracer.plugin.analysis;

import com.tracer.plugin.analysis.LagLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents the lag analysis results for a specific chunk.
 */
@EqualsAndHashCode(of = {"worldName", "chunkX", "chunkZ", "timestamp"})
public final class LagScore {
    
    // Chunk identification
    private final String worldName;
    private final int chunkX;
    private final int chunkZ;
    
    // Analysis timestamp
    private final long timestamp;
    
    // Entity counts
    private final int entityCount;
    private final int tileEntityCount;
    private final int redstoneCount;
    
    // Calculated scores
    private double overallScore;
    private final double currentTps;
    
    // Lag assessment
    private LagLevel lagLevel;
    
    // Detailed lag sources
    private List<String> lagSources;
    
    // Details string for display
    private String details;
    
    /**
     * Create a new LagScore.
     * 
     * @param worldName The world name
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param entityCount The number of entities
     * @param tileEntityCount The number of tile entities
     * @param redstoneCount The number of redstone components
     * @param currentTps The current server TPS
     */
    public LagScore(final String worldName, final int chunkX, final int chunkZ,
                   final int entityCount, final int tileEntityCount, final int redstoneCount,
                   final double currentTps) {
        this.worldName = worldName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.timestamp = System.currentTimeMillis();
        this.entityCount = entityCount;
        this.tileEntityCount = tileEntityCount;
        this.redstoneCount = redstoneCount;
        this.currentTps = currentTps;
        
        // Calculate overall score
        this.overallScore = this.calculateOverallScore();
        
        // Determine lag level
        this.lagLevel = LagLevel.fromScore(this.overallScore);
        
        // Generate lag sources
        this.lagSources = this.generateLagSources();
    }
    
    /**
     * Calculate the overall lag score based on entity counts.
     * 
     * @return The calculated score
     */
    private double calculateOverallScore() {
        double score = 0.0;
        
        // Entity contribution (weight: 1.0)
        score += this.entityCount * 1.0;
        
        // Tile entity contribution (weight: 2.0)
        score += this.tileEntityCount * 2.0;
        
        // Redstone contribution (weight: 3.0)
        score += this.redstoneCount * 3.0;
        
        // TPS penalty (lower TPS = higher score)
        if (this.currentTps < 20.0) {
            score += (20.0 - this.currentTps) * 5.0;
        }
        
        return score;
    }
    
    /**
     * Generate a list of lag sources based on the analysis.
     * 
     * @return List of lag source descriptions
     */
    private List<String> generateLagSources() {
        final List<String> sources = new ArrayList<>();
        
        if (this.entityCount > 50) {
            sources.add("High entity count: " + this.entityCount);
        }
        
        if (this.tileEntityCount > 20) {
            sources.add("High tile entity count: " + this.tileEntityCount);
        }
        
        if (this.redstoneCount > 10) {
            sources.add("High redstone component count: " + this.redstoneCount);
        }
        
        if (this.currentTps < 18.0) {
            sources.add("Low server TPS: " + String.format("%.1f", this.currentTps));
        }
        
        if (sources.isEmpty()) {
            sources.add("No significant lag sources detected");
        }
        
        return sources;
    }
    
    /**
     * Check if this lag score is still fresh (within the specified age).
     * 
     * @param maxAgeMs Maximum age in milliseconds
     * @return true if the score is fresh
     */
    public boolean isFresh(final long maxAgeMs) {
        return (System.currentTimeMillis() - this.timestamp) <= maxAgeMs;
    }
    
    /**
     * Get the chunk location.
     * 
     * @param world The world instance
     * @return The chunk location
     */
    public Location getChunkLocation(final World world) {
        return new Location(world, this.chunkX * 16, 64, this.chunkZ * 16);
    }
    
    /**
     * Get a formatted description of the lag score.
     * 
     * @return Formatted description
     */
    public String getFormattedDescription() {
        return String.format("Chunk [%d, %d] in %s - Score: %.1f (%s) - Entities: %d, TileEntities: %d, Redstone: %d",
            this.chunkX, this.chunkZ, this.worldName, this.overallScore, this.lagLevel.name(),
            this.entityCount, this.tileEntityCount, this.redstoneCount);
    }
    
    /**
     * Get the age of this score in seconds.
     * 
     * @return Age in seconds
     */
    public long getAgeSeconds() {
        return (System.currentTimeMillis() - this.timestamp) / 1000L;
    }
    
    /**
     * Check if this chunk has significant lag.
     * 
     * @return true if lag level is HIGH or CRITICAL
     */
    public boolean hasSignificantLag() {
        return this.lagLevel == LagLevel.HIGH || this.lagLevel == LagLevel.CRITICAL;
    }
    
    /**
     * Get recommendations for reducing lag in this chunk.
     * 
     * @return List of recommendations
     */
    public List<String> getRecommendations() {
        final List<String> recommendations = new ArrayList<>();
        
        if (this.entityCount > 50) {
            recommendations.add("Consider reducing entity count (current: " + this.entityCount + ")");
        }
        
        if (this.tileEntityCount > 20) {
            recommendations.add("Optimize or reduce tile entities (current: " + this.tileEntityCount + ")");
        }
        
        if (this.redstoneCount > 10) {
            recommendations.add("Simplify redstone circuits (current: " + this.redstoneCount + ")");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("No specific optimizations needed");
        }
        
        return recommendations;
    }
    
    /**
     * Convert to a summary map for easy serialization.
     * 
     * @return Map containing summary data
     */
    public Map<String, Object> toSummaryMap() {
        return Map.of(
            "world", this.worldName,
            "chunkX", this.chunkX,
            "chunkZ", this.chunkZ,
            "timestamp", this.timestamp,
            "score", this.overallScore,
            "level", this.lagLevel.name(),
            "entities", this.entityCount,
            "tileEntities", this.tileEntityCount,
            "redstone", this.redstoneCount,
            "tps", this.currentTps
        );
    }
    
    // Getter methods
    public String getWorldName() { return worldName; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public long getTimestamp() { return timestamp; }
    public int getEntityCount() { return entityCount; }
    public int getTileEntityCount() { return tileEntityCount; }
    public int getRedstoneCount() { return redstoneCount; }
    public double getOverallScore() { return overallScore; }
    public double getCurrentTps() { return currentTps; }
    public LagLevel getLagLevel() { return lagLevel; }
    public List<String> getLagSources() { return lagSources; }
    public String getDetails() { return details; }
    
    // Setter methods
    public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
    public void setLagLevel(LagLevel lagLevel) { this.lagLevel = lagLevel; }
    public void setLagSources(List<String> lagSources) { this.lagSources = lagSources; }
    public void setDetails(String details) { this.details = details; }
    
    // Missing method that ChunkAnalyzer expects
    public String getFormattedString() {
        return String.format("[%s] %s (%.1f) - %s", 
            lagLevel.getDisplayName(), 
            getFormattedDescription(), 
            overallScore, 
            String.join(", ", lagSources));
    }
    
    public org.bukkit.Location getLocation() {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(this.worldName);
        if (world != null) {
            return new org.bukkit.Location(world, this.chunkX * 16 + 8, 64, this.chunkZ * 16 + 8);
        }
        return null;
    }
}