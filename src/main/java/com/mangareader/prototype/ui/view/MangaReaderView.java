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
import com.mangareader.prototype.service.MangaServiceImpl;
import com.mangareader.prototype.ui.component.ThemeManager;
import com.mangareader.prototype.util.Logger;

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
    private static final Map<String, Double> MANGA_ZOOM_LEVELS = new HashMap<>();
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
    private final ThemeManager themeManager;

    private final MangaServiceImpl mangaService;
    private final LibraryService libraryService;
    private final ExecutorService executorService;

    private Chapter currentChapter;
    private String currentMangaId;
    private List<String> pageUrls;
    private int currentPageIndex = 0;
    private double zoomLevel = 1.0;
    private boolean isWebtoonMode = false;
    private Runnable onBackCallback;

    private List<Chapter> chapterList;
    private int currentChapterIndex = -1;

    public MangaReaderView() {
        this(null);
    }

    public MangaReaderView(Runnable onBackCallback) {
        this.onBackCallback = onBackCallback;
        this.mangaService = MangaServiceImpl.getInstance();
        this.libraryService = LibraryService.getInstance();
        this.executorService = Executors.newSingleThreadExecutor();
        this.themeManager = ThemeManager.getInstance();

        imageContainer = new StackPane();
        imageContainer.setStyle("-fx-background-color: #2b2b2b;");

        webtoonContainer = new VBox();
        webtoonContainer.setStyle("-fx-background-color: #2b2b2b;");
        webtoonContainer.setAlignment(Pos.CENTER);
        webtoonContainer.setSpacing(3); // Minimal spacing for webtoon reading

        webtoonScrollPane = new ScrollPane(webtoonContainer);
        webtoonScrollPane.setStyle("-fx-background-color: #2b2b2b;");
        webtoonScrollPane.setFitToWidth(true);
        webtoonScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        webtoonScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        webtoonScrollPane.setPannable(true);

        webtoonScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (isWebtoonMode && currentMangaId != null && currentChapter != null) {
                saveWebtoonScrollPosition(newVal.doubleValue());
            }
        });

        currentImageView = new ImageView();
        currentImageView.setPreserveRatio(true);
        currentImageView.setSmooth(true);
        currentImageView.setCache(true);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(50, 50);
        progressIndicator.setVisible(false);

        errorLabel = new Label("Error loading pages.");
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px;");
        errorLabel.setVisible(false);

        imageContainer.getChildren().addAll(currentImageView, progressIndicator, errorLabel);

        pageInfoLabel = new Label("Page 0 / 0");
        pageInfoLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

        backButton = new Button("< Back to Details");
        backButton.setStyle(
                "-fx-background-color: #6c757d; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 8 12;");
        backButton.setOnAction(e -> {
            if (onBackCallback != null) {
                onBackCallback.run();
            }
        });

        modeToggleButton = new Button("< Traditional");
        modeToggleButton.setStyle(
                "-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 8 12;");
        modeToggleButton.setOnAction(e -> toggleReadingMode());

        prevButton = new Button("< Previous");
        prevButton.setDisable(true);
        prevButton.setOnAction(e -> previousPage());

        nextButton = new Button("Next >");
        nextButton.setDisable(true);
        nextButton.setOnAction(e -> nextPage());

        prevChapterButton = new Button("< Prev Chapter");
        prevChapterButton.setStyle(
                "-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 6 10;");
        prevChapterButton.setDisable(true);
        prevChapterButton.setOnAction(e -> previousChapter());

        nextChapterButton = new Button("Next Chapter >");
        nextChapterButton.setStyle(
                "-fx-background-color: #007bff; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 6 10;");
        nextChapterButton.setDisable(true);
        nextChapterButton.setOnAction(e -> nextChapter());

        zoomSlider = new Slider(0.3, 1.8, 1.0);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setPrefWidth(200);
        zoomSlider.setMajorTickUnit(0.3);
        zoomSlider.setMinorTickCount(2);
        zoomSlider.setSnapToTicks(false);
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double newZoom = newVal.doubleValue();

            if (newZoom < 0.3) {
                newZoom = 0.3;
            }
            if (newZoom > 1.8) {
                newZoom = 1.8;
            }

            final double finalZoom = newZoom;
            zoomLevel = finalZoom;

            if (Math.abs(finalZoom - newVal.doubleValue()) > 0.01) {
                Platform.runLater(() -> zoomSlider.setValue(finalZoom));
            }

            saveZoomLevel();

            if (isWebtoonMode) {
                updateWebtoonImageSizes();
            } else {
                updateImageSize();
            }
        });

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

        controlsBox = new HBox(15);
        controlsBox.setAlignment(Pos.CENTER);
        controlsBox.setPadding(new Insets(15));
        controlsBox.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.9); -fx-border-color: #444; -fx-border-width: 1 0 0 0;");

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

        setCenter(imageContainer);
        setBottom(controlsBox);

        setFocusTraversable(true);
        setOnKeyPressed(this::handleKeyPressed);

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

        webtoonScrollPane.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (isWebtoonMode) {
                Platform.runLater(this::updateWebtoonImageSizes);
            }
        });

        themeManager.addThemeChangeListener(theme -> Platform.runLater(this::applyTheme));
        applyTheme();
    }

    private void applyTheme() {
        boolean isDarkTheme = themeManager.isDarkTheme();
        String backgroundColor = isDarkTheme ? "#2b2b2b" : "#f5f5f5";
        String containerBackground = isDarkTheme ? "#2b2b2b" : "#ffffff";
        String textColor = isDarkTheme ? "#f0f0f0" : "#1f1f1f";
        String borderColor = isDarkTheme ? "#444444" : "#cccccc";
        String controlsBackground = isDarkTheme ? "rgba(0, 0, 0, 0.9)" : "rgba(255, 255, 255, 0.9)";

        setStyle(String.format("-fx-background-color: %s;", backgroundColor));
        imageContainer.setStyle(String.format("-fx-background-color: %s;", containerBackground));
        webtoonContainer.setStyle(String.format("-fx-background-color: %s;", containerBackground));
        webtoonScrollPane.setStyle(String.format("-fx-background-color: %s;", containerBackground));

        pageInfoLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 14px;", textColor));

        String backButtonBg = isDarkTheme ? "#6c757d" : "#dee2e6";
        String backButtonText = isDarkTheme ? "#ffffff" : "#1f1f1f";
        backButton.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 12px; -fx-padding: 8 12;",
                backButtonBg, backButtonText));

        String modeToggleBg = isDarkTheme ? "#28a745" : "#198754";
        modeToggleButton.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: #ffffff; -fx-font-size: 12px; -fx-padding: 8 12;",
                modeToggleBg));

        String chapterButtonBg = isDarkTheme ? "#007bff" : "#0d6efd";
        prevChapterButton.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-padding: 6 10;",
                chapterButtonBg));
        nextChapterButton.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-padding: 6 10;",
                chapterButtonBg));

        controlsBox.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-width: 1 0 0 0;",
                controlsBackground, borderColor));

    controlsBox.getChildren().stream()
        .filter(node -> node instanceof Label)
        .forEach(node -> node.setStyle(String.format("-fx-text-fill: %s;", textColor)));
    }

    private void toggleReadingMode() {
        isWebtoonMode = !isWebtoonMode;

        saveReadingMode();

        if (isWebtoonMode) {
            modeToggleButton.setText("ðŸ“œ Webtoon");
            setCenter(webtoonScrollPane);
            setupWebtoonView();
            prevButton.setVisible(false);
            nextButton.setVisible(false);
            pageInfoLabel.setVisible(false);
        } else {
            modeToggleButton.setText("ðŸ“– Traditional");
            setCenter(imageContainer);
            displayCurrentPage();
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

                        double containerWidth = webtoonScrollPane.getWidth();
                        if (containerWidth <= 0) {
                            containerWidth = getScene() != null ? getScene().getWidth() - 250 : 800;
                        }

                        double baseWidth = Math.max(600, containerWidth * 0.85);
                        double targetWidth = baseWidth * zoomLevel;

                        double maxAllowedWidth = containerWidth * 2.0;
                        if (targetWidth > maxAllowedWidth) {
                            targetWidth = maxAllowedWidth;
                        }

                        double minAllowedWidth = containerWidth * 0.3;
                        if (targetWidth < minAllowedWidth) {
                            targetWidth = minAllowedWidth;
                        }

                        pageImageView.setFitWidth(targetWidth);

                        Image image = new Image(pageUrl, true);

                        image.errorProperty().addListener((obs, wasError, isError) -> {
                            if (isError) {
                                System.err
                                        .println("Error loading manga page image (likely corrupted JPEG): " + pageUrl);
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

                        webtoonContainer.getChildren().add(pageImageView);

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

        if (currentMangaId != null && libraryService.isInLibrary(currentMangaId)) {
            restoreReadingPosition();
        }

        boolean shouldUseWebtoonMode = false;

        if (currentMangaId != null && MANGA_READING_MODES.containsKey(currentMangaId)) {
            shouldUseWebtoonMode = MANGA_READING_MODES.get(currentMangaId);
            System.out.println(
                    "Using saved reading mode preference: " + (shouldUseWebtoonMode ? "Webtoon" : "Traditional"));
        } else {
            if (chapter.getReadingFormat() != null && "webtoon".equals(chapter.getReadingFormat())) {
                shouldUseWebtoonMode = true;
            } else {
                if (currentMangaId != null) {
                    shouldUseWebtoonMode = detectWebtoonFromGenres();
                }
            }
        }

        if (shouldUseWebtoonMode && !isWebtoonMode) {
            toggleReadingMode();
        } else if (!shouldUseWebtoonMode && isWebtoonMode) {
            toggleReadingMode();
        }

        progressIndicator.setVisible(true);
        errorLabel.setVisible(false);
        currentImageView.setImage(null);

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
                    Logger.error("MangaReaderView", "Failed to load chapter pages", e);
                });
            }
        });
    }

    /**
     * Detect if manga should use webtoon mode based on genres
     */
    private boolean detectWebtoonFromGenres() {
        try {
            Optional<Manga> mangaOpt = mangaService.getMangaDetails(currentMangaId);
            if (mangaOpt.isPresent()) {
                Manga manga = mangaOpt.get();
                List<String> genres = manga.getGenres();

                if (genres != null) {
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

        pageInfoLabel.setText(String.format("Page %d / %d", currentPageIndex + 1, pageUrls.size()));

        image.progressProperty().addListener((obs, oldProgress, newProgress) -> {
            if (newProgress.doubleValue() >= 1.0) {
                Platform.runLater(this::updateImageSize);
            }
        });

        Platform.runLater(this::updateImageSize);

        Platform.runLater(() -> requestFocus());
    }

    private void updateImageSize() {
        if (currentImageView.getImage() == null)
            return;

        double availableWidth = imageContainer.getWidth();
        double availableHeight = imageContainer.getHeight();

        if (availableWidth <= 0 || availableHeight <= 0) {
            availableWidth = getScene() != null ? getScene().getWidth() - 40 : 800;
            availableHeight = getScene() != null ? getScene().getHeight() - 150 : 600;
        }

        availableWidth -= 40;
        availableHeight -= 150;

        Image image = currentImageView.getImage();
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();

        if (imageWidth <= 0 || imageHeight <= 0)
            return;

        double scaleX = availableWidth / imageWidth;
        double scaleY = availableHeight / imageHeight;
        double baseScale = Math.min(scaleX, scaleY);
        double scale = baseScale * zoomLevel;

        scale = Math.max(scale, 0.1);

        double maxAllowedWidth = availableWidth * 1.2;
        double maxAllowedHeight = availableHeight * 1.2;

        double scaledWidth = imageWidth * scale;
        double scaledHeight = imageHeight * scale;

        if (scaledWidth > maxAllowedWidth) {
            scale = maxAllowedWidth / imageWidth;
            scaledHeight = imageHeight * scale; // Recalculate height
        }
        if (scaledHeight > maxAllowedHeight) {
            scale = Math.min(scale, maxAllowedHeight / imageHeight);
        }

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

        double baseWidth = Math.max(600, containerWidth * 0.85);
        double targetWidth = baseWidth * zoomLevel;

        double maxAllowedWidth = containerWidth * 2.0;
        if (targetWidth > maxAllowedWidth) {
            targetWidth = maxAllowedWidth;
        }

        double minAllowedWidth = containerWidth * 0.3;
        if (targetWidth < minAllowedWidth) {
            targetWidth = minAllowedWidth;
        }

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
            saveReadingPosition();
        }
    }

    private void nextPage() {
        if (pageUrls != null && currentPageIndex < pageUrls.size() - 1) {
            currentPageIndex++;
            displayCurrentPage();
            updateNavigationButtons();
            saveReadingPosition();
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
            zoomSlider.setValue(Math.min(1.8, zoomSlider.getValue() + 0.1));
            event.consume();
        } else if (code == KeyCode.MINUS) {
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

    public Chapter getCurrentChapter() {
        return currentChapter;
    }

    public int getCurrentPageIndex() {
        return currentPageIndex;
    }

    public int getTotalPages() {
        return pageUrls != null ? pageUrls.size() : 0;
    }

    public void setChapterList(List<Chapter> chapters, Chapter currentChapter) {
        this.chapterList = chapters;
        this.currentChapterIndex = -1;

        if (chapters != null && currentChapter != null) {
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
        if (chapterList == null || chapterList.isEmpty() || currentChapterIndex < 0) {
            prevChapterButton.setDisable(true);
            nextChapterButton.setDisable(true);
            return;
        }

        prevChapterButton.setDisable(currentChapterIndex <= 0);

        nextChapterButton.setDisable(currentChapterIndex >= chapterList.size() - 1);
    }

    private void previousChapter() {
        if (chapterList == null || chapterList.isEmpty() || currentChapterIndex <= 0) {
            return;
        }

        Chapter prevChapter = chapterList.get(currentChapterIndex - 1);
        this.currentChapterIndex--;
        loadChapter(prevChapter);
        updateChapterNavigationButtons();
    }

    private void nextChapter() {
        if (chapterList == null || chapterList.isEmpty() || currentChapterIndex >= chapterList.size() - 1) {
            return;
        }

        Chapter nextChapter = chapterList.get(currentChapterIndex + 1);
        this.currentChapterIndex++;
        loadChapter(nextChapter);
        updateChapterNavigationButtons();
    }

    /**
     * Set the manga ID for progress tracking
     */
    public void setMangaId(String mangaId) {
        this.currentMangaId = mangaId;

        restoreZoomLevel();

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
            modeToggleButton.setText("ðŸ“œ Webtoon");
        } else {
            modeToggleButton.setText("ðŸ“– Traditional");
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
                if (currentChapter.getId().equals(pos.getChapterId())) {
                    if (isWebtoonMode) {
                        int totalPages = pageUrls != null ? pageUrls.size() : 1;
                        double scrollProgress = totalPages > 1 ? (double) pos.getPageNumber() / (totalPages - 1) : 0.0;
                        Platform.runLater(() -> {
                            webtoonScrollPane.setVvalue(Math.max(0.0, Math.min(1.0, scrollProgress)));
                        });
                        System.out.println("Restored webtoon scroll position: " + (scrollProgress * 100) + "%");
                    } else {
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

            double progress = scrollValue;

            if (progress >= 0.95) {
                libraryService.markChapterAsRead(currentMangaId, currentChapter.getId());
            }

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