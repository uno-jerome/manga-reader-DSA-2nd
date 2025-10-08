package com.mangareader.prototype.ui.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.mangareader.prototype.model.Manga;
import com.mangareader.prototype.model.SearchParams;
import com.mangareader.prototype.model.SearchResult;
import com.mangareader.prototype.service.MangaServiceImpl;
import com.mangareader.prototype.source.MangaSource;
import com.mangareader.prototype.ui.component.AddSeriesModal;
import com.mangareader.prototype.ui.component.ThemeManager;
import com.mangareader.prototype.util.ImageCache;
import com.mangareader.prototype.util.ThreadPoolManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;

public class AddSeriesView extends VBox implements ThemeManager.ThemeChangeListener {
    private final ComboBox<MangaSource> sourceSelector;
    private final TextField searchField;
    private final Button searchButton;
    private final CheckBox nsfwCheckbox;
    private final CheckBox searchAllSourcesCheckbox; // New checkbox for multi-source search
    private final GridPane mangaGrid;
    private final List<MangaSource> sources;
    private final ScrollPane scrollPane;
    private final ThemeManager themeManager;
    private int columns = 5;
    private List<Manga> currentResults = new ArrayList<>();
    private final Map<String, VBox> mangaNodeCache = new HashMap<>();
    private Consumer<Manga> onMangaSelectedCallback;

    private Pagination pagination;
    private Label resultsCountLabel;
    private int currentPage = 1;
    private int totalPages = 1;
    private final int itemsPerPage = 20;
    private final int CARD_WIDTH = 180;
    private final int CARD_HEIGHT = 270;
    private final int MIN_COLUMNS = 5;
    private final int MAX_COLUMNS = 6;

    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    private Button advancedSearchButton;
    private VBox advancedSearchPane;
    private FlowPane genreSelector;
    private ComboBox<String> statusSelector;
    private final SearchParams searchParams = new SearchParams();
    private final Map<String, CheckBox> genreCheckboxes = new HashMap<>();
    private boolean isAdvancedSearchVisible = false;

    public AddSeriesView() {
        this(null);
    }

    public AddSeriesView(Consumer<Manga> onMangaSelectedCallback) {
        this.onMangaSelectedCallback = onMangaSelectedCallback;
        this.themeManager = ThemeManager.getInstance();

        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_CENTER);

        // Get sources from MangaServiceImpl (includes MangaDex, Mgeko, etc.)
        MangaServiceImpl mangaService = MangaServiceImpl.getInstance();
        sources = new ArrayList<>(mangaService.getAllSources());

