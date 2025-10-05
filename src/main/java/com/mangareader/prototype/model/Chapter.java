package com.mangareader.prototype.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Chapter {
    private String id;
    private String mangaId;
    private String title;
    private double number;
    private String volume;
    private List<String> pageUrls;
    private LocalDateTime releaseDate;
    private String readingFormat; // "normal" or "webtoon"
    private boolean downloaded;
    private String downloadPath;

    public Chapter() {
        this.pageUrls = new ArrayList<>();
        this.readingFormat = "normal"; // Default to normal reading mode
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMangaId() {
        return mangaId;
    }

    public void setMangaId(String mangaId) {
        this.mangaId = mangaId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getNumber() {
        return number;
    }

    public void setNumber(double number) {
        this.number = number;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public List<String> getPageUrls() {
        return pageUrls;
    }

    public void setPageUrls(List<String> pageUrls) {
        this.pageUrls = pageUrls;
    }

    public LocalDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public boolean isDownloaded() {
        return downloaded;
    }

    public void setDownloaded(boolean downloaded) {
        this.downloaded = downloaded;
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public void setDownloadPath(String downloadPath) {
        this.downloadPath = downloadPath;
    }

    public String getReadingFormat() {
        return readingFormat;
    }

    public void setReadingFormat(String readingFormat) {
        this.readingFormat = readingFormat;
    }
}