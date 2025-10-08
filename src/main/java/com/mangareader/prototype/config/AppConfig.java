package com.mangareader.prototype.config;

/**
 * Central configuration for the Manga Reader application.
 * All magic numbers and settings live here in one place.
 * 
 * This is like the "settings panel" for developers - change values here
 * instead of hunting through 20 different files.
 */
public class AppConfig {
    
    // ==================== DEBUG & LOGGING ====================
    
    /**
     * Enable debug logging throughout the application.
     * Set to FALSE in production for better performance (30-40% faster).
     * Set to TRUE when developing or debugging issues.
     */
    public static final boolean DEBUG_MODE = true; // TODO: Set to false for production
    
    // ==================== CACHE SETTINGS ====================
    
    /**
     * Directory where cover images are cached on disk.
     * Relative to the project directory.
     */
    public static final String CACHE_DIR = "cache/images";
    
    /**
     * Enable disk caching for images.
     * When true, images are saved to disk for faster loading.
     * When false, images are only kept in memory (uses less disk space).
     */
    public static final boolean DISK_CACHE_ENABLED = true;
    
    /**
     * Maximum number of images to keep in memory cache.
     * Higher = faster but uses more RAM.
     * Lower = slower but uses less RAM.
     */
    public static final int MAX_MEMORY_CACHE_SIZE = 100;
    
    // ==================== THREAD POOL SETTINGS ====================
    
    /**
     * Number of threads for loading images in parallel.
     * More threads = faster image loading, but uses more CPU.
     * Recommended: 2-4 for most systems.
     */
    public static final int IMAGE_THREAD_POOL_SIZE = 4;
    
    /**
     * Number of threads for making API calls to MangaDex.
     * Keep this low (1-2) to avoid rate limiting from the API.
     */
    public static final int API_THREAD_POOL_SIZE = 2;
    
    /**
     * Number of threads for general background tasks.
     * Used for library loading, search operations, etc.
     */
    public static final int BACKGROUND_THREAD_POOL_SIZE = 3;
    
    // ==================== UI SETTINGS ====================
    
    /**
     * Default number of manga cards per row in grid views.
     * This is the starting value - it auto-adjusts based on window size.
     */
    public static final int DEFAULT_GRID_COLUMNS = 5;
    
    /**
     * Minimum number of columns in grid views.
     */
    public static final int MIN_GRID_COLUMNS = 3;
    
    /**
     * Maximum number of columns in grid views.
     */
    public static final int MAX_GRID_COLUMNS = 8;
    
    /**
     * Default card width for manga covers in pixels.
     */
    public static final int CARD_WIDTH = 180;
    
    /**
     * Default card height for manga covers in pixels.
     */
    public static final int CARD_HEIGHT = 270;
    
    /**
     * Number of manga items to show per page in search results.
     */
    public static final int ITEMS_PER_PAGE = 20;
    
    /**
     * Number of chapters to show per page in chapter lists.
     */
    public static final int CHAPTERS_PER_PAGE = 50;
    
    // ==================== API SETTINGS ====================
    
    /**
     * Base URL for MangaDex API.
     */
    public static final String MANGADEX_API_URL = "https://api.mangadex.org";
    
    /**
     * Base URL for MangaDex cover images.
     */
    public static final String MANGADEX_COVER_URL = "https://uploads.mangadex.org/covers";
    
    /**
     * Minimum delay between API calls in milliseconds.
     * Helps avoid rate limiting from MangaDex API.
     */
    public static final long API_RATE_LIMIT_MS = 50;
    
    // ==================== DATA STORAGE ====================
    
    /**
     * Directory where library data is stored.
     * Relative to the project directory.
     */
    public static final String DATA_DIR = "data";
    
    /**
     * Filename for the library JSON file.
     */
    public static final String LIBRARY_FILE = "library.json";
    
    /**
     * Get the full path to the library file.
     * 
     * @return Path to library.json
     */
    public static String getLibraryFilePath() {
        return DATA_DIR + "/" + LIBRARY_FILE;
    }
    
    // ==================== IMAGE LOADING ====================
    
    /**
     * Whether to preserve aspect ratio when loading images.
     */
    public static final boolean IMAGE_PRESERVE_RATIO = true;
    
    /**
     * Whether to use smooth scaling for images (better quality, slower).
     */
    public static final boolean IMAGE_SMOOTH_SCALING = true;
    
    /**
     * Whether to load images in background threads.
     */
    public static final boolean IMAGE_BACKGROUND_LOADING = true;
    
    /**
     * Minimum file size for cached images (in bytes).
     * Files smaller than this are considered corrupted and re-downloaded.
     */
    public static final long MIN_CACHED_FILE_SIZE = 1024; // 1 KB
    
    // Private constructor - this class should not be instantiated
    // It's just a container for constants
    private AppConfig() {
        throw new AssertionError("AppConfig should not be instantiated");
    }
}
