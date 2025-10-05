package com.mangareader.prototype.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javafx.scene.image.Image;

/**
 * Image cache utility to prevent reloading covers when theme changes.
 * Supports both memory and disk caching for better performance.
 */
public class ImageCache {
    private static final ImageCache instance = new ImageCache();
    private final Map<String, Image> memoryCache = new ConcurrentHashMap<>();
    private final Path cacheDir;
    private final boolean diskCacheEnabled;

    private static final double DEFAULT_WIDTH = 180;
    private static final double DEFAULT_HEIGHT = 270;
    private static final boolean DEFAULT_PRESERVE_RATIO = true;
    private static final boolean DEFAULT_SMOOTH = true;
    private static final boolean DEFAULT_BACKGROUND_LOADING = true;

    private ImageCache() {
        String projectDir = System.getProperty("user.dir");
        this.cacheDir = Paths.get(projectDir, "cache", "images");

        boolean cacheCreated = false;
        try {
            Files.createDirectories(cacheDir);
            cacheCreated = true;
            System.out.println("Image cache directory created at: " + cacheDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to create image cache directory: " + e.getMessage());
        }

        this.diskCacheEnabled = cacheCreated;
    }

    public static ImageCache getInstance() {
        return instance;
    }

    /**
     * Get cached image or load and cache if not present using default dimensions
     */
    public Image getImage(String url) {
        return getImage(url, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Get cached image or load and cache if not present with custom dimensions
     */
    public Image getImage(String url, double width, double height) {
        if (url == null || url.isEmpty()) {
            return getPlaceholderImage("No+Cover", width, height);
        }

        if (!isValidImageUrl(url)) {
            System.err.println("Invalid image URL: " + url);
            return getPlaceholderImage("Invalid+URL", width, height);
        }

        String cacheKey = url + "_" + width + "x" + height;
        return memoryCache.computeIfAbsent(cacheKey, k -> loadImageWithDiskCache(url, width, height));
    }

    /**
     * Get a placeholder image for errors or missing covers
     */
    public Image getPlaceholderImage(String text) {
        return getPlaceholderImage(text, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public Image getPlaceholderImage(String text, double width, double height) {
        String placeholderUrl = String.format("https://via.placeholder.com/%dx%d?text=%s",
                (int) width, (int) height, text);
        String cacheKey = placeholderUrl + "_" + width + "x" + height;
        return memoryCache.computeIfAbsent(cacheKey, k -> loadImage(placeholderUrl, width, height));
    }

    /**
     * Clear the memory cache
     */
    public void clearMemoryCache() {
        memoryCache.clear();
    }

    /**
     * Clear the entire cache (memory and disk)
     */
    public void clearCache() {
        clearMemoryCache();
        if (diskCacheEnabled) {
            clearDiskCache();
        }
    }

    /**
     * Remove specific image from cache
     */
    public void removeFromCache(String url) {
        memoryCache.remove(url);
        if (diskCacheEnabled) {
            try {
                String filename = getCacheFileName(url);
                Path cachedFile = cacheDir.resolve(filename);
                Files.deleteIfExists(cachedFile);
            } catch (Exception e) {
                System.err.println("Error removing cached file: " + e.getMessage());
            }
        }
    }

    /**
     * Get cache size for debugging
     */
    public int getMemoryCacheSize() {
        return memoryCache.size();
    }

    /**
     * Get disk cache size for debugging
     */
    public long getDiskCacheSize() {
        if (!diskCacheEnabled)
            return 0;

        try {
            return Files.walk(cacheDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private boolean isValidImageUrl(String url) {
        try {
            URL testUrl = URI.create(url).toURL();
            String protocol = testUrl.getProtocol();
            return "http".equals(protocol) || "https".equals(protocol);
        } catch (Exception e) {
            return false;
        }
    }

    private Image downloadAndCacheImage(String url, Path cachedFile, double width, double height) {
        try {
            URL imageUrl = URI.create(url).toURL();
            try (ReadableByteChannel rbc = Channels.newChannel(imageUrl.openStream());
                    FileOutputStream fos = new FileOutputStream(cachedFile.toFile())) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            }

            if (Files.size(cachedFile) < 1024) {
                System.err.println("Downloaded file too small, likely corrupted: " + url);
                Files.deleteIfExists(cachedFile);
                return null;
            }

            String fileUri = cachedFile.toUri().toString();
            Image testImage = new Image(fileUri, width, height, DEFAULT_PRESERVE_RATIO, DEFAULT_SMOOTH,
                    DEFAULT_BACKGROUND_LOADING);

            if (testImage.isError()) {
                System.err.println("Downloaded image is corrupted: " + url);
                Files.deleteIfExists(cachedFile);
                return null;
            }

            return testImage;
        } catch (Exception e) {
            System.err.println("Error downloading and caching image: " + e.getMessage());
            try {
                Files.deleteIfExists(cachedFile);
            } catch (IOException cleanupError) {
                System.err.println("Error cleaning up corrupted cache file: " + cleanupError.getMessage());
            }
            return null;
        }
    }

    private Image loadImageWithDiskCache(String url, double width, double height) {
        if (!diskCacheEnabled) {
            return loadImage(url, width, height);
        }

        try {
            String filename = getCacheFileName(url);
            Path cachedFile = cacheDir.resolve(filename);

            if (Files.exists(cachedFile)) {
                System.out.println("Loading image from disk cache: " + filename);

                if (Files.size(cachedFile) < 1024) {
                    System.err.println("Cached file too small, removing: " + filename);
                    Files.deleteIfExists(cachedFile);
                    return downloadAndCacheImage(url, cachedFile, width, height);
                }

                try {
                    String fileUri = cachedFile.toUri().toString();
                    Image cachedImage = new Image(fileUri, width, height, DEFAULT_PRESERVE_RATIO, DEFAULT_SMOOTH,
                            DEFAULT_BACKGROUND_LOADING);

                    if (cachedImage.isError()) {
                        System.err.println("Cached image is corrupted, re-downloading: " + filename);
                        Files.deleteIfExists(cachedFile);
                        Image newImage = downloadAndCacheImage(url, cachedFile, width, height);
                        return newImage != null ? newImage : loadImage(url, width, height);
                    }

                    return cachedImage;
                } catch (Exception e) {
                    System.err.println("Error loading cached image, re-downloading: " + e.getMessage());
                    Files.deleteIfExists(cachedFile);
                    Image newImage = downloadAndCacheImage(url, cachedFile, width, height);
                    return newImage != null ? newImage : loadImage(url, width, height);
                }
            } else {
                System.out.println("Downloading and caching image: " + url);
                Image image = downloadAndCacheImage(url, cachedFile, width, height);
                return image != null ? image : loadImage(url, width, height);
            }
        } catch (Exception e) {
            System.err.println("Error with disk cache, falling back to direct load: " + e.getMessage());
            return loadImage(url, width, height);
        }
    }

    private Image loadImage(String url, double width, double height) {
        try {
            System.out.println("Loading and caching image: " + url);

            Image image = new Image(url, width, height, DEFAULT_PRESERVE_RATIO, DEFAULT_SMOOTH,
                    DEFAULT_BACKGROUND_LOADING);

            image.exceptionProperty().addListener((obs, oldEx, newEx) -> {
                if (newEx != null) {
                    System.err.println("Image loading exception for " + url + ": " + newEx.getMessage());
                    String cacheKey = url + "_" + width + "x" + height;
                    memoryCache.remove(cacheKey);
                }
            });

            image.errorProperty().addListener((obs, wasError, isError) -> {
                if (isError) {
                    System.err.println("Image error detected for " + url + " - likely corrupted JPEG data");
                    String cacheKey = url + "_" + width + "x" + height;
                    memoryCache.remove(cacheKey);
                }
            });

            return image;
        } catch (Exception e) {
            System.err.println("Error loading image: " + url + " | " + e.getMessage());
            return new Image("https://via.placeholder.com/180x270?text=Error",
                    width, height, DEFAULT_PRESERVE_RATIO, DEFAULT_SMOOTH, DEFAULT_BACKGROUND_LOADING);
        }
    }

    private String getCacheFileName(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(url.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString() + ".jpg";
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(url.hashCode()) + ".jpg";
        }
    }

    private void clearDiskCache() {
        try {
            Files.walk(cacheDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("Error deleting cached file: " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error clearing disk cache: " + e.getMessage());
        }
    }
}
