package com.tracer.plugin.analysis;

import org.bukkit.ChatColor;
import org.bukkit.Particle;

/**
 * Represents different levels of lag severity.
 */
public enum LagLevel {
    
    /**
     * Low lag - minimal performance impact.
     */
    LOW(ChatColor.GREEN, "&a", Particle.HAPPY_VILLAGER, "Low", "No action needed"),
    
    /**
     * Medium lag - noticeable but manageable.
     */
    MEDIUM(ChatColor.YELLOW, "&e", Particle.FLAME, "Medium", "Monitor and consider optimization"),
    
    /**
     * High lag - significant performance impact.
     */
    HIGH(ChatColor.GOLD, "&6", Particle.LAVA, "High", "Optimization recommended"),
    
    /**
     * Critical lag - severe performance impact.
     */
    CRITICAL(ChatColor.RED, "&c", Particle.LARGE_SMOKE, "Critical", "Immediate action required");
    
    private final ChatColor chatColor;
    private final String colorCode;
    private final Particle particle;
    private final String displayName;
    private final String description;
    
    /**
     * Constructor for LagLevel enum.
     * 
     * @param chatColor The ChatColor associated with this level
     * @param colorCode The color code string for this level
     * @param particle The particle effect for this level
     * @param displayName The display name for this level
     * @param description The description for this level
     */
    LagLevel(final ChatColor chatColor, final String colorCode, final Particle particle,
             final String displayName, final String description) {
        this.chatColor = chatColor;
        this.colorCode = colorCode;
        this.particle = particle;
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Get the ChatColor for this lag level.
     * @return The ChatColor
     */
    public ChatColor getChatColor() {
        return this.chatColor;
    }
    
    /**
     * Get the color code string for this lag level.
     * @return The color code
     */
    public String getColorCode() {
        return this.colorCode;
    }
    
    /**
     * Get the particle effect for this lag level.
     * @return The particle effect
     */
    public Particle getParticle() {
        return this.particle;
    }
    
    /**
     * Get the display name for this lag level.
     * @return The display name
     */
    public String getDisplayName() {
        return this.displayName;
    }
    
    /**
     * Get the description for this lag level.
     * @return The description
     */
    public String getDescription() {
        return this.description;
    }
    
    /**
     * Get a formatted name with color codes.
     * @return Formatted name with colors
     */
    public String getFormattedName() {
        return this.colorCode + this.displayName;
    }
    
    /**
     * Determine lag level from a numeric score.
     * 
     * @param score The lag score
     * @return The appropriate LagLevel
     */
    public static LagLevel fromScore(final double score) {
        if (score >= 80) {
            return CRITICAL;
        } else if (score >= 60) {
            return HIGH;
        } else if (score >= 30) {
            return MEDIUM;
        } else {
            return LOW;
        }
    }
    
    /**
     * Parse a LagLevel from a string.
     * 
     * @param levelString The string representation
     * @return The LagLevel, or LOW if not found
     */
    public static LagLevel fromString(final String levelString) {
        if (levelString == null) {
            return LOW;
        }
        
        try {
            return LagLevel.valueOf(levelString.toUpperCase());
        } catch (final IllegalArgumentException e) {
            return LOW;
        }
    }
    
    /**
     * Check if this lag level requires attention.
     * @return true if HIGH or CRITICAL
     */
    public boolean requiresAttention() {
        return this == HIGH || this == CRITICAL;
    }
    
    /**
     * Check if this lag level is critical.
     * @return true if CRITICAL
     */
    public boolean isCritical() {
        return this == CRITICAL;
    }
    
    /**
     * Get recommended action for this lag level.
     * @return Recommended action string
     */
    public String getRecommendedAction() {
        switch (this) {
            case LOW:
                return "Continue monitoring";
            case MEDIUM:
                return "Consider optimization";
            case HIGH:
                return "Optimization recommended";
            case CRITICAL:
                return "Immediate action required";
            default:
                return "Monitor performance";
        }
    }
    
    /**
     * Get the priority level (higher number = more severe).
     * @return Priority level
     */
    public int getPriority() {
        return this.ordinal();
    }
    
    /**
     * Check if this level is more severe than another.
     * @param other The other lag level
     * @return true if this level is more severe
     */
    public boolean isMoreSevereThan(final LagLevel other) {
        return this.getPriority() > other.getPriority();
    }
    
    /**
     * Get the next more severe level.
     * @return The next level, or CRITICAL if already at max
     */
    public LagLevel getNextLevel() {
        final LagLevel[] levels = values();
        final int currentIndex = this.ordinal();
        
        if (currentIndex < levels.length - 1) {
            return levels[currentIndex + 1];
        }
        
        return CRITICAL;
    }
    
    /**
     * Get the previous less severe level.
     * @return The previous level, or LOW if already at min
     */
    public LagLevel getPreviousLevel() {
        final LagLevel[] levels = values();
        final int currentIndex = this.ordinal();
        
        if (currentIndex > 0) {
            return levels[currentIndex - 1];
        }
        
        return LOW;
    }
}