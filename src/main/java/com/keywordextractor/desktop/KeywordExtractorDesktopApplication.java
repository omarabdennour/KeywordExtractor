package com.keywordextractor.desktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

public class KeywordExtractorDesktopApplication extends Application {
    private ConfigurableApplicationContext applicationContext;

    @Override
    public void init() {
        String[] args = getParameters().getRaw().toArray(String[]::new);

        // Start Spring without a web server so JavaFX can own the desktop lifecycle.
        applicationContext = new SpringApplicationBuilder(KeywordExtractorSpringApplication.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                KeywordExtractorDesktopApplication.class.getResource("keyword-extractor-view.fxml"));
        fxmlLoader.setControllerFactory(applicationContext::getBean);
        Scene scene = new Scene(fxmlLoader.load(), 1120, 760);
        scene.getStylesheets().add(
                KeywordExtractorDesktopApplication.class.getResource("keyword-extractor.css").toExternalForm());
        stage.setTitle("Keyword Extractor");
        stage.getIcons().add(new Image(
                KeywordExtractorDesktopApplication.class.getResourceAsStream("app-icon.png")));
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }
}
