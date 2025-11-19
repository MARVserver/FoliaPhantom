package com.patch.foliaphantom.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FoliaPhantomApp extends Application {

    private TextArea statusArea;
    private ListView<String> fileListView;
    private List<File> selectedFiles = new ArrayList<>();
    private ProgressBar progressBar;
    private Label progressLabel;

    private double xOffset = 0;
    private double yOffset = 0;

    @Override
    public void start(Stage primaryStage) {
        // Transparent stage for rounded corners
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setTitle("Folia Phantom GUI");

        // Root layout
        StackPane root = new StackPane();
        root.setId("root-pane");

        // Window Dragging Logic
        root.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        });

        // Glass Container
        VBox glassContainer = new VBox(15);
        glassContainer.setAlignment(Pos.TOP_CENTER);
        glassContainer.setPadding(new Insets(20));
        glassContainer.setMaxWidth(750);
        glassContainer.setMaxHeight(600);
        glassContainer.setId("glass-container");

        // Window Controls (Close/Minimize)
        HBox windowControls = new HBox(10);
        windowControls.setAlignment(Pos.TOP_RIGHT);
        Button minimizeButton = new Button("-");
        minimizeButton.getStyleClass().add("window-control-button");
        minimizeButton.setOnAction(e -> primaryStage.setIconified(true));

        Button closeButton = new Button("X");
        closeButton.getStyleClass().add("window-control-button");
        closeButton.setId("close-button");
        closeButton.setOnAction(e -> primaryStage.close());

        windowControls.getChildren().addAll(minimizeButton, closeButton);

        // Title
        Label titleLabel = new Label("Folia Phantom");
        titleLabel.setId("title-label");

        // Drag and Drop Area
        VBox fileSelectionBox = new VBox(10);
        fileSelectionBox.setAlignment(Pos.CENTER);
        fileSelectionBox.setId("drag-drop-area");

        Label dragDropLabel = new Label("Drag & Drop JARs here or Click to Select");
        dragDropLabel.setId("drag-drop-label");

        Button selectButton = new Button("Select Files");
        selectButton.getStyleClass().add("glass-button");
        selectButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Plugin JARs");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("JAR Files", "*.jar"));
            List<File> files = fileChooser.showOpenMultipleDialog(primaryStage);
            if (files != null) {
                addFiles(files);
            }
        });

        fileSelectionBox.getChildren().addAll(dragDropLabel, selectButton);

        // Drag & Drop Events
        fileSelectionBox.setOnDragOver(event -> {
            if (event.getGestureSource() != fileSelectionBox && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        fileSelectionBox.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> validFiles = new ArrayList<>();
                for (File file : db.getFiles()) {
                    if (file.getName().toLowerCase().endsWith(".jar")) {
                        validFiles.add(file);
                    }
                }
                addFiles(validFiles);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        fileListView = new ListView<>();
        fileListView.setId("file-list");
        fileListView.setPrefHeight(120);
        fileListView.setPlaceholder(new Label("No files selected"));

        // Patch Button & Progress
        Button patchButton = new Button("Patch All Plugins");
        patchButton.setId("patch-button");
        patchButton.getStyleClass().add("glass-button");
        patchButton.setOnAction(e -> patchPlugins());

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(600);
        progressBar.setVisible(false);

        progressLabel = new Label("");
        progressLabel.setId("progress-label");

        // Status Area
        statusArea = new TextArea();
        statusArea.setEditable(false);
        statusArea.setPrefHeight(150);
        statusArea.setWrapText(true);
        statusArea.setId("status-area");

        glassContainer.getChildren().addAll(
                windowControls,
                titleLabel,
                fileSelectionBox,
                fileListView,
                patchButton,
                progressBar,
                progressLabel,
                statusArea);

        // Force rounded corners with a clip
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(glassContainer.widthProperty());
        clip.heightProperty().bind(glassContainer.heightProperty());
        clip.setArcWidth(60);
        clip.setArcHeight(60);
        glassContainer.setClip(clip);

        root.getChildren().add(glassContainer);

        Scene scene = new Scene(root, 900, 700);
        scene.setFill(Color.TRANSPARENT); // Important for rounded corners

        // Load CSS
        try {
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        } catch (Exception e) {
            System.err.println("Could not load style.css: " + e.getMessage());
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void addFiles(List<File> files) {
        for (File file : files) {
            if (!selectedFiles.contains(file)) {
                selectedFiles.add(file);
                fileListView.getItems().add(file.getName());
            }
        }
        statusArea.appendText("Added " + files.size() + " files. Total: " + selectedFiles.size() + "\n");
    }

    private void patchPlugins() {
        if (selectedFiles.isEmpty()) {
            statusArea.appendText("Error: No files selected.\n");
            return;
        }

        statusArea.appendText("Starting parallel patch process...\n");
        progressBar.setVisible(true);
        progressBar.setProgress(0);

        int totalFiles = selectedFiles.size();
        AtomicInteger completedFiles = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(totalFiles, 4)); // 4 threads max

        for (File file : selectedFiles) {
            executor.submit(() -> {
                Platform.runLater(() -> statusArea.appendText("Patching: " + file.getName() + "...\n"));

                try {
                    // Simulation of work (random duration)
                    Thread.sleep((long) (500 + Math.random() * 1000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                int current = completedFiles.incrementAndGet();
                double progress = (double) current / totalFiles;

                Platform.runLater(() -> {
                    statusArea.appendText("Done: " + file.getName() + "\n");
                    progressBar.setProgress(progress);
                    progressLabel.setText(current + " / " + totalFiles + " completed");

                    if (current == totalFiles) {
                        statusArea.appendText("All operations completed successfully.\n");
                        progressBar.setVisible(false);
                        progressLabel.setText("Completed!");
                    }
                });
            });
        }
        executor.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
