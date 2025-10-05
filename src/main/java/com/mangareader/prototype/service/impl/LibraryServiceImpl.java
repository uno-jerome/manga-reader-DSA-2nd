package com.mangareader.prototype.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mangareader.prototype.model.Manga;
import com.mangareader.prototype.service.LibraryService;

/**
 * Implementation of LibraryService with JSON file storage
 * Manages the user's manga library with proper isolation from external sources
 */
public class LibraryServiceImpl implements LibraryService {

    private final Map<String, LibraryEntry> library;
    private final ObjectMapper objectMapper;
    private final Path dataDir;
    private final Path libraryFile;

    public LibraryServiceImpl() {
        this.library = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        String projectDir = System.getProperty("user.dir");
        this.dataDir = Paths.get(projectDir, "data");
        this.libraryFile = dataDir.resolve("library.json");

        initializeLibrary();
    }

    private void initializeLibrary() {
        try {
            Files.createDirectories(dataDir);

            if (Files.exists(libraryFile)) {
                loadLibrary();
                System.out.println("Loaded " + library.size() + " manga from library");
            } else {
                System.out.println("Starting with empty library");
            }
        } catch (IOException e) {
            System.err.println("Error initializing library: " + e.getMessage());
        }
    }

    private void loadLibrary() throws IOException {
        if (Files.exists(libraryFile) && Files.size(libraryFile) > 0) {
            List<LibraryEntry> entries = objectMapper.readValue(
                    libraryFile.toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, LibraryEntry.class));
            entries.forEach(entry -> library.put(entry.getManga().getId(), entry));
        }
    }

    private void saveLibrary() {
        try {
            List<LibraryEntry> entries = new ArrayList<>(library.values());
            objectMapper.writeValue(libraryFile.toFile(), entries);
            System.out.println("Saved " + entries.size() + " manga to library");
        } catch (IOException e) {
            System.err.println("Error saving library: " + e.getMessage());
        }
    }

    @Override
    public List<Manga> getLibrary() {
        return library.values().stream()
                .map(LibraryEntry::getManga)
                .sorted((a, b) -> b.getLastUpdated() != null && a.getLastUpdated() != null
                        ? b.getLastUpdated().compareTo(a.getLastUpdated())
                        : a.getTitle().compareTo(b.getTitle()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean addToLibrary(Manga manga) {
        if (manga == null || manga.getId() == null) {
            return false;
        }

        if (library.containsKey(manga.getId())) {
            System.out.println("Manga already in library: " + manga.getTitle());
            return false;
        }

        manga.setLastUpdated(LocalDateTime.now());

        LibraryEntry entry = new LibraryEntry(manga, LocalDateTime.now());
        library.put(manga.getId(), entry);
        saveLibrary();

        System.out.println("Added to library: " + manga.getTitle());
        return true;
    }

    @Override
    public boolean removeFromLibrary(String mangaId) {
        if (mangaId == null) {
            return false;
        }

        LibraryEntry removed = library.remove(mangaId);
        if (removed != null) {
            saveLibrary();
            System.out.println("Removed from library: " + removed.getManga().getTitle());
            return true;
        }
        return false;
    }

    @Override
    public boolean isInLibrary(String mangaId) {
        return mangaId != null && library.containsKey(mangaId);
    }

    @Override
    public Optional<Manga> getLibraryManga(String mangaId) {
        LibraryEntry entry = library.get(mangaId);
        return entry != null ? Optional.of(entry.getManga()) : Optional.empty();
    }

    @Override
    public void updateReadingProgress(String mangaId, int chaptersRead, int totalChapters) {
        LibraryEntry entry = library.get(mangaId);
        if (entry != null) {
            entry.setChaptersRead(chaptersRead);
            entry.setTotalChapters(totalChapters);
            entry.setLastRead(LocalDateTime.now());
            saveLibrary();
        }
    }

    @Override
    public void clearLibrary() {
        library.clear();
        try {
            if (Files.exists(libraryFile)) {
                Files.delete(libraryFile);
            }
            System.out.println("Library cleared");
        } catch (IOException e) {
            System.err.println("Error clearing library: " + e.getMessage());
        }
    }

    @Override
    public LibraryStats getLibraryStats() {
        int total = library.size();
        int reading = 0;
        int completed = 0;
        int planned = 0;

        for (LibraryEntry entry : library.values()) {
            String status = entry.getManga().getStatus();
            if ("Reading".equalsIgnoreCase(status)) {
                reading++;
            } else if ("Completed".equalsIgnoreCase(status)) {
                completed++;
            } else if ("Plan to Read".equalsIgnoreCase(status)) {
                planned++;
            }
        }

        return new LibraryStats(total, reading, completed, planned);
    }

    @Override
    public List<Manga> searchLibrary(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getLibrary();
        }

        String searchTerm = query.toLowerCase().trim();
        return library.values().stream()
                .map(LibraryEntry::getManga)
                .filter(manga -> manga.getTitle().toLowerCase().contains(searchTerm) ||
                        (manga.getAuthor() != null && manga.getAuthor().toLowerCase().contains(searchTerm)) ||
                        (manga.getArtist() != null && manga.getArtist().toLowerCase().contains(searchTerm)))
                .sorted((a, b) -> a.getTitle().compareTo(b.getTitle()))
                .collect(Collectors.toList());
    }

    @Override
    public void updateReadingPosition(String mangaId, String chapterId, int pageNumber, int totalPages) {
        LibraryEntry entry = library.get(mangaId);
        if (entry != null) {
            entry.setCurrentChapterId(chapterId);
            entry.setCurrentPageNumber(pageNumber);
            entry.setCurrentChapterTotalPages(totalPages);
            entry.setLastRead(LocalDateTime.now());

            if ("Plan to Read".equals(entry.getReadingStatus())) {
                entry.setReadingStatus("Reading");
            }

            saveLibrary();
        }
    }

    @Override
    public Optional<LibraryService.ReadingPosition> getReadingPosition(String mangaId) {
        LibraryEntry entry = library.get(mangaId);
        if (entry != null && entry.getCurrentChapterId() != null) {
            return Optional.of(new LibraryService.ReadingPosition(
                    mangaId,
                    entry.getCurrentChapterId(),
                    entry.getCurrentPageNumber(),
                    entry.getCurrentChapterTotalPages(),
                    entry.getLastRead()));
        }
        return Optional.empty();
    }

    @Override
    public void markChapterAsRead(String mangaId, String chapterId) {
        LibraryEntry entry = library.get(mangaId);
        if (entry != null) {
            entry.markChapterAsRead(chapterId);
            entry.setLastRead(LocalDateTime.now());

            if ("Plan to Read".equals(entry.getReadingStatus())) {
                entry.setReadingStatus("Reading");
            }

            if (entry.getTotalChapters() > 0 && entry.getChaptersRead() >= entry.getTotalChapters()) {
                entry.setReadingStatus("Completed");
            }

            saveLibrary();
        }
    }

    @Override
    public boolean isChapterRead(String mangaId, String chapterId) {
        LibraryEntry entry = library.get(mangaId);
        return entry != null && entry.isChapterRead(chapterId);
    }

    @Override
    public double getReadingProgress(String mangaId) {
        LibraryEntry entry = library.get(mangaId);
        return entry != null ? entry.getProgressPercentage() : 0.0;
    }

    @Override
    public void updateTotalChapters(String mangaId, int totalChapters) {
        LibraryEntry entry = library.get(mangaId);
        if (entry != null) {
            entry.setTotalChapters(totalChapters);
            saveLibrary();
            System.out.println("Updated total chapters for " + entry.getManga().getTitle() + ": " + totalChapters);
        }
    }

    @Override
    public Optional<LibraryEntryInfo> getLibraryEntryInfo(String mangaId) {
        LibraryEntry entry = library.get(mangaId);
        if (entry != null) {
            return Optional.of(new LibraryEntryInfo(
                    entry.getChaptersRead(),
                    entry.getTotalChapters(),
                    entry.getReadingStatus()));
        }
        return Optional.empty();
    }

    /**
     * Library entry wrapper to store additional metadata
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LibraryEntry {
        private Manga manga;
        private LocalDateTime addedDate;
        private LocalDateTime lastRead;
        private int chaptersRead;
        private int totalChapters;
        private String readingStatus; // "Reading", "Completed", "Plan to Read", "On Hold", "Dropped"

        private String currentChapterId;
        private int currentPageNumber;
        private int currentChapterTotalPages;
        private List<String> readChapterIds; // Track which chapters have been fully read

        public LibraryEntry() {
            this.readChapterIds = new ArrayList<>();
        }

        public LibraryEntry(Manga manga, LocalDateTime addedDate) {
            this.manga = manga;
            this.addedDate = addedDate;
            this.readingStatus = "Plan to Read";
            this.chaptersRead = 0;
            this.totalChapters = 0;
            this.currentPageNumber = 0;
            this.currentChapterTotalPages = 0;
            this.readChapterIds = new ArrayList<>();
        }

        public Manga getManga() {
            return manga;
        }

        public void setManga(Manga manga) {
            this.manga = manga;
        }

        public LocalDateTime getAddedDate() {
            return addedDate;
        }

        public void setAddedDate(LocalDateTime addedDate) {
            this.addedDate = addedDate;
        }

        public LocalDateTime getLastRead() {
            return lastRead;
        }

        public void setLastRead(LocalDateTime lastRead) {
            this.lastRead = lastRead;
        }

        public int getChaptersRead() {
            return chaptersRead;
        }

        public void setChaptersRead(int chaptersRead) {
            this.chaptersRead = chaptersRead;
        }

        public int getTotalChapters() {
            return totalChapters;
        }

        public void setTotalChapters(int totalChapters) {
            this.totalChapters = totalChapters;
        }

        public String getReadingStatus() {
            return readingStatus;
        }

        public void setReadingStatus(String readingStatus) {
            this.readingStatus = readingStatus;
        }

        public String getCurrentChapterId() {
            return currentChapterId;
        }

        public void setCurrentChapterId(String currentChapterId) {
            this.currentChapterId = currentChapterId;
        }

        public int getCurrentPageNumber() {
            return currentPageNumber;
        }

        public void setCurrentPageNumber(int currentPageNumber) {
            this.currentPageNumber = currentPageNumber;
        }

        public int getCurrentChapterTotalPages() {
            return currentChapterTotalPages;
        }

        public void setCurrentChapterTotalPages(int currentChapterTotalPages) {
            this.currentChapterTotalPages = currentChapterTotalPages;
        }

        public List<String> getReadChapterIds() {
            return readChapterIds;
        }

        public void setReadChapterIds(List<String> readChapterIds) {
            this.readChapterIds = readChapterIds != null ? readChapterIds : new ArrayList<>();
        }

        public boolean isChapterRead(String chapterId) {
            return readChapterIds != null && readChapterIds.contains(chapterId);
        }

        public void markChapterAsRead(String chapterId) {
            if (readChapterIds == null) {
                readChapterIds = new ArrayList<>();
            }
            if (!readChapterIds.contains(chapterId)) {
                readChapterIds.add(chapterId);
                chaptersRead = readChapterIds.size();
            }
        }

        @JsonIgnore
        public double getProgressPercentage() {
            return totalChapters > 0 ? (double) chaptersRead / totalChapters : 0.0;
        }
    }
}