        sourceSelector = new ComboBox<>();
        sourceSelector.getItems().addAll(sources);
        sourceSelector.setPromptText("Select Source");
        sourceSelector.setMaxWidth(200);
        sourceSelector.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(MangaSource source, boolean empty) {
                super.updateItem(source, empty);
                setText((empty || source == null) ? null : source.getName());
            }
        });
        sourceSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(MangaSource source, boolean empty) {
                super.updateItem(source, empty);
                setText((empty || source == null) ? "Select Source" : source.getName());
            }
        });
        if (!sources.isEmpty()) {
            sourceSelector.getSelectionModel().selectFirst();
            // Auto-load content from first source
            Platform.runLater(() -> autoLoadSourceContent());
        }

        searchField = new TextField();
        searchField.setPromptText("Search for a series...");
        searchField.setPrefWidth(300);
        searchButton = new Button("Search");

        sourceSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateFiltersIfAvailable(newVal);

                if (searchField != null) {
                    String currentQuery = searchField.getText().trim();
                    if (!currentQuery.isEmpty()) {
                        // Re-search with new source
                        searchParams.setQuery(currentQuery);
                        searchParams.setPage(1);
                        currentPage = 1;
                        pagination.setCurrentPageIndex(0);
                        performAdvancedSearch();
                    } else {
                        // Auto-load popular content for any source
                        autoLoadSourceContent();
                    }
                }
            }
        });

        advancedSearchButton = new Button("Filters");
        advancedSearchButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white;");
        advancedSearchButton.setOnAction(e -> toggleAdvancedSearch());

        nsfwCheckbox = new CheckBox("NSFW");
        nsfwCheckbox.setSelected(false);
        nsfwCheckbox.setTooltip(new Tooltip("Show NSFW content"));

        nsfwCheckbox.setStyle(
                "-fx-font-size: 14px;" +
                        "-fx-text-fill: #666;" +
                        "-fx-background-color: transparent;" + // Overall control background
                        "-fx-box-fill: white;" + // White background for the checkbox box
                        "-fx-box-border: #ccc;" + // Gray border for the checkbox box
                        "-fx-border-width: 1px;" + // Border width for the box
                        "-fx-mark-color: transparent;" // No checkmark visible when unchecked
        );

        nsfwCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                nsfwCheckbox.setStyle(
                        "-fx-font-size: 14px;" +
                                "-fx-text-fill: #666;" + // Text color remains gray
                                "-fx-background-color: transparent;" +
                                "-fx-mark-color: white;" + // White checkmark
                                "-fx-box-fill: #007bff;" + // Blue background for the checkbox box
                                "-fx-box-border: #007bff;" + // Blue border for the checkbox box
                                "-fx-border-width: 1px;");
            } else {
                nsfwCheckbox.setStyle(
                        "-fx-font-size: 14px;" +
                                "-fx-text-fill: #666;" + // Text color remains gray
                                "-fx-background-color: transparent;" +
                                "-fx-mark-color: transparent;" + // Make checkmark transparent when unchecked
                                "-fx-box-fill: white;" + // White background for the checkbox box
                                "-fx-box-border: #ccc;" + // Gray border for the checkbox box
                                "-fx-border-width: 1px;");
            }

            searchParams.setIncludeNsfw(newVal);
        });

        // Add "Search All Sources" checkbox
        searchAllSourcesCheckbox = new CheckBox("All Sources");
        searchAllSourcesCheckbox.setSelected(false);
        searchAllSourcesCheckbox.setTooltip(new Tooltip("Search all available sources (MangaDex, Mgeko, etc.)"));
        searchAllSourcesCheckbox.setStyle(
                "-fx-font-size: 14px;" +
                        "-fx-text-fill: #666;" +
                        "-fx-background-color: transparent;" +
                        "-fx-box-fill: white;" +
                        "-fx-box-border: #ccc;" +
                        "-fx-border-width: 1px;" +
                        "-fx-mark-color: transparent;"
        );

        searchAllSourcesCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                searchAllSourcesCheckbox.setStyle(
                        "-fx-font-size: 14px;" +
                                "-fx-text-fill: #666;" +
                                "-fx-background-color: transparent;" +
                                "-fx-mark-color: white;" +
                                "-fx-box-fill: #28a745;" + // Green for "All Sources"
                                "-fx-box-border: #28a745;" +
                                "-fx-border-width: 1px;");
                sourceSelector.setDisable(true); // Disable source selector when searching all
            } else {
                searchAllSourcesCheckbox.setStyle(
                        "-fx-font-size: 14px;" +
                                "-fx-text-fill: #666;" +
                                "-fx-background-color: transparent;" +
                                "-fx-mark-color: transparent;" +
                                "-fx-box-fill: white;" +
                                "-fx-box-border: #ccc;" +
                                "-fx-border-width: 1px;");
                sourceSelector.setDisable(false); // Re-enable source selector
            }
        });

        HBox searchBox = new HBox(8, sourceSelector, searchField, searchButton, advancedSearchButton, searchAllSourcesCheckbox, nsfwCheckbox);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        setupAdvancedSearchPane();

        resultsCountLabel = new Label("No results");
        resultsCountLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        pagination = new Pagination();
        pagination.setPageCount(1);
        pagination.setCurrentPageIndex(0);
        pagination.setMaxPageIndicatorCount(10);
        pagination.setStyle("-fx-border-color: transparent;");

        pagination.currentPageIndexProperty().addListener((obs, oldVal, newVal) -> {
            if (oldVal.intValue() != newVal.intValue()) {
                currentPage = newVal.intValue() + 1; // Convert from 0-based to 1-based
                searchParams.setPage(currentPage);
                performAdvancedSearch();
            }
        });

        HBox paginationBox = new HBox(20, resultsCountLabel, pagination);
        paginationBox.setAlignment(Pos.CENTER_LEFT);
        paginationBox.setPadding(new Insets(10, 0, 10, 0));

        mangaGrid = new GridPane();
        mangaGrid.setHgap(16);
        mangaGrid.setVgap(16);
        mangaGrid.setPadding(new Insets(16, 0, 0, 0));

        scrollPane = new ScrollPane(mangaGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #181818;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        widthProperty().addListener((obs, oldVal, newVal) -> updateGridColumns());
        scrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> updateGridColumns());
        updateGridColumns();

        updateMangaGridWithPlaceholders();

        searchButton.setOnAction(e -> {
            String query = searchField.getText().trim();
            searchParams.setQuery(query);
            searchParams.setPage(1);
            searchParams.setLimit(itemsPerPage);
            currentPage = 1;
            pagination.setCurrentPageIndex(0);
            performAdvancedSearch();
        });

        searchField.setOnAction(e -> {
            String query = searchField.getText().trim();
            searchParams.setQuery(query);
            searchParams.setPage(1);
            searchParams.setLimit(itemsPerPage);
            currentPage = 1;
            pagination.setCurrentPageIndex(0);
            performAdvancedSearch();
        });

        VBox contentBox = new VBox(10,
                searchBox,
                advancedSearchPane, // Initially hidden, will toggle with advancedSearchButton
                paginationBox,
                scrollPane);

        getChildren().add(contentBox);

        advancedSearchPane.setVisible(false);
        advancedSearchPane.setManaged(false);

        showInitialState();

        Platform.runLater(() -> themeManager.addThemeChangeListener(this));

        // Auto-load content from selected source on initialization
        Platform.runLater(() -> {
            MangaSource selectedSource = sourceSelector.getValue();
            if (selectedSource != null) {
                autoLoadSourceContent();
            }
        });
    }

    private void setupAdvancedSearchPane() {
        advancedSearchPane = new VBox(15);
        advancedSearchPane.setPadding(new Insets(15));
        advancedSearchPane.getStyleClass().add("advanced-search-pane");

        Label genreLabel = new Label("Genres");
        genreLabel.getStyleClass().add("filter-label");

        genreSelector = new FlowPane();
        genreSelector.setHgap(10);
        genreSelector.setVgap(8);
        genreSelector.setPrefWrapLength(800);

        Label statusLabel = new Label("Status");
        statusLabel.getStyleClass().add("filter-label");

        statusSelector = new ComboBox<>();
        statusSelector.setPromptText("Any Status");
        statusSelector.setPrefWidth(200);
        statusSelector.getStyleClass().add("filter-combobox");

        statusSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals("Any Status")) {
                searchParams.setStatus(newVal.toLowerCase());
            } else {
                searchParams.setStatus(null);
            }
        });

        Button applyFiltersButton = new Button("Apply Filters");
        applyFiltersButton.getStyleClass().addAll("button", "success");
        applyFiltersButton.setOnAction(e -> {
            searchParams.setPage(1);
            currentPage = 1;
            pagination.setCurrentPageIndex(0);
            performAdvancedSearch();
        });

        Button resetFiltersButton = new Button("Reset Filters");
        resetFiltersButton.getStyleClass().addAll("button", "danger");
        resetFiltersButton.setOnAction(e -> resetFilters());

        HBox buttonBox = new HBox(10, applyFiltersButton, resetFiltersButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        advancedSearchPane.getChildren().addAll(
                genreLabel,
                genreSelector,
                new Separator(),
                statusLabel,
                statusSelector,
                new Separator(),
                buttonBox);

        if (sourceSelector.getValue() != null) {
            updateFiltersIfAvailable(sourceSelector.getValue());
        }

        updateAdvancedSearchPaneTheme();
    }

    private void updateFiltersIfAvailable(MangaSource source) {
        try {
            updateGenreFilters(source);
            updateStatusFilters(source);
        } catch (Exception e) {
            System.err.println("Error updating filters: " + e.getMessage());
        }
    }

    private void updateGenreFilters(MangaSource source) {
        genreSelector.getChildren().clear();
        genreCheckboxes.clear();

        List<String> genres = source.getAvailableGenres();

        for (String genre : genres) {
            CheckBox genreCheck = new CheckBox(genre);
            genreCheck.getStyleClass().add("filter-checkbox");
            genreCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    searchParams.addIncludedGenre(genre);
                } else {
                    searchParams.removeIncludedGenre(genre);
                }
            });
            genreSelector.getChildren().add(genreCheck);
            genreCheckboxes.put(genre, genreCheck);
        }
    }

    private void updateStatusFilters(MangaSource source) {
        statusSelector.getItems().clear();
        statusSelector.getItems().add("Any Status");
        statusSelector.getItems().addAll(source.getAvailableStatuses());
        statusSelector.setValue("Any Status");
    }

    private void toggleAdvancedSearch() {
        isAdvancedSearchVisible = !isAdvancedSearchVisible;
        advancedSearchPane.setVisible(isAdvancedSearchVisible);
        advancedSearchPane.setManaged(isAdvancedSearchVisible);

        if (isAdvancedSearchVisible) {
            advancedSearchButton.setStyle("-fx-background-color: #007bff; -fx-text-fill: white;");
        } else {
            advancedSearchButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white;");
        }
    }

    private void resetFilters() {
        for (CheckBox checkbox : genreCheckboxes.values()) {
            checkbox.setSelected(false);
        }

        statusSelector.setValue("Any Status");

        searchParams.clearParams();

        searchParams.setQuery(searchField.getText().trim());
        searchParams.setIncludeNsfw(nsfwCheckbox.isSelected());
        searchParams.setPage(1);
        searchParams.setLimit(itemsPerPage);

        currentPage = 1;
        pagination.setCurrentPageIndex(0);
    }

    private void updateGridColumns() {
        if (scrollPane == null || scrollPane.getViewportBounds() == null) {
            return;
        }

        double availableWidth = scrollPane.getViewportBounds().getWidth();
        if (availableWidth <= 0) {
            if (getScene() != null) {
                availableWidth = getScene().getWidth() - 40; // Account for padding
            } else {
                return; // No valid width available
            }
        }

        int spacing = (int) mangaGrid.getHgap();
        int totalCardWidth = CARD_WIDTH + spacing;
        int newColumns = Math.max(MIN_COLUMNS, (int) ((availableWidth + spacing) / totalCardWidth));
        newColumns = Math.min(newColumns, MAX_COLUMNS);

        if (newColumns != columns) {
            columns = newColumns;
            if (!currentResults.isEmpty()) {
                Platform.runLater(() -> updateMangaGridWithResults(currentResults));
            }
        }
    }

    private void updateMangaGridWithPlaceholders() {
        mangaGrid.getChildren().clear();

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(60, 60);
        Label loadingLabel = new Label("Loading manga...");
        loadingLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: white;");

        VBox loadingBox = new VBox(10, progressIndicator, loadingLabel);
        loadingBox.setAlignment(Pos.CENTER);

        mangaGrid.add(loadingBox, 0, 0, columns, 1);
    }

    private void performAdvancedSearch() {
        // Check if "Search All Sources" is enabled
        if (searchAllSourcesCheckbox.isSelected()) {
            System.out.println("🔍 [DEBUG] All Sources mode - searching all sources");
            performMultiSourceSearch();
            return;
        }
        
        MangaSource selectedSource = sourceSelector.getValue();
        if (selectedSource == null) {
            mangaGrid.getChildren().clear();
            Label selectSourceLabel = new Label("Please select a manga source first");
            selectSourceLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
            VBox messageBox = new VBox(selectSourceLabel);
            messageBox.setAlignment(Pos.CENTER);
            messageBox.setStyle("-fx-padding: 50px;");
            mangaGrid.add(messageBox, 0, 0, columns, 1);
            return;
        }

        System.out.println("🔍 [DEBUG] Single source mode - searching only: " + selectedSource.getName());
        updateMangaGridWithPlaceholders();

        ThreadPoolManager.getInstance().executeApiTask(() -> {
            try {
                SearchResult result = selectedSource.advancedSearch(searchParams);
                Platform.runLater(() -> {
                    currentPage = result.getCurrentPage();
                    totalPages = result.getTotalPages();
                    pagination.setPageCount(totalPages);
                    pagination.setCurrentPageIndex(currentPage - 1); // Convert from 1-based to 0-based
                    resultsCountLabel.setText(String.format("Found %d results", result.getTotalResults()));

                    updateMangaGridWithResults(result.getResults());
                });
            } catch (Exception e) {
                System.err.println("Advanced search failed, falling back to basic search: " + e.getMessage());
                List<Manga> results = selectedSource.search(searchParams.getQuery(), searchParams.isIncludeNsfw());
                Platform.runLater(() -> {
                    resultsCountLabel.setText(String.format("Found %d results", results.size()));
                    pagination.setPageCount(1);
                    pagination.setCurrentPageIndex(0);
                    updateMangaGridWithResults(results);
                });
            }
        });
    }

    /**
     * Search all available sources and combine results.
     */
    private void performMultiSourceSearch() {
        updateMangaGridWithPlaceholders();

        ThreadPoolManager.getInstance().executeApiTask(() -> {
            try {
                MangaServiceImpl mangaService = MangaServiceImpl.getInstance();
                String query = searchParams.getQuery();
                
                System.out.println("🔍 Searching all sources for: " + query);
                List<Manga> allResults = mangaService.searchAllSources(query);
                
                Platform.runLater(() -> {
                    resultsCountLabel.setText(String.format("Found %d results from %d sources", 
                            allResults.size(), sources.size()));
                    pagination.setPageCount(1);
                    pagination.setCurrentPageIndex(0);
                    updateMangaGridWithResults(allResults);
                });
            } catch (Exception e) {
                System.err.println("Multi-source search failed: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    mangaGrid.getChildren().clear();
                    Label errorLabel = new Label("Search failed: " + e.getMessage());
                    errorLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #ff6b6b;");
                    mangaGrid.add(errorLabel, 0, 0);
                });
            }
        });
    }

    private void updateMangaGridWithResults(List<Manga> mangaList) {
        currentResults = mangaList;
        mangaGrid.getChildren().clear();

        if (mangaList.isEmpty()) {
            Label noResultsLabel = new Label("No results found");
            noResultsLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: white;");
            mangaGrid.add(noResultsLabel, 0, 0);
            cleanupCache();
            return;
        }

        // Debug: Show source distribution
        System.out.println("📊 [DEBUG] Results breakdown:");
        mangaList.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                m -> m.getSource() != null ? m.getSource() : "Unknown",
                java.util.stream.Collectors.counting()
            ))
            .forEach((source, count) -> 
                System.out.println("  - " + source + ": " + count + " results")
            );

        List<Runnable> coverLoadTasks = new ArrayList<>();

        int gridRow = 0;
        int gridCol = 0;

        for (int i = 0; i < mangaList.size(); i++) {
            final int index = i;
            Manga manga = mangaList.get(i);
            String mangaId = manga.getId();

            VBox coverBox = mangaNodeCache.get(mangaId);
            if (coverBox == null) {
                coverBox = createLoadingPlaceholder();
                final VBox finalCoverBox = coverBox;

                coverLoadTasks.add(() -> {
                    VBox actualCover = createMangaCover(manga);
                    mangaNodeCache.put(mangaId, actualCover);
                    Platform.runLater(() -> {
                        int row = index / columns;
                        int col = index % columns;
                        mangaGrid.getChildren().remove(finalCoverBox);
                        mangaGrid.add(actualCover, col, row);
                    });
                });
            }

            mangaGrid.add(coverBox, gridCol, gridRow);

            gridCol++;
            if (gridCol >= columns) {
                gridCol = 0;
                gridRow++;
            }
        }

        if (!coverLoadTasks.isEmpty()) {
            executorService.submit(() -> {
                long lastTaskTime = 0;
                long minInterval = 50; // Minimum time between tasks in milliseconds

                for (Runnable task : coverLoadTasks) {
                    try {
                        long now = System.currentTimeMillis();
                        long timeToWait = Math.max(0, minInterval - (now - lastTaskTime));
                        if (timeToWait > 0) {
                            Thread.sleep(timeToWait);
                        }

                        lastTaskTime = System.currentTimeMillis();
                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        cleanupCache();
    }

    private void cleanupCache() {
        Set<String> visibleMangaIds;
        if (currentResults != null && !currentResults.isEmpty()) {
            visibleMangaIds = currentResults.stream()
                    .map(Manga::getId)
                    .collect(Collectors.toSet());
        } else {
            visibleMangaIds = Collections.emptySet();
        }

        mangaNodeCache.keySet().removeIf(id -> !visibleMangaIds.contains(id));
    }

    private VBox createLoadingPlaceholder() {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(CARD_WIDTH);
        box.setPrefHeight(CARD_HEIGHT + 40);
        box.setPadding(new Insets(0, 0, 8, 0));
        box.getStyleClass().add("manga-card");

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(40, 40);
        progressIndicator.setStyle("-fx-progress-color: white;");

        box.getChildren().add(progressIndicator);
        return box;
    }

    private VBox createMangaCover(Manga manga) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPrefWidth(CARD_WIDTH);
        box.setPrefHeight(CARD_HEIGHT + 40);
        box.setPadding(new Insets(0, 0, 8, 0));
        box.getStyleClass().add("manga-card");

        ImageView imageView = new ImageView();
        imageView.setFitWidth(CARD_WIDTH);
        imageView.setFitHeight(CARD_HEIGHT);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        imageView.setCache(true);

        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        Rectangle clip = new Rectangle(CARD_WIDTH, CARD_HEIGHT);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        imageContainer.setClip(clip);

        // Add source badge (top-right corner)
        if (manga.getSource() != null) {
            Label sourceBadge = new Label(manga.getSource());
            sourceBadge.setStyle(
                    "-fx-background-color: rgba(0, 0, 0, 0.7);" +
                    "-fx-text-fill: white;" +
                    "-fx-font-size: 10px;" +
                    "-fx-padding: 2 6 2 6;" +
                    "-fx-background-radius: 3;");
            StackPane.setAlignment(sourceBadge, Pos.TOP_RIGHT);
            StackPane.setMargin(sourceBadge, new Insets(5, 5, 0, 0));
            imageContainer.getChildren().add(sourceBadge);
        }

        try {
            ImageCache imageCache = ImageCache.getInstance();
            if (manga.getCoverUrl() != null && !manga.getCoverUrl().isEmpty()) {
                Image image = imageCache.getImage(manga.getCoverUrl(), CARD_WIDTH, CARD_HEIGHT);

                image.errorProperty().addListener((obs, wasError, isError) -> {
                    if (isError) {
                        System.err.println("Image loading error in UI for: " + manga.getCoverUrl());
                        Platform.runLater(() -> {
                            Image errorImage = imageCache.getPlaceholderImage("Error", CARD_WIDTH, CARD_HEIGHT);
                            imageView.setImage(errorImage);
                        });
                    }
                });

                image.exceptionProperty().addListener((obs, oldEx, newEx) -> {
                    if (newEx != null) {
                        System.err.println(
                                "Image exception in UI: " + newEx.getMessage() + " for: " + manga.getCoverUrl());
                        Platform.runLater(() -> {
                            Image errorImage = imageCache.getPlaceholderImage("Error", CARD_WIDTH, CARD_HEIGHT);
                            imageView.setImage(errorImage);
                        });
                    }
                });

                imageView.setImage(image);
            } else {
                Image placeholderImage = imageCache.getPlaceholderImage("No+Cover", CARD_WIDTH, CARD_HEIGHT);
                imageView.setImage(placeholderImage);
            }
        } catch (Exception e) {
            System.err.println("Error loading image: " + manga.getCoverUrl() + " | " + e.getMessage());
            ImageCache imageCache = ImageCache.getInstance();
            Image errorImage = imageCache.getPlaceholderImage("Error", CARD_WIDTH, CARD_HEIGHT);
            imageView.setImage(errorImage);
        }

        Label titleLabel = new Label(manga.getTitle());
        titleLabel.setWrapText(false);
        titleLabel.setTextAlignment(TextAlignment.CENTER);
        titleLabel.setMaxWidth(CARD_WIDTH - (8 * 2));
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        
        // Use theme-aware text color instead of hardcoded #eee
        String titleColor = themeManager.getTextColor();
        titleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + titleColor + "; -fx-font-weight: bold;");

        HBox titleBox = new HBox(titleLabel);
        titleBox.setPrefWidth(CARD_WIDTH);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPadding(new Insets(0, 8, 0, 8));

        box.getChildren().addAll(imageContainer, titleBox);
        VBox.setVgrow(titleBox, Priority.NEVER);
        VBox.setMargin(titleBox, new Insets(5, 0, 0, 0));

        box.setOnMouseClicked(event -> {
            // Get the correct source for this manga (use manga's source field)
            MangaSource mangaSource = null;
            if (manga.getSource() != null) {
                for (MangaSource src : sources) {
                    if (src.getName().equals(manga.getSource())) {
                        mangaSource = src;
                        break;
                    }
                }
            }
            // Fallback to currently selected source if manga source not found
            if (mangaSource == null) {
                mangaSource = sourceSelector.getValue();
            }
            
            if (mangaSource != null) {
                final MangaSource finalSource = mangaSource;
                ProgressIndicator loadingIndicator = new ProgressIndicator();
                loadingIndicator.setMaxSize(40, 40);
                imageContainer.getChildren().add(loadingIndicator);

                ThreadPoolManager.getInstance().executeApiTask(() -> {
                    try {
                        finalSource.getMangaDetails(manga.getId()).ifPresentOrElse(
                                fullManga -> Platform.runLater(() -> {
                                    imageContainer.getChildren().remove(loadingIndicator);
                                    AddSeriesModal modal = new AddSeriesModal(fullManga);
                                    Optional<Manga> result = modal.showAndAwaitResult();
                                    if (onMangaSelectedCallback != null && result.isPresent()) {
                                        onMangaSelectedCallback.accept(result.get());
                                    }
                                }),
                                () -> Platform.runLater(() -> {
                                    imageContainer.getChildren().remove(loadingIndicator);
                                    Label errorLabel = new Label("Failed to load details");
                                    errorLabel.setStyle("-fx-text-fill: #ff6b6b;");
                                    imageContainer.getChildren().add(errorLabel);
                                }));
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            imageContainer.getChildren().remove(loadingIndicator);
                            Label errorLabel = new Label("Error: " + e.getMessage());
                            errorLabel.setStyle("-fx-text-fill: #ff6b6b;");
                            imageContainer.getChildren().add(errorLabel);
                        });
                    }
                });
            } else {
                AddSeriesModal modal = new AddSeriesModal(manga);
                Optional<Manga> result = modal.showAndAwaitResult();
                if (onMangaSelectedCallback != null && result.isPresent()) {
                    onMangaSelectedCallback.accept(result.get());
                }
            }
        });

        return box;
    }

    /**
     * Automatically load popular content for MangaDex source
     */
    /**
     * Auto-load popular/recent content when a source is selected with no search query.
     * Works for any source (MangaDex, Mgeko, etc.)
     */
    private void autoLoadSourceContent() {
        MangaSource selectedSource = sourceSelector.getValue();
        if (selectedSource == null) {
            return;
        }

        searchField.setText("");
        searchParams.setQuery("");
        searchParams.setPage(1);
        searchParams.setLimit(itemsPerPage);
        currentPage = 1;
        pagination.setCurrentPageIndex(0);

        updateMangaGridWithPlaceholders();

        ThreadPoolManager.getInstance().executeApiTask(() -> {
            try {
                SearchResult result = selectedSource.advancedSearch(searchParams);
                Platform.runLater(() -> {
                    currentPage = result.getCurrentPage();
                    totalPages = result.getTotalPages();
                    pagination.setPageCount(totalPages);
                    pagination.setCurrentPageIndex(currentPage - 1);
                    resultsCountLabel.setText(String.format("Popular from %s - %d results", 
                            selectedSource.getName(), result.getTotalResults()));

                    updateMangaGridWithResults(result.getResults());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mangaGrid.getChildren().clear();
                    Label errorLabel = new Label("Failed to load content: " + e.getMessage());
                    errorLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #ff6b6b;");
                    VBox errorBox = new VBox(errorLabel);
                    errorBox.setAlignment(Pos.CENTER);
                    errorBox.setStyle("-fx-padding: 50px;");
                    mangaGrid.add(errorBox, 0, 0, columns, 1);
                    resultsCountLabel.setText("Error loading content");
                });
            }
        });
    }

    /**
     * Show a clean initial state without any preloading
     */
    private void showInitialState() {
        mangaGrid.getChildren().clear();

        Label welcomeLabel = new Label("Select a source and search for manga");
        welcomeLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #888; -fx-font-weight: bold;");

        Label instructionLabel = new Label("Use the search bar above or click 'Filters' for advanced options");
        instructionLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        VBox welcomeBox = new VBox(10, welcomeLabel, instructionLabel);
        welcomeBox.setAlignment(Pos.CENTER);
        welcomeBox.setStyle("-fx-padding: 50px;");

        mangaGrid.add(welcomeBox, 0, 0, columns, 1);

        resultsCountLabel.setText("Ready to search");
        pagination.setPageCount(1);
        pagination.setCurrentPageIndex(0);
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme newTheme) {
        String backgroundColor = themeManager.getBackgroundColor();
        setStyle("-fx-background-color: " + backgroundColor + ";");

        updateComponentThemes();
    }

    private void updateComponentThemes() {
        String textColor = themeManager.getTextColor();
        String secondaryBackgroundColor = themeManager.getSecondaryBackgroundColor();
        String borderColor = themeManager.getBorderColor();

        if (searchField != null) {
            searchField.setStyle(String.format(
                    "-fx-background-color: %s; " +
                            "-fx-text-fill: %s; " +
                            "-fx-border-color: %s; " +
                            "-fx-border-width: 1px; " +
                            "-fx-background-radius: 4px; " +
                            "-fx-border-radius: 4px;",
                    secondaryBackgroundColor, textColor, borderColor));
        }

        updateAdvancedSearchPaneTheme();

        if (scrollPane != null) {
            scrollPane.setStyle(String.format(
                    "-fx-background-color: %s; " +
                            "-fx-border-color: %s;",
                    secondaryBackgroundColor, borderColor));
        }

        if (resultsCountLabel != null) {
            resultsCountLabel.setStyle(String.format(
                    "-fx-font-size: 14px; " +
                            "-fx-text-fill: %s;",
                    textColor));
        }
    }

    private void updateAdvancedSearchPaneTheme() {
        // CSS classes now handle all theme styling automatically
        // No need to iterate through components
    }

    public void dispose() {
        executorService.shutdown();
    }
}
