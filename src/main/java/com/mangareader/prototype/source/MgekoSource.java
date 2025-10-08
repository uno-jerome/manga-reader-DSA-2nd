package com.mangareader.prototype.source;

import com.mangareader.prototype.model.Chapter;
import com.mangareader.prototype.model.Manga;
import com.mangareader.prototype.model.SearchParams;
import com.mangareader.prototype.model.SearchResult;
import com.mangareader.prototype.util.Logger;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MangaGeko (mgeko.cc) source implementation
 * Based on Keiyoushi's MangaRawClub extension
 * https://github.com/keiyoushi/extensions-source/blob/main/src/en/mangarawclub/src/eu/kanade/tachiyomi/extension/en/mangarawclub/MangaRawClub.kt
 */
public class MgekoSource implements MangaSource {
    private static final String BASE_URL = "https://www.mgeko.cc";
    private static final String NAME = "MangaGeko";
    
    private final CloseableHttpClient httpClient;

    public MgekoSource() {
        this.httpClient = HttpClients.createDefault();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getId() {
        return "mgeko";
    }

    public List<Manga> getPopularManga(int page) {
        List<Manga> results = new ArrayList<>();
        try {
            String url = BASE_URL + "/browse-comics/?results=" + page + "&filter=views";
            Logger.info("MgekoSource", "Fetching popular manga: " + url);

            Document doc = fetchDocument(url);
            
            Elements mangaElements = doc.select("ul.novel-list > li.novel-item");
            
            Logger.info("MgekoSource", "Found " + mangaElements.size() + " manga elements");
            
            for (Element element : mangaElements) {
                try {
                    Manga manga = parseMangaFromElement(element);
                    if (manga != null && !results.contains(manga)) {
                        results.add(manga);
                    }
                } catch (Exception e) {
                    Logger.error("MgekoSource", "Error parsing manga element", e);
                }
            }
            
            Logger.info("MgekoSource", "Found " + results.size() + " popular manga");
        } catch (Exception e) {
            Logger.error("MgekoSource", "Error fetching popular manga", e);
        }
        return results;
    }

    @Override
    public List<Manga> search(String query, boolean includeNsfw) {
        List<Manga> results = new ArrayList<>();
        try {
            // MangaGeko search using the search endpoint
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = BASE_URL + "/search/?search=" + encodedQuery;
            Logger.info("MgekoSource", "Searching: " + url);

            Document doc = fetchDocument(url);
            
            Elements mangaElements = doc.select("ul.novel-list > li.novel-item");
            
            Logger.info("MgekoSource", "Found " + mangaElements.size() + " search result elements");
            
            for (Element element : mangaElements) {
                try {
                    Manga manga = parseMangaFromElement(element);
                    if (manga != null && !results.contains(manga)) {
                        results.add(manga);
                    }
                } catch (Exception e) {
                    Logger.error("MgekoSource", "Error parsing search result", e);
                }
            }
            
            Logger.info("MgekoSource", "Search found " + results.size() + " results");
        } catch (Exception e) {
            Logger.error("MgekoSource", "Error searching manga", e);
        }
        return results;
    }

    @Override
    public SearchResult advancedSearch(SearchParams params) {
        SearchResult result = new SearchResult();
        
        if (params.getQuery() == null || params.getQuery().trim().isEmpty()) {
            Logger.info("MgekoSource", "No query - loading popular manga");
            List<Manga> popularMangas = getPopularManga(params.getPage());
            result.setResults(popularMangas);
            result.setTotalResults(popularMangas.size());
            result.setCurrentPage(params.getPage());
            result.setTotalPages(1); // mgeko doesn't have clear pagination info
        } else {
            List<Manga> mangas = search(params.getQuery(), params.isIncludeNsfw());
            result.setResults(mangas);
            result.setTotalResults(mangas.size());
            result.setCurrentPage(1);
            result.setTotalPages(1);
        }
        
        return result;
    }

    @Override
    public List<String> getAvailableGenres() {
        // Based on mgeko.cc browse page genres
        return List.of(
            "Action", "Adult", "Adventure", "Comedy", "Doujinshi", "Drama",
            "Ecchi", "Fantasy", "Gender bender", "Harem", "Historical", "Horror",
            "Isekai", "Josei", "Manhua", "Manhwa", "Martial arts", "Mature",
            "Mystery", "One shot", "Psychological", "Romance", "School life",
            "Sci fi", "Seinen", "Shoujo", "Shounen", "Slice of life", "Sports",
            "Supernatural", "Tragedy", "Webtoons"
        );
    }

    @Override
    public List<String> getAvailableStatuses() {
        return List.of("Ongoing", "Completed", "Cancelled", "On Hiatus");
    }

    @Override
    public Optional<Manga> getMangaDetails(String mangaId) {
        try {
            String url = BASE_URL + "/manga/" + mangaId + "/";
            Logger.info("MgekoSource", "Getting manga details: " + url);

            Document doc = fetchDocument(url);
            
            Manga manga = new Manga();
            manga.setId(mangaId);
            manga.setSource(NAME);
            
            Element titleElement = doc.selectFirst("h1");
            if (titleElement != null) {
                manga.setTitle(titleElement.text().trim());
            }
            
            // Element altTitleElement = doc.selectFirst("h2.alternative-title");
            
            Element authorElement = doc.selectFirst("div.author span[itemprop=author]");
            if (authorElement != null) {
                String author = authorElement.text().trim();
                manga.setAuthor(author.equals("Updating") ? "Unknown" : author);
                manga.setArtist(manga.getAuthor()); // Usually same
            }
            
            Element coverElement = doc.selectFirst("div.rank img");
            if (coverElement == null) {
                coverElement = doc.selectFirst("meta[property=og:image]");
                if (coverElement != null) {
                    String coverUrl = coverElement.attr("content");
                    if (!coverUrl.isEmpty()) {
                        manga.setCoverUrl(coverUrl);
                    }
                }
            } else {
                String coverUrl = coverElement.attr("src");
                if (coverUrl.isEmpty()) {
                    coverUrl = coverElement.attr("data-src");
                }
                if (!coverUrl.isEmpty() && !coverUrl.startsWith("http")) {
                    coverUrl = BASE_URL + coverUrl;
                }
                manga.setCoverUrl(coverUrl);
            }
            
            Element descElement = doc.selectFirst("p.description");
            if (descElement != null) {
                String description = descElement.text().trim();
                if (description.contains("The Summary is")) {
                    int summaryIndex = description.indexOf("The Summary is");
                    description = description.substring(summaryIndex + "The Summary is".length()).trim();
                }
                manga.setDescription(description);
            }
            
            Elements genreElements = doc.select("div.categories a");
            List<String> genres = new ArrayList<>();
            for (Element genre : genreElements) {
                String genreText = genre.text().trim();
                if (!genreText.isEmpty() && !genreText.equalsIgnoreCase("Categories")) {
                    genres.add(genreText);
                }
            }
            manga.setGenres(genres);
            
            Element statusElement = doc.selectFirst("div.status span:last-child");
            if (statusElement != null) {
                String status = statusElement.text().trim();
                manga.setStatus(mapStatus(status));
            }
            
            // Element ratingElement = doc.selectFirst("span[itemprop=ratingValue]");
            
            return Optional.of(manga);
        } catch (Exception e) {
            Logger.error("MgekoSource", "Error getting manga details for: " + mangaId, e);
            return Optional.empty();
        }
    }

    @Override
    public List<Chapter> getChapters(String mangaId) {
        List<Chapter> chapters = new ArrayList<>();
        try {
            // Important: mgeko requires /all-chapters/ suffix to get full chapter list
            String url = BASE_URL + "/manga/" + mangaId + "/all-chapters/";
            Logger.info("MgekoSource", "Getting chapters: " + url);

            Document doc = fetchDocument(url);
            
            Elements chapterElements = doc.select("ul.chapter-list > li");
            
            Logger.info("MgekoSource", "Found " + chapterElements.size() + " chapter elements");
            
            for (Element element : chapterElements) {
                try {
                    Chapter chapter = parseChapterFromElement(element, mangaId);
                    if (chapter != null) {
                        chapters.add(chapter);
                    }
                } catch (Exception e) {
                    Logger.error("MgekoSource", "Error parsing chapter", e);
                }
            }
            
            Logger.info("MgekoSource", "Found " + chapters.size() + " chapters");
        } catch (Exception e) {
            Logger.error("MgekoSource", "Error getting chapters", e);
        }
        return chapters;
    }

    @Override
    public List<String> getChapterPages(String mangaId, String chapterId) {
        List<String> pages = new ArrayList<>();
        try {
            String url = BASE_URL + chapterId;
            Logger.info("MgekoSource", "Getting chapter pages: " + url);

            Document doc = fetchDocument(url);
            
            // MangaGeko uses: <div class="page-in"><img onerror="..." src="actual-image-url"></div>
            Elements imageElements = doc.select(".page-in img[onerror]");
            
            Logger.info("MgekoSource", "Found " + imageElements.size() + " page elements");
            
            for (Element img : imageElements) {
                String imageUrl = img.attr("src");
                
                if (!imageUrl.isEmpty() && !imageUrl.contains("loading.gif") && !imageUrl.contains("placeholder")) {
                    if (!imageUrl.startsWith("http")) {
                        imageUrl = BASE_URL + imageUrl;
                    }
                    pages.add(imageUrl);
                }
            }
            
            Logger.info("MgekoSource", "Found " + pages.size() + " pages");
        } catch (Exception e) {
            Logger.error("MgekoSource", "Error getting chapter pages", e);
        }
        return pages;
    }

    /**
     * Fetch and parse HTML document from URL
     */
    private Document fetchDocument(String url) throws IOException {
        HttpGet request = new HttpGet(url);
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        request.addHeader("Referer", BASE_URL + "/");
        
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            try {
                String html = EntityUtils.toString(response.getEntity());
                return Jsoup.parse(html);
            } catch (org.apache.hc.core5.http.ParseException e) {
                throw new IOException("Failed to parse response", e);
            }
        }
    }

