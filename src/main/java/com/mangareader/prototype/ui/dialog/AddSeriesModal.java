package com.mangareader.prototype.ui.dialog;

import java.util.Arrays;
import java.util.Optional;

import com.mangareader.prototype.model.Manga;
import com.mangareader.prototype.service.MangaService;
import com.mangareader.prototype.service.impl.DefaultMangaServiceImpl;
import com.mangareader.prototype.ui.component.ThemeManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AddSeriesModal extends Dialog<Manga> implements ThemeManager.ThemeChangeListener {

    private final MangaService mangaService;
    private final ThemeManager themeManager;
    private Manga mangaToAdd;

    private final ImageView coverImageView;
    private final TextField titleField;
    private final TextArea descriptionArea;
    private final TextField authorsField;
    private final TextField artistsField;
    private final FlowPane tagsFlowPane;
    private final TextField addTagField;
    private final TextField languageField; // Changed from ComboBox since it's read-only
    private final TextField statusField; // Changed from ComboBox since it's read-only
    private TextField coverUrlField; // Added coverUrlField as a class member

    public AddSeriesModal(Manga manga) {
        this.mangaService = new DefaultMangaServiceImpl();
        this.themeManager = ThemeManager.getInstance();
        this.mangaToAdd = manga;

        System.out.println("Creating AddSeriesModal with manga data:");
        System.out.println("  ID: " + manga.getId());
        System.out.println("  Title: " + manga.getTitle());
        System.out.println("  Author: " + manga.getAuthor());
        System.out.println("  Artist: " + manga.getArtist());

        setTitle("View Series");
        setHeaderText(null);

        DialogPane dialogPane = getDialogPane();
        dialogPane.getStyleClass().add("add-series-dialog");
        dialogPane.setPrefWidth(900);
        dialogPane.setPrefHeight(700);

        String cssPath = themeManager.getCurrentTheme().getCssPath();
        try {
            String cssUrl = getClass().getResource(cssPath).toExternalForm();
            dialogPane.getStylesheets().add(cssUrl);
        } catch (Exception e) {
            System.err.println("Error loading theme CSS for modal: " + e.getMessage());
            try {
                String fallbackUrl = getClass().getResource("/styles/main.css").toExternalForm();
                dialogPane.getStylesheets().add(fallbackUrl);
            } catch (Exception fallbackError) {
                System.err.println("Error loading fallback CSS: " + fallbackError.getMessage());
            }
        }

        coverImageView = new ImageView();
        coverImageView.setFitWidth(200);
        coverImageView.setFitHeight(300);
        coverImageView.setPreserveRatio(true);
        coverImageView.setSmooth(true);
        coverImageView.setCache(true);

        String placeholderUrl = "https://via.placeholder.com/200x300/f8f9fa/6c757d?text=No+Cover";
        Image placeholderImage = new Image(placeholderUrl, true);

        // Add error handling even for placeholder images
        placeholderImage.errorProperty().addListener((obs, wasError, isError) -> {
            if (isError) {
                System.err.println("Error loading placeholder image, using fallback");
                // Use a simple fallback if even placeholder fails
                Platform.runLater(() -> {
                    try {
                        Image fallbackImage = new Image(
                                "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjMwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjZjhmOWZhIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxNiIgZmlsbD0iIzZjNzU3ZCIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPk5vIENvdmVyPC90ZXh0Pjwvc3ZnPg==");
                        coverImageView.setImage(fallbackImage);
                    } catch (Exception e) {
                        System.err.println("All image loading failed: " + e.getMessage());
                    }
                });
            }
        });

        coverImageView.setImage(placeholderImage);

        // Load cover image if available
        loadCoverImage(manga);

        // Title Field
        titleField = new TextField(manga.getTitle());
        titleField.setPromptText("Title");
        titleField.setPrefColumnCount(30);
        titleField.setEditable(false); // Make title read-only
        titleField.setFocusTraversable(false);

        // Description Area
        descriptionArea = new TextArea(manga.getDescription());
        descriptionArea.setPromptText("Description");
        descriptionArea.setWrapText(true);
        descriptionArea.setPrefRowCount(6);
        descriptionArea.setPrefColumnCount(30);
        descriptionArea.setEditable(false); // Make description read-only
        descriptionArea.setFocusTraversable(false);

        // Authors Field
        String authorText = manga.getAuthor() != null ? manga.getAuthor() : "";
        authorsField = new TextField(authorText);
        authorsField.setPromptText("Author(s)");
        authorsField.setPrefColumnCount(30);
        authorsField.setEditable(false); // Make non-editable since data is from MangaDex
        authorsField.setFocusTraversable(false);
        System.out.println("Setting author field text: " + authorText);

        // Artists Field
        String artistText = manga.getArtist() != null ? manga.getArtist() : "";
        artistsField = new TextField(artistText);
        artistsField.setPromptText("Artist(s)");
        artistsField.setPrefColumnCount(30);
        artistsField.setEditable(false); // Make non-editable since data is from MangaDex
        artistsField.setFocusTraversable(false);
        System.out.println("Setting artist field text: " + artistText);

        // Tags FlowPane (for displaying chips) - Auto-adjusting layout
        tagsFlowPane = new FlowPane(5, 5); // Reduced spacing for tighter layout
        tagsFlowPane.setPadding(new Insets(5)); // Reduced padding

        // Remove fixed height constraints to let content determine size
        tagsFlowPane.setPrefWrapLength(400);
        tagsFlowPane.setMaxWidth(Double.MAX_VALUE);
        if (manga.getGenres() != null) {
            manga.getGenres().forEach(this::addTagChip);
        }
        tagsFlowPane.setDisable(true); // Make tags field non-editable

        // Wrap tagsFlowPane in a ScrollPane with dynamic height
        ScrollPane tagsScrollPane = new ScrollPane(tagsFlowPane);
        tagsScrollPane.setFitToWidth(true);
        // Calculate height based on tag content (approximately 30px per row + padding)
        double estimatedHeight = calculateTagsHeight();
        tagsScrollPane.setPrefHeight(Math.max(40, Math.min(estimatedHeight, 120))); // Min 40px, max 120px
        tagsScrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        tagsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tagsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Initialize detailsGrid before adding components
        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(15);
        detailsGrid.setVgap(20);
        detailsGrid.setAlignment(Pos.TOP_LEFT);

        // Add Tag Field
        addTagField = new TextField();
        addTagField.setPromptText("Add Tags (comma-separated)");
        addTagField.setOnAction(event -> {
            String text = addTagField.getText().trim();
            if (!text.isEmpty()) {
                Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(this::addTagChip);
                addTagField.clear();
            }
        });
        addTagField.setVisible(false); // Hide add tag field when tags are read-only

        // Language Field (read-only, no dropdown needed)
        languageField = new TextField(manga.getLanguage() != null ? manga.getLanguage() : "English");
        languageField.setPromptText("Language");
        languageField.setEditable(false);
        languageField.setFocusTraversable(false);
        languageField.setAlignment(Pos.CENTER_LEFT); // Better text alignment

        // Status Field (read-only, no dropdown needed)
        statusField = new TextField(manga.getStatus() != null ? manga.getStatus() : "Ongoing");
        statusField.setPromptText("Status");
        statusField.setEditable(false);
        statusField.setFocusTraversable(false);
        statusField.setAlignment(Pos.CENTER_LEFT); // Better text alignment

        // --- Layout --- //
        GridPane contentGrid = new GridPane();
        contentGrid.setHgap(40);
        contentGrid.setVgap(15);
        contentGrid.setPadding(new Insets(30, 40, 30, 40));
        contentGrid.setAlignment(Pos.CENTER);

        // Left side: Cover Image and URL
        VBox leftColumn = new VBox(20);
        leftColumn.setAlignment(Pos.TOP_CENTER);

        // Cover image container with title
        VBox coverContainer = new VBox(10);
        coverContainer.setAlignment(Pos.CENTER);
        Label coverLabel = new Label("Cover Image");
        coverContainer.getChildren().addAll(coverLabel, coverImageView);

        // Create coverUrlField as a class-level field so it can be updated from image
        // loading methods
        coverUrlField = new TextField(manga.getCoverUrl() != null ? manga.getCoverUrl() : "");
        coverUrlField.setEditable(false);
        coverUrlField.setFocusTraversable(false);
        coverUrlField.setPromptText("Cover URL");

        leftColumn.getChildren().addAll(coverContainer, coverUrlField);
        leftColumn.setPrefWidth(250);
        leftColumn.setMaxWidth(250);

        // Right side: Details fields
        Label titleLabel = new Label("Title");
        Label descLabel = new Label("Description");
        Label authorLabel = new Label("Author(s)");
        Label artistLabel = new Label("Artist(s)");
        Label tagsLabel = new Label("Tags");
        Label langLabel = new Label("Language");
        Label statusLabel = new Label("Status");

        detailsGrid.add(titleLabel, 0, 0);
        detailsGrid.add(titleField, 1, 0);
        detailsGrid.add(descLabel, 0, 1);
        detailsGrid.add(descriptionArea, 1, 1);
        detailsGrid.add(authorLabel, 0, 2);
        detailsGrid.add(authorsField, 1, 2);
        detailsGrid.add(artistLabel, 0, 3);
        detailsGrid.add(artistsField, 1, 3);
        detailsGrid.add(tagsLabel, 0, 4);
        detailsGrid.add(tagsScrollPane, 1, 4);
        detailsGrid.add(langLabel, 0, 5);
        detailsGrid.add(languageField, 1, 5);
        detailsGrid.add(statusLabel, 0, 6);
        detailsGrid.add(statusField, 1, 6);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(90);
        labelCol.setMaxWidth(120);
        labelCol.setHgrow(Priority.NEVER);
        ColumnConstraints inputCol = new ColumnConstraints();
        inputCol.setHgrow(Priority.ALWAYS);
        detailsGrid.getColumnConstraints().addAll(labelCol, inputCol);

        contentGrid.add(leftColumn, 0, 0);
        contentGrid.add(detailsGrid, 1, 0);
        GridPane.setVgrow(detailsGrid, Priority.ALWAYS);
        GridPane.setHgrow(detailsGrid, Priority.ALWAYS);

        dialogPane.setContent(contentGrid);

        // --- Buttons --- //
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType addSeriesButtonType = new ButtonType("View Series", ButtonBar.ButtonData.APPLY);
        dialogPane.getButtonTypes().addAll(cancelButtonType, addSeriesButtonType);

        // Button actions
        setResultConverter(dialogButton -> {
            if (dialogButton == addSeriesButtonType) {
                updateMangaObject();
                mangaService.addToLibrary(mangaToAdd);
                return mangaToAdd;
            }
            return null;
        });

        // Initialize theme styling after all components are created
        initializeTheme();

        updateTagChipsTheme();
    }

    private void addTagChip(String tagText) {
        Label tagLabel = new Label(tagText);
        tagLabel.getStyleClass().add("tag-chip");

        boolean isDark = themeManager.isDarkTheme();
        String chipBg = isDark ? "#4a4a4a" : "#e7f3ff";
        String chipText = isDark ? "#87ceeb" : "#0066cc";
        String chipBorder = isDark ? "#5a5a5a" : "#b3d9ff";

        tagLabel.setStyle(String.format(
                "-fx-background-color: %s; -fx-padding: 6 12; -fx-background-radius: 18; " +
                        "-fx-font-size: 12px; -fx-text-fill: %s; -fx-border-color: %s; " +
                        "-fx-border-width: 1; -fx-border-radius: 18;",
                chipBg, chipText, chipBorder));

        // Remove close button for read-only tags
        HBox tagChip = new HBox(5, tagLabel);
        tagChip.setAlignment(Pos.CENTER_LEFT);
        tagChip.getStyleClass().add("tag-chip-container");
        tagsFlowPane.getChildren().add(tagChip);
    }

    private void updateMangaObject() {
        // All fields are read-only - data comes from MangaDex source
        System.out.println("Manga object preserved - all data from MangaDex source");
    }

    public Optional<Manga> showAndAwaitResult() {
        // Try to get full manga details before showing the dialog
        if (mangaToAdd != null && mangaToAdd.getId() != null && !mangaToAdd.getId().isEmpty()) {
            // Check if we need to fetch author/artist data
            if ((mangaToAdd.getAuthor() == null || mangaToAdd.getAuthor().isEmpty()) ||
                    (mangaToAdd.getArtist() == null || mangaToAdd.getArtist().isEmpty())) {

                System.out.println("Fetching complete manga details before showing dialog");
                try {
                    // Try to get full details synchronously to ensure data is available when dialog
                    // opens
                    Optional<Manga> fullManga = mangaService.getMangaDetails(mangaToAdd.getId());
                    if (fullManga.isPresent()) {
                        Manga completeData = fullManga.get();

                        // Update author field if needed
                        if (completeData.getAuthor() != null && !completeData.getAuthor().isEmpty()) {
                            mangaToAdd.setAuthor(completeData.getAuthor());
                            authorsField.setText(completeData.getAuthor());
                            System.out.println("Pre-dialog: Updated author to " + completeData.getAuthor());
                        }

                        // Update artist field if needed
                        if (completeData.getArtist() != null && !completeData.getArtist().isEmpty()) {
                            mangaToAdd.setArtist(completeData.getArtist());
                            artistsField.setText(completeData.getArtist());
                            System.out.println("Pre-dialog: Updated artist to " + completeData.getArtist());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error fetching complete manga details: " + e.getMessage());
                }
            }
        }

        // Show the dialog and return the result
        Optional<Manga> result = showAndWait();
        // Only return the result if it's present (i.e., user clicked "Add Series")
        // If result is empty, it means user cancelled or closed the dialog
        return result;
    }

    /**
     * Loads the cover image for a manga with fallbacks for error cases.
     * If the initial cover URL fails, tries to fetch it directly from the source.
     *
     * @param manga The manga to load the cover for
     */
    private void loadCoverImage(Manga manga) {
        String placeholderUrl = "https://via.placeholder.com/200x300/f8f9fa/6c757d?text=No+Cover";
        // Always set placeholder first
        coverImageView.setImage(new Image(placeholderUrl, true));

        // Check if there's a valid cover URL
        if (manga.getCoverUrl() == null || manga.getCoverUrl().isEmpty()) {
            System.out.println("No cover URL found, fetching from source");
            tryFallbackCoverUrl(manga, placeholderUrl);
            return;
        }

        System.out.println("Loading cover from URL: " + manga.getCoverUrl());
        try {
            Image coverImg = new Image(manga.getCoverUrl(), true);

            // Handle load errors
            coverImg.exceptionProperty().addListener((obs, oldEx, newEx) -> {
                if (newEx != null) {
                    System.err.println("Error loading cover image: " + newEx.getMessage());
                    tryFallbackCoverUrl(manga, placeholderUrl);
                }
            });

            // Handle image error
            coverImg.errorProperty().addListener((obs, wasError, isError) -> {
                if (isError) {
                    System.err.println("Image error loading cover");
                    tryFallbackCoverUrl(manga, placeholderUrl);
                } else if (!isError && coverImg.getProgress() == 1.0) {
                    // Image successfully loaded
                    coverImageView.setImage(coverImg);
                    System.out.println("Cover image successfully loaded");
                }
            });

            // Add progress listener to set the image when fully loaded
            coverImg.progressProperty().addListener((obs, oldProgress, newProgress) -> {
                if (newProgress.doubleValue() == 1.0 && !coverImg.isError()) {
                    coverImageView.setImage(coverImg);
                    System.out.println("Cover image loaded from progress listener");
                }
            });

        } catch (Exception e) {
            System.err.println("Exception during image loading: " + e.getMessage());
            tryFallbackCoverUrl(manga, placeholderUrl);
        }
    }

    /**
     * Attempts to fetch a cover URL directly from the manga source as a fallback
     *
     * @param manga          The manga to load the cover for
     * @param placeholderUrl URL to use if fallback also fails
     */
    private void tryFallbackCoverUrl(Manga manga, String placeholderUrl) {
        // Try to get the cover URL directly from the source
        if (manga.getId() != null && !manga.getId().isEmpty()) {
            System.out.println("Trying to fetch fallback cover URL for manga ID: " + manga.getId());
            new Thread(() -> {
                try {
                    // First try to get full manga details, which should include cover and
                    // author/artist info
                    mangaService.getMangaDetails(manga.getId()).ifPresentOrElse(
                            fullManga -> {
                                String newCoverUrl = null;

                                // Update cover URL if available
                                if (fullManga.getCoverUrl() != null && !fullManga.getCoverUrl().isEmpty()) {
                                    newCoverUrl = fullManga.getCoverUrl();
                                    manga.setCoverUrl(newCoverUrl);
                                    System.out.println("Retrieved cover URL from full manga details: " + newCoverUrl);
                                }

                                // Update author if available
                                if (fullManga.getAuthor() != null && !fullManga.getAuthor().isEmpty()) {
                                    String author = fullManga.getAuthor();
                                    manga.setAuthor(author);
                                    System.out.println("Retrieved author from full manga details: " + author);
                                    // Update the author field on the UI thread
                                    javafx.application.Platform.runLater(() -> {
                                        if (authorsField != null) {
                                            authorsField.setText(author);
                                        }
                                    });
                                }

                                // Update artist if available
                                if (fullManga.getArtist() != null && !fullManga.getArtist().isEmpty()) {
                                    String artist = fullManga.getArtist();
                                    manga.setArtist(artist);
                                    System.out.println("Retrieved artist from full manga details: " + artist);
                                    // Update the artist field on the UI thread
                                    javafx.application.Platform.runLater(() -> {
                                        if (artistsField != null) {
                                            artistsField.setText(artist);
                                        }
                                    });
                                }

                                // Update the cover URL field and load image if we have a new cover URL
                                if (newCoverUrl != null) {
                                    final String finalCoverUrl = newCoverUrl;

                                    // Update the cover URL field on the UI thread
                                    javafx.application.Platform.runLater(() -> {
                                        if (coverUrlField != null) {
                                            coverUrlField.setText(finalCoverUrl);
                                        }
                                    });

                                    // Load the new image on UI thread
                                    javafx.application.Platform.runLater(() -> {
                                        try {
                                            Image newCoverImg = new Image(finalCoverUrl, true);
                                            newCoverImg.progressProperty()
                                                    .addListener((obs, oldProgress, newProgress) -> {
                                                        if (newProgress.doubleValue() == 1.0
                                                                && !newCoverImg.isError()) {
                                                            coverImageView.setImage(newCoverImg);
                                                            System.out.println(
                                                                    "Fallback cover image successfully loaded");
                                                        }
                                                    });

                                            newCoverImg.errorProperty().addListener((obs, wasError, isError) -> {
                                                if (isError) {
                                                    System.err
                                                            .println("Error loading fallback cover, using placeholder");
                                                    coverImageView.setImage(new Image(placeholderUrl, true));
                                                }
                                            });
                                        } catch (Exception e) {
                                            System.err.println("Error loading fallback image: " + e.getMessage());
                                            coverImageView.setImage(new Image(placeholderUrl, true));
                                        }
                                    });
                                }
                            },
                            // If no details available, try direct cover URL method
                            () -> fallbackToDirectCoverUrl(manga, placeholderUrl));
                } catch (Exception e) {
                    System.err.println("Error in fallback cover process: " + e.getMessage());
                    javafx.application.Platform
                            .runLater(() -> coverImageView.setImage(new Image(placeholderUrl, true)));
                }
            }).start();
        } else {
            // No manga ID to get cover, use placeholder
            coverImageView.setImage(new Image(placeholderUrl, true));
        }
    }

    /**
     * Helper method to fetch cover URL directly as a last resort
     * 
     * @param manga          The manga to get cover for
     * @param placeholderUrl The placeholder to use if all fails
     */
    private void fallbackToDirectCoverUrl(Manga manga, String placeholderUrl) {
        try {
            String directCoverUrl = mangaService.getCoverUrl(manga.getId());
            if (directCoverUrl != null && !directCoverUrl.isEmpty()) {
                manga.setCoverUrl(directCoverUrl);
                System.out.println("Retrieved direct cover URL: " + directCoverUrl);

                // Update the cover URL field on the UI thread
                javafx.application.Platform.runLater(() -> {
                    if (coverUrlField != null) {
                        coverUrlField.setText(directCoverUrl);
                    }
                });

                // Load the new image on UI thread
                javafx.application.Platform.runLater(() -> {
                    try {
                        Image directCoverImg = new Image(directCoverUrl, true);
                        directCoverImg.progressProperty().addListener((obs, oldProgress, newProgress) -> {
                            if (newProgress.doubleValue() == 1.0 && !directCoverImg.isError()) {
                                coverImageView.setImage(directCoverImg);
                                System.out.println("Direct cover image successfully loaded");
                            }
                        });

                        directCoverImg.errorProperty().addListener((obs, wasError, isError) -> {
                            if (isError) {
                                System.err.println("Error loading direct cover, using placeholder");
                                coverImageView.setImage(new Image(placeholderUrl, true));
                            }
                        });
                    } catch (Exception e) {
                        System.err.println("Error loading direct cover image: " + e.getMessage());
                        coverImageView.setImage(new Image(placeholderUrl, true));
                    }
                });
            } else {
                // If still no cover, use placeholder
                javafx.application.Platform
                        .runLater(() -> coverImageView.setImage(new Image(placeholderUrl, true)));
            }
        } catch (Exception e) {
            System.err.println("Error getting direct cover: " + e.getMessage());
            javafx.application.Platform
                    .runLater(() -> coverImageView.setImage(new Image(placeholderUrl, true)));
        }
    }

    /**
     * Apply the current theme stylesheet to the dialog
     */
    private void applyCurrentTheme(DialogPane dialogPane) {
        dialogPane.getStylesheets().clear();
        String cssPath = themeManager.getCurrentTheme().getCssPath();
        try {
            String cssUrl = getClass().getResource(cssPath).toExternalForm();
            dialogPane.getStylesheets().add(cssUrl);
        } catch (Exception e) {
            System.err.println("Error loading theme CSS for modal: " + e.getMessage());
            // Fallback to light theme
            try {
                String fallbackUrl = getClass().getResource("/styles/main.css").toExternalForm();
                dialogPane.getStylesheets().add(fallbackUrl);
            } catch (Exception fallbackError) {
                System.err.println("Error loading fallback CSS: " + fallbackError.getMessage());
            }
        }
    }

    /**
     * Apply theme-aware styling to all UI components
     */
    private void applyThemeStyles() {
        boolean isDark = themeManager.isDarkTheme();

        // Update dialog pane background
        DialogPane dialogPane = getDialogPane();
        String dialogBg = isDark ? "#2b2b2b" : "#ffffff";
        dialogPane.setStyle("-fx-background-color: " + dialogBg + ";");

        // Update cover image view
        String coverBg = isDark ? "#3c3c3c" : "#f8f9fa";
        String coverBorder = isDark ? "#5a5a5a" : "#dee2e6";
        coverImageView.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1; " +
                        "-fx-border-radius: 8; -fx-background-radius: 8; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);",
                coverBg, coverBorder));

        // Read-only field styling
        String readOnlyBg = isDark ? "#3c3c3c" : "#f8f9fa";
        String readOnlyBorder = isDark ? "#5a5a5a" : "#dee2e6";
        String readOnlyText = isDark ? "#b0b0b0" : "#495057";

        titleField.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-text-fill: %s; " +
                        "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 12;",
                readOnlyBg, readOnlyBorder, readOnlyText));

        descriptionArea.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-text-fill: %s; " +
                        "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 12;",
                readOnlyBg, readOnlyBorder, readOnlyText));

        // Author and artist fields with better styling
        authorsField.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-text-fill: %s; " +
                        "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 12;",
                readOnlyBg, readOnlyBorder, readOnlyText));

        artistsField.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-text-fill: %s; " +
                        "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 12;",
                readOnlyBg, readOnlyBorder, readOnlyText));

        // Language and status fields with modern styling and proper alignment
        languageField.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-text-fill: %s; " +
                        "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 12; -fx-alignment: center-left;",
                readOnlyBg, readOnlyBorder, readOnlyText));

        statusField.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-text-fill: %s; " +
                        "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 12; -fx-alignment: center-left;",
                readOnlyBg, readOnlyBorder, readOnlyText));

        // Update cover URL field with modern styling
        coverUrlField.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-text-fill: %s; " +
                        "-fx-font-size: 11px; -fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 6 10;",
                readOnlyBg, readOnlyBorder, readOnlyText));

        // Update labels
        updateLabelsTheme();

        // Update tag chips
        updateTagChipsTheme();
    }

    /**
     * Update label styling for current theme
     */
    private void updateLabelsTheme() {
        String textColor = themeManager.getTextColor();
        String primaryColor = themeManager.isDarkTheme() ? "#0096c9" : "#007bff";

        // Find and update all labels in the dialog
        DialogPane dialogPane = getDialogPane();
        updateLabelsInNode(dialogPane, textColor, primaryColor);
    }

    /**
     * Recursively update labels in a node
     */
    private void updateLabelsInNode(javafx.scene.Node node, String textColor, String primaryColor) {
        if (node instanceof Label) {
            Label label = (Label) node;
            // Use consistent styling for all labels
            label.setStyle(String.format("-fx-font-weight: bold; -fx-text-fill: %s;", textColor));
        } else if (node instanceof javafx.scene.Parent) {
            javafx.scene.Parent parent = (javafx.scene.Parent) node;
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                updateLabelsInNode(child, textColor, primaryColor);
            }
        }
    }

    /**
     * Update tag chip styling for current theme
     */
    private void updateTagChipsTheme() {
        boolean isDark = themeManager.isDarkTheme();
        String chipBg = isDark ? "#4a4a4a" : "#e7f3ff";
        String chipText = isDark ? "#87ceeb" : "#0066cc";
        String chipBorder = isDark ? "#5a5a5a" : "#b3d9ff";

        tagsFlowPane.getChildren().forEach(node -> {
            if (node instanceof HBox) {
                HBox chipContainer = (HBox) node;
                chipContainer.getChildren().forEach(child -> {
                    if (child instanceof Label) {
                        Label tagLabel = (Label) child;
                        tagLabel.setStyle(String.format(
                                "-fx-background-color: %s; -fx-padding: 6 12; -fx-background-radius: 18; " +
                                        "-fx-font-size: 12px; -fx-text-fill: %s; -fx-border-color: %s; " +
                                        "-fx-border-width: 1; -fx-border-radius: 18;",
                                chipBg, chipText, chipBorder));
                    }
                });
            }
        });
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme newTheme) {
        Platform.runLater(() -> {
            // Update dialog stylesheet
            applyCurrentTheme(getDialogPane());
            // Update component styles
            applyThemeStyles();
        });
    }

    /**
     * Initialize the dialog with current theme and apply styles
     */
    private void initializeTheme() {
        // Register for theme changes
        themeManager.addThemeChangeListener(this);

        Platform.runLater(() -> applyThemeStyles());
    }

    /**
     * Calculate estimated height needed for tag chips based on number of tags
     * This helps auto-adjust the tags container height
     */
    private double calculateTagsHeight() {
        if (mangaToAdd.getGenres() == null || mangaToAdd.getGenres().isEmpty()) {
            return 40; // Minimum height for empty state
        }

        // Estimate: each tag chip is ~25px tall, ~80px wide on average
        // Container is ~400px wide, so roughly 4-5 tags per row
        int tagCount = mangaToAdd.getGenres().size();
        int tagsPerRow = 4; // Conservative estimate
        int estimatedRows = Math.max(1, (tagCount + tagsPerRow - 1) / tagsPerRow); // Ceiling division

        // Each row: 25px tag height + 5px gap + padding
        return (estimatedRows * 30) + 20; // 20px for container padding
    }
}