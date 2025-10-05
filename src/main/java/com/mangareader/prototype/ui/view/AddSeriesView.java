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
import com.mangareader.prototype.source.MangaSource;
import com.mangareader.prototype.source.impl.MangaDexSource;
import com.mangareader.prototype.ui.component.ThemeManager;
import com.mangareader.prototype.ui.dialog.AddSeriesModal;
import com.mangareader.prototype.util.ImageCache;

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
    private final GridPane mangaGrid;
    private final List<MangaSource> sources;
    private final ScrollPane scrollPane;
    private final ThemeManager themeManager;
    private int columns = 5;
    private List<Manga> currentResults = new ArrayList<>();
    private final Map<String, VBox> mangaNodeCache = new HashMap<>();
    private Consumer<Manga> onMangaSelectedCallback;

    // Pagination components
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

    // Advanced search components
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

        // Initialize sources with improved implementations
        sources = new ArrayList<>();
        sources.add(new MangaDexSource());

        // Source selector
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
            // Auto-load content if MangaDex is selected by default
            MangaSource firstSource = sources.get(0);
            if ("mangadex".equals(firstSource.getId())) {
                Platform.runLater(() -> autoLoadMangaDexContent());
            }
        }

        // Search bar
        searchField = new TextField();
        searchField.setPromptText("Search for a series...");
        searchField.setPrefWidth(300);
        searchButton = new Button("Search");

        // Source selection listener to update genre/status filters if available
        sourceSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateFiltersIfAvailable(newVal);

                // If there's already a search query, re-search with new source
                if (searchField != null) {
                    String currentQuery = searchField.getText().trim();
                    if (!currentQuery.isEmpty()) {
                        searchParams.setQuery(currentQuery);
                        searchParams.setPage(1);
                        currentPage = 1;
                        pagination.setCurrentPageIndex(0);
                        performAdvancedSearch();
                    } else if ("mangadex".equals(newVal.getId())) {
                        // Auto-load popular content for MangaDex when no search query
                        autoLoadMangaDexContent();
                    }
                }
            }
        });

        // Advanced search button
        advancedSearchButton = new Button("Filters");
        advancedSearchButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white;");
        advancedSearchButton.setOnAction(e -> toggleAdvancedSearch());

        // NSFW Checkbox
        nsfwCheckbox = new CheckBox("NSFW");
        nsfwCheckbox.setSelected(false);
        nsfwCheckbox.setTooltip(new Tooltip("Show NSFW content"));

        // Initial style (unchecked state): white box with gray border, no checkmark
        nsfwCheckbox.setStyle(
                "-fx-font-size: 14px;" +
                        "-fx-text-fill: #666;" +
                        "-fx-background-color: transparent;" + // Overall control background
                        "-fx-box-fill: white;" + // White background for the checkbox box
                        "-fx-box-border: #ccc;" + // Gray border for the checkbox box
                        "-fx-border-width: 1px;" + // Border width for the box
                        "-fx-mark-color: transparent;" // No checkmark visible when unchecked
        );

        // Add listener to update style when checked/unchecked
        nsfwCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                // Checked state: blue box with white checkmark
                nsfwCheckbox.setStyle(
                        "-fx-font-size: 14px;" +
                                "-fx-text-fill: #666;" + // Text color remains gray
                                "-fx-background-color: transparent;" +
                                "-fx-mark-color: white;" + // White checkmark
                                "-fx-box-fill: #007bff;" + // Blue background for the checkbox box
                                "-fx-box-border: #007bff;" + // Blue border for the checkbox box
                                "-fx-border-width: 1px;");
            } else {
                // Unchecked state: white box with gray border, no checkmark
                nsfwCheckbox.setStyle(
                        "-fx-font-size: 14px;" +
                                "-fx-text-fill: #666;" + // Text color remains gray
                                "-fx-background-color: transparent;" +
                                "-fx-mark-color: transparent;" + // Make checkmark transparent when unchecked
                                "-fx-box-fill: white;" + // White background for the checkbox box
                                "-fx-box-border: #ccc;" + // Gray border for the checkbox box
                                "-fx-border-width: 1px;");
            }

            // Update search params
            searchParams.setIncludeNsfw(newVal);
        });

        // Setup search box
        HBox searchBox = new HBox(8, sourceSelector, searchField, searchButton, advancedSearchButton, nsfwCheckbox);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        // Create advanced search pane
        setupAdvancedSearchPane();

        // Create pagination controls
        resultsCountLabel = new Label("No results");
        resultsCountLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        pagination = new Pagination();
        pagination.setPageCount(1);
        pagination.setCurrentPageIndex(0);
        pagination.setMaxPageIndicatorCount(10);
        pagination.setStyle("-fx-border-color: transparent;");

        // Pagination change listener
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

        // Manga grid
        mangaGrid = new GridPane();
        mangaGrid.setHgap(16);
        mangaGrid.setVgap(16);
        mangaGrid.setPadding(new Insets(16, 0, 0, 0));

        // Wrap grid in a scroll pane
        scrollPane = new ScrollPane(mangaGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #181818;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Responsive columns
        widthProperty().addListener((obs, oldVal, newVal) -> updateGridColumns());
        scrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> updateGridColumns());
        updateGridColumns();

        updateMangaGridWithPlaceholders();

        // Search button action
        searchButton.setOnAction(e -> {
            String query = searchField.getText().trim();
            searchParams.setQuery(query);
            searchParams.setPage(1);
            searchParams.setLimit(itemsPerPage);
            currentPage = 1;
            pagination.setCurrentPageIndex(0);
            performAdvancedSearch();
        });

        // Add Enter key support for search field
        searchField.setOnAction(e -> {
            String query = searchField.getText().trim();
            searchParams.setQuery(query);
            searchParams.setPage(1);
            searchParams.setLimit(itemsPerPage);
            currentPage = 1;
            pagination.setCurrentPageIndex(0);
            performAdvancedSearch();
        });

        // Add all components to main layout
        VBox contentBox = new VBox(10,
                searchBox,
                advancedSearchPane, // Initially hidden, will toggle with advancedSearchButton
                paginationBox,
                scrollPane);

        getChildren().add(contentBox);

        // Hide advanced search panel initially
        advancedSearchPane.setVisible(false);
        advancedSearchPane.setManaged(false);

        // Show clean initial state without preloading
        showInitialState();

        // Register theme listener after initialization
        Platform.runLater(() -> themeManager.addThemeChangeListener(this));

        // Auto-load content if MangaDex is selected by default (after all
        // initialization
        // is complete)
        Platform.runLater(() -> {
            MangaSource selectedSource = sourceSelector.getValue();
            if (selectedSource != null && "mangadex".equals(selectedSource.getId())) {
                autoLoadMangaDexContent();
            }
        });
    }

    private void setupAdvancedSearchPane() {
        advancedSearchPane = new VBox(15);
        advancedSearchPane.setPadding(new Insets(15));
        // Remove hard-coded styling - will be set by updateComponentThemes()

        // Genre section
        Label genreLabel = new Label("Genres");
        genreLabel.getStyleClass().add("filter-label");

        genreSelector = new FlowPane();
        genreSelector.setHgap(10);
        genreSelector.setVgap(8);
        genreSelector.setPrefWrapLength(800);

        // Status section
        Label statusLabel = new Label("Status");
        statusLabel.getStyleClass().add("filter-label");

        statusSelector = new ComboBox<>();
        statusSelector.setPromptText("Any Status");
        statusSelector.setPrefWidth(200);

        // Status change listener
        statusSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals("Any Status")) {
                searchParams.setStatus(newVal.toLowerCase());
            } else {
                searchParams.setStatus(null);
            }
        });

        // Filter buttons
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

        // Assemble the advanced search pane
        advancedSearchPane.getChildren().addAll(
                genreLabel,
                genreSelector,
                new Separator(),
                statusLabel,
                statusSelector,
                new Separator(),
                buttonBox);

        // Initialize filters if a source is selected
        if (sourceSelector.getValue() != null) {
            updateFiltersIfAvailable(sourceSelector.getValue());
        }

        // Apply initial theme
        updateAdvancedSearchPaneTheme();
    }

    private void updateFiltersIfAvailable(MangaSource source) {
        try {
            updateGenreFilters(source);
            updateStatusFilters(source);
        } catch (Exception e) {
            System.err.println("Error updating filters: " + e.getMessage());
            // Handle any exceptions that might occur if the source doesn't support filters
        }
    }

    private void updateGenreFilters(MangaSource source) {
        genreSelector.getChildren().clear();
        genreCheckboxes.clear();

        List<String> genres = source.getAvailableGenres();
        String textColor = themeManager.getTextColor();

        for (String genre : genres) {
            CheckBox genreCheck = new CheckBox(genre);
            genreCheck.setStyle(String.format(
                    "-fx-text-fill: %s; " +
                            "-fx-padding: 5px;",
                    textColor));
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

        // Update button style
        if (isAdvancedSearchVisible) {
            advancedSearchButton.setStyle("-fx-background-color: #007bff; -fx-text-fill: white;");
        } else {
            advancedSearchButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white;");
        }
    }

    private void resetFilters() {
        // Clear genre checkboxes
        for (CheckBox checkbox : genreCheckboxes.values()) {
            checkbox.setSelected(false);
        }

        // Reset status
        statusSelector.setValue("Any Status");

        // Clear search params
        searchParams.clearParams();

        // Maintain current search query and NSFW setting
        searchParams.setQuery(searchField.getText().trim());
        searchParams.setIncludeNsfw(nsfwCheckbox.isSelected());
        searchParams.setPage(1);
        searchParams.setLimit(itemsPerPage);

        // Reset pagination
        currentPage = 1;
        pagination.setCurrentPageIndex(0);
    }

    private int calculateNewColumnCount(double availableWidth) {
        if (availableWidth <= 0) {
            return MIN_COLUMNS;
        }
        double spacing = mangaGrid.getHgap();
        int newColumns = Math.max(MIN_COLUMNS, (int) ((availableWidth + spacing) / (CARD_WIDTH + spacing)));
        return Math.min(newColumns, MAX_COLUMNS);
    }

    private void updateGridColumns() {
        if (scrollPane == null || scrollPane.getViewportBounds() == null) {
            return;
        }

        double availableWidth = scrollPane.getViewportBounds().getWidth();
        if (availableWidth <= 0) {
            // Use scene width as fallback
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
                // Update the grid layout on the next frame
                Platform.runLater(() -> updateMangaGridWithResults(currentResults));
            }
        }
    }

    private void updateMangaGridWithPlaceholders() {
        mangaGrid.getChildren().clear();

        // Create loading indicator
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(60, 60);
        Label loadingLabel = new Label("Loading manga...");
        loadingLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: white;");

        VBox loadingBox = new VBox(10, progressIndicator, loadingLabel);
        loadingBox.setAlignment(Pos.CENTER);

        mangaGrid.add(loadingBox, 0, 0, columns, 1);
    }

    private void performAdvancedSearch() {
        MangaSource selectedSource = sourceSelector.getValue();
        if (selectedSource == null) {
            // Show message to select a source
            mangaGrid.getChildren().clear();
            Label selectSourceLabel = new Label("Please select a manga source first");
            selectSourceLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #ff6b6b; -fx-font-weight: bold;");
            VBox messageBox = new VBox(selectSourceLabel);
            messageBox.setAlignment(Pos.CENTER);
            messageBox.setStyle("-fx-padding: 50px;");
            mangaGrid.add(messageBox, 0, 0, columns, 1);
            return;
        }

        // Show loading indicator
        updateMangaGridWithPlaceholders();

        // Run in background thread
        new Thread(() -> {
            try {
                SearchResult result = selectedSource.advancedSearch(searchParams);
                Platform.runLater(() -> {
                    // Update pagination
                    currentPage = result.getCurrentPage();
                    totalPages = result.getTotalPages();
                    pagination.setPageCount(totalPages);
                    pagination.setCurrentPageIndex(currentPage - 1); // Convert from 1-based to 0-based
                    resultsCountLabel.setText(String.format("Found %d results", result.getTotalResults()));

                    // Update grid
                    updateMangaGridWithResults(result.getResults());
                });
            } catch (Exception e) {
                // Fall back to basic search if advanced search fails
                System.err.println("Advanced search failed, falling back to basic search: " + e.getMessage());
                List<Manga> results = selectedSource.search(searchParams.getQuery(), searchParams.isIncludeNsfw());
                Platform.runLater(() -> {
                    resultsCountLabel.setText(String.format("Found %d results", results.size()));
                    pagination.setPageCount(1);
                    pagination.setCurrentPageIndex(0);
                    updateMangaGridWithResults(results);
                });
            }
        }).start();
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

        // Create a list to store covers that need to be loaded
        List<Runnable> coverLoadTasks = new ArrayList<>();

        // Add covers to grid - no need for pagination slicing since API already returns
        // paginated results
        int gridRow = 0;
        int gridCol = 0;

        for (int i = 0; i < mangaList.size(); i++) {
            final int index = i;
            Manga manga = mangaList.get(i);
            String mangaId = manga.getId();

            // Try to get from cache first
            VBox coverBox = mangaNodeCache.get(mangaId);
            if (coverBox == null) {
                // Create placeholder with loading indicator
                coverBox = createLoadingPlaceholder();
                final VBox finalCoverBox = coverBox;

                // Add task to load the actual cover
                coverLoadTasks.add(() -> {
                    VBox actualCover = createMangaCover(manga);
                    mangaNodeCache.put(mangaId, actualCover);
                    Platform.runLater(() -> {
                        // Replace placeholder with actual cover
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

        // Execute cover loading tasks in background with rate limiting
        if (!coverLoadTasks.isEmpty()) {
            executorService.submit(() -> {
                // Use a token bucket approach for rate limiting
                long lastTaskTime = 0;
                long minInterval = 50; // Minimum time between tasks in milliseconds

                for (Runnable task : coverLoadTasks) {
                    try {
                        // Rate limit the task execution
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
        // Create a set of manga IDs that should be kept in cache
        Set<String> visibleMangaIds;
        if (currentResults != null && !currentResults.isEmpty()) {
            // Keep all current results in cache since they represent the current page
            visibleMangaIds = currentResults.stream()
                    .map(Manga::getId)
                    .collect(Collectors.toSet());
        } else {
            visibleMangaIds = Collections.emptySet();
        }

        // Remove entries not in the visible set
        mangaNodeCache.keySet().removeIf(id -> !visibleMangaIds.contains(id));
    }

    private VBox createLoadingPlaceholder() {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(CARD_WIDTH);
        box.setPrefHeight(CARD_HEIGHT + 40);
        box.setPadding(new Insets(0, 0, 8, 0));
        box.setStyle(
                "-fx-background-color: #222;" +
                        "-fx-background-radius: 20;" +
                        "-fx-effect: dropshadow(gaussian, #000, 4, 0, 0, 2);");

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
        box.setStyle(
                "-fx-background-color: #222;" +
                        "-fx-background-radius: 20;" +
                        "-fx-effect: dropshadow(gaussian, #000, 4, 0, 0, 2);");

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

        try {
            ImageCache imageCache = ImageCache.getInstance();
            if (manga.getCoverUrl() != null && !manga.getCoverUrl().isEmpty()) {
                Image image = imageCache.getImage(manga.getCoverUrl(), CARD_WIDTH, CARD_HEIGHT);

                // Add error listeners for better error handling
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
        titleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #eee; -fx-font-weight: bold;");

        HBox titleBox = new HBox(titleLabel);
        titleBox.setPrefWidth(CARD_WIDTH);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPadding(new Insets(0, 8, 0, 8));

        box.getChildren().addAll(imageContainer, titleBox);
        VBox.setVgrow(titleBox, Priority.NEVER);
        VBox.setMargin(titleBox, new Insets(5, 0, 0, 0));

        // Add click handler with better error handling
        box.setOnMouseClicked(event -> {
            MangaSource selectedSource = sourceSelector.getValue();
            if (selectedSource != null) {
                // Show loading indicator
                ProgressIndicator loadingIndicator = new ProgressIndicator();
                loadingIndicator.setMaxSize(40, 40);
                imageContainer.getChildren().add(loadingIndicator);

                // Fetch full manga details before showing modal
                new Thread(() -> {
                    try {
                        selectedSource.getMangaDetails(manga.getId()).ifPresentOrElse(
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
                                    // Show error to user
                                    Label errorLabel = new Label("Failed to load details");
                                    errorLabel.setStyle("-fx-text-fill: #ff6b6b;");
                                    imageContainer.getChildren().add(errorLabel);
                                }));
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            imageContainer.getChildren().remove(loadingIndicator);
                            // Show error to user
                            Label errorLabel = new Label("Error: " + e.getMessage());
                            errorLabel.setStyle("-fx-text-fill: #ff6b6b;");
                            imageContainer.getChildren().add(errorLabel);
                        });
                    }
                }).start();
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
    private void autoLoadMangaDexContent() {
        MangaSource selectedSource = sourceSelector.getValue();
        if (selectedSource == null || !"mangadex".equals(selectedSource.getId())) {
            return;
        }

        // Clear search field and reset parameters
        searchField.setText("");
        searchParams.setQuery("");
        searchParams.setPage(1);
        searchParams.setLimit(itemsPerPage);
        currentPage = 1;
        pagination.setCurrentPageIndex(0);

        // Show loading indicator
        updateMangaGridWithPlaceholders();

        // Load popular manga in background
        new Thread(() -> {
            try {
                SearchResult result = selectedSource.advancedSearch(searchParams);
                Platform.runLater(() -> {
                    // Update pagination
                    currentPage = result.getCurrentPage();
                    totalPages = result.getTotalPages();
                    pagination.setPageCount(totalPages);
                    pagination.setCurrentPageIndex(currentPage - 1);
                    resultsCountLabel.setText(String.format("Popular manga - %d results", result.getTotalResults()));

                    // Update grid
                    updateMangaGridWithResults(result.getResults());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mangaGrid.getChildren().clear();
                    Label errorLabel = new Label("Failed to load popular manga: " + e.getMessage());
                    errorLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #ff6b6b;");
                    VBox errorBox = new VBox(errorLabel);
                    errorBox.setAlignment(Pos.CENTER);
                    errorBox.setStyle("-fx-padding: 50px;");
                    mangaGrid.add(errorBox, 0, 0, columns, 1);
                    resultsCountLabel.setText("Error loading content");
                });
            }
        }).start();
    }

    /**
     * Show a clean initial state without any preloading
     */
    private void showInitialState() {
        mangaGrid.getChildren().clear();

        // Create a welcome message
        Label welcomeLabel = new Label("Select a source and search for manga");
        welcomeLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #888; -fx-font-weight: bold;");

        Label instructionLabel = new Label("Use the search bar above or click 'Filters' for advanced options");
        instructionLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        VBox welcomeBox = new VBox(10, welcomeLabel, instructionLabel);
        welcomeBox.setAlignment(Pos.CENTER);
        welcomeBox.setStyle("-fx-padding: 50px;");

        mangaGrid.add(welcomeBox, 0, 0, columns, 1);

        // Reset pagination
        resultsCountLabel.setText("Ready to search");
        pagination.setPageCount(1);
        pagination.setCurrentPageIndex(0);
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme newTheme) {
        // Update the main background
        String backgroundColor = themeManager.getBackgroundColor();
        setStyle("-fx-background-color: " + backgroundColor + ";");

        // Only update visible manga cards
        mangaGrid.getChildren().stream()
                .filter(node -> node instanceof VBox)
                .map(node -> (VBox) node)
                .forEach(card -> {
                    String cardBackgroundColor = themeManager.isDarkTheme() ? "#222" : "#fff";
                    String textColor = themeManager.getTextColor();

                    // Update card background
                    String currentStyle = card.getStyle();
                    String newStyle = currentStyle.replaceAll("-fx-background-color: [^;]+;", "")
                            + " -fx-background-color: " + cardBackgroundColor + ";";
                    card.setStyle(newStyle);

                    // Update text color in labels
                    card.getChildren().stream()
                            .filter(child -> child instanceof HBox)
                            .map(child -> (HBox) child)
                            .flatMap(hbox -> hbox.getChildren().stream())
                            .filter(node -> node instanceof Label)
                            .map(node -> (Label) node)
                            .forEach(label -> {
                                String labelStyle = label.getStyle();
                                String newLabelStyle = labelStyle.replaceAll("-fx-text-fill: [^;]+;", "")
                                        + " -fx-text-fill: " + textColor + ";";
                                label.setStyle(newLabelStyle);
                            });
                });

        // Update other UI components
        updateComponentThemes();
    }

    private void updateComponentThemes() {
        String textColor = themeManager.getTextColor();
        String secondaryBackgroundColor = themeManager.getSecondaryBackgroundColor();
        String borderColor = themeManager.getBorderColor();

        // Update search field
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

        // Update advanced search pane theme
        updateAdvancedSearchPaneTheme();

        // Update scroll pane
        if (scrollPane != null) {
            scrollPane.setStyle(String.format(
                    "-fx-background-color: %s; " +
                            "-fx-border-color: %s;",
                    secondaryBackgroundColor, borderColor));
        }

        // Update pagination label
        if (resultsCountLabel != null) {
            resultsCountLabel.setStyle(String.format(
                    "-fx-font-size: 14px; " +
                            "-fx-text-fill: %s;",
                    textColor));
        }
    }

    private void updateAdvancedSearchPaneTheme() {
        if (advancedSearchPane == null)
            return;

        String secondaryBackgroundColor = themeManager.getSecondaryBackgroundColor();
        String borderColor = themeManager.getBorderColor();
        String textColor = themeManager.getTextColor();

        // Update advanced search pane background
        advancedSearchPane.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-radius: 5; " +
                        "-fx-background-radius: 5;",
                secondaryBackgroundColor, borderColor));

        // Update all child nodes recursively
        advancedSearchPane.getChildren().forEach(node -> {
            if (node instanceof Label label && label.getStyleClass().contains("filter-label")) {
                label.setStyle(String.format(
                        "-fx-font-size: 14px; " +
                                "-fx-font-weight: bold; " +
                                "-fx-text-fill: %s;",
                        textColor));
            } else if (node instanceof FlowPane flowPane) {
                flowPane.getChildren().forEach(child -> {
                    if (child instanceof CheckBox checkbox) {
                        checkbox.setStyle(String.format(
                                "-fx-text-fill: %s; " +
                                        "-fx-padding: 5px;",
                                textColor));
                    }
                });
            } else if (node instanceof ComboBox<?> comboBox) {
                comboBox.setStyle(String.format(
                        "-fx-background-color: %s; " +
                                "-fx-text-fill: %s; " +
                                "-fx-border-color: %s; " +
                                "-fx-border-width: 1px; " +
                                "-fx-background-radius: 4px; " +
                                "-fx-border-radius: 4px;",
                        secondaryBackgroundColor, textColor, borderColor));
            } else if (node instanceof Separator separator) {
                separator.setStyle(String.format(
                        "-fx-background-color: %s;",
                        borderColor));
            } else if (node instanceof HBox buttonBox) {
                buttonBox.getChildren().forEach(child -> {
                    if (child instanceof Button button &&
                            !button.getStyleClass().contains("success") &&
                            !button.getStyleClass().contains("danger")) {
                        button.setStyle(String.format(
                                "-fx-background-color: %s; " +
                                        "-fx-text-fill: %s; " +
                                        "-fx-border-color: %s;",
                                secondaryBackgroundColor, textColor, borderColor));
                    }
                });
            }
        });
    }

    public void dispose() {
        executorService.shutdown();
    }
}