    /**
     * Parse manga from HTML element (li.novel-item)
     * Structure:
     * <li class="novel-item">
     *   <a href="/manga/{slug}/">
     *     <div class="cover-wrap">
     *       <figure class="novel-cover">
     *         <img data-src="..." alt="Title">
     *       </figure>
     *     </div>
     *     <h4 class="novel-title">Title</h4>
     *   </a>
     * </li>
     */
    private Manga parseMangaFromElement(Element element) {
        try {
            Manga manga = new Manga();
            
            Element linkElement = element.selectFirst("a");
            if (linkElement == null) {
                return null;
            }
            
            String href = linkElement.attr("href");
            if (!href.startsWith("/manga/")) {
                return null;
            }
            
            String slug = href.replace("/manga/", "").replace("/", "");
            manga.setId(slug);
            manga.setSource(NAME);
            
            Element titleElement = element.selectFirst("h4.novel-title, .novel-title");
            if (titleElement != null) {
                manga.setTitle(titleElement.text().trim());
            } else {
                manga.setTitle(linkElement.attr("title"));
            }
            
            Element imgElement = element.selectFirst(".novel-cover img, img");
            if (imgElement != null) {
                String coverUrl = imgElement.attr("data-src");
                if (coverUrl.isEmpty()) {
                    coverUrl = imgElement.attr("src");
                }
                if (!coverUrl.isEmpty() && !coverUrl.startsWith("http")) {
                    coverUrl = BASE_URL + coverUrl;
                }
                manga.setCoverUrl(coverUrl);
            }
            
            return manga;
        } catch (Exception e) {
            Logger.error("MgekoSource", "Error parsing manga element", e);
            return null;
        }
    }

