package com.mangareader.prototype;

import com.mangareader.prototype.ui.component.ThemeManager;
import com.mangareader.prototype.ui.view.MainView;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MangaReaderApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainView mainView = new MainView();

        Scene scene = new Scene(mainView, 1280, 720);

        ThemeManager themeManager = ThemeManager.getInstance();
        themeManager.initializeWithScene(scene);

        primaryStage.setTitle("MangaReader");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}