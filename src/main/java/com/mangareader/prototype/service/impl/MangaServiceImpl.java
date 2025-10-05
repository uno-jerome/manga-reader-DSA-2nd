package com.mangareader.prototype.service.impl;

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
import com.mangareader.prototype.service.MangaService;
import com.mangareader.prototype.source.MangaSource;
import com.mangareader.prototype.source.impl.MangaDexSource;

public abstract class MangaServiceImpl implements MangaService {
    private final Map<String, Manga> library;
    private final ObjectMapper objectMapper;
    private final Path dataDir;
    private final Path libraryFile;
    private final MangaSource mangaSource;

    public MangaServiceImpl() {
        this.library = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        this.dataDir = Paths.get(System.getProperty("user.home"), ".houdoku");
        this.libraryFile = dataDir.resolve("library.json");

        this.mangaSource = new MangaDexSource();

        try {
            Files.createDirectories(dataDir);
            loadLibrary();
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    @Override
    public List<Manga> searchManga(String query) {
        return mangaSource.search(query, false); // Pass false for includeNsfw by default
    }

    @Override
    public Optional<Manga> getMangaById(String id) {
        Manga manga = library.get(id);
        if (manga == null) {
            return mangaSource.getMangaDetails(id);
        }
        return Optional.of(manga);
    }

    @Override
    public List<Chapter> getChapters(String mangaId) {
        Manga manga = library.get(mangaId);
        if (manga != null && !manga.getChapters().isEmpty()) {
            return manga.getChapters();
        }
        return mangaSource.getChapters(mangaId);
    }

    @Override
    public Optional<Chapter> getChapter(String mangaId, String chapterId) {
        List<Chapter> chapters = getChapters(mangaId);
        return chapters.stream()
                .filter(chapter -> chapter.getId().equals(chapterId))
                .findFirst();
    }

    @Override
    public void downloadChapter(Chapter chapter) {
        List<String> pageUrls = mangaSource.getChapterPages(chapter.getMangaId(), chapter.getId());
        Path chapterDir = dataDir.resolve("downloads")
                .resolve(chapter.getMangaId())
                .resolve(String.format("chapter_%s", chapter.getId()));

        try {
            Files.createDirectories(chapterDir);
            chapter.setDownloaded(true);
            chapter.setDownloadPath(chapterDir.toString());
            saveLibrary();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateMangaInfo(Manga manga) {
        library.put(manga.getId(), manga);
        try {
            saveLibrary();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Manga> getLibrary() {
        return new ArrayList<>(library.values());
    }

    @Override
    public void addToLibrary(Manga manga) {
        library.put(manga.getId(), manga);
        try {
            saveLibrary();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeFromLibrary(String mangaId) {
        library.remove(mangaId);
        try {
            saveLibrary();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Optional<Manga> getMangaDetails(String mangaId) {
        Manga manga = library.get(mangaId);
        if (manga != null) {
            return Optional.of(manga);
        }
        return mangaSource.getMangaDetails(mangaId);
    }

    @Override
    public String getCoverUrl(String mangaId) {
        Manga manga = library.get(mangaId);
        if (manga != null && manga.getCoverUrl() != null && !manga.getCoverUrl().isEmpty()) {
            return manga.getCoverUrl();
        }
        return mangaSource.getCoverUrl(mangaId);
    }
}