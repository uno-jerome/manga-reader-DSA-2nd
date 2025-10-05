package com.mangareader.prototype.model;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents the result of a paginated search.
 * It includes the found manga items and pagination information.
 */
public class SearchResult {
    private List<Manga> results;
    private int currentPage;
    private int totalPages;
    private int totalResults;
    private boolean hasNextPage;
    private boolean hasPreviousPage;

    public SearchResult() {
        this.results = new ArrayList<>();
        this.currentPage = 1;
        this.totalPages = 1;
        this.totalResults = 0;
        this.hasNextPage = false;
        this.hasPreviousPage = false;
    }

    public List<Manga> getResults() {
        return results;
    }

    public void setResults(List<Manga> results) {
        this.results = results != null ? results : new ArrayList<>();
    }

    public void addResult(Manga manga) {
        if (manga != null) {
            results.add(manga);
        }
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = Math.max(1, currentPage);
        this.hasPreviousPage = this.currentPage > 1;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = Math.max(1, totalPages);
        this.hasNextPage = this.currentPage < this.totalPages;
    }

    public int getTotalResults() {
        return totalResults;
    }

    public void setTotalResults(int totalResults) {
        this.totalResults = Math.max(0, totalResults);
    }

    public boolean hasNextPage() {
        return hasNextPage;
    }

    public boolean hasPreviousPage() {
        return hasPreviousPage;
    }

    public void updatePaginationInfo() {
        this.hasPreviousPage = this.currentPage > 1;
        this.hasNextPage = this.currentPage < this.totalPages;
    }
}
