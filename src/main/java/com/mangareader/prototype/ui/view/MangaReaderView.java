package com.mangareader.prototype.ui.view;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mangareader.prototype.model.Chapter;
import com.mangareader.prototype.model.Manga;
import com.mangareader.prototype.service.LibraryService;
import com.mangareader.prototype.service.MangaService;
import com.mangareader.prototype.service.impl.DefaultMangaServiceImpl;
import com.mangareader.prototype.service.impl.LibraryServiceImpl;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class MangaReaderView extends BorderPane {
    // Zoom memory - stores zoom levels per manga
    private static final Map<String, Double> MANGA_ZOOM_LEVELS = new HashMap<>();

    // Reading mode memory - stores reading mode preferences per manga
    private static final Map<String, Boolean> MANGA_READING_MODES = new HashMap<>();

    private final StackPane imageContainer;
    private final ScrollPane webtoonScrollPane;
    private final VBox webtoonContainer;
    private final ImageView currentImageView;
    private final ProgressIndicator progressIndicator;
    private final Label errorLabel;
    private final Label pageInfoLabel;
    private final Button prevButton;
    private final Button nextButton;
    private final Button prevChapterButton;
    private final Button nextChapterButton;
    private final Button backButton;
    private final Button modeToggleButton;
    private final Slider zoomSlider;
    private final HBox controlsBox;

    private final MangaService mangaService;
    private final LibraryService libraryService;
    private final ExecutorService executorService;

    private Chapter currentChapter;
    private String currentMangaId;
    private List<String> pageUrls;
    private int currentPageIndex = 0;
    private double zoomLevel = 1.0;
    private boolean isWebtoonMode = false;
    private Runnable onBackCallback;

    // Chapter navigation data
    private List<Chapter> chapterList;
    private int currentChapterIndex = -1;

    public MangaReaderView() {
        this(null);
    }

    public MangaReaderView(Runnable onBackCallback) {
        this.onBackCallback = onBackCallback;
        this.mangaService = new DefaultMangaServiceImpl();
        this.libraryService = new LibraryServiceImpl();
        this.executorService = Executors.newSingleThreadExecutor();

        // Main image container with dark background (for traditional mode)
        imageContainer = new StackPane();
        imageContainer.setStyle("-fx-background-color: #2b2b2b;");

        // Webtoon container setup (for continuous scrolling)
        webtoonContainer = new VBox();
        webtoonContainer.setStyle("-fx-background-color: #2b2b2b;");
        webtoonContainer.setAlignment(Pos.CENTER);
        webtoonContainer.setSpacing(3); // Minimal spacing for webtoon reading

        webtoonScrollPane = new ScrollPane(webtoonContainer);
        webtoonScrollPane.setStyle("-fx-background-color: #2b2b2b;");
        webtoonScrollPane.setFitToWidth(true);
        webtoonScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        webtoonScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        webtoonScrollPane.setPannable(true); // Allow panning/dragging

        // Add scroll listener for webtoon progress tracking
        webtoonScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (isWebtoonMode && currentMangaId != null && currentChapter != null) {
                saveWebtoonScrollPosition(newVal.doubleValue());
            }
        });

        // Current page image view (for traditional mode)
        currentImageView = new ImageView();
        currentImageView.setPreserveRatio(true);
        currentImageView.setSmooth(true);
        currentImageView.setCache(true);

        // Progress indicator
        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(50, 50);
        progressIndicator.setVisible(false);

        // Error label
        errorLabel = new Label("Error loading pages.");
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px;");
        errorLabel.setVisible(false);

        // Add image and overlays to container
        imageContainer.getChildren().addAll(currentImageView, progressIndicator, errorLabel);

        // Page info label
        pageInfoLabel = new Label("Page 0 / 0");
        pageInfoLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

        // Navigation buttons
        backButton = new Button("⬅ Back to Details");
        backButton.setStyle(
                "-fx-background-color: #6c757d; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 8 12;");
        backButton.setOnAction(e -> {
            if (onBackCallback != null) {
                onBackCallback.run();
            }
        });

        // Mode toggle button
        modeToggleButton = new Button("📖 Traditional");
        modeToggleButton.setStyle(
                "-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 8 12;");
        modeToggleButton.setOnAction(e -> toggleReadingMode());

        prevButton = new Button("◀ Previous");
        prevButton.setDisable(true);
        prevButton.setOnAction(e -> previousPage());

        nextButton = new Button("Next ▶");
        nextButton.setDisable(true);
        nextButton.setOnAction(e -> nextPage());

        // Chapter navigation buttons
        prevChapterButton = new Button("◄◄ Prev Chapter");
        prevChapterButton.setStyle(
                "-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 6 10;");
        prevChapterButton.setDisable(true);
        prevChapterButton.setOnAction(e -> previousChapter());

        nextChapterButton = new Button("Next Chapter ►►");
        nextChapterButton.setStyle(
                "-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 6 10;");
        nextChapterButton.setDisable(true);
        nextChapterButton.setOnAction(e -> nextChapter());

        // Zoom slider with conservative limits to ensure controls always remain
        // accessible
        zoomSlider = new Slider(0.3, 1.8, 1.0);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setPrefWidth(200);
        zoomSlider.setMajorTickUnit(0.3);
        zoomSlider.setMinorTickCount(2);
        zoomSlider.setSnapToTicks(false);
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            // Validate zoom level to ensure controls remain accessible with stricter limits
            double newZoom = newVal.doubleValue();

            // Clamp zoom level to more conservative bounds for better navigation control
            if (newZoom < 0.3) { // Slightly increased minimum for better readability
                newZoom = 0.3;
            }
            if (newZoom > 1.8) { // Slightly reduced maximum to ensure controls always remain accessible
                newZoom = 1.8;
            }

            final double finalZoom = newZoom;
            zoomLevel = finalZoom;

            // Update slider if value was clamped
            if (Math.abs(finalZoom - newVal.doubleValue()) > 0.01) {
                Platform.runLater(() -> zoomSlider.setValue(finalZoom));
            }

            // Save zoom level for current manga
            saveZoomLevel();

            if (isWebtoonMode) {
                updateWebtoonImageSizes();
            } else {
                updateImageSize();
            }
        });

        // Auto-fit button for quick fit-to-window
        Button autoFitButton = new Button("Fit");
        autoFitButton.setOnAction(e -> {
            zoomSlider.setValue(1.0);
            if (isWebtoonMode) {
                updateWebtoonImageSizes();
            } else {
                updateImageSize();
            }
        });

        Label zoomLabel = new Label("Zoom:");
        zoomLabel.setStyle("-fx-text-fill: white;");

        // Controls container - make it always visible and sticky at bottom
        controlsBox = new HBox(15);
        controlsBox.setAlignment(Pos.CENTER);
        controlsBox.setPadding(new Insets(15));
        controlsBox.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.9); -fx-border-color: #444; -fx-border-width: 1 0 0 0;");

        // Ensure controls always stay visible - set minimum height and make it sticky
        controlsBox.setMinHeight(80);
        controlsBox.setMaxHeight(80);

        controlsBox.getChildren().addAll(
                backButton,
                new Label("   "),
                modeToggleButton,
                new Label(" "),
                prevChapterButton,
                new Label(" "),
                prevButton, pageInfoLabel, nextButton,
                new Label(" "),
                nextChapterButton,
                new Label("   "),
                zoomLabel, zoomSlider, autoFitButton);

        // Layout
        setCenter(imageContainer);
        setBottom(controlsBox);

        setFocusTraversable(true);
        setOnKeyPressed(this::handleKeyPressed);

        // Auto-focus for keyboard events
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(() -> requestFocus());
            }
        });

        imageContainer.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            Platform.runLater(this::updateImageSize);
        });

        imageContainer.heightProperty().addListener((obs, oldHeight, newHeight) -> {
            Platform.runLater(this::updateImageSize);
        });

        // Listen for webtoon container resize to update webtoon images
        webtoonScrollPane.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (isWebtoonMode) {
                Platform.runLater(this::updateWebtoonImageSizes);
            }
        });
    }

    private void toggleReadingMode() {
        isWebtoonMode = !isWebtoonMode;

        // Save the user's reading mode preference for this manga
        saveReadingMode();

        if (isWebtoonMode) {
            // Switch to webtoon mode
            modeToggleButton.setText("📜 Webtoon");
            setCenter(webtoonScrollPane);
            setupWebtoonView();
            // In webtoon mode, hide individual page navigation but keep other controls
            prevButton.setVisible(false);
            nextButton.setVisible(false);
            pageInfoLabel.setVisible(false);
        } else {
            // Switch to traditional mode
            modeToggleButton.setText("📖 Traditional");
            setCenter(imageContainer);
            displayCurrentPage();
            // Show page navigation in traditional mode
            prevButton.setVisible(true);
            nextButton.setVisible(true);
            pageInfoLabel.setVisible(true);
            updateNavigationButtons();
        }
    }

    private void setupWebtoonView() {
        if (pageUrls == null || pageUrls.isEmpty()) {
            return;
        }

        webtoonContainer.getChildren().clear();
        progressIndicator.setVisible(true);

        // Load all images in sequence for continuous scrolling
        executorService.submit(() -> {
            try {
                for (int i = 0; i < pageUrls.size(); i++) {
                    final int pageIndex = i;
                    final String pageUrl = pageUrls.get(i);

                    Platform.runLater(() -> {
                        ImageView pageImageView = new ImageView();
                        pageImageView.setPreserveRatio(true);
                        pageImageView.setSmooth(true);
                        pageImageView.setCache(true);

                        // Set width to fit the container properly for webtoon reading
                        // Use a larger width for better readability
                        double containerWidth = webtoonScrollPane.getWidth();
                        if (containerWidth <= 0) {
                            containerWidth = getScene() != null ? getScene().getWidth() - 250 : 800;
                        }

                        // Set the image to use most of the available width, applying zoom level with
                        // stricter limits
                        double baseWidth = Math.max(600, containerWidth * 0.85);
                        double targetWidth = baseWidth * zoomLevel;

                        double maxAllowedWidth = containerWidth * 2.0; // Reduced from 2.5 for better control
                        if (targetWidth > maxAllowedWidth) {
                            targetWidth = maxAllowedWidth;
                        }

                        // Also ensure minimum width for readability
                        double minAllowedWidth = containerWidth * 0.3;
                        if (targetWidth < minAllowedWidth) {
                            targetWidth = minAllowedWidth;
                        }

                        pageImageView.setFitWidth(targetWidth);

                        // Create image with better error handling for JPEG corruption
                        Image image = new Image(pageUrl, true);

                        // Add error listeners to handle corrupted JPEG data
                        image.errorProperty().addListener((obs, wasError, isError) -> {
                            if (isError) {
                                System.err
                                        .println("Error loading manga page image (likely corrupted JPEG): " + pageUrl);
                                // Set a placeholder image instead
                                Platform.runLater(() -> {
                                    Image errorImage = new Image(
                                            "https://via.placeholder.com/600x800/333333/ffffff?text=Image+Error");
                                    pageImageView.setImage(errorImage);
                                });
                            }
                        });

                        image.exceptionProperty().addListener((obs, oldEx, newEx) -> {
                            if (newEx != null) {
                                System.err.println(
                                        "Exception loading manga page: " + newEx.getMessage() + " for: " + pageUrl);
                                Platform.runLater(() -> {
                                    Image errorImage = new Image(
                                            "https://via.placeholder.com/600x800/333333/ffffff?text=Load+Failed");
                                    pageImageView.setImage(errorImage);
                                });
                            }
                        });

                        pageImageView.setImage(image);

                        // For continuous webtoon reading, eliminate gaps between pages
                        webtoonContainer.getChildren().add(pageImageView);

                        // Update progress
                        if (pageIndex == pageUrls.size() - 1) {
                            progressIndicator.setVisible(false);
                        }
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    displayError("Failed to load webtoon pages: " + e.getMessage());
                    progressIndicator.setVisible(false);
                });
            }
        });
    }

    public void loadChapter(Chapter chapter) {
        if (chapter == null) {
            displayError("No chapter provided.");
            return;
        }

        this.currentChapter = chapter;
        this.currentPageIndex = 0;

        // Try to restore reading position if in library
        if (currentMangaId != null && libraryService.isInLibrary(currentMangaId)) {
            restoreReadingPosition();
        }

        // Auto-detect reading mode based on chapter format OR manga genres
        // But first check if user has a saved preference for this manga
        boolean shouldUseWebtoonMode = false;

        if (currentMangaId != null && MANGA_READING_MODES.containsKey(currentMangaId)) {
            // User has a saved reading mode preference - use it
            shouldUseWebtoonMode = MANGA_READING_MODES.get(currentMangaId);
            System.out.println(
                    "Using saved reading mode preference: " + (shouldUseWebtoonMode ? "Webtoon" : "Traditional"));
        } else {
            // No saved preference, use auto-detection
            // First check chapter's reading format
            if (chapter.getReadingFormat() != null && "webtoon".equals(chapter.getReadingFormat())) {
                shouldUseWebtoonMode = true;
            } else {
                // Auto-detect based on manga genres if we have manga ID
                if (currentMangaId != null) {
                    shouldUseWebtoonMode = detectWebtoonFromGenres();
                }
            }
        }

        // Apply the detected mode
        if (shouldUseWebtoonMode && !isWebtoonMode) {
            toggleReadingMode();
        } else if (!shouldUseWebtoonMode && isWebtoonMode) {
            toggleReadingMode();
        }

        progressIndicator.setVisible(true);
        errorLabel.setVisible(false);
        currentImageView.setImage(null);

        // Update chapter navigation buttons
        updateChapterNavigationButtons();

        executorService.submit(() -> {
            try {
                List<String> urls = mangaService.getChapterPages(chapter.getMangaId(), chapter.getId());
                Platform.runLater(() -> {
                    this.pageUrls = urls;
                    progressIndicator.setVisible(false);
                    if (urls != null && !urls.isEmpty()) {
                        if (isWebtoonMode) {
                            setupWebtoonView();
                        } else {
                            displayCurrentPage();
                            updateNavigationButtons();
                        }
                    } else {
                        displayError("No pages found for this chapter.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    displayError("Failed to load pages: " + e.getMessage());
                    progressIndicator.setVisible(false);
                    e.printStackTrace();
                });
            }
        });
    }

    /**
     * Detect if manga should use webtoon mode based on genres
     */
    private boolean detectWebtoonFromGenres() {
        try {
            // Get manga details to check genres
            Optional<Manga> mangaOpt = mangaService.getMangaDetails(currentMangaId);
            if (mangaOpt.isPresent()) {
                Manga manga = mangaOpt.get();
                List<String> genres = manga.getGenres();

                if (genres != null) {
                    // Check for webtoon-related genres (case-insensitive)
                    for (String genre : genres) {
                        String lowerGenre = genre.toLowerCase();
                        if (lowerGenre.contains("webtoon") ||
                                lowerGenre.contains("long strip") ||
                                lowerGenre.contains("web comic") ||
                                lowerGenre.contains("manhwa") ||
                                lowerGenre.contains("manhua")) {
                            System.out.println("Auto-detected webtoon format based on genre: " + genre);
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error detecting webtoon format from genres: " + e.getMessage());
        }
        return false;
    }

    private void displayCurrentPage() {
        if (pageUrls == null || pageUrls.isEmpty() || currentPageIndex < 0 || currentPageIndex >= pageUrls.size()) {
            return;
        }

        String pageUrl = pageUrls.get(currentPageIndex);
        Image image = new Image(pageUrl, true);

        // Add error listeners to handle corrupted JPEG data
        image.errorProperty().addListener((obs, wasError, isError) -> {
            if (isError) {
                System.err.println("Error loading manga page (likely corrupted JPEG): " + pageUrl);
                Platform.runLater(() -> {
                    Image errorImage = new Image("https://via.placeholder.com/800x600/333333/ffffff?text=Image+Error");
                    currentImageView.setImage(errorImage);
                });
            }
        });

        image.exceptionProperty().addListener((obs, oldEx, newEx) -> {
            if (newEx != null) {
                System.err.println("Exception loading page: " + newEx.getMessage() + " for: " + pageUrl);
                Platform.runLater(() -> {
                    Image errorImage = new Image("https://via.placeholder.com/800x600/333333/ffffff?text=Load+Failed");
                    currentImageView.setImage(errorImage);
                });
            }
        });

        currentImageView.setImage(image);

        // Update page info
        pageInfoLabel.setText(String.format("Page %d / %d", currentPageIndex + 1, pageUrls.size()));

        // Listen for image to load and then resize
        image.progressProperty().addListener((obs, oldProgress, newProgress) -> {
            if (newProgress.doubleValue() >= 1.0) {
                Platform.runLater(this::updateImageSize);
            }
        });

        // Also update size immediately in case image loads quickly
        Platform.runLater(this::updateImageSize);

        // Ensure focus for keyboard navigation
        Platform.runLater(() -> requestFocus());
    }

    private void updateImageSize() {
        if (currentImageView.getImage() == null)
            return;

        // Get available space (minus controls)
        double availableWidth = imageContainer.getWidth();
        double availableHeight = imageContainer.getHeight();

        if (availableWidth <= 0 || availableHeight <= 0) {
            // Fallback to reasonable defaults based on scene size
            availableWidth = getScene() != null ? getScene().getWidth() - 40 : 800;
            availableHeight = getScene() != null ? getScene().getHeight() - 150 : 600; // Reserve more space for
                                                                                       // controls
        }

        // Account for padding and controls - ensure controls always remain visible
        availableWidth -= 40; // Account for padding
        availableHeight -= 150; // Reserve sufficient space for bottom controls (increased from 120)

        // Get image dimensions
        Image image = currentImageView.getImage();
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();

        if (imageWidth <= 0 || imageHeight <= 0)
            return;

        // Calculate scale factor to fit image in available space
        double scaleX = availableWidth / imageWidth;
        double scaleY = availableHeight / imageHeight;
        double baseScale = Math.min(scaleX, scaleY);
        double scale = baseScale * zoomLevel;

        // Ensure minimum readable size
        scale = Math.max(scale, 0.1);

        // Strict maximum limits to ensure navigation controls always remain accessible
        // The image should never exceed the available container space significantly
        double maxAllowedWidth = availableWidth * 1.2; // Reduced from 1.5 for stricter control
        double maxAllowedHeight = availableHeight * 1.2;

        double scaledWidth = imageWidth * scale;
        double scaledHeight = imageHeight * scale;

        // Enforce strict limits - if zoom would make image too large, reduce scale
        if (scaledWidth > maxAllowedWidth) {
            scale = maxAllowedWidth / imageWidth;
            scaledHeight = imageHeight * scale; // Recalculate height
        }
        if (scaledHeight > maxAllowedHeight) {
            scale = Math.min(scale, maxAllowedHeight / imageHeight);
        }

        // Set the size
        currentImageView.setFitWidth(imageWidth * scale);
        currentImageView.setFitHeight(imageHeight * scale);
    }

    private void updateWebtoonImageSizes() {
        if (webtoonContainer == null || webtoonContainer.getChildren().isEmpty()) {
            return;
        }

        double containerWidth = webtoonScrollPane.getWidth();
        if (containerWidth <= 0) {
            containerWidth = getScene() != null ? getScene().getWidth() - 250 : 800;
        }

        // Set the image to use most of the available width, applying zoom level with
        // stricter limits
        double baseWidth = Math.max(600, containerWidth * 0.85);
        double targetWidth = baseWidth * zoomLevel;

        // Prevent excessive zoom that could make navigation impossible - stricter
        // limits
        double maxAllowedWidth = containerWidth * 2.0; // Reduced from 2.5 for better control
        if (targetWidth > maxAllowedWidth) {
            targetWidth = maxAllowedWidth;
        }

        // Also ensure minimum width for readability
        double minAllowedWidth = containerWidth * 0.3;
        if (targetWidth < minAllowedWidth) {
            targetWidth = minAllowedWidth;
        }

        // Update all webtoon images
        final double finalTargetWidth = targetWidth;
        webtoonContainer.getChildren().forEach(node -> {
            if (node instanceof ImageView) {
                ImageView imageView = (ImageView) node;
                imageView.setFitWidth(finalTargetWidth);
            }
        });
    }

    private void previousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--;
            displayCurrentPage();
            updateNavigationButtons();
            saveReadingPosition(); // Auto-save progress
        }
    }

    private void nextPage() {
        if (pageUrls != null && currentPageIndex < pageUrls.size() - 1) {
            currentPageIndex++;
            displayCurrentPage();
            updateNavigationButtons();
            saveReadingPosition(); // Auto-save progress
        }
    }

    private void updateNavigationButtons() {
        prevButton.setDisable(currentPageIndex <= 0);
        nextButton.setDisable(pageUrls == null || currentPageIndex >= pageUrls.size() - 1);
    }

    private void handleKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();

        if (code == KeyCode.LEFT || code == KeyCode.A) {
            previousPage();
            event.consume();
        } else if (code == KeyCode.RIGHT || code == KeyCode.D) {
            nextPage();
            event.consume();
        } else if (code == KeyCode.PLUS || code == KeyCode.EQUALS) {
            // Respect the new zoom limits (max 1.8x)
            zoomSlider.setValue(Math.min(1.8, zoomSlider.getValue() + 0.1));
            event.consume();
        } else if (code == KeyCode.MINUS) {
            // Respect the new zoom limits (min 0.3x)
            zoomSlider.setValue(Math.max(0.3, zoomSlider.getValue() - 0.1));
            event.consume();
        } else if (code == KeyCode.DIGIT0) {
            zoomSlider.setValue(1.0); // Reset zoom
            event.consume();
        } else if (code == KeyCode.ESCAPE) {
            if (onBackCallback != null) {
                onBackCallback.run();
            }
            event.consume();
        }
    }

    private void displayError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        currentImageView.setImage(null);
        pageInfoLabel.setText("Page 0 / 0");
        updateNavigationButtons();
    }

    // Getter methods for accessing current state
    public Chapter getCurrentChapter() {
        return currentChapter;
    }

    public int getCurrentPageIndex() {
        return currentPageIndex;
    }

    public int getTotalPages() {
        return pageUrls != null ? pageUrls.size() : 0;
    }

    // Chapter navigation methods
    public void setChapterList(List<Chapter> chapters, Chapter currentChapter) {
        this.chapterList = chapters;
        this.currentChapterIndex = -1; // Reset to invalid index first

        if (chapters != null && currentChapter != null) {
            // Find current chapter index
            for (int i = 0; i < chapters.size(); i++) {
                if (chapters.get(i).getId().equals(currentChapter.getId())) {
                    this.currentChapterIndex = i;
                    break;
                }
            }
        }
        updateChapterNavigationButtons();
    }

    private void updateChapterNavigationButtons() {
        // Disable both buttons if no chapter list or invalid index
        if (chapterList == null || chapterList.isEmpty() || currentChapterIndex < 0) {
            prevChapterButton.setDisable(true);
            nextChapterButton.setDisable(true);
            return;
        }

        // Previous chapter button: disable if at first chapter (index 0)
        prevChapterButton.setDisable(currentChapterIndex <= 0);

        // Next chapter button: disable if at last chapter
        nextChapterButton.setDisable(currentChapterIndex >= chapterList.size() - 1);
    }

    private void previousChapter() {
        // Additional safety checks
        if (chapterList == null || chapterList.isEmpty() || currentChapterIndex <= 0) {
            return;
        }

        Chapter prevChapter = chapterList.get(currentChapterIndex - 1);
        this.currentChapterIndex--; // Update index before loading
        loadChapter(prevChapter);
        updateChapterNavigationButtons();
    }

    private void nextChapter() {
        // Additional safety checks
        if (chapterList == null || chapterList.isEmpty() || currentChapterIndex >= chapterList.size() - 1) {
            return;
        }

        Chapter nextChapter = chapterList.get(currentChapterIndex + 1);
        this.currentChapterIndex++; // Update index before loading
        loadChapter(nextChapter);
        updateChapterNavigationButtons();
    }

    /**
     * Set the manga ID for progress tracking
     */
    public void setMangaId(String mangaId) {
        this.currentMangaId = mangaId;

        // Restore zoom level for this manga
        restoreZoomLevel();

        // Restore reading mode preference for this manga
        restoreReadingMode();
    }

    /**
     * Save zoom level for current manga
     */
    private void saveZoomLevel() {
        if (currentMangaId != null) {
            MANGA_ZOOM_LEVELS.put(currentMangaId, zoomLevel);
        }
    }

    /**
     * Save reading mode preference for current manga
     */
    private void saveReadingMode() {
        if (currentMangaId != null) {
            MANGA_READING_MODES.put(currentMangaId, isWebtoonMode);
        }
    }

    /**
     * Restore zoom level for current manga
     */
    private void restoreZoomLevel() {
        if (currentMangaId != null && MANGA_ZOOM_LEVELS.containsKey(currentMangaId)) {
            double savedZoom = MANGA_ZOOM_LEVELS.get(currentMangaId);
            // Set zoom level and update slider
            zoomLevel = savedZoom;
            Platform.runLater(() -> zoomSlider.setValue(savedZoom));
        }
    }

    /**
     * Restore reading mode preference for current manga
     */
    private void restoreReadingMode() {
        if (currentMangaId != null && MANGA_READING_MODES.containsKey(currentMangaId)) {
            boolean savedWebtoonMode = MANGA_READING_MODES.get(currentMangaId);

            // Apply the saved reading mode if it's different from current
            if (savedWebtoonMode != isWebtoonMode) {
                isWebtoonMode = savedWebtoonMode;
                updateModeToggleButton();
            }
        }
    }

    /**
     * Update the mode toggle button text and appearance
     */
    private void updateModeToggleButton() {
        if (isWebtoonMode) {
            modeToggleButton.setText("📜 Webtoon");
        } else {
            modeToggleButton.setText("📖 Traditional");
        }
    }

    /**
     * Save current reading position to library
     */
    private void saveReadingPosition() {
        if (currentMangaId != null && currentChapter != null &&
                libraryService.isInLibrary(currentMangaId)) {

            int totalPages = pageUrls != null ? pageUrls.size() : 0;
            libraryService.updateReadingPosition(
                    currentMangaId,
                    currentChapter.getId(),
                    currentPageIndex,
                    totalPages);

            // Mark chapter as read if we've reached the end
            if (currentPageIndex >= totalPages - 1) {
                libraryService.markChapterAsRead(currentMangaId, currentChapter.getId());
            }
        }
    }

    /**
     * Restore reading position from library
     */
    private void restoreReadingPosition() {
        if (currentMangaId != null && currentChapter != null) {
            Optional<LibraryService.ReadingPosition> position = libraryService.getReadingPosition(currentMangaId);

            if (position.isPresent()) {
                LibraryService.ReadingPosition pos = position.get();
                // Only restore if it's the same chapter
                if (currentChapter.getId().equals(pos.getChapterId())) {
                    if (isWebtoonMode) {
                        // For webtoon mode, restore scroll position
                        int totalPages = pageUrls != null ? pageUrls.size() : 1;
                        double scrollProgress = totalPages > 1 ? (double) pos.getPageNumber() / (totalPages - 1) : 0.0;
                        Platform.runLater(() -> {
                            webtoonScrollPane.setVvalue(Math.max(0.0, Math.min(1.0, scrollProgress)));
                        });
                        System.out.println("Restored webtoon scroll position: " + (scrollProgress * 100) + "%");
                    } else {
                        // For traditional mode, restore page index
                        currentPageIndex = Math.max(0, pos.getPageNumber());
                        System.out.println("Restored reading position: page " + (currentPageIndex + 1));
                    }
                }
            }
        }
    }

    /**
     * Save webtoon scroll position for progress tracking
     */
    private void saveWebtoonScrollPosition(double scrollValue) {
        if (currentMangaId != null && currentChapter != null &&
                libraryService.isInLibrary(currentMangaId) && isWebtoonMode) {

            // Calculate progress based on scroll position
            // scrollValue ranges from 0.0 (top) to 1.0 (bottom)
            double progress = scrollValue;

            // Mark as completed if scrolled to near the bottom (95%)
            if (progress >= 0.95) {
                libraryService.markChapterAsRead(currentMangaId, currentChapter.getId());
            }

            // Save the scroll position as progress
            int totalPages = pageUrls != null ? pageUrls.size() : 0;
            int simulatedPageIndex = (int) (progress * Math.max(1, totalPages - 1));

            libraryService.updateReadingPosition(
                    currentMangaId,
                    currentChapter.getId(),
                    simulatedPageIndex,
                    totalPages);
        }
    }

    public List<Chapter> getChapterList() {
        return chapterList;
    }

    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}