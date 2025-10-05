package com.mangareader.prototype.ui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javafx.scene.Scene;

/**
 * Manages application themes (light/dark mode)
 */
public class ThemeManager {
    private static final String THEME_PREFERENCE_KEY = "application.theme";
    private static final String LIGHT_THEME = "light";
    private static final String DARK_THEME = "dark";

    private static ThemeManager instance;
    private final Preferences preferences;
    private Theme currentTheme;
    private Scene scene;
    private final List<ThemeChangeListener> listeners;

    public enum Theme {
        LIGHT("light", "/styles/main.css"),
        DARK("dark", "/styles/dark.css");

        private final String name;
        private final String cssPath;

        Theme(String name, String cssPath) {
            this.name = name;
            this.cssPath = cssPath;
        }

        public String getName() {
            return name;
        }

        public String getCssPath() {
            return cssPath;
        }
    }

    private ThemeManager() {
        preferences = Preferences.userNodeForPackage(ThemeManager.class);
        listeners = new ArrayList<>();
        loadTheme();
    }

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    public void initializeWithScene(Scene scene) {
        this.scene = scene;
        applyTheme(currentTheme);
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public void setTheme(Theme theme) {
        this.currentTheme = theme;
        saveTheme();
        applyTheme(theme);
        notifyListeners(theme);
    }

    public void toggleTheme() {
        Theme newTheme = (currentTheme == Theme.LIGHT) ? Theme.DARK : Theme.LIGHT;
        setTheme(newTheme);
    }

    /**
     * Add a listener to be notified of theme changes
     */
    public void addThemeChangeListener(ThemeChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a theme change listener
     */
    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notify all listeners of theme change
     */
    private void notifyListeners(Theme newTheme) {
        for (ThemeChangeListener listener : listeners) {
            try {
                listener.onThemeChanged(newTheme);
            } catch (Exception e) {
                System.err.println("Error notifying theme change listener: " + e.getMessage());
            }
        }
    }

    public boolean isDarkTheme() {
        return currentTheme == Theme.DARK;
    }

    private void loadTheme() {
        try {
            String themeName = preferences.get(THEME_PREFERENCE_KEY, LIGHT_THEME);
            currentTheme = DARK_THEME.equals(themeName) ? Theme.DARK : Theme.LIGHT;
            System.out.println("Loaded theme from preferences: " + currentTheme.getName());
        } catch (Exception e) {
            System.err.println("Error loading theme preferences, using default: " + e.getMessage());
            currentTheme = Theme.LIGHT;
        }
    }

    private void saveTheme() {
        try {
            preferences.put(THEME_PREFERENCE_KEY, currentTheme.getName());
            preferences.flush();
            System.out.println("Saved theme to preferences: " + currentTheme.getName());
        } catch (Exception e) {
            System.err.println("Error saving theme preferences: " + e.getMessage());
        }
    }

    private void applyTheme(Theme theme) {
        if (scene == null) {
            return;
        }

        scene.getStylesheets().clear();

        try {
            String cssPath = getClass().getResource(theme.getCssPath()).toExternalForm();
            scene.getStylesheets().add(cssPath);
            System.out.println("Applied theme: " + theme.getName() + " (" + cssPath + ")");
        } catch (Exception e) {
            System.err.println("Error loading theme CSS: " + e.getMessage());
            if (theme != Theme.LIGHT) {
                try {
                    String defaultCssPath = getClass().getResource(Theme.LIGHT.getCssPath()).toExternalForm();
                    scene.getStylesheets().add(defaultCssPath);
                } catch (Exception fallbackError) {
                    System.err.println("Failed to load fallback theme: " + fallbackError.getMessage());
                }
            }
        }

        refreshUIComponents();
    }

    /**
     * Force refresh of UI components to apply new theme
     */
    private void refreshUIComponents() {
        if (scene != null && scene.getRoot() != null) {
            scene.getRoot().applyCss();
        }
    }

    /**
     * Get the appropriate text color for the current theme
     */
    public String getTextColor() {
        return isDarkTheme() ? "#e0e0e0" : "#333333";
    }

    /**
     * Get the appropriate background color for the current theme
     */
    public String getBackgroundColor() {
        return isDarkTheme() ? "#2b2b2b" : "#ffffff";
    }

    /**
     * Get the appropriate secondary background color for the current theme
     */
    public String getSecondaryBackgroundColor() {
        return isDarkTheme() ? "#3c3c3c" : "#f5f5f5";
    }

    /**
     * Get the appropriate border color for the current theme
     */
    public String getBorderColor() {
        return isDarkTheme() ? "#555555" : "#e0e0e0";
    }

    /**
     * Interface for components that want to be notified of theme changes
     */
    public interface ThemeChangeListener {
        void onThemeChanged(Theme newTheme);
    }
}
