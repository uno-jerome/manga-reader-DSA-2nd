package com.mangareader.prototype.ui.view;

import com.mangareader.prototype.ui.component.ThemeManager;
import com.mangareader.prototype.util.ImageCache;
import com.mangareader.prototype.util.ThreadPoolManager;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import com.mangareader.prototype.model.Chapter;
import com.mangareader.prototype.model.Manga;
import com.mangareader.prototype.service.LibraryService;
import com.mangareader.prototype.service.MangaServiceImpl;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

public class MangaDetailView extends BorderPane implements ThemeManager.ThemeChangeListener {
    private final ImageView coverImageView;
    private final Label titleLabel;
    private final Label authorLabel;
    private final Label artistLabel;
    private final Label statusLabel;
    private final Label languageLabel;
    private final Label lastUpdatedLabel;
    private final TextArea descriptionArea;
    private final FlowPane genresPane;
    private final Button readButton;
    private final Button addToLibraryButton;
    private final Button removeFromLibraryButton;
    private final Button refreshButton;
    private final TableView<Chapter> chaptersTable;
    private final TabPane chaptersTabPane;
    private final TextField chapterSearchField;
    private final ComboBox<String> sortComboBox;
    private final ComboBox<String> filterComboBox;
    private final Pagination chapterPagination;
    private final Label totalChaptersLabel;
    private final ProgressBar readProgressBar;
    private final Label readProgressLabel;

    private final GridPane statsGrid;
    private final Map<String, Label> statsLabels = new HashMap<>();

    private final MangaServiceImpl mangaService;
    private final LibraryService libraryService;
    private final Consumer<Chapter> onChapterSelectedCallback;
    private Manga currentManga;
    private final ObservableList<Chapter> chapters = FXCollections.observableArrayList();
    private final FilteredList<Chapter> filteredChapters = new FilteredList<>(chapters, p -> true);
    private final SortedList<Chapter> sortedChapters = new SortedList<>(filteredChapters);
    private final int CHAPTERS_PER_PAGE = 50;
    private int currentChapterPage = 0;
    private boolean updatingPagination = false;
    private final ThemeManager themeManager;

    public MangaDetailView() {
        this(null, null);
    }

    public MangaDetailView(Consumer<Chapter> onChapterSelectedCallback) {
        this(onChapterSelectedCallback, null);
    }

    public MangaDetailView(Consumer<Chapter> onChapterSelectedCallback, Runnable onBackCallback) {
        this.onChapterSelectedCallback = onChapterSelectedCallback;
        this.mangaService = MangaServiceImpl.getInstance();
        this.libraryService = LibraryService.getInstance();
        this.themeManager = ThemeManager.getInstance();

        setPadding(new Insets(20));

        coverImageView = new ImageView();
        coverImageView.setFitWidth(250);
        coverImageView.setFitHeight(350);
        coverImageView.setPreserveRatio(true);
        coverImageView.setSmooth(true);
        coverImageView.setCache(true);

        Rectangle clip = new Rectangle(250, 350);
        clip.setArcWidth(20);
        clip.setArcHeight(20);
        coverImageView.setClip(clip);

        coverImageView.setStyle(
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 10, 0, 0, 4);");

        String placeholderUrl = "https://via.placeholder.com/250x350/f8f9fa/6c757d?text=No+Cover";
        coverImageView.setImage(new Image(placeholderUrl, true));

        titleLabel = new Label();
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-wrap-text: true;");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(700);

        authorLabel = new Label();
        authorLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        artistLabel = new Label();
        artistLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        statusLabel = new Label();

        languageLabel = new Label();

        lastUpdatedLabel = new Label();

        descriptionArea = new TextArea();
        descriptionArea.setEditable(false);
        descriptionArea.setWrapText(true);
        descriptionArea.setPrefRowCount(8);

        genresPane = new FlowPane();
        genresPane.setHgap(8);
        genresPane.setVgap(8);
        genresPane.setPrefWrapLength(600);
        genresPane.setPadding(new Insets(10, 0, 10, 0));

        statsGrid = new GridPane();
        statsGrid.setHgap(15);
        statsGrid.setVgap(10);
        statsGrid.setPadding(new Insets(15));

        String[] statsKeys = { "Rating", "Users", "Follows", "Popularity", "Release Year", "Chapter Count" };
        for (int i = 0; i < statsKeys.length; i++) {
            Label keyLabel = new Label(statsKeys[i] + ":");
            keyLabel.setStyle("-fx-font-weight: bold;");
            Label valueLabel = new Label("--");
            valueLabel.setStyle("-fx-font-size: 14px;");

            statsLabels.put(statsKeys[i], valueLabel);
            statsGrid.add(keyLabel, 0, i);
            statsGrid.add(valueLabel, 1, i);
        }

        readProgressBar = new ProgressBar(0);
        readProgressBar.setPrefWidth(200);

        readProgressLabel = new Label("0 / 0 chapters read");

        VBox progressBox = new VBox(5, new Label("Reading Progress"), readProgressBar, readProgressLabel);
        progressBox.setPadding(new Insets(15, 0, 15, 0));

        readButton = new Button("Start Reading");
        readButton.setPrefWidth(150);

        addToLibraryButton = new Button("Add to Library");
        addToLibraryButton.setPrefWidth(150);

        removeFromLibraryButton = new Button("Remove from Library");
        removeFromLibraryButton.setPrefWidth(150);
        removeFromLibraryButton.setVisible(false);

        refreshButton = new Button("Refresh");
        refreshButton.setPrefWidth(150);

        HBox primaryButtonBox = new HBox(10, readButton, removeFromLibraryButton);
        HBox secondaryButtonBox = new HBox(10, addToLibraryButton, refreshButton);

        VBox buttonLayout = new VBox(10, primaryButtonBox, secondaryButtonBox);
        buttonLayout.setAlignment(Pos.CENTER_LEFT);
        buttonLayout.setPadding(new Insets(15, 0, 15, 0));

        chaptersTable = new TableView<>();
        chaptersTable.getStyleClass().add("chapter-table");
        chaptersTable.setMinHeight(400);

        chaptersTable.setRowFactory(tv -> {
            TableRow<Chapter> row = new TableRow<Chapter>() {
                @Override
                protected void updateItem(Chapter item, boolean empty) {
                    super.updateItem(item, empty);

                    if (item == null || empty) {
                        setStyle("");
                    } else {
                        setStyle("-fx-background-color: transparent;");
                        String hoverColor = themeManager.isDarkTheme() ? "#3c3c3c" : "#f8f9fa";
                        setOnMouseEntered(e -> setStyle("-fx-background-color: " + hoverColor + ";"));
                        setOnMouseExited(e -> setStyle("-fx-background-color: transparent;"));
                    }
                }
            };

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && onChapterSelectedCallback != null) {
                    onChapterSelectedCallback.accept(row.getItem());
                }
            });

