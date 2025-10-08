package com.mangareader.prototype.util;

/**
 * Simple logging utility for the manga reader application.
 * Provides different log levels that can be toggled via AppConfig.
 * 
 * Think of this as a traffic light system for messages:
 * - DEBUG (yellow): Detailed info for developers
 * - INFO (green): General information
 * - ERROR (red): Something went wrong
 */
public class Logger {
    
    private static boolean debugEnabled = false;
    
    /**
     * Enable or disable debug logging.
     * When disabled, debug() calls do nothing = faster performance.
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }
    
    /**
     * Log debug information - only shown when debug mode is ON.
     * Use this for detailed developer info like "Loading image from cache"
     * 
     * @param message The debug message to log
     */
    public static void debug(String message) {
        if (debugEnabled) {
            System.out.println("[DEBUG] " + message);
        }
    }
    
    /**
     * Log debug information with context (class name).
     * Example: Logger.debug("ImageCache", "Cache hit for: " + url);
     * 
     * @param context The source of the log (class name or component)
     * @param message The debug message to log
     */
    public static void debug(String context, String message) {
        if (debugEnabled) {
            System.out.println("[DEBUG] [" + context + "] " + message);
        }
    }
    
    /**
     * Log general information - always shown.
     * Use this for important events like "Library loaded with 42 manga"
     * 
     * @param message The info message to log
     */
    public static void info(String message) {
        System.out.println("[INFO] " + message);
    }
    
    /**
     * Log information with context.
     * 
     * @param context The source of the log
     * @param message The info message to log
     */
    public static void info(String context, String message) {
        System.out.println("[INFO] [" + context + "] " + message);
    }
    
    /**
     * Log error information - always shown in red (stderr).
     * Use this when something goes wrong like "Failed to load image"
     * 
     * @param message The error message to log
     */
    public static void error(String message) {
        System.err.println("[ERROR] " + message);
    }
    
    /**
     * Log error with context.
     * 
     * @param context The source of the error
     * @param message The error message to log
     */
    public static void error(String context, String message) {
        System.err.println("[ERROR] [" + context + "] " + message);
    }
    
    /**
     * Log error with exception details.
     * 
     * @param message The error message
     * @param throwable The exception that was caught
     */
    public static void error(String message, Throwable throwable) {
        System.err.println("[ERROR] " + message + ": " + throwable.getMessage());
        if (debugEnabled) {
            throwable.printStackTrace();
        }
    }
    
    /**
     * Log error with context and exception.
     * 
     * @param context The source of the error
     * @param message The error message
     * @param throwable The exception that was caught
     */
    public static void error(String context, String message, Throwable throwable) {
        System.err.println("[ERROR] [" + context + "] " + message + ": " + throwable.getMessage());
        if (debugEnabled) {
            throwable.printStackTrace();
        }
    }
    
    /**
     * Log warning information - shown in stderr but less severe than errors.
     * Use this for recoverable issues like "Cache file corrupted, re-downloading"
     * 
     * @param message The warning message to log
     */
    public static void warn(String message) {
        System.err.println("[WARN] " + message);
    }
    
    /**
     * Log warning with context.
     * 
     * @param context The source of the warning
     * @param message The warning message to log
     */
    public static void warn(String context, String message) {
        System.err.println("[WARN] [" + context + "] " + message);
    }
}
