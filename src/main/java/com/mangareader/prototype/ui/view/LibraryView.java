package com.mangareader.prototype.ui.view;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.mangareader.prototype.model.Manga;
import com.mangareader.prototype.service.LibraryService;
import com.mangareader.prototype.service.impl.LibraryServiceImpl;
import com.mangareader.prototype.ui.component.ThemeManager;
import com.mangareader.prototype.util.ImageCache;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class LibraryView extends BorderPane implements ThemeManager.ThemeChangeListener {
    private final LibraryService libraryService;
    private final ThemeManager themeManager;
    private final GridPane mangaGrid;
    private final ScrollPane scrollPane;
    private TextField searchField;
    private Label statsLabel;
    private Button addSeriesButton;
    private final VBox emptyStateContainer;
    private Consumer<Manga> onMangaSelectedCallback;
    private Runnable onAddSeriesCallback;

    private int columns = 5;
    private final int CARD_WIDTH = 180;
    private final int CARD_HEIGHT = 270;
    private final int MIN_COLUMNS = 3;
    private final int MAX_COLUMNS = 8;

    public LibraryView() {
        this(null);
    }

    public LibraryView(Consumer<Manga> onMangaSelectedCallback) {
        this.onMangaSelectedCallback = onMangaSelectedCallback;
        this.libraryService = new LibraryServiceImpl();
        this.themeManager = ThemeManager.getInstance();

        HBox topBar = createTopBar();

        mangaGrid = new GridPane();
        mangaGrid.setHgap(16);
        mangaGrid.setVgap(16);
        mangaGrid.setPadding(new Insets(20));

        scrollPane = new ScrollPane(mangaGrid);
        scrollPane.setFitToWidth(true);

        emptyStateContainer = createEmptyStateView();

        setTop(topBar);
        setCenter(scrollPane);

        updateComponentThemes();

        loadLibraryContent();

        widthProperty().addListener((obs, oldVal, newVal) -> updateGridColumns());
        scrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> updateGridColumns());
        updateGridColumns();

        this.themeManager.addThemeChangeListener(this);
    }

    private HBox createTopBar() {
        searchField = new TextField();
        searchField.setPromptText("Search your library...");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterLibrary(newVal));

        statsLabel = new Label("Loading library...");

        addSeriesButton = new Button("+ Add New Series");
        addSeriesButton.setOnAction(e -> showAddSeriesView());

        HBox leftSection = new HBox(15, new Label("📚 My Library"), statsLabel);
        leftSection.setAlignment(Pos.CENTER_LEFT);

        HBox rightSection = new HBox(15, searchField, addSeriesButton);
        rightSection.setAlignment(Pos.CENTER_RIGHT);

        HBox topBar = new HBox();
        topBar.setPadding(new Insets(15, 20, 15, 20));

        HBox.setHgrow(leftSection, Priority.ALWAYS);
        topBar.getChildren().addAll(leftSection, rightSection);

        return topBar;
    }

    private VBox createEmptyStateView() {
        Label emptyIcon = new Label("📚");
        emptyIcon.setStyle("-fx-font-size: 48px;");

        Label emptyTitle = new Label("Your Library is Empty");
        emptyTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label emptyMessage = new Label(
                "Your personal manga collection will appear here once you start adding series.\nUse the \"+ Add New Series\" button above or navigate to \"Add Series\" to browse and add manga.");
        emptyMessage.setStyle("-fx-font-size: 16px; -fx-text-alignment: center;");
        emptyMessage.setWrapText(true);
        emptyMessage.setMaxWidth(400);

        Button browseButton = new Button("🔍 Browse & Add Series");
        browseButton.setStyle(
                "-fx-background-color: #28a745; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 16px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 12 24; " +
                        "-fx-background-radius: 8;");
        browseButton.setOnAction(e -> showAddSeriesView());

        // Add a "Clear Library" button for testing (can be removed in production)
        Button clearButton = new Button("Clear Library (Testing)");
        clearButton.setStyle(
                "-fx-background-color: #dc3545; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 12px; " +
                        "-fx-padding: 8 16; " +
                        "-fx-background-radius: 5;");
        clearButton.setOnAction(e -> {
            libraryService.clearLibrary();
            loadLibraryContent();
        });

        VBox emptyContainer = new VBox(20, emptyIcon, emptyTitle, emptyMessage, browseButton, clearButton);
        emptyContainer.setAlignment(Pos.CENTER);
        emptyContainer.setPadding(new Insets(50));

        return emptyContainer;
    }

    private void loadLibraryContent() {
        new Thread(() -> {
            try {
                List<Manga> libraryManga = libraryService.getLibrary();
                Platform.runLater(() -> {
                    updateStatsLabel(libraryManga.size());
                    if (libraryManga.isEmpty()) {
                        showEmptyState();
                    } else {
                        showLibraryGrid();
                        updateMangaGrid(libraryManga);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statsLabel.setText("Error loading library");
                    showEmptyState();
                });
                System.err.println("Error loading library: " + e.getMessage());
            }
        }).start();
    }

    private void updateStatsLabel(int count) {
        if (count == 0) {
            statsLabel.setText("No manga in library");
        } else if (count == 1) {
            statsLabel.setText("1 manga in library");
        } else {
            statsLabel.setText(count + " manga in library");
        }
    }

    private void showEmptyState() {
        setCenter(emptyStateContainer);
    }

    private void showLibraryGrid() {
        setCenter(scrollPane);
    }

    private void updateMangaGrid(List<Manga> mangaList) {
        mangaGrid.getChildren().clear();

        if (mangaList.isEmpty()) {
            Label noResultsLabel = new Label("No manga found matching your search");
            noResultsLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #666;");
            mangaGrid.add(noResultsLabel, 0, 0);
            return;
        }

        for (int i = 0; i < mangaList.size(); i++) {
            Manga manga = mangaList.get(i);
            VBox coverBox = createMangaCover(manga);
            mangaGrid.add(coverBox, i % columns, i / columns);
        }
    }

    private VBox createMangaCover(Manga manga) {
        // High-quality cover image
        ImageView imageView = new ImageView();
        imageView.setFitWidth(CARD_WIDTH);
        imageView.setFitHeight(CARD_HEIGHT);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true); // High-quality scaling
        imageView.setCache(true); // Performance optimization

        StackPane imageContainer = new StackPane(imageView);

        // Add rounded clipping for modern look
        Rectangle clip = new Rectangle(CARD_WIDTH, CARD_HEIGHT);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        imageContainer.setClip(clip);

        // Container styling
        String imageBackgroundColor = themeManager.getSecondaryBackgroundColor();
        String borderColor = themeManager.getBorderColor();
        imageContainer.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 1; " +
                        "-fx-background-radius: 12; " +
                        "-fx-border-radius: 12; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);",
                imageBackgroundColor, borderColor));

        // Load cover image using cache
        try {
            ImageCache imageCache = ImageCache.getInstance();
            if (manga.getCoverUrl() != null && !manga.getCoverUrl().isEmpty()) {
                Image image = imageCache.getImage(manga.getCoverUrl());
                imageView.setImage(image);
            } else {
                Image placeholderImage = imageCache.getPlaceholderImage("No+Cover");
                imageView.setImage(placeholderImage);
            }
        } catch (Exception e) {
            ImageCache imageCache = ImageCache.getInstance();
            Image errorImage = imageCache.getPlaceholderImage("Error");
            imageView.setImage(errorImage);
        }

        // Theme colors
        String textColor = themeManager.getTextColor();
        String secondaryTextColor = themeManager.isDarkTheme() ? "#b0b0b0" : "#666666";

        Label titleLabel = new Label(manga.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(CARD_WIDTH);
        titleLabel.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: %s;", textColor));

        // Progress information with actual data from library service
        String progressText = "Progress: 0/0 chapters";
        String readingStatusText = "Plan to Read";

        try {
            // Get reading progress from library service
            double progress = libraryService.getReadingProgress(manga.getId());
            Optional<LibraryService.ReadingPosition> position = libraryService.getReadingPosition(manga.getId());

            // Get detailed library entry info for better progress display
            Optional<LibraryService.LibraryEntryInfo> entryInfo = libraryService.getLibraryEntryInfo(manga.getId());

            if (entryInfo.isPresent()) {
                LibraryService.LibraryEntryInfo info = entryInfo.get();
                int chaptersRead = info.getChaptersRead();
                int totalChapters = info.getTotalChapters();

                if (totalChapters > 0) {
                    progressText = String.format("%d/%d chapters", chaptersRead, totalChapters);

                    if (chaptersRead > 0) {
                        readingStatusText = "Reading";

                        // Show current reading position within the current chapter
                        if (position.isPresent()) {
                            LibraryService.ReadingPosition pos = position.get();
                            int totalPages = pos.getTotalPages();

                            if (totalPages > 0) {
                                // Show which chapter you're currently reading
                                progressText += String.format(" (Chapter %d)",
                                        chaptersRead + 1);
                            }

                            if (chaptersRead >= totalChapters) {
                                readingStatusText = "Completed";
                                progressText = "Completed (" + totalChapters + " chapters)";
                            }
                        }
                    } else {
                        progressText = "0 chapters available";
                    }
                } else {
                    // Fallback for basic progress display
                    if (progress > 0) {
                        int progressPercent = (int) (progress * 100);
                        progressText = String.format("Progress: %d%%", progressPercent);
                        readingStatusText = "Reading";

                        if (progress >= 1.0) {
                            readingStatusText = "Completed";
                            progressText = "Completed";
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting reading progress: " + e.getMessage());
        }

        Label progressLabel = new Label(progressText);
        progressLabel.setStyle(String.format("-fx-font-size: 11px; -fx-text-fill: %s;", secondaryTextColor));

        // Update status label with reading status
        Label statusLabel = new Label(readingStatusText);
        statusLabel.setStyle(String.format("-fx-font-size: 12px; -fx-text-fill: %s;", secondaryTextColor));

        VBox infoBox = new VBox(5, titleLabel, statusLabel, progressLabel);
        infoBox.setPadding(new Insets(8));
        infoBox.setMaxWidth(CARD_WIDTH);

        VBox box = new VBox(0, imageContainer, infoBox);

        // Card styling
        String cardBackgroundColor = themeManager.getSecondaryBackgroundColor();
        box.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-background-radius: 8; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);",
                cardBackgroundColor));

        // Click handler
        box.setOnMouseClicked(e -> {
            if (onMangaSelectedCallback != null) {
                onMangaSelectedCallback.accept(manga);
            }
        });

        // Hover effects
        box.setOnMouseEntered(e -> box.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-background-radius: 8; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 12, 0, 0, 4); " +
                        "-fx-scale-x: 1.05; -fx-scale-y: 1.05;",
                cardBackgroundColor)));

        box.setOnMouseExited(e -> box.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-background-radius: 8; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2); " +
                        "-fx-scale-x: 1.0; -fx-scale-y: 1.0;",
                cardBackgroundColor)));

        return box;
    }

    private void filterLibrary(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            loadLibraryContent();
            return;
        }

        new Thread(() -> {
            try {
                List<Manga> allManga = libraryService.getLibrary();
                List<Manga> filteredManga = allManga.stream()
                        .filter(manga -> manga.getTitle().toLowerCase().contains(searchText.toLowerCase()) ||
                                (manga.getAuthor() != null
                                        && manga.getAuthor().toLowerCase().contains(searchText.toLowerCase())))
                        .toList();

                Platform.runLater(() -> {
                    if (filteredManga.isEmpty() && !allManga.isEmpty()) {
                        statsLabel.setText("No matches found");
                    } else {
                        updateStatsLabel(filteredManga.size());
                    }
                    updateMangaGrid(filteredManga);
                });
            } catch (Exception e) {
                System.err.println("Error filtering library: " + e.getMessage());
            }
        }).start();
    }

    private void updateGridColumns() {
        double availableWidth = scrollPane.getViewportBounds().getWidth();
        if (availableWidth > 0) {
            int newColumns = Math.max(MIN_COLUMNS, (int) (availableWidth / (CARD_WIDTH + mangaGrid.getHgap())));
            newColumns = Math.min(newColumns, MAX_COLUMNS);
            if (newColumns != columns) {
                columns = newColumns;
                // Refresh the grid layout if we have content
                if (!mangaGrid.getChildren().isEmpty()) {
                    loadLibraryContent();
                }
            }
        }
    }

    private void showAddSeriesView() {
        // Call the add series callback to navigate to AddSeriesView
        if (onAddSeriesCallback != null) {
            onAddSeriesCallback.run();
        }
    }

    public void refreshLibrary() {
        loadLibraryContent();
    }

    public void setOnMangaSelectedCallback(Consumer<Manga> callback) {
        this.onMangaSelectedCallback = callback;
    }

    public void setOnAddSeriesCallback(Runnable callback) {
        this.onAddSeriesCallback = callback;
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme newTheme) {
        // Update the main background
        String backgroundColor = themeManager.getBackgroundColor();
        setStyle("-fx-background-color: " + backgroundColor + ";");

        // Update search field and other components
        updateComponentThemes();

        // Update existing manga cards styling without rebuilding the grid
        updateExistingCardThemes();
    }

    private void updateComponentThemes() {
        String backgroundColor = themeManager.getBackgroundColor();
        String textColor = themeManager.getTextColor();
        String secondaryBackgroundColor = themeManager.getSecondaryBackgroundColor();
        String borderColor = themeManager.getBorderColor();

        // Update top bar styling
        HBox topBar = (HBox) getTop();
        if (topBar != null) {
            topBar.setStyle(String.format(
                    "-fx-background-color: %s; " +
                            "-fx-border-color: %s; " +
                            "-fx-border-width: 0 0 1 0;",
                    secondaryBackgroundColor, borderColor));

            // Update "My Library" label in the top bar
            topBar.getChildren().forEach(child -> {
                if (child instanceof HBox) {
                    HBox section = (HBox) child;
                    section.getChildren().forEach(sectionChild -> {
                        if (sectionChild instanceof Label) {
                            Label label = (Label) sectionChild;
                            label.setStyle("-fx-text-fill: " + textColor + ";");
                        }
                    });
                }
            });
        }

        // Update search field
        if (searchField != null) {
            searchField.setStyle(String.format(
                    "-fx-background-color: %s; " +
                            "-fx-text-fill: %s; " +
                            "-fx-border-color: %s; " +
                            "-fx-border-width: 1px; " +
                            "-fx-background-radius: 4px; " +
                            "-fx-border-radius: 4px;",
                    backgroundColor, textColor, borderColor));
        }

        // Update stats label
        if (statsLabel != null) {
            statsLabel.setStyle("-fx-text-fill: " + textColor + ";");
        }

        // Update add series button
        if (addSeriesButton != null) {
            addSeriesButton.setStyle(String.format(
                    "-fx-background-color: #0096c9; " +
                            "-fx-text-fill: white; " +
                            "-fx-border-color: #0088b3; " +
                            "-fx-border-width: 1px; " +
                            "-fx-background-radius: 6px; " +
                            "-fx-border-radius: 6px; " +
                            "-fx-padding: 10 20;"));
        }

        // Update scroll pane
        if (scrollPane != null) {
            scrollPane.setStyle(String.format(
                    "-fx-background-color: %s; " +
                            "-fx-border-color: %s;",
                    backgroundColor, borderColor));
        }

        // Update empty state container and its children
        if (emptyStateContainer != null) {
            emptyStateContainer.setStyle("-fx-background-color: " + backgroundColor + ";");

            // Update empty state text colors
            emptyStateContainer.getChildren().forEach(child -> {
                if (child instanceof Label) {
                    Label label = (Label) child;
                    String currentStyle = label.getStyle();

                    // Remove any existing text-fill and add the new one
                    String newStyle = currentStyle.replaceAll("-fx-text-fill: [^;]+;", "")
                            .trim();
                    if (!newStyle.isEmpty() && !newStyle.endsWith(";")) {
                        newStyle += ";";
                    }
                    newStyle += " -fx-text-fill: " + textColor + ";";
                    label.setStyle(newStyle);
                }
            });
        }
    }

    private void updateExistingCardThemes() {
        String cardBackgroundColor = themeManager.getSecondaryBackgroundColor();
        String borderColor = themeManager.getBorderColor();
        String textColor = themeManager.getTextColor();

        // Update styling of existing manga cards
        mangaGrid.getChildren().forEach(node -> {
            if (node instanceof VBox) {
                VBox card = (VBox) node;

                // Update the main card background color
                card.setStyle(String.format(
                        "-fx-background-color: %s; " +
                                "-fx-background-radius: 8; " +
                                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);",
                        cardBackgroundColor));

                // Update hover effects by resetting the event handlers
                card.setOnMouseEntered(e -> card.setStyle(String.format(
                        "-fx-background-color: %s; " +
                                "-fx-background-radius: 8; " +
                                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 12, 0, 0, 4); " +
                                "-fx-scale-x: 1.05; -fx-scale-y: 1.05;",
                        cardBackgroundColor)));

                card.setOnMouseExited(e -> card.setStyle(String.format(
                        "-fx-background-color: %s; " +
                                "-fx-background-radius: 8; " +
                                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2); " +
                                "-fx-scale-x: 1.0; -fx-scale-y: 1.0;",
                        cardBackgroundColor)));

                // Update the card's child components
                card.getChildren().forEach(child -> {
                    if (child instanceof StackPane) {
                        StackPane imageContainer = (StackPane) child;
                        imageContainer.setStyle(String.format(
                                "-fx-background-color: %s; " +
                                        "-fx-border-color: %s; " +
                                        "-fx-border-width: 1; " +
                                        "-fx-background-radius: 8; " +
                                        "-fx-border-radius: 8;",
                                cardBackgroundColor, borderColor));
                    } else if (child instanceof VBox) {
                        VBox infoBox = (VBox) child;
                        // Update labels in the info box
                        infoBox.getChildren().forEach(infoChild -> {
                            if (infoChild instanceof Label) {
                                Label label = (Label) infoChild;
                                String currentStyle = label.getStyle();
                                // Update text color while preserving other styles
                                String newStyle = currentStyle.replaceAll("-fx-text-fill: [^;]+;", "")
                                        + " -fx-text-fill: " + textColor + ";";
                                label.setStyle(newStyle);
                            }
                        });
                    }
                });
            }
        });
    }
}