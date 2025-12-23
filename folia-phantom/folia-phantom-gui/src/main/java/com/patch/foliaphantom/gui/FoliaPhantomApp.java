/*
 * Folia Phantom - Professional GUI Application
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
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
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.patch.foliaphantom.core.PluginPatcher;

import java.awt.Desktop;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

/**
 * Modern Dashboard-style GUI for Folia Phantom.
 */
public class FoliaPhantomApp extends Application {

    private static final String VERSION = "1.1.0";

    // UI Elements
    private TextArea statusArea;
    private ListView<FileItem> fileListView;
    private ProgressBar progressBar;
    private Label progressLabel;
    private Label statsLabel;
    private Button patchButton;
    private Button openDirButton;
    private CheckBox verboseLoggingCheckbox;

    // State
    private final List<File> selectedFiles = new ArrayList<>();
    private File outputDirectory = null;
    private double xOffset = 0;
    private double yOffset = 0;

    // Statistics
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    @Override
    public void start(Stage primaryStage) {
        primaryStage.initStyle(StageStyle.TRANSPARENT);

        StackPane root = new StackPane();
        root.setId("root-pane");

        // Dragging logic
        root.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        });

        // Main Layout: Sidebar (Left) + Content (Right)
        HBox mainLayout = new HBox(0);
        mainLayout.setId("main-container");

        VBox sidebar = buildSidebar(primaryStage);
        VBox content = buildContentArea(primaryStage);

        mainLayout.getChildren().addAll(sidebar, content);

        // Window Decorator
        VBox windowWrapper = new VBox(0);
        windowWrapper.getChildren().addAll(buildTitleBar(primaryStage), mainLayout);
        windowWrapper.setId("window-wrapper");

        applyRoundedClip(windowWrapper);
        root.getChildren().add(windowWrapper);

        Scene scene = new Scene(root, 950, 650);
        scene.setFill(Color.TRANSPARENT);
        loadStylesheet(scene);

        primaryStage.setScene(scene);
        primaryStage.show();

        logInfo("Folia Phantom Dashboard initialized.");
    }

    private HBox buildTitleBar(Stage stage) {
        HBox titleBar = new HBox();
        titleBar.setId("title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(10, 15, 10, 20));

        Label title = new Label("Folia Phantom 👻");
        title.setId("window-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minBtn = new Button("─");
        minBtn.getStyleClass().add("win-btn");
        minBtn.setOnAction(e -> stage.setIconified(true));

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("win-btn");
        closeBtn.setId("win-close");
        closeBtn.setOnAction(e -> stage.close());

        titleBar.getChildren().addAll(title, spacer, minBtn, closeBtn);
        return titleBar;
    }

    private VBox buildSidebar(Stage stage) {
        VBox sidebar = new VBox(15);
        sidebar.setId("sidebar");
        sidebar.setPrefWidth(300);
        sidebar.setPadding(new Insets(20));

        Label sectionTitle = new Label("PLUGINS TO PATCH");
        sectionTitle.getStyleClass().add("sidebar-title");

        fileListView = new ListView<>();
        fileListView.setId("sidebar-list");
        VBox.setVgrow(fileListView, Priority.ALWAYS);
        fileListView.setPlaceholder(new Label("Drop JARs here"));

        // Drag & Drop for Sidebar
        fileListView.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles())
                e.acceptTransferModes(TransferMode.COPY);
        });
        fileListView.setOnDragDropped(e -> {
            if (e.getDragboard().hasFiles()) {
                addFiles(e.getDragboard().getFiles());
                e.setDropCompleted(true);
            }
        });

        HBox listActions = new HBox(10);
        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("action-btn-sm");
        addBtn.setOnAction(e -> selectFiles(stage));

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().addAll("action-btn-sm", "btn-danger");
        clearBtn.setOnAction(e -> clearFiles());

        listActions.getChildren().addAll(addBtn, clearBtn);

        sidebar.getChildren().addAll(sectionTitle, fileListView, listActions);
        return sidebar;
    }

    private VBox buildContentArea(Stage stage) {
        VBox content = new VBox(20);
        content.setId("content-area");
        content.setPadding(new Insets(30));
        HBox.setHgrow(content, Priority.ALWAYS);

        // Header Card
        VBox headerCard = new VBox(5);
        headerCard.getStyleClass().add("card");
        Label welcome = new Label("Welcome to Folia Phantom");
        welcome.setId("welcome-text");
        Label desc = new Label("Professional bytecode transformer for Folia servers.");
        desc.getStyleClass().add("muted-text");
        headerCard.getChildren().addAll(welcome, desc);

        // Control Panel
        GridPane controls = new GridPane();
        controls.setHgap(20);
        controls.setVgap(15);
        controls.getStyleClass().add("card");

        Label optLabel = new Label("Configuration");
        optLabel.getStyleClass().add("card-title");
        controls.add(optLabel, 0, 0, 2, 1);

        verboseLoggingCheckbox = new CheckBox("Verbose logging");
        verboseLoggingCheckbox.getStyleClass().add("custom-checkbox");
        controls.add(verboseLoggingCheckbox, 0, 1);

        Button outDirBtn = new Button("Change Output Folder");
        outDirBtn.getStyleClass().add("glass-button-sm");
        outDirBtn.setOnAction(e -> chooseOutputDir(stage));
        controls.add(outDirBtn, 1, 1);

        // Progress Card
        VBox progressCard = new VBox(10);
        progressCard.getStyleClass().add("card");
        Label taskLabel = new Label("Process Task");
        taskLabel.getStyleClass().add("card-title");

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("dashboard-progress");

        progressLabel = new Label("Idle");
        progressLabel.getStyleClass().add("muted-text");

        progressCard.getChildren().addAll(taskLabel, progressBar, progressLabel);

        // Action Row
        HBox actionRow = new HBox(15);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        patchButton = new Button("EXECUTE PATCH");
        patchButton.setId("main-patch-btn");

        openDirButton = new Button("Open Results");
        openDirButton.setId("open-results-btn");
        openDirButton.setVisible(false);
        openDirButton.setOnAction(e -> openResultsDir());

        statsLabel = new Label("0 files selected");
        statsLabel.getStyleClass().add("stats-info");

        actionRow.getChildren().addAll(patchButton, openDirButton, statsLabel);
        patchButton.setOnAction(e -> runPatchProcess());

        // Console
        VBox consoleBox = new VBox(5);
        Label consoleTitle = new Label("CONSOLLE");
        consoleTitle.getStyleClass().add("card-title");
        statusArea = new TextArea();
        statusArea.setEditable(false);
        statusArea.setId("dashboard-console");
        VBox.setVgrow(statusArea, Priority.ALWAYS);
        consoleBox.getChildren().addAll(consoleTitle, statusArea);

        content.getChildren().addAll(headerCard, controls, progressCard, actionRow, consoleBox);
        return content;
    }

    private void chooseOutputDir(Stage stage) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Output Directory");
        File dir = dc.showDialog(stage);
        if (dir != null) {
            outputDirectory = dir;
            logInfo("Output set to: " + dir.getAbsolutePath());
        }
    }

    private void openResultsDir() {
        try {
            File dir = outputDirectory != null ? outputDirectory : new File(".");
            Desktop.getDesktop().open(dir);
        } catch (Exception e) {
            logError("Could not open directory: " + e.getMessage());
        }
    }

    private void runPatchProcess() {
        if (selectedFiles.isEmpty()) {
            logError("Please add files first.");
            return;
        }

        setUIState(false);
        successCount.set(0);
        failureCount.set(0);

        int total = selectedFiles.size();
        AtomicInteger completed = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        Logger patcherLogger = createLogger();

        logInfo("Starting process...");

        for (File file : new ArrayList<>(selectedFiles)) {
            executor.submit(() -> {
                try {
                    File output = new File(outputDirectory != null ? outputDirectory : file.getParentFile(),
                            file.getName().replace(".jar", "-patched.jar"));
                    new PluginPatcher(patcherLogger).patchPlugin(file, output);
                    successCount.incrementAndGet();
                    Platform.runLater(() -> logSuccess("Done: " + file.getName()));
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    Platform.runLater(() -> logError("Fail: " + file.getName() + " (" + e.getMessage() + ")"));
                } finally {
                    int c = completed.incrementAndGet();
                    Platform.runLater(() -> {
                        progressBar.setProgress((double) c / total);
                        progressLabel.setText("Processing " + c + "/" + total + "...");
                        if (c == total)
                            finalizeProcess();
                    });
                }
            });
        }
        executor.shutdown();
    }

    private void finalizeProcess() {
        setUIState(true);
        logInfo("--- Completed ---");
        logInfo("Success: " + successCount.get() + " | Failed: " + failureCount.get());
        progressLabel.setText("Operation completed successfully.");
        openDirButton.setVisible(true);
    }

    private void setUIState(boolean enabled) {
        patchButton.setDisable(!enabled);
        fileListView.setDisable(!enabled);
    }

    private void addFiles(List<File> files) {
        for (File f : files) {
            if (f.getName().endsWith(".jar") && !selectedFiles.contains(f)) {
                selectedFiles.add(f);
                fileListView.getItems().add(new FileItem(f));
            }
        }
        updateStats();
    }

    private void selectFiles(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR Files", "*.jar"));
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files != null)
            addFiles(files);
    }

    private void clearFiles() {
        selectedFiles.clear();
        fileListView.getItems().clear();
        updateStats();
        openDirButton.setVisible(false);
    }

    private void updateStats() {
        statsLabel.setText(selectedFiles.size() + " files selected");
    }

    private void logInfo(String m) {
        appendLog("[INFO] " + m);
    }

    private void logSuccess(String m) {
        appendLog("[SUCCESS] " + m);
    }

    private void logError(String m) {
        appendLog("[ERROR] " + m);
    }

    private void appendLog(String m) {
        String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
        Platform.runLater(() -> {
            statusArea.appendText("[" + ts + "] " + m + "\n");
        });
    }

    private void applyRoundedClip(VBox v) {
        Rectangle r = new Rectangle();
        r.widthProperty().bind(v.widthProperty());
        r.heightProperty().bind(v.heightProperty());
        r.setArcWidth(20);
        r.setArcHeight(20);
        v.setClip(r);
    }

    private void loadStylesheet(Scene s) {
        s.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
    }

    private Logger createLogger() {
        Logger l = Logger.getLogger("FP-" + System.currentTimeMillis());
        l.setUseParentHandlers(false);
        l.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                if (verboseLoggingCheckbox.isSelected())
                    appendLog("[PATCHER] " + r.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return l;
    }

    public static void main(String[] args) {
        launch(args);
    }

    // Inner class for File List presentation
    static class FileItem {
        final File file;

        FileItem(File f) {
            this.file = f;
        }

        @Override
        public String toString() {
            return String.format("%s (%.1f MB)", file.getName(), file.length() / (1024.0 * 1024.0));
        }
    }
}
