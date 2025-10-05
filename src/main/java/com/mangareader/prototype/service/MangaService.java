package com.mangareader.prototype.service;

import java.util.List;
import java.util.Optional;

import com.mangareader.prototype.model.Chapter;
import com.mangareader.prototype.model.Manga;

public interface MangaService {
    List<Manga> searchManga(String query);

    Optional<Manga> getMangaById(String id);

    List<Chapter> getChapters(String mangaId);

    List<String> getChapterPages(String mangaId, String chapterId);

    Optional<Chapter> getChapter(String mangaId, String chapterId);

    void downloadChapter(Chapter chapter);

    void updateMangaInfo(Manga manga);

    List<Manga> getLibrary();

    void addToLibrary(Manga manga);

    void removeFromLibrary(String mangaId);

    Optional<Manga> getMangaDetails(String mangaId);

    String getCoverUrl(String mangaId);
}