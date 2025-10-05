package com.mangareader.prototype.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class represents search parameters for advanced manga searches.
 * It includes filters for genres, status, and pagination.
 */
public class SearchParams {
    private String query;
    private boolean includeNsfw;
    private List<String> includedGenres;
    private List<String> excludedGenres;
    private String status;
    private int page;
    private int limit;
    private Map<String, String> additionalParams;

    public SearchParams() {
        this.includedGenres = new ArrayList<>();
        this.excludedGenres = new ArrayList<>();
        this.page = 1;
        this.limit = 20;
        this.additionalParams = new HashMap<>();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isIncludeNsfw() {
        return includeNsfw;
    }

    public void setIncludeNsfw(boolean includeNsfw) {
        this.includeNsfw = includeNsfw;
    }

    public List<String> getIncludedGenres() {
        return includedGenres;
    }

    public void addIncludedGenre(String genre) {
        if (!includedGenres.contains(genre)) {
            includedGenres.add(genre);
        }
    }

    public void removeIncludedGenre(String genre) {
        includedGenres.remove(genre);
    }

    public List<String> getExcludedGenres() {
        return excludedGenres;
    }

    public void addExcludedGenre(String genre) {
        if (!excludedGenres.contains(genre)) {
            excludedGenres.add(genre);
        }
    }

    public void removeExcludedGenre(String genre) {
        excludedGenres.remove(genre);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(1, page);
    }

    public void nextPage() {
        this.page++;
    }

    public void previousPage() {
        if (this.page > 1) {
            this.page--;
        }
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = Math.max(1, limit); 
    }

    public Map<String, String> getAdditionalParams() {
        return additionalParams;
    }

    public void addAdditionalParam(String key, String value) {
        additionalParams.put(key, value);
    }

    public void removeAdditionalParam(String key) {
        additionalParams.remove(key);
    }

    public void clearParams() {
        query = null;
        includedGenres.clear();
        excludedGenres.clear();
        status = null;
        page = 1;
        additionalParams.clear();
    }
}
