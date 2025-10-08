package com.mangareader.prototype.source;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mangareader.prototype.model.Chapter;
import com.mangareader.prototype.model.Manga;
import com.mangareader.prototype.model.SearchParams;
import com.mangareader.prototype.model.SearchResult;
import com.mangareader.prototype.util.Logger;

public class MangaDexSource implements MangaSource {
    private static final String BASE_URL = "https://api.mangadex.org";
    private static final String COVER_BASE_URL = "https://uploads.mangadex.org/covers";
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public MangaDexSource() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClients.createDefault();
    }

    @Override
    public String getName() {
        return "MangaDex";
    }

    @Override
    public String getId() {
        return "mangadex";
    }

    @Override
    public List<Manga> search(String query, boolean includeNsfw) {
        List<Manga> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String url = String.format(
                    "%s/manga?title=%s&limit=20&includes[]=cover_art&includes[]=author&includes[]=artist", BASE_URL,
                    encodedQuery);

            if (!includeNsfw) {
                url += "&contentRating[]=safe&contentRating[]=suggestive";
            }

            System.out.println("Requesting URL: " + url);
            HttpGet request = new HttpGet(url);

            HttpClientResponseHandler<String> responseHandler = response -> {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    System.out.println("API Response received with status: " + status);
                    // Print a small preview of the response for debugging
                    if (responseBody.length() > 500) {
                        System.out.println("Response preview: " + responseBody.substring(0, 500) + "...");
                    } else {
                        System.out.println("Response: " + responseBody);
                    }
                    return responseBody;
                } else {
                    throw new IOException("Unexpected response status: " + status);
                }
            };

            String response = httpClient.execute(request, responseHandler);
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");

            if (data.isArray()) {
                for (JsonNode mangaNode : data) {
                    Manga manga = parseMangaFromJson(mangaNode);
                    results.add(manga);
                }
            }
        } catch (IOException e) {
            Logger.error("MangaDexSource", "Error searching manga", e);
        }
        return results;
    }

    @Override
    public SearchResult advancedSearch(SearchParams params) {
        SearchResult result = new SearchResult();
        try {
            StringBuilder urlBuilder = new StringBuilder(
                    String.format("%s/manga?includes[]=cover_art&includes[]=author&includes[]=artist", BASE_URL));

            if (params.getQuery() != null && !params.getQuery().isEmpty()) {
                String encodedQuery = URLEncoder.encode(params.getQuery(), StandardCharsets.UTF_8.toString());
                urlBuilder.append("&title=").append(encodedQuery);
            }

            if (!params.isIncludeNsfw()) {
                urlBuilder.append("&contentRating[]=safe&contentRating[]=suggestive");
            } else {
                urlBuilder.append(
                        "&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica&contentRating[]=pornographic");
            }

            if (!params.getIncludedGenres().isEmpty()) {
                for (String genre : params.getIncludedGenres()) {
                    String genreId = getGenreIdByName(genre);
                    if (genreId != null) {
                        urlBuilder.append("&includedTags[]=").append(genreId);
                    }
                }
            }

            if (!params.getExcludedGenres().isEmpty()) {
                for (String genre : params.getExcludedGenres()) {
                    String genreId = getGenreIdByName(genre);
                    if (genreId != null) {
                        urlBuilder.append("&excludedTags[]=").append(genreId);
                    }
                }
            }

            if (params.getStatus() != null && !params.getStatus().isEmpty()) {
                urlBuilder.append("&status[]=").append(params.getStatus());
            }

            urlBuilder.append("&limit=").append(params.getLimit());
            urlBuilder.append("&offset=").append((params.getPage() - 1) * params.getLimit());

            for (String key : params.getAdditionalParams().keySet()) {
                String value = params.getAdditionalParams().get(key);
                urlBuilder.append("&").append(key).append("=").append(value);
            }

            String url = urlBuilder.toString();
            System.out.println("Advanced search URL: " + url);
            HttpGet request = new HttpGet(url);

            HttpClientResponseHandler<String> responseHandler = response -> {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    return EntityUtils.toString(response.getEntity());
                } else {
                    throw new IOException("Unexpected response status: " + status);
                }
            };

            String response = httpClient.execute(request, responseHandler);
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");

            if (root.has("total")) {
                int totalResults = root.get("total").asInt();
                result.setTotalResults(totalResults);

                int totalPages = (int) Math.ceil((double) totalResults / params.getLimit());
                result.setTotalPages(totalPages);
            } else {
                result.setTotalPages(1);
                result.setTotalResults(data.size());
            }

            result.setCurrentPage(params.getPage());
            result.updatePaginationInfo();

            List<Manga> mangas = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode mangaNode : data) {
                    Manga manga = parseMangaFromJson(mangaNode);
                    mangas.add(manga);
                }
            }
            result.setResults(mangas);

        } catch (IOException e) {
            Logger.error("MangaDexSource", "Error in advanced search", e);
        }
        return result;
    }

    @Override
    public List<String> getAvailableGenres() {
        return Arrays.asList(
                "Action", "Adventure", "Comedy", "Drama", "Fantasy",
                "Horror", "Mystery", "Psychological", "Romance",
                "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
                "School Life", "Shounen", "Shoujo", "Seinen", "Josei",
                "Martial Arts", "Historical", "Medical", "Music", "Military");
    }

    @Override
    public List<String> getAvailableStatuses() {
        return Arrays.asList(
                "ongoing", "completed", "hiatus", "cancelled");
    }

    private String getGenreIdByName(String genreName) { // These are the actual UUID-based tag IDs from MangaDex API
        switch (genreName.toLowerCase()) {
            case "action":
                return "391b0423-d847-456f-aff0-8b0cfc03066b";
            case "adventure":
                return "87cc87cd-a395-47af-b27a-93258283bbc6";
            case "comedy":
                return "4d32cc48-9f00-4cca-9b5a-a839f0764984";
            case "drama":
                return "b9af3a63-f058-46de-a9a0-e0c13906197a";
            case "fantasy":
                return "cdc58593-87dd-415e-bbc0-2ec27bf404cc";
            case "horror":
                return "cdad7e68-1419-41dd-bdce-27753074a640";
            case "mystery":
                return "ee968100-4191-4968-93d3-f82d72be7e46";
            case "psychological":
                return "3b60b75c-a2d7-4860-ab56-05f391bb889c";
            case "romance":
                return "423e2eae-a7a2-4a8b-ac03-a8351462d71d";
            case "sci-fi":
                return "256c8bd9-4904-4360-bf4f-508a76d67183";
            case "slice of life":
                return "e5301a23-ebd9-49dd-a0cb-2add944c7fe9";
            case "sports":
                return "69964a64-2f90-4d33-beeb-f3ed2875eb4c";
            case "supernatural":
                return "eabc5b4c-6aff-42f3-b657-3e90cbd00b75";
            case "thriller":
                return "07251805-a27e-4d59-b488-f0bfbec15168";
            case "school life":
                return "caaa44eb-cd40-4177-b930-79d3ef2afe87";
            case "shounen":
                return "27a532ba-8adc-4e1d-ab25-4b4b680b0d7b";
            case "shoujo":
                return "a3c67850-4684-404e-9b7f-c69850ee5da6";
            case "seinen":
                return "a1f53773-c69a-4ce5-8cab-fffcd90b1565";
            case "josei":
                return "aa04485a-91c2-4b02-9b59-ec9f0e86b9c3";
            case "martial arts":
                return "799c202e-7daa-44eb-9cf7-8a3c0441531e";
            case "historical":
                return "33771934-028e-4cb3-8744-691e866a923e";
            case "medical":
                return "c8cbe35b-1b2b-4a3f-9c37-db84c4514856";
            case "music":
                return "f8f62932-27da-4fe4-8ee1-6779a8c5edba";
            case "military":
                return "ac72833b-c4e9-4878-b9db-6c8a4a99444a";
            default:
                return null;
        }
    }

    @Override
    public Optional<Manga> getMangaDetails(String mangaId) {
        try {
            String url = String.format("%s/manga/%s?includes[]=cover_art&includes[]=author&includes[]=artist", BASE_URL,
                    mangaId);
            System.out.println("Getting manga details from URL: " + url);
            HttpGet request = new HttpGet(url);

            HttpClientResponseHandler<String> responseHandler = response -> {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    return EntityUtils.toString(response.getEntity());
                } else {
                    throw new IOException("Unexpected response status: " + status);
                }
            };

            String response = httpClient.execute(request, responseHandler);
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");

            if (data != null) {
                Manga manga = parseMangaFromJson(data);
                return Optional.of(manga);
            }
        } catch (IOException e) {
            Logger.error("MangaDexSource", "Error getting manga details", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Chapter> getChapters(String mangaId) {
        List<Chapter> chapters = new ArrayList<>();
        try {
            String mangaReadingFormat = "normal"; // default
            Optional<Manga> mangaDetails = getMangaDetails(mangaId);
            if (mangaDetails.isPresent() && mangaDetails.get().getReadingFormat() != null) {
                mangaReadingFormat = mangaDetails.get().getReadingFormat();
            }

            String url = String.format("%s/manga/%s/feed?translatedLanguage[]=en&limit=500", BASE_URL, mangaId);
            HttpGet request = new HttpGet(url);

            HttpClientResponseHandler<String> responseHandler = response -> {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    return EntityUtils.toString(response.getEntity());
                } else {
                    throw new IOException("Unexpected response status: " + status);
                }
            };

            String response = httpClient.execute(request, responseHandler);
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");

            if (data.isArray()) {
                for (JsonNode chapterNode : data) {
                    Chapter chapter = parseChapterFromJson(chapterNode);
                    chapter.setMangaId(mangaId);
                    chapter.setReadingFormat(mangaReadingFormat);
                    chapters.add(chapter);
                }
            }
        } catch (IOException e) {
            Logger.error("MangaDexSource", "Error getting chapters", e);
        }
        return chapters;
    }

    @Override
    public List<String> getChapterPages(String mangaId, String chapterId) {
        List<String> pages = new ArrayList<>();
        try {
            String url = String.format("%s/at-home/server/%s", BASE_URL, chapterId);
            System.out.println("Fetching chapter pages from URL: " + url);
            System.out.println("Chapter ID: " + chapterId);
            HttpGet request = new HttpGet(url);

            HttpClientResponseHandler<String> responseHandler = response -> {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    System.out.println("Chapter pages API response status: " + status);
                    System.out.println("Response preview: "
                            + (responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody));
                    return responseBody;
                } else {
                    System.err.println("Error response status for chapter pages: " + status);
                    throw new IOException("Unexpected response status: " + status);
                }
            };

            String response = httpClient.execute(request, responseHandler);
            JsonNode root = objectMapper.readTree(response);

            JsonNode resultNode = root.get("result");
            if (resultNode == null || !"ok".equals(resultNode.asText())) {
                System.err.println(
                        "API response result is not 'ok': " + (resultNode != null ? resultNode.asText() : "null"));
                return pages;
            }

            JsonNode baseUrlNode = root.get("baseUrl");
            if (baseUrlNode == null) {
                System.err.println("No 'baseUrl' found in response");
                return pages;
            }
            String baseUrl = baseUrlNode.asText();
            System.out.println("Base URL: " + baseUrl);

            JsonNode chapter = root.get("chapter");
            if (chapter == null) {
                System.err.println("No 'chapter' node found in response");
                return pages;
            }

            JsonNode hash = chapter.get("hash");
            JsonNode dataArray = chapter.get("data");

            if (hash == null) {
                System.err.println("No 'hash' found in chapter data");
                return pages;
            }

            if (dataArray == null || !dataArray.isArray()) {
                System.err.println("No 'data' array found in chapter data");
                return pages;
            }

            System.out.println("Found " + dataArray.size() + " pages for chapter");
            for (JsonNode page : dataArray) {
                String pageUrl = String.format("%s/data/%s/%s", baseUrl, hash.asText(), page.asText());
                pages.add(pageUrl);
                System.out.println("Added page URL: " + pageUrl);
            }
        } catch (IOException e) {
            System.err.println("Error fetching chapter pages: " + e.getMessage());
            Logger.error("MangaDexSource", "Error getting chapter pages", e);
        }

        System.out.println("Total pages found: " + pages.size());
        return pages;
    }

    @Override
    public String getCoverUrl(String mangaId) {
        try {
            String url = String.format("%s/manga/%s?includes[]=cover_art", BASE_URL, mangaId);
            HttpGet request = new HttpGet(url);

            HttpClientResponseHandler<String> responseHandler = response -> {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    return EntityUtils.toString(response.getEntity());
                } else {
                    throw new IOException("Unexpected response status: " + status);
                }
            };

            String response = httpClient.execute(request, responseHandler);
            JsonNode root = objectMapper.readTree(response);
            JsonNode relationships = root.get("data").get("relationships");

            if (relationships.isArray()) {
                for (JsonNode rel : relationships) {
                    if ("cover_art".equals(rel.get("type").asText())) {
                        JsonNode attributes = rel.path("attributes");
                        if (attributes != null && !attributes.isMissingNode()) {
                            JsonNode fileNameNode = attributes.path("fileName");
                            if (!fileNameNode.isMissingNode() && !fileNameNode.isNull()) {
                                String fileName = fileNameNode.asText();
                                return String.format("%s/%s/%s", COVER_BASE_URL, mangaId, fileName);
                            }
                        }
                        String coverId = rel.get("id").asText();
                        return String.format("%s/%s/%s.jpg", COVER_BASE_URL, mangaId, coverId);
                    }
                }
            }
        } catch (IOException e) {
            Logger.error("MangaDexSource", "Error getting cover URL", e);
        }
        return null;
    }

    private Manga parseMangaFromJson(JsonNode node) {
        Manga manga = new Manga();
        JsonNode attributes = node.path("attributes");
        manga.setId(node.path("id").asText());
        
        // Extract title with fallback to multiple languages
        // MangaDex API returns titles as: {"en": "Title", "ja": "タイトル", "ja-ro": "Romaji"}
        String title = extractLocalizedString(attributes.path("title"));
        manga.setTitle(title != null && !title.isEmpty() ? title : "Unknown Title");
        
        // Extract description with same fallback mechanism
        String description = extractLocalizedString(attributes.path("description"));
        manga.setDescription(description != null && !description.isEmpty() ? description : "No description available");
        
        manga.setStatus(attributes.path("status").asText());
        String updatedAt = attributes.path("updatedAt").asText("");
        if (!updatedAt.isEmpty()) {
            manga.setLastUpdated(LocalDateTime.parse(updatedAt, DateTimeFormatter.ISO_DATE_TIME));
        }
        List<String> genres = new ArrayList<>();
        JsonNode tags = attributes.path("tags");
        if (tags.isArray()) {
            for (JsonNode tag : tags) {
                JsonNode tagName = tag.path("attributes").path("name").path("en");
                if (!tagName.isMissingNode())
                    genres.add(tagName.asText());
            }
        }
        manga.setGenres(genres);
        manga.setLanguage(attributes.path("originalLanguage").asText("en"));
        StringBuilder authors = new StringBuilder();
        StringBuilder artists = new StringBuilder();
        JsonNode relationships = node.path("relationships");
        if (relationships.isArray()) {
            for (JsonNode rel : relationships) {
                String type = rel.path("type").asText();
                if (rel != null && "author".equals(type)) {
                    JsonNode attr = rel.path("attributes");
                    if (!attr.isMissingNode() && attr.has("name")) {
                        if (authors.length() > 0)
                            authors.append(", ");
                        authors.append(attr.get("name").asText());
                        System.out.println("Found author: " + attr.get("name").asText());
                    }
                }
                if (rel != null && "artist".equals(type)) {
                    JsonNode attr = rel.path("attributes");
                    if (!attr.isMissingNode() && attr.has("name")) {
                        if (artists.length() > 0)
                            artists.append(", ");
                        artists.append(attr.get("name").asText());
                        System.out.println("Found artist: " + attr.get("name").asText());
                    }
                }
                if (rel != null && "cover_art".equals(rel.path("type").asText())) {
                    JsonNode attrNode = rel.path("attributes");
                    JsonNode fileNameNode = attrNode.path("fileName");
                    if (!fileNameNode.isMissingNode() && !fileNameNode.isNull()) {
                        String coverFileName = fileNameNode.asText();
                        String constructedUrl = String.format("%s/%s/%s", COVER_BASE_URL, manga.getId(), coverFileName);
                        manga.setCoverUrl(constructedUrl);
                        System.out.println("Set cover URL: " + constructedUrl);
                    }
                }
            }
        }
        manga.setAuthor(authors.toString());
        manga.setArtist(artists.toString());
        if (manga.getCoverUrl() == null)
            manga.setCoverUrl("");
        return manga;
    }

    private Chapter parseChapterFromJson(JsonNode node) {
        Chapter chapter = new Chapter();
        JsonNode attributes = node.get("attributes");

        if (node.has("id")) {
            chapter.setId(node.get("id").asText());
        }

        if (attributes != null) {
            if (attributes.has("mangaId")) {
                JsonNode mangaIdNode = attributes.get("mangaId");
                if (mangaIdNode != null && !mangaIdNode.isNull()) {
                    chapter.setMangaId(mangaIdNode.asText());
                }
            }

            if (attributes.has("title")) {
                JsonNode titleNode = attributes.get("title");
                if (titleNode != null && !titleNode.isNull()) {
                    chapter.setTitle(titleNode.asText());
                } else {
                    chapter.setTitle(""); // Default empty title
                }
            } else {
                chapter.setTitle(""); // Default empty title
            }

            if (attributes.has("chapter")) {
                JsonNode chapterNode = attributes.get("chapter");
                if (chapterNode != null && !chapterNode.isNull()) {
                    try {
                        chapter.setNumber(Double.parseDouble(chapterNode.asText()));
                    } catch (NumberFormatException e) {
                        chapter.setNumber(0.0); // Default to 0 if parsing fails
                        System.err.println("Error parsing chapter number: " + e.getMessage());
                    }
                } else {
                    chapter.setNumber(0.0); // Default to 0
                }
            } else {
                chapter.setNumber(0.0); // Default to 0
            }

            if (attributes.has("volume")) {
                JsonNode volumeNode = attributes.get("volume");
                if (volumeNode != null && !volumeNode.isNull()) {
                    chapter.setVolume(volumeNode.asText());
                }
            }

            if (attributes.has("publishAt")) {
                JsonNode publishAtNode = attributes.get("publishAt");
                if (publishAtNode != null && !publishAtNode.isNull()) {
                    String publishAt = publishAtNode.asText();
                    try {
                        chapter.setReleaseDate(LocalDateTime.parse(publishAt,
                                DateTimeFormatter.ISO_DATE_TIME));
                    } catch (Exception e) {
                        System.err.println("Error parsing publish date: " + e.getMessage());
                    }
                }
            }
        }

        return chapter;
    }

    /**
     * Extracts localized string from MangaDex API response with fallback mechanism.
     * Tries multiple language codes in order: en, en-us, ja-ro, ja, and any other available.
     * 
     * @param node JsonNode containing localized strings (e.g., title or description object)
     * @return The first non-empty localized string found, or null if none available
     */
    private String extractLocalizedString(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        // Priority order: English, English-US, Romanized Japanese, Japanese, then any other
        String[] preferredLanguages = { "en", "en-us", "ja-ro", "ja" };

        // Try preferred languages first
        for (String lang : preferredLanguages) {
            if (node.has(lang)) {
                String value = node.get(lang).asText();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        // If no preferred language found, try any available language
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String value = entry.getValue().asText();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        return null;
    }
}