    /**
     * Parse chapter from HTML element
     * Structure from Keiyoushi:
     * <li>
     *   <a href="/reader/en/{slug}-chapter-{num}/">
     *     <div class="chapter-number">Chapter 56-eng-li</div>
     *   </a>
     * </li>
     */
    private Chapter parseChapterFromElement(Element element, String mangaId) {
        try {
            Chapter chapter = new Chapter();
            chapter.setMangaId(mangaId);
            
            Element linkElement = element.selectFirst("a");
            if (linkElement != null) {
                String chapterUrl = linkElement.attr("href");
                chapter.setId(chapterUrl); // Store full path as ID (e.g., "/reader/en/...")
                
                Element chapterTitleElement = linkElement.selectFirst(".chapter-title, .chapter-number");
                if (chapterTitleElement != null) {
                    String chapterText = chapterTitleElement.ownText().trim();
                    chapterText = chapterText.replace("-eng-li", "");
                    
                    try {
                        String numberStr = chapterText.replaceAll("[^0-9.]", "");
                        if (!numberStr.isEmpty()) {
                            chapter.setNumber(Float.parseFloat(numberStr));
                        }
                    } catch (NumberFormatException e) {
                        chapter.setNumber(0);
                    }
                    
                    chapter.setTitle("Chapter " + chapterText);
                } else {
                    // Fallback: extract from URL
                    String[] parts = chapterUrl.split("-chapter-");
                    if (parts.length > 1) {
                        String numPart = parts[1].replace("/", "").replace("-eng-li", "");
                        chapter.setTitle("Chapter " + numPart);
                        try {
                            chapter.setNumber(Float.parseFloat(numPart.replaceAll("[^0-9.]", "")));
                        } catch (NumberFormatException e) {
                            chapter.setNumber(0);
                        }
                    }
                }
            }
            
            
            return chapter;
        } catch (Exception e) {
            Logger.error("MgekoSource", "Error parsing chapter element", e);
            return null;
        }
    }

    /**
     * Map mgeko status to standard status
     */
    private String mapStatus(String status) {
        if (status == null || status.isEmpty()) {
            return "Unknown";
        }
        
        String lower = status.toLowerCase();
        if (lower.contains("ongoing") || lower.contains("updating")) {
            return "Ongoing";
        } else if (lower.contains("completed") || lower.contains("complete")) {
            return "Completed";
        } else if (lower.contains("hiatus") || lower.contains("on hold")) {
            return "On Hiatus";
        } else if (lower.contains("cancelled") || lower.contains("dropped")) {
            return "Cancelled";
        }
        
        return status;
    }

    @Override
    public String getCoverUrl(String mangaId) {
        try {
            Optional<Manga> manga = getMangaDetails(mangaId);
            if (manga.isPresent()) {
                return manga.get().getCoverUrl();
            }
        } catch (Exception e) {
            Logger.error("MgekoSource", "Error getting cover URL", e);
        }
        return null;
    }
}
