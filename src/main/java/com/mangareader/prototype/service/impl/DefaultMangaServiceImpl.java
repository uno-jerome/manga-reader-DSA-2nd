package com.mangareader.prototype.service.impl;

public class DefaultMangaServiceImpl extends MangaServiceImpl {

    @Override
    public java.util.List<String> getChapterPages(String mangaId, String chapterId) {
        return new com.mangareader.prototype.source.impl.MangaDexSource().getChapterPages(mangaId, chapterId);
    }
}
