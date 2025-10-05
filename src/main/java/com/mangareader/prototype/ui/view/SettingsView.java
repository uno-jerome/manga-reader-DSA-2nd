package com.mangareader.prototype.ui.view;

import com.mangareader.prototype.ui.component.ThemeManager;
import com.mangareader.prototype.util.ImageCache;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class SettingsView extends StackPane implements ThemeManager.ThemeChangeListener {
    private final ThemeManager themeManager;
    private Button currentThemeButton;

    public SettingsView() {
        themeManager = ThemeManager.getInstance();
        initializeUI();
        themeManager.addThemeChangeListener(this);
    }

    private void initializeUI() {
        VBox mainContainer = new VBox(20);
        mainContainer.setPadding(new Insets(30));
        mainContainer.setAlignment(Pos.TOP_LEFT);

        Label titleLabel = new Label("Settings");
        titleLabel.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");

        VBox themeSection = createThemeSection();

        Separator separator1 = new Separator();
        separator1.setPrefWidth(400);

        VBox cacheSection = createCacheSection();

        Separator separator2 = new Separator();
        separator2.setPrefWidth(400);

        mainContainer.getChildren().addAll(
                titleLabel,
                themeSection,
                separator1,
                cacheSection,
                separator2);

        getChildren().add(mainContainer);
    }

    private VBox createThemeSection() {
        VBox themeSection = new VBox(15);

        Label themeSectionTitle = new Label("Appearance");
        themeSectionTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        HBox themeRow = new HBox(15);
        themeRow.setAlignment(Pos.CENTER_LEFT);

        Label themeLabel = new Label("Theme:");
        themeLabel.setStyle("-fx-font-size: 14px;");
        themeLabel.setPrefWidth(100);

        Button themeButton = new Button();
        this.currentThemeButton = themeButton;
        updateThemeButtonText(themeButton);
        currentThemeButton.setStyle(
                "-fx-font-size: 14px; " +
                        "-fx-padding: 8 16; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-radius: 6;");

        Button toggleThemeButton = new Button("Switch Theme");
        toggleThemeButton.setStyle(
                "-fx-font-size: 14px; " +
                        "-fx-padding: 8 16; " +
                        "-fx-background-color: #0096c9; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-radius: 6;");

        toggleThemeButton.setOnAction(e -> {
            themeManager.toggleTheme();
            updateThemeButtonText(themeButton);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        themeRow.getChildren().addAll(
                themeLabel,
                themeButton,
                spacer,
                toggleThemeButton);

        Label themeDescription = new Label(
                "Switch between light and dark themes. Your preference will be saved automatically.");
        themeDescription.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        themeDescription.setWrapText(true);
        themeDescription.setPrefWidth(500);

        themeSection.getChildren().addAll(
                themeSectionTitle,
                themeRow,
                themeDescription);

        return themeSection;
    }

    private void updateThemeButtonText(Button button) {
        String currentThemeName = themeManager.getCurrentTheme().getName();
        String displayName = currentThemeName.substring(0, 1).toUpperCase() + currentThemeName.substring(1);
        button.setText(displayName + " Theme");

        if (themeManager.isDarkTheme()) {
            button.setStyle(
                    "-fx-font-size: 14px; " +
                            "-fx-padding: 8 16; " +
                            "-fx-background-color: #3c3c3c; " +
                            "-fx-text-fill: #e0e0e0; " +
                            "-fx-border-color: #555555; " +
                            "-fx-background-radius: 6; " +
                            "-fx-border-radius: 6;");
        } else {
            button.setStyle(
                    "-fx-font-size: 14px; " +
                            "-fx-padding: 8 16; " +
                            "-fx-background-color: #f5f5f5; " +
                            "-fx-text-fill: #333333; " +
                            "-fx-border-color: #cccccc; " +
                            "-fx-background-radius: 6; " +
                            "-fx-border-radius: 6;");
        }
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme newTheme) {
        if (currentThemeButton != null) {
            updateThemeButtonText(currentThemeButton);
        }
    }

    private VBox createCacheSection() {
        VBox cacheSection = new VBox(15);

        Label cacheSectionTitle = new Label("Storage & Cache");
        cacheSectionTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        HBox cacheInfoRow = new HBox(15);
        cacheInfoRow.setAlignment(Pos.CENTER_LEFT);

        Label cacheLabel = new Label("Image Cache:");
        cacheLabel.setStyle("-fx-font-size: 14px;");
        cacheLabel.setPrefWidth(100);

        ImageCache imageCache = ImageCache.getInstance();
        int memoryCacheSize = imageCache.getMemoryCacheSize();
        long diskCacheSize = imageCache.getDiskCacheSize();
        String cacheInfo = String.format("%d images in memory, %.2f MB on disk",
                memoryCacheSize, diskCacheSize / (1024.0 * 1024.0));

        Label cacheInfoLabel = new Label(cacheInfo);
        cacheInfoLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearCacheButton = new Button("Clear Cache");
        clearCacheButton.setStyle(
                "-fx-font-size: 14px; " +
                        "-fx-padding: 8 16; " +
                        "-fx-background-color: #dc3545; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-radius: 6;");

        clearCacheButton.setOnAction(e -> clearImageCache(cacheInfoLabel));

        cacheInfoRow.getChildren().addAll(
                cacheLabel,
                cacheInfoLabel,
                spacer,
                clearCacheButton);

        Label cacheDescription = new Label(
                "Clear cached images to free up disk space. Images will be re-downloaded when needed.");
        cacheDescription.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        cacheDescription.setWrapText(true);
        cacheDescription.setPrefWidth(500);

        cacheSection.getChildren().addAll(
                cacheSectionTitle,
                cacheInfoRow,
                cacheDescription);

        return cacheSection;
    }

    private void clearImageCache(Label cacheInfoLabel) {
        try {
            ImageCache imageCache = ImageCache.getInstance();

            imageCache.clearCache();

            Platform.runLater(() -> {
                cacheInfoLabel.setText("0 images in memory, 0.00 MB on disk");
            });

            showCacheAlert("Cache Cleared",
                    "Image cache has been cleared successfully! " +
                            "Cover images will be re-downloaded when needed.",
                    Alert.AlertType.INFORMATION);

        } catch (Exception ex) {
            System.err.println("Error clearing cache: " + ex.getMessage());
            showCacheAlert("Error",
                    "Failed to clear cache: " + ex.getMessage(),
                    Alert.AlertType.ERROR);
        }
    }

    private void showCacheAlert(String title, String message, Alert.AlertType alertType) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}