            return row;
        });

        chapterSearchField = new TextField();
        chapterSearchField.setPromptText("Search chapters...");
        chapterSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterChapters(newVal);
        });

        sortComboBox = new ComboBox<>();
        sortComboBox.getItems().addAll("Newest First", "Oldest First", "By Volume");
        sortComboBox.setValue("Newest First");
        sortComboBox.getStyleClass().add("combo-box-elegant");
        sortComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                sortChapters(newVal);
            }
        });

        filterComboBox = new ComboBox<>();
        filterComboBox.setPromptText("Filter by Volume");
        filterComboBox.getItems().addAll("All");
        filterComboBox.setValue("All");
        filterComboBox.getStyleClass().add("combo-box-elegant");
        filterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                filterChaptersByVolume(newVal);
            }
        });

        HBox searchBox = new HBox(10,
                new Label("Search:"), chapterSearchField,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                new Label("Sort:"), sortComboBox,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                new Label("Volume:"), filterComboBox);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setPadding(new Insets(0, 0, 10, 0));

        totalChaptersLabel = new Label("Total: 0 chapters");
        totalChaptersLabel.setStyle("-fx-font-size: 14px;");

        chapterPagination = new Pagination(1, 0);
        chapterPagination.getStyleClass().add("simple-pagination");
        chapterPagination.currentPageIndexProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingPagination) {
                currentChapterPage = newVal.intValue();
                updateChapterTable();
            }
        });

        HBox paginationBox = new HBox(10, totalChaptersLabel, chapterPagination);
        paginationBox.setAlignment(Pos.CENTER_LEFT);

        TableColumn<Chapter, String> chapterColumn = new TableColumn<>("Chapter");
        chapterColumn.setPrefWidth(70);
        chapterColumn.setCellValueFactory(data -> {
            Chapter chapter = data.getValue();
            return new SimpleObjectProperty<>("Ch. " + chapter.getNumber());
        });

        TableColumn<Chapter, String> titleColumn = new TableColumn<>("Title");
        titleColumn.setPrefWidth(400);
        titleColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getTitle()));

        TableColumn<Chapter, String> volumeColumn = new TableColumn<>("Volume");
        volumeColumn.setPrefWidth(70);
        volumeColumn.setCellValueFactory(data -> {
            Chapter chapter = data.getValue();
            return new SimpleObjectProperty<>(chapter.getVolume() != null ? "Vol. " + chapter.getVolume() : "");
        });

        TableColumn<Chapter, String> dateColumn = new TableColumn<>("Date");
        dateColumn.setPrefWidth(100);
        dateColumn.setCellValueFactory(data -> {
            Chapter chapter = data.getValue();
            return new SimpleObjectProperty<>(chapter.getReleaseDate() != null
                    ? chapter.getReleaseDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    : "");
        });

        TableColumn<Chapter, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setPrefWidth(100);
        statusColumn.setCellValueFactory(data -> {
            Chapter chapter = data.getValue();
            boolean isRead = false;
            if (currentManga != null && libraryService.isInLibrary(currentManga.getId())) {
                isRead = libraryService.isChapterRead(currentManga.getId(), chapter.getId());
            }
            return new SimpleObjectProperty<>(isRead ? "Read" : "Unread");
        });

        chaptersTable.getColumns().clear();
        chaptersTable.getColumns().add(chapterColumn);
        chaptersTable.getColumns().add(titleColumn);
        chaptersTable.getColumns().add(volumeColumn);
        chaptersTable.getColumns().add(dateColumn);
        chaptersTable.getColumns().add(statusColumn);

        chaptersTabPane = new TabPane();

        BorderPane listContent = new BorderPane(chaptersTable, null, null, chapterPagination, null);
        ScrollPane listScrollPane = new ScrollPane(listContent);
        listScrollPane.setFitToWidth(true);
        listScrollPane.setFitToHeight(true);
        listScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        listScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        listScrollPane.setStyle("-fx-background-color: transparent;");

        Tab listTab = new Tab("List", listScrollPane);
        listTab.setClosable(false);

        ScrollPane gridScrollPane = new ScrollPane(createChapterGrid());
        gridScrollPane.setFitToWidth(true);
        gridScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        gridScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Tab gridTab = new Tab("Grid", gridScrollPane);
        gridTab.setClosable(false);

        Tab volumeTab = new Tab("Volumes", createVolumeView());
        volumeTab.setClosable(false);

        chaptersTabPane.getTabs().addAll(listTab, gridTab, volumeTab);
        chaptersTabPane.getStyleClass().add("floating-tab-pane");

        chaptersTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab == listTab) {
                Platform.runLater(() -> {
                    System.out.println("DEBUG: Switching to List tab - COMPLETELY RECREATING list content");

                    currentChapterPage = 0;
                    BorderPane newListContent = new BorderPane(chaptersTable, null, null, chapterPagination, null);
                    ScrollPane newListScrollPane = new ScrollPane(newListContent);
                    newListScrollPane.setFitToWidth(true);
                    newListScrollPane.setFitToHeight(true);
                    newListScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                    newListScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    newListScrollPane.setStyle("-fx-background-color: transparent;");

                    listTab.setContent(newListScrollPane);

                    chaptersTable.setItems(FXCollections.observableArrayList());
                    chaptersTable.refresh();

                    Platform.runLater(() -> {
                        updatingPagination = true;
                        try {
                            int pageCount = Math.max(1,
                                    (int) Math.ceil((double) sortedChapters.size() / CHAPTERS_PER_PAGE));
                            chapterPagination.setPageCount(pageCount);
                            chapterPagination.setCurrentPageIndex(0);
                            System.out.println("DEBUG: Tab reset - Set pagination to page 0 of " + pageCount);
                        } finally {
                            updatingPagination = false;
                        }

                        updateChapterTable();

                        System.out.println("DEBUG: COMPLETE RECREATION of List tab completed - page 0, total chapters: "
                                + sortedChapters.size() +
                                ", table items after reset: " + chaptersTable.getItems().size());
                    });
                });
            } else if (newTab != null && newTab == gridTab) {
                System.out.println("DEBUG: Switching to Grid tab - resetting grid scroll state");
                Platform.runLater(() -> {
                    ScrollPane currentGridScrollPane = (ScrollPane) gridTab.getContent();
                    currentGridScrollPane.setVvalue(0);
                    currentGridScrollPane.setHvalue(0);
                    System.out.println("DEBUG: Grid scroll position reset to top");
                });
            } else if (newTab != null && newTab.getText().equals("Volumes")) {
                System.out.println("DEBUG: Switching to Volumes tab - ensuring clean state");
                Platform.runLater(() -> {
                    if (newTab.getContent() instanceof ScrollPane scrollPane) {
                        scrollPane.setVvalue(0);
                        scrollPane.setHvalue(0);
                    }
                });
            }
        });

        VBox topInfoLayout = new VBox(10);

        VBox titleAuthorBox = new VBox(5);
        titleAuthorBox.getChildren().addAll(titleLabel, new HBox(10, authorLabel, artistLabel));

        HBox statusBox = new HBox(10, statusLabel, languageLabel, lastUpdatedLabel);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        topInfoLayout.getChildren().addAll(titleAuthorBox, statusBox, genresPane);

        HBox infoGridLayout = new HBox(20);
        infoGridLayout.setPadding(new Insets(10, 0, 10, 0));

        VBox detailsBox = new VBox(15,
                descriptionArea,
                buttonLayout,
                progressBox);
        detailsBox.setPrefWidth(600);

        VBox statsBox = new VBox(10,
                new Label("Statistics"),
                statsGrid);

        infoGridLayout.getChildren().addAll(detailsBox, statsBox);

        Label chaptersHeader = new Label("Chapters");
        chaptersHeader.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        VBox chaptersBox = new VBox(10,
                chaptersHeader,
                searchBox,
                chaptersTabPane);
        chaptersBox.setPadding(new Insets(20, 0, 0, 0));

        VBox leftPanel = new VBox(20, coverImageView);
        leftPanel.setAlignment(Pos.TOP_CENTER);

        VBox rightPanel = new VBox(20,
                topInfoLayout,
                infoGridLayout,
                chaptersBox);

        HBox mainLayout = new HBox(30, leftPanel, rightPanel);
        mainLayout.setPadding(new Insets(20));

        Button backButton = new Button("< Back");
        backButton.setStyle(
                "-fx-font-size: 14px; " +
                        "-fx-background-color: #6c757d; " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 10 20; " +
                        "-fx-background-radius: 5;");
        backButton.setOnAction(e -> {
        });

        VBox completeLayout = new VBox(15);
        completeLayout.setPadding(new Insets(10, 20, 20, 20));
        completeLayout.getChildren().addAll(backButton, mainLayout);

        setCenter(new ScrollPane(completeLayout));

        sortChapters("Newest First");

        themeManager.addThemeChangeListener(this);
    }

    private GridPane createChapterGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(15));
        return grid;
    }

    private ScrollPane createVolumeView() {
        Accordion accordion = new Accordion();
        accordion.setPadding(new Insets(15));
        ScrollPane scrollPane = new ScrollPane(accordion);
        scrollPane.setFitToWidth(true);
        return scrollPane;
    }

    private void updateChapterGrid(GridPane grid, List<Chapter> chapters) {
        grid.getChildren().clear();
        int col = 0;
        int row = 0;
        int maxCols = 5;

        for (Chapter chapter : chapters) {
            VBox chapterBox = new VBox(5);
            chapterBox.setPadding(new Insets(10));
            chapterBox.setAlignment(Pos.CENTER);

            String cardBg = themeManager.isDarkTheme() ? "#3c3c3c" : "white";
            String borderColor = themeManager.isDarkTheme() ? "#555555" : "#dee2e6";
            chapterBox.setStyle(
                    "-fx-background-color: " + cardBg + "; " +
                            "-fx-border-color: " + borderColor + "; " +
                            "-fx-border-radius: 8; " +
                            "-fx-background-radius: 8; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");

            Label chapterNumLabel = new Label(String.format("Chapter %.1f", chapter.getNumber()));
            chapterNumLabel.setStyle("-fx-font-weight: bold;");

            Label volumeLabel = new Label(chapter.getVolume() != null ? "Vol. " + chapter.getVolume() : "");
            String volumeTextColor = themeManager.isDarkTheme() ? "#a0a0a0" : "#666";
            volumeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + volumeTextColor + ";");

            Circle readStatusIndicator = new Circle(5);
            readStatusIndicator.setFill(Color.LIGHTGRAY);

            boolean isRead = false;
            if (currentManga != null && libraryService.isInLibrary(currentManga.getId())) {
                try {
                    Optional<Manga> libraryManga = libraryService.getLibraryManga(currentManga.getId());
                    if (libraryManga.isPresent()) {
                        isRead = libraryService.isChapterRead(currentManga.getId(), chapter.getId());
                    }
                } catch (Exception e) {
                    System.err.println("Error checking chapter read status: " + e.getMessage());
                }
            }

            if (isRead) {
                readStatusIndicator.setFill(Color.GREEN);
            }

            HBox statusBox = new HBox(5, readStatusIndicator, chapterNumLabel);
            statusBox.setAlignment(Pos.CENTER);

            Button chapterReadButton = new Button("Read");
            chapterReadButton.getStyleClass().add("primary");

            chapterReadButton.setOnAction(e -> {
                if (onChapterSelectedCallback != null) {
                    onChapterSelectedCallback.accept(chapter);
                }
            });

            chapterBox.getChildren().addAll(statusBox, volumeLabel, chapterReadButton);
            grid.add(chapterBox, col, row);

            col++;
            if (col >= maxCols) {
                col = 0;
                row++;
            }
        }
    }

    private void updateVolumeChapterGridLayout(GridPane grid, List<Chapter> chapters) {
        grid.getChildren().clear();
        int col = 0;
        int row = 0;
        int maxCols = 5;

        for (Chapter chapter : chapters) {
            Button chapterBtn = new Button("Chapter " + chapter.getNumber());
            chapterBtn.setMaxWidth(Double.MAX_VALUE);

            chapterBtn.setOnAction(e -> {
                if (onChapterSelectedCallback != null) {
                    onChapterSelectedCallback.accept(chapter);
                }
            });

            Tooltip tooltip = new Tooltip(chapter.getTitle());
            Tooltip.install(chapterBtn, tooltip);

            grid.add(chapterBtn, col, row);
            GridPane.setHgrow(chapterBtn, Priority.ALWAYS);
            GridPane.setFillWidth(chapterBtn, true);

            col++;
            if (col >= maxCols) {
                col = 0;
                row++;
            }
        }
    }

    private void updateVolumeView(Accordion accordion, List<Chapter> chapters) {
        accordion.getPanes().clear();

        Map<String, List<Chapter>> volumeChapters = chapters.stream()
                .collect(Collectors.groupingBy(
                        chapter -> chapter.getVolume() != null ? chapter.getVolume() : "No Volume"));

        volumeChapters.forEach((volume, volumeChapterList) -> {
            String title = volume.equals("No Volume") ? "Chapters (No Volume)" : "Volume " + volume;

            GridPane chapterGrid = new GridPane();
            chapterGrid.setHgap(10);
            chapterGrid.setVgap(10);
            chapterGrid.setPadding(new Insets(15));

            updateVolumeChapterGridLayout(chapterGrid, volumeChapterList);

            TitledPane volumePane = new TitledPane(title + " (" + volumeChapterList.size() + " chapters)",
                    new ScrollPane(chapterGrid));

            accordion.getPanes().add(volumePane);
        });
    }

    private void updateChapterTable() {
        Platform.runLater(() -> {
            if (updatingPagination) {
                System.out.println("DEBUG: Skipping updateChapterTable - already updating pagination");
                return;
            }

            System.out.println("DEBUG: updateChapterTable called - currentChapterPage: " + currentChapterPage +
                    ", sortedChapters.size(): " + sortedChapters.size() +
                    ", current table items: " + chaptersTable.getItems().size());

            int fromIndex = currentChapterPage * CHAPTERS_PER_PAGE;

            if (fromIndex >= sortedChapters.size() && !sortedChapters.isEmpty()) {
                System.out.println("DEBUG: Page out of bounds, resetting to page 0");
                currentChapterPage = 0;
                fromIndex = 0;
            }

            updatingPagination = true;
            try {
                int pageCount = (int) Math.ceil((double) sortedChapters.size() / CHAPTERS_PER_PAGE);
                if (pageCount == 0) {
                    pageCount = 1;
                }
                chapterPagination.setPageCount(pageCount);

                if (currentChapterPage >= pageCount) {
                    currentChapterPage = pageCount - 1;
                }
                chapterPagination.setCurrentPageIndex(currentChapterPage);

                System.out.println(
                        "DEBUG: Set pagination - pageCount: " + pageCount + ", currentPage: " + currentChapterPage);
            } finally {
                updatingPagination = false;
            }

            chaptersTable.setItems(FXCollections.observableArrayList());
            chaptersTable.refresh();

            final int finalFromIndex = fromIndex;

            Platform.runLater(() -> {
                chaptersTable.refresh();
                chaptersTable.scrollTo(0);

                int toIndex = Math.min(finalFromIndex + CHAPTERS_PER_PAGE, sortedChapters.size());

                if (finalFromIndex < sortedChapters.size()) {
                    List<Chapter> currentPageChapters = sortedChapters.subList(finalFromIndex, toIndex);

                    System.out.println("DEBUG: Adding " + currentPageChapters.size() + " chapters to table (page "
                            + currentChapterPage + ", fromIndex: " + finalFromIndex + ", toIndex: " + toIndex + ")");

                    ObservableList<Chapter> newItems = FXCollections.observableArrayList(currentPageChapters);
                    chaptersTable.setItems(newItems);

                    Platform.runLater(() -> {
                        chaptersTable.refresh();
                        chaptersTable.scrollTo(0);
                        System.out.println("DEBUG: Final table state - items: " + chaptersTable.getItems().size());
                    });
                } else {
                    System.out.println("DEBUG: No chapters to display for current page");
                }
            });
        });
    }

    private void filterChapters(String searchText) {
        String lowerCaseSearch = searchText.toLowerCase();

        Predicate<Chapter> textPredicate = chapter -> {
            if (searchText.isEmpty()) {
                return true;
            }

            return chapter.getTitle().toLowerCase().contains(lowerCaseSearch) ||
                    String.valueOf(chapter.getNumber()).contains(lowerCaseSearch);
        };

        String volumeFilter = filterComboBox.getValue();
        Predicate<Chapter> volumePredicate = getVolumeFilterPredicate(volumeFilter);

        filteredChapters.setPredicate(volumePredicate.and(textPredicate));

        currentChapterPage = 0;
        updateChapterTable();
    }

    private void filterChaptersByVolume(String volumeFilter) {
        Predicate<Chapter> volumePredicate = getVolumeFilterPredicate(volumeFilter);
        String searchText = chapterSearchField.getText();
        String lowerCaseSearch = searchText.toLowerCase();

        Predicate<Chapter> textPredicate = chapter -> {
            if (searchText.isEmpty()) {
                return true;
            }

            return chapter.getTitle().toLowerCase().contains(lowerCaseSearch) ||
                    String.valueOf(chapter.getNumber()).contains(lowerCaseSearch);
        };

        filteredChapters.setPredicate(volumePredicate.and(textPredicate));

        currentChapterPage = 0;
        updateChapterTable();
    }

    private Predicate<Chapter> getVolumeFilterPredicate(String volumeFilter) {
        if (volumeFilter == null || volumeFilter.isEmpty() || volumeFilter.equals("All")) {
            return chapter -> true;
        } else if (volumeFilter.equals("No Volume")) {
            return chapter -> chapter.getVolume() == null || chapter.getVolume().isEmpty();
        } else {
            String volumeNumber = volumeFilter.replace("Volume ", "").trim();
            return chapter -> chapter.getVolume() != null && chapter.getVolume().equals(volumeNumber);
        }
    }

    private void sortChapters(String sortOption) {
        switch (sortOption) {
            case "Newest First":
                sortedChapters.setComparator(Comparator.comparing(Chapter::getNumber).reversed());
                break;
            case "Oldest First":
                sortedChapters.setComparator(Comparator.comparing(Chapter::getNumber));
                break;
            case "By Volume":
                sortedChapters.setComparator((c1, c2) -> {
                    if (c1.getVolume() == null && c2.getVolume() == null) {
                        return Double.compare(c1.getNumber(), c2.getNumber());
                    } else if (c1.getVolume() == null) {
                        return 1;
                    } else if (c2.getVolume() == null) {
                        return -1;
                    } else {
                        int volumeComparison = c1.getVolume().compareTo(c2.getVolume());
                        if (volumeComparison == 0) {
                            return Double.compare(c1.getNumber(), c2.getNumber());
                        }
                        return volumeComparison;
                    }
                });
                break;
            default:
                break;
        }

        currentChapterPage = 0;

        updateChapterTable();

        Platform.runLater(() -> {
            Tab gridTabForSort = chaptersTabPane.getTabs().get(1);
            ScrollPane gridScrollPane = (ScrollPane) gridTabForSort.getContent();
            if (gridScrollPane.getContent() instanceof GridPane gridPane) {
                updateChapterGrid(gridPane, sortedChapters);
            } else {
                GridPane chapterGrid = createChapterGrid();
                gridScrollPane.setContent(chapterGrid);
                updateChapterGrid(chapterGrid, sortedChapters);
            }

            Tab volumeTabForSort = chaptersTabPane.getTabs().get(2);
            ScrollPane volumeScrollPane = (ScrollPane) volumeTabForSort.getContent();
            if (volumeScrollPane.getContent() instanceof Accordion accordion) {
                updateVolumeView(accordion, sortedChapters);
            } else {
                Accordion volumeAccordion = new Accordion();
                volumeAccordion.setPadding(new Insets(15));
                volumeScrollPane.setContent(volumeAccordion);
                updateVolumeView(volumeAccordion, sortedChapters);
            }
        });
    }

    private void loadChapters(String mangaId) {
        chapters.clear();

        Label emptyLabel = new Label("");
        chaptersTable.setPlaceholder(emptyLabel);

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(50, 50);

        Label loadingLabel = new Label("Loading chapters...");
        loadingLabel.setStyle("-fx-font-size: 16px;");

        VBox loadingBox = new VBox(15, progressIndicator, loadingLabel);
        loadingBox.setAlignment(Pos.CENTER);

        chaptersTable.setPlaceholder(loadingBox);

        Tab gridTabForLoading = chaptersTabPane.getTabs().get(1);
        Label loadingGridLabel = new Label("Loading chapters...");
        loadingGridLabel.setStyle("-fx-font-size: 16px;");

        ((ScrollPane) gridTabForLoading.getContent()).setContent(new StackPane(loadingGridLabel));

        Tab volumeTabForLoading = chaptersTabPane.getTabs().get(2);
        Label loadingVolumeLabel = new Label("Loading chapters...");
        loadingVolumeLabel.setStyle("-fx-font-size: 16px;");

        ((ScrollPane) volumeTabForLoading.getContent()).setContent(new StackPane(loadingVolumeLabel));

        ThreadPoolManager.getInstance().executeApiTask(() -> {
            List<Chapter> fetchedChapters = mangaService.getChapters(mangaId);
            Platform.runLater(() -> {
                if (fetchedChapters != null && !fetchedChapters.isEmpty()) {
                    chapters.addAll(fetchedChapters);

                    statsLabels.get("Chapter Count").setText(String.valueOf(fetchedChapters.size()));

                    totalChaptersLabel.setText("Total: " + fetchedChapters.size() + " chapters");

                    sortedChapters.setComparator(Comparator.comparing(Chapter::getNumber).reversed());

                    filterComboBox.getItems().clear();
                    filterComboBox.getItems().add("All");
                    filterComboBox.getItems().add("No Volume");

                    fetchedChapters.stream()
                            .map(Chapter::getVolume)
                            .filter(v -> v != null && !v.isEmpty())
                            .distinct()
                            .forEach(volume -> filterComboBox.getItems().add("Volume " + volume));

                    filterComboBox.setValue("All");

                    Tab gridTabForFetch = chaptersTabPane.getTabs().get(1);
                    GridPane chapterGrid = createChapterGrid();
                    ((ScrollPane) gridTabForFetch.getContent()).setContent(chapterGrid);
                    updateChapterGrid(chapterGrid, fetchedChapters);

                    Tab volumeTabForFetch = chaptersTabPane.getTabs().get(2);
                    Accordion volumeAccordion = new Accordion();
                    volumeAccordion.setPadding(new Insets(15));
                    ((ScrollPane) volumeTabForFetch.getContent()).setContent(volumeAccordion);
                    updateVolumeView(volumeAccordion, fetchedChapters);

                    if (currentManga != null && libraryService.isInLibrary(currentManga.getId())) {
                        libraryService.updateTotalChapters(currentManga.getId(), fetchedChapters.size());
                    }
                    int chaptersRead = 0;
                    if (currentManga != null && libraryService.isInLibrary(currentManga.getId())) {
                        Optional<LibraryService.LibraryEntryInfo> entryInfo = libraryService
                                .getLibraryEntryInfo(currentManga.getId());
                        if (entryInfo.isPresent()) {
                            chaptersRead = entryInfo.get().getChaptersRead();
                        }
                    }

                    double progress = chapters.isEmpty() ? 0 : (double) chaptersRead / chapters.size();
                    readProgressBar.setProgress(progress);
                    readProgressLabel.setText(chaptersRead + " / " + chapters.size() + " chapters read");

                    updateChapterTable();

                    updateReadingButtonText();

                } else {
                    totalChaptersLabel.setText("Total: 0 chapters");
                    statsLabels.get("Chapter Count").setText("0");

                    readProgressBar.setProgress(0);
                    readProgressLabel.setText("0 / 0 chapters read");

                    Label noChaptersTableLabel = new Label("No chapters available");
                    noChaptersTableLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #666;");
                    chaptersTable.setPlaceholder(noChaptersTableLabel);

                    Tab gridTabForEmpty = chaptersTabPane.getTabs().get(1);
                    Label noChaptersGridLabel = new Label("No chapters available");
                    noChaptersGridLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #666;");
                    ((ScrollPane) gridTabForEmpty.getContent()).setContent(new StackPane(noChaptersGridLabel));

                    Tab volumeTabForEmpty = chaptersTabPane.getTabs().get(2);
                    Label noChaptersVolumeLabel = new Label("No chapters available");
                    noChaptersVolumeLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #666;");
                    ((ScrollPane) volumeTabForEmpty.getContent()).setContent(new StackPane(noChaptersVolumeLabel));

                    updateReadingButtonText();
                }
            });
        });
    }

    private void loadCoverImage(Manga manga) {
        if (manga.getCoverUrl() == null || manga.getCoverUrl().isEmpty()) {
            return;
        }

        try {
            ImageCache imageCache = ImageCache.getInstance();
            Image image = imageCache.getImage(manga.getCoverUrl());

            image.errorProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    System.err.println("Error loading cover image: " + manga.getCoverUrl());
                    Image placeholderImage = imageCache.getPlaceholderImage("No+Cover");
                    Platform.runLater(() -> coverImageView.setImage(placeholderImage));
                }
            });

            image.exceptionProperty().addListener((obs, oldEx, newEx) -> {
                if (newEx != null) {
                    System.err.println("Exception loading cover image: " + newEx.getMessage());
                    Image placeholderImage = imageCache.getPlaceholderImage("Error");
                    Platform.runLater(() -> coverImageView.setImage(placeholderImage));
                }
            });

            coverImageView.setImage(image);
        } catch (Exception e) {
            System.err.println("Error in loadCoverImage: " + e.getMessage());
            ImageCache imageCache = ImageCache.getInstance();
            Image placeholderImage = imageCache.getPlaceholderImage("Error");
            coverImageView.setImage(placeholderImage);
        }
    }

    public void displayManga(Manga manga) {
        this.currentManga = manga;
        titleLabel.setText(manga.getTitle());
        authorLabel.setText("Author: " + (manga.getAuthor() != null ? manga.getAuthor() : "Unknown"));
        artistLabel.setText("Artist: " + (manga.getArtist() != null ? manga.getArtist() : "Unknown"));
        statusLabel.setText(manga.getStatus() != null ? manga.getStatus() : "Unknown");
        languageLabel.setText(manga.getLanguage() != null ? manga.getLanguage() : "Unknown");

        if (manga.getLastUpdated() != null) {
            lastUpdatedLabel
                    .setText("Updated: " + manga.getLastUpdated().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        }

        descriptionArea.setText(manga.getDescription());

        loadCoverImage(manga);

        genresPane.getChildren().clear();
        if (manga.getGenres() != null) {
            for (String genre : manga.getGenres()) {
                Label genreLabel = new Label(genre);
                String genreBg = themeManager.isDarkTheme() ? "#4a4a4a" : "#e7f3ff";
                String genreText = themeManager.isDarkTheme() ? "#87ceeb" : "#0066cc";
                genreLabel.setStyle(
                        "-fx-background-color: " + genreBg + "; " +
                                "-fx-text-fill: " + genreText + "; " +
                                "-fx-padding: 5 10; " +
                                "-fx-background-radius: 15; " +
                                "-fx-font-size: 12px;");
                genresPane.getChildren().add(genreLabel);
            }
        }

        updateAddToLibraryButton(manga);

        statsLabels.get("Rating").setText("4.8/5");
        statsLabels.get("Users").setText("12,345");
        statsLabels.get("Follows").setText("5,678");
        statsLabels.get("Popularity").setText("#123");
        statsLabels.get("Release Year").setText("2020");
        statsLabels.get("Chapter Count").setText("Loading...");

        loadChapters(manga.getId());

        updateReadingButtonText();

        readButton.setOnAction(e -> {
            if (!chapters.isEmpty()) {
                boolean hasReadChapters = false;
                if (libraryService.isInLibrary(manga.getId())) {
                    hasReadChapters = chapters.stream()
                            .anyMatch(ch -> libraryService.isChapterRead(manga.getId(), ch.getId()));
                }

                readButton.setText(hasReadChapters ? "Continue Reading" : "Start Reading");

                Chapter chapterToRead;
                if (hasReadChapters) {
                    chapterToRead = chapters.stream()
                            .filter(ch -> !libraryService.isChapterRead(manga.getId(), ch.getId()))
                            .min(Comparator.comparing(Chapter::getNumber))
                            .orElse(chapters.stream()
                                    .max(Comparator.comparing(Chapter::getNumber))
                                    .orElse(chapters.get(0)));
                } else {
                    chapterToRead = chapters.stream()
                            .min(Comparator.comparing(Chapter::getNumber))
                            .orElse(chapters.get(0));
                }

                if (onChapterSelectedCallback != null) {
                    onChapterSelectedCallback.accept(chapterToRead);
                }
            }
        });
    }

    private void updateAddToLibraryButton(Manga manga) {
        if (libraryService.isInLibrary(manga.getId())) {
            addToLibraryButton.setText("In Library");
            addToLibraryButton.setDisable(true);
            addToLibraryButton.setStyle(
                    "-fx-font-size: 14px; " +
                            "-fx-background-color: #28a745; " +
                            "-fx-text-fill: white; " +
                            "-fx-padding: 10 20; " +
                            "-fx-background-radius: 5; " +
                            "-fx-opacity: 0.8;");

            removeFromLibraryButton.setVisible(true);
            removeFromLibraryButton.setDisable(false);
            removeFromLibraryButton.setStyle(
                    "-fx-font-size: 14px; " +
                            "-fx-background-color: #dc3545; " +
                            "-fx-text-fill: white; " +
                            "-fx-padding: 10 20; " +
                            "-fx-background-radius: 5;");

            removeFromLibraryButton.setOnAction(e -> {
                boolean success = libraryService.removeFromLibrary(manga.getId());
                if (success) {
                    updateAddToLibraryButton(manga);

                    readProgressBar.setProgress(0);
                    readProgressLabel.setText("0 / 0 chapters read");

                    updateReadingButtonText();
                }
            });
        } else {
            addToLibraryButton.setText("Add to Library");
            addToLibraryButton.setDisable(false);
            addToLibraryButton.setStyle(
                    "-fx-font-size: 14px; " +
                            "-fx-background-color: #007bff; " +
                            "-fx-text-fill: white; " +
                            "-fx-padding: 10 20; " +
                            "-fx-background-radius: 5;");

            removeFromLibraryButton.setVisible(false);
        }
    }

    @SuppressWarnings("unused")
    private void updateVolumeFilter() {
        filterComboBox.getItems().clear();
        filterComboBox.getItems().add("All");
        filterComboBox.setValue("All");
    }

    @SuppressWarnings("unused")
    private void clear() {
        titleLabel.setText("");
        authorLabel.setText("");
        artistLabel.setText("");
        statusLabel.setText("");
        languageLabel.setText("");
        lastUpdatedLabel.setText("");
        descriptionArea.setText("");
        coverImageView.setImage(null);
        genresPane.getChildren().clear();
        chapters.clear();

        for (Label label : statsLabels.values()) {
            label.setText("--");
        }

        readProgressBar.setProgress(0);
        readProgressLabel.setText("0 / 0 chapters read");

        Label emptyLabel = new Label("");
        chaptersTable.setPlaceholder(emptyLabel);
    }

    public Button getReadButton() {
        return readButton;
    }

    public Button getAddToLibraryButton() {
        return addToLibraryButton;
    }

    public Button getRefreshButton() {
        return refreshButton;
    }

    private void applyTheme() {
        setStyle("-fx-background-color: " + themeManager.getBackgroundColor() + ";");

        String statsGridBg = themeManager.isDarkTheme() ? "#3c3c3c" : "#f0f0f0";
        String borderColor = themeManager.getBorderColor();
        statsGrid.setStyle(
                "-fx-background-color: " + statsGridBg + "; " +
                        "-fx-border-color: " + borderColor + "; " +
                        "-fx-border-radius: 5; " +
                        "-fx-background-radius: 5;");

        String descBg = themeManager.isDarkTheme() ? "#3c3c3c" : "#f8f9fa";
        descriptionArea.setStyle(
                "-fx-control-inner-background: " + descBg + "; " +
                        "-fx-background-color: " + descBg + "; " +
                        "-fx-border-color: " + borderColor + "; " +
                        "-fx-border-radius: 5; " +
                        "-fx-background-radius: 5; " +
                        "-fx-focus-color: transparent; " +
                        "-fx-faint-focus-color: transparent;");

        readButton.getStyleClass().removeAll("primary", "success", "warning");
        readButton.getStyleClass().add("primary");

        addToLibraryButton.getStyleClass().removeAll("primary", "success", "warning");

        refreshButton.getStyleClass().removeAll("primary", "success", "warning");

        String labelBg = themeManager.isDarkTheme() ? "#4a4a4a" : "#e7f3ff";
        String labelText = themeManager.isDarkTheme() ? "#87ceeb" : "#0066cc";
        statusLabel.setStyle(
                "-fx-font-size: 14px; -fx-padding: 5 10; -fx-background-radius: 15; " +
                        "-fx-background-color: " + labelBg + "; -fx-text-fill: " + labelText + ";");
        languageLabel.setStyle(
                "-fx-font-size: 14px; -fx-padding: 5 10; -fx-background-radius: 15; " +
                        "-fx-background-color: " + labelBg + "; -fx-text-fill: " + labelText + ";");

        lastUpdatedLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + themeManager.getTextColor() + ";");

        String progressColor = themeManager.isDarkTheme() ? "#28a745" : "#28a745";
        readProgressBar.setStyle("-fx-accent: " + progressColor + ";");

        updateGenreLabels();
    }

    private void updateGenreLabels() {
        String genreBg = themeManager.isDarkTheme() ? "#4a4a4a" : "#e7f3ff";
        String genreText = themeManager.isDarkTheme() ? "#87ceeb" : "#0066cc";

        genresPane.getChildren().forEach(node -> {
            if (node instanceof Label genreLabel) {
                genreLabel.setStyle(
                        "-fx-background-color: " + genreBg + "; " +
                                "-fx-text-fill: " + genreText + "; " +
                                "-fx-padding: 5 10; " +
                                "-fx-background-radius: 15; " +
                                "-fx-font-size: 12px;");
            }
        });
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme newTheme) {
        Platform.runLater(() -> {
            applyTheme();

            Tab gridTab = chaptersTabPane.getTabs().get(1);
            ScrollPane gridScrollPane = (ScrollPane) gridTab.getContent();
            if (gridScrollPane.getContent() instanceof GridPane gridPane) {
                updateChapterGrid(gridPane, sortedChapters);
            }
        });
    }

    public void refreshReadingProgress() {
        if (currentManga != null && libraryService.isInLibrary(currentManga.getId())) {
            Optional<LibraryService.LibraryEntryInfo> entryInfo = libraryService
                    .getLibraryEntryInfo(currentManga.getId());
            if (entryInfo.isPresent()) {
                LibraryService.LibraryEntryInfo info = entryInfo.get();
                int chaptersRead = info.getChaptersRead();
                int totalChapters = Math.max(info.getTotalChapters(), chapters.size());

                double progress = totalChapters > 0 ? (double) chaptersRead / totalChapters : 0.0;
                readProgressBar.setProgress(progress);
                readProgressLabel.setText(chaptersRead + " / " + totalChapters + " chapters read");

                Platform.runLater(() -> {
                    if (chaptersTable != null) {
                        chaptersTable.refresh();
                    }

                    Tab gridTab = chaptersTabPane.getTabs().get(1);
                    ScrollPane gridScrollPane = (ScrollPane) gridTab.getContent();
                    if (gridScrollPane.getContent() instanceof GridPane gridPane) {
                        updateChapterGrid(gridPane, chapters);
                    }

                    Tab volumeTab = chaptersTabPane.getTabs().get(2);
                    ScrollPane volumeScrollPane = (ScrollPane) volumeTab.getContent();
                    if (volumeScrollPane.getContent() instanceof Accordion accordion) {
                        updateVolumeView(accordion, chapters);
                    }
                });
            }
        }
    }

    private void updateReadingButtonText() {
        if (currentManga != null && !chapters.isEmpty() && libraryService.isInLibrary(currentManga.getId())) {
            boolean hasReadChapters = chapters.stream()
                    .anyMatch(ch -> libraryService.isChapterRead(currentManga.getId(), ch.getId()));
            readButton.setText(hasReadChapters ? "Continue Reading" : "Start Reading");
        } else {
            readButton.setText("Start Reading");
        }
    }
}
