package com.mangareader.prototype.source;

import java.util.List;
import java.util.Optional;

import com.mangareader.prototype.model.Chapter;
import com.mangareader.prototype.model.Manga;
import com.mangareader.prototype.model.SearchParams;
import com.mangareader.prototype.model.SearchResult;

public interface MangaSource {
    String getName();

    String getId();

    /**
     * Basic search method for backward compatibility
     */
    List<Manga> search(String query, boolean includeNsfw);

    /**
     * Advanced search with filters and pagination
     */
    SearchResult advancedSearch(SearchParams params);

    /**
     * Get the available genres for this source
     */
    List<String> getAvailableGenres();

    /**
     * Get the available status options for this source
     */
    List<String> getAvailableStatuses();

    Optional<Manga> getMangaDetails(String mangaId);

    List<Chapter> getChapters(String mangaId);

    List<String> getChapterPages(String mangaId, String chapterId);

    String getCoverUrl(String mangaId);
}