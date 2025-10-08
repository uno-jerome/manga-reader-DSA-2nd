package com.mangareader.prototype.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mangareader.prototype.model.Chapter;
import com.mangareader.prototype.model.Manga;
import com.mangareader.prototype.source.MangaDexSource;
import com.mangareader.prototype.source.MgekoSource;
import com.mangareader.prototype.source.MangaSource;
import com.mangareader.prototype.util.Logger;

public class MangaServiceImpl {
    private static MangaServiceImpl instance;
    
    private final Map<String, Manga> library;
    private final ObjectMapper objectMapper;
    private final Path dataDir;
    private final Path libraryFile;
    private final List<MangaSource> sources; // Multiple sources!
    private MangaSource currentSource; // Currently active source

    private MangaServiceImpl() {
        this.library = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        this.dataDir = Paths.get(System.getProperty("user.home"), ".houdoku");
        this.libraryFile = dataDir.resolve("library.json");        this.sources = new ArrayList<>();
        this.sources.add(new MangaDexSource());
        this.sources.add(new MgekoSource());        this.currentSource = this.sources.get(0);
        
        Logger.info("MangaServiceImpl", "Initialized with " + sources.size() + " sources: " +
                sources.stream().map(MangaSource::getName).toList());

        try {
            Files.createDirectories(dataDir);
            loadLibrary();
        } catch (IOException e) {
            Logger.error("MangaServiceImpl", "Error initializing service", e);
        }
    }

    public static synchronized MangaServiceImpl getInstance() {
        if (instance == null) {
            instance = new MangaServiceImpl();
        }
        return instance;
    }

    private void loadLibrary() throws IOException {
        if (Files.exists(libraryFile)) {
            List<Manga> mangaList = objectMapper.readValue(
                    libraryFile.toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Manga.class));
            mangaList.forEach(manga -> library.put(manga.getId(), manga));
        }
    }

    private void saveLibrary() throws IOException {
        objectMapper.writeValue(libraryFile.toFile(), new ArrayList<>(library.values()));
    }

    // ==================== SOURCE MANAGEMENT ====================
    
    /**
     * Get all available manga sources.
     */
    public List<MangaSource> getAllSources() {
        return new ArrayList<>(sources);
    }
    
    /**
     * Get current active source.
     */
    public MangaSource getCurrentSource() {
        return currentSource;
    }
    
    /**
     * Switch to a different manga source.
     */
    public void setCurrentSource(String sourceName) {
        for (MangaSource source : sources) {
            if (source.getName().equalsIgnoreCase(sourceName)) {
                this.currentSource = source;
                Logger.info("MangaServiceImpl", "Switched to source: " + sourceName);
                return;
            }
        }
        Logger.error("MangaServiceImpl", "Source not found: " + sourceName, null);
    }
    
    /**
     * Search manga from current source only.
     */
    public List<Manga> searchManga(String query) {
        return currentSource.search(query, false);
    }
    
    /**
     * Search manga from ALL available sources and combine results.
     */
    public List<Manga> searchAllSources(String query) {
        List<Manga> allResults = new ArrayList<>();
        for (MangaSource source : sources) {
            try {
                Logger.info("MangaServiceImpl", "Searching " + source.getName() + " for: " + query);
                List<Manga> results = source.search(query, false);
                allResults.addAll(results);
                Logger.info("MangaServiceImpl", "Found " + results.size() + " results from " + source.getName());
            } catch (Exception e) {
                Logger.error("MangaServiceImpl", "Error searching " + source.getName(), e);
            }
        }
        return allResults;
    }

    public Optional<Manga> getMangaById(String id) {
        Manga manga = library.get(id);
        if (manga == null) {            return currentSource.getMangaDetails(id);
        }
        return Optional.of(manga);
    }

    public List<Chapter> getChapters(String mangaId) {
        Manga manga = library.get(mangaId);
        if (manga != null && !manga.getChapters().isEmpty()) {
            return manga.getChapters();
        }        MangaSource source = getSourceForManga(manga);
        return source.getChapters(mangaId);
    }

    public Optional<Chapter> getChapter(String mangaId, String chapterId) {
        List<Chapter> chapters = getChapters(mangaId);
        return chapters.stream()
                .filter(chapter -> chapter.getId().equals(chapterId))
                .findFirst();
    }

    public void downloadChapter(Chapter chapter) {
        Manga manga = library.get(chapter.getMangaId());
        MangaSource source = getSourceForManga(manga);
        
        source.getChapterPages(chapter.getMangaId(), chapter.getId());
        
        Path chapterDir = dataDir.resolve("downloads")
                .resolve(chapter.getMangaId())
                .resolve(String.format("chapter_%s", chapter.getId()));

        try {
            Files.createDirectories(chapterDir);
            chapter.setDownloaded(true);
            chapter.setDownloadPath(chapterDir.toString());
            saveLibrary();
        } catch (IOException e) {
            Logger.error("MangaServiceImpl", "Error downloading chapter", e);
        }
    }

    public void updateMangaInfo(Manga manga) {
        library.put(manga.getId(), manga);
        try {
            saveLibrary();
        } catch (IOException e) {
            Logger.error("MangaServiceImpl", "Error updating manga info", e);
        }
    }

    public List<Manga> getLibrary() {
        return new ArrayList<>(library.values());
    }

    public void addToLibrary(Manga manga) {
        library.put(manga.getId(), manga);
        try {
            saveLibrary();
        } catch (IOException e) {
            Logger.error("MangaServiceImpl", "Error adding to library", e);
        }
    }

    public void removeFromLibrary(String mangaId) {
        library.remove(mangaId);
        try {
            saveLibrary();
        } catch (IOException e) {
            Logger.error("MangaServiceImpl", "Error removing from library", e);
        }
    }

    public Optional<Manga> getMangaDetails(String mangaId) {
        Manga manga = library.get(mangaId);
        if (manga != null) {
            return Optional.of(manga);
        }        return currentSource.getMangaDetails(mangaId);
    }

    public String getCoverUrl(String mangaId) {
        Manga manga = library.get(mangaId);
        if (manga != null && manga.getCoverUrl() != null && !manga.getCoverUrl().isEmpty()) {
            return manga.getCoverUrl();
        }
        MangaSource source = getSourceForManga(manga);
        return source.getCoverUrl(mangaId);
    }

    public List<String> getChapterPages(String mangaId, String chapterId) {
        Manga manga = library.get(mangaId);
        MangaSource source = getSourceForManga(manga);
        return source.getChapterPages(mangaId, chapterId);
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Get the appropriate source for a manga.
     * If manga has a source set, use that. Otherwise use current source.
     */
    private MangaSource getSourceForManga(Manga manga) {
        if (manga != null && manga.getSource() != null) {
            for (MangaSource source : sources) {
                if (source.getName().equals(manga.getSource())) {
                    return source;
                }
            }
        }        return currentSource;
    }
}