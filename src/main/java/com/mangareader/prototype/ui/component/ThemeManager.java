package com.mangareader.prototype.ui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import com.mangareader.prototype.util.ThreadPoolManager;

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
    
    private String cachedBaseCssUrl;
    private String cachedLightThemeCssUrl;
    private String cachedDarkThemeCssUrl;

    public enum Theme {
        LIGHT("light", "/styles/base.css", "/styles/light-theme.css"),
        DARK("dark", "/styles/base.css", "/styles/dark-theme.css");

        private final String name;
        private final String baseCssPath;
        private final String themeCssPath;

        Theme(String name, String baseCssPath, String themeCssPath) {
            this.name = name;
            this.baseCssPath = baseCssPath;
            this.themeCssPath = themeCssPath;
        }

        public String getName() {
            return name;
        }

        public String getBaseCssPath() {
            return baseCssPath;
        }

        public String getThemeCssPath() {
            return themeCssPath;
        }

        @Deprecated
        public String getCssPath() {
            return themeCssPath;
        }
    }

    private ThemeManager() {
        preferences = Preferences.userNodeForPackage(ThemeManager.class);
        listeners = new ArrayList<>();
        loadTheme();
        initializeCssCache();
    }
    
    private void initializeCssCache() {
        try {
            cachedBaseCssUrl = getClass().getResource(Theme.LIGHT.getBaseCssPath()).toExternalForm();
            cachedLightThemeCssUrl = getClass().getResource(Theme.LIGHT.getThemeCssPath()).toExternalForm();
            cachedDarkThemeCssUrl = getClass().getResource(Theme.DARK.getThemeCssPath()).toExternalForm();
            System.out.println("CSS URLs cached successfully");
        } catch (Exception e) {
            System.err.println("Error caching CSS URLs: " + e.getMessage());
        }
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
        notifyListeners(theme);
        applyTheme(theme);
        saveTheme();
    }

    public void toggleTheme() {
        Theme newTheme = (currentTheme == Theme.LIGHT) ? Theme.DARK : Theme.LIGHT;
        setTheme(newTheme);
    }

    public void addThemeChangeListener(ThemeChangeListener listener) {
        listeners.add(listener);
    }

    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(Theme newTheme) {
        javafx.application.Platform.runLater(() -> {
            for (ThemeChangeListener listener : listeners) {
                try {
                    listener.onThemeChanged(newTheme);
                } catch (Exception e) {
                    System.err.println("Error notifying theme change listener: " + e.getMessage());
                }
            }
        });
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
            ThreadPoolManager.getInstance().executeGeneralTask(() -> {
                try {
                    preferences.flush();
                    System.out.println("Saved theme to preferences: " + currentTheme.getName());
                } catch (Exception e) {
                    System.err.println("Error flushing theme preferences: " + e.getMessage());
                }
            });
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
            if (cachedBaseCssUrl != null) {
                scene.getStylesheets().add(cachedBaseCssUrl);
                
                String themeCssUrl = (theme == Theme.DARK) ? cachedDarkThemeCssUrl : cachedLightThemeCssUrl;
                if (themeCssUrl != null) {
                    scene.getStylesheets().add(themeCssUrl);
                }
                
                System.out.println("Applied cached theme: " + theme.getName());
            } else {
                String baseCssPath = getClass().getResource(theme.getBaseCssPath()).toExternalForm();
                scene.getStylesheets().add(baseCssPath);
                
                String themeCssPath = getClass().getResource(theme.getThemeCssPath()).toExternalForm();
                scene.getStylesheets().add(themeCssPath);
                
                System.out.println("Applied theme (uncached): " + theme.getName());
            }
        } catch (Exception e) {
            System.err.println("Error loading theme CSS: " + e.getMessage());
            if (theme != Theme.LIGHT && cachedBaseCssUrl != null && cachedLightThemeCssUrl != null) {
                try {
                    scene.getStylesheets().add(cachedBaseCssUrl);
                    scene.getStylesheets().add(cachedLightThemeCssUrl);
                } catch (Exception fallbackError) {
                    System.err.println("Failed to load fallback theme: " + fallbackError.getMessage());
                }
            }
        }
    }

    public String getTextColor() {
        return isDarkTheme() ? "#e0e0e0" : "#333333";
    }


    public String getBackgroundColor() {
        return isDarkTheme() ? "#2b2b2b" : "#ffffff";
    }

    public String getSecondaryBackgroundColor() {
        return isDarkTheme() ? "#3c3c3c" : "#f5f5f5";
    }

    public String getBorderColor() {
        return isDarkTheme() ? "#555555" : "#e0e0e0";
    }

    public interface ThemeChangeListener {
        void onThemeChanged(Theme newTheme);
    }
}
