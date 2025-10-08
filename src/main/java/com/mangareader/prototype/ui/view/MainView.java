package com.mangareader.prototype.ui.view;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.mangareader.prototype.model.Chapter;
import com.mangareader.prototype.model.Manga;
import com.mangareader.prototype.service.LibraryService;
import com.mangareader.prototype.service.MangaServiceImpl;
import com.mangareader.prototype.ui.component.Sidebar;
import com.mangareader.prototype.ui.component.ThemeManager;
import com.mangareader.prototype.util.ThreadPoolManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Main view for the manga reader application.
 * Contains the sidebar, content area, and top bar.
 */
public class MainView extends BorderPane implements ThemeManager.ThemeChangeListener {
    private final Sidebar sidebar;
    private final StackPane contentArea;
    private final ToolBar topBar;
    private final ThemeManager themeManager;
    private LibraryView currentLibraryView;
    private AddSeriesView currentAddSeriesView;
    private boolean programmaticSelection = false;

    private enum NavigationSource {
        ADD_SERIES, LIBRARY
    }

    private NavigationSource currentNavigationSource = NavigationSource.ADD_SERIES;

    public MainView() {
        themeManager = ThemeManager.getInstance();

        sidebar = new Sidebar();
        contentArea = new StackPane();
        topBar = createTopBar();

        setLeft(sidebar);
        setCenter(contentArea);
        setTop(topBar);

        String backgroundColor = themeManager.getBackgroundColor();
        setBackground(new Background(new BackgroundFill(Color.web(backgroundColor), null, null)));
        setPadding(new Insets(0));

        updateToolbarTheme();

        showLibraryView();

        sidebar.getNavigationTree().getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !programmaticSelection) {
                switch (newVal.getValue()) {
                    case "Library" -> showLibraryView();
                    case "Settings" -> showSettingsView();
                    case "Add Series" -> showAddSeriesView();
                }
            }
        });

        themeManager.addThemeChangeListener(this);
    }

    private ToolBar createTopBar() {
        ToolBar toolBar = new ToolBar();
        return toolBar;
    }

    private void showLibraryView() {
        updateSidebarSelection("Library");

        currentNavigationSource = NavigationSource.LIBRARY;

        contentArea.getChildren().clear();
        currentLibraryView = new LibraryView(this::showMangaDetailViewFromLibrary);
        currentLibraryView.setOnAddSeriesCallback(this::showAddSeriesView);
        contentArea.getChildren().add(currentLibraryView);
    }

    /**
     * Show manga detail view when navigating from library
     */
    private void showMangaDetailViewFromLibrary(Manga manga) {
        currentNavigationSource = NavigationSource.LIBRARY;
        showMangaDetailView(manga);
    }

    private void showSettingsView() {
        updateSidebarSelection("Settings");

        contentArea.getChildren().clear();
        contentArea.getChildren().add(new SettingsView());
    }

    private void showAddSeriesView() {
        updateSidebarSelection("Add Series");

        currentNavigationSource = NavigationSource.ADD_SERIES;

        contentArea.getChildren().clear();
        
        // Reuse existing AddSeriesView to preserve source selection
        if (currentAddSeriesView == null) {
            currentAddSeriesView = new AddSeriesView(this::showMangaDetailView);
        }
        
        contentArea.getChildren().add(currentAddSeriesView);
    }

    private void showMangaDetailView(Manga manga) {
        contentArea.getChildren().clear();

        Runnable backCallback = (currentNavigationSource == NavigationSource.LIBRARY)
                ? this::showLibraryView
                : this::showAddSeriesView;

        MangaDetailView mangaDetailView = new MangaDetailView(
                chapter -> showMangaReaderView(chapter, manga),
                backCallback);

        mangaDetailView.getAddToLibraryButton().setOnAction(e -> {
            LibraryService libraryService = LibraryService.getInstance();

            if (libraryService.isInLibrary(manga.getId())) {
                mangaDetailView.getAddToLibraryButton().setText("Already in Library");
                mangaDetailView.getAddToLibraryButton().setStyle(
                        "-fx-font-size: 14px; " +
                                "-fx-background-color: #ffc107; " +
                                "-fx-text-fill: black; " +
                                "-fx-padding: 10 20; " +
                                "-fx-background-radius: 5;");
                return;
            }

            boolean success = libraryService.addToLibrary(manga);

            if (success) {
                if (currentLibraryView != null) {
                    currentLibraryView.refreshLibrary();
                }

                mangaDetailView.getAddToLibraryButton().setText("Added to Library!");
                mangaDetailView.getAddToLibraryButton().setDisable(true);
                mangaDetailView.getAddToLibraryButton().setStyle(
                        "-fx-font-size: 14px; " +
                                "-fx-background-color: #28a745; " +
                                "-fx-text-fill: white; " +
                                "-fx-padding: 10 20; " +
                                "-fx-background-radius: 5;");

                ThreadPoolManager.getInstance().schedule(() -> {
                    Platform.runLater(() -> {
                        mangaDetailView.getAddToLibraryButton().setText("In Library");
                        mangaDetailView.getAddToLibraryButton().setDisable(true);
                        mangaDetailView.getAddToLibraryButton().setStyle(
                                "-fx-font-size: 14px; " +
                                        "-fx-background-color: #28a745; " +
                                        "-fx-text-fill: white; " +
                                        "-fx-padding: 10 20; " +
                                        "-fx-background-radius: 5; " +
                                        "-fx-opacity: 0.8;");
                    });
                }, 2, TimeUnit.SECONDS);
            } else {
                mangaDetailView.getAddToLibraryButton().setText("Failed to Add");
                mangaDetailView.getAddToLibraryButton().setStyle(
                        "-fx-font-size: 14px; " +
                                "-fx-background-color: #dc3545; " +
                                "-fx-text-fill: white; " +
                                "-fx-padding: 10 20; " +
                                "-fx-background-radius: 5;");
            }
        });

        mangaDetailView.displayManga(manga);
        contentArea.getChildren().add(mangaDetailView);
    }

    private void showMangaReaderView(Chapter chapter, Manga manga) {
        contentArea.getChildren().clear();
        MangaReaderView mangaReaderView = new MangaReaderView(() -> {
            showMangaDetailView(manga);
            Platform.runLater(() -> {
                contentArea.getChildren().stream()
                        .filter(node -> node instanceof MangaDetailView)
                        .findFirst()
                        .ifPresent(node -> ((MangaDetailView) node).refreshReadingProgress());
            });
        });

        mangaReaderView.setMangaId(manga.getId());

        MangaServiceImpl mangaService = MangaServiceImpl.getInstance();
        List<Chapter> allChapters = mangaService.getChapters(manga.getId());

        mangaReaderView.setChapterList(allChapters, chapter);

        mangaReaderView.loadChapter(chapter);
        contentArea.getChildren().add(mangaReaderView);
    }

    public Sidebar getSidebar() {
        return sidebar;
    }

    public StackPane getContentArea() {
        return contentArea;
    }

    public ToolBar getTopBar() {
        return topBar;
    }

    private void updateSidebarSelection(String itemName) {
        programmaticSelection = true;

        TreeView<String> navigationTree = sidebar.getNavigationTree();
        TreeItem<String> root = navigationTree.getRoot();

        for (TreeItem<String> item : root.getChildren()) {
            if (item.getValue().equals(itemName)) {
                navigationTree.getSelectionModel().select(item);
                break;
            }
        }

        programmaticSelection = false;
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme newTheme) {
        String backgroundColor = themeManager.getBackgroundColor();
        setBackground(new Background(new BackgroundFill(Color.web(backgroundColor), null, null)));

        if (topBar != null) {
            updateToolbarTheme();
        }
    }

    private void updateToolbarTheme() {
        String backgroundColor = themeManager.getSecondaryBackgroundColor();
        String borderColor = themeManager.getBorderColor();

        topBar.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: 0 0 1 0;",
                backgroundColor, borderColor));
    }
}