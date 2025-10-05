package com.mangareader.prototype.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Manga {
    private String id;
    private String title;
    private String author;
    private String artist;
    private String description;
    private List<String> genres;
    private String status;
    private String coverUrl;
    private LocalDateTime lastUpdated;
    private List<Chapter> chapters;
    private String language;
    private String readingFormat;

    public Manga() {
        this.genres = new ArrayList<>();
        this.chapters = new ArrayList<>();
        this.readingFormat = "normal";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    public void setChapters(List<Chapter> chapters) {
        this.chapters = chapters;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getReadingFormat() {
        return readingFormat;
    }

    public void setReadingFormat(String readingFormat) {
        this.readingFormat = readingFormat;
    }
}