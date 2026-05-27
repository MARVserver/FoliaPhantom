package com.patch.foliaphantom.gui;

import com.patch.foliaphantom.patcher.PluginPatcher;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Folia Phantom の JavaFX デスクトップ GUI アプリケーション（改善版）。
 *
 * <p>ガラスモーフィズム（Glassmorphism）デザインを採用した
 * ビジュアルパッチツール。以下の機能を提供する：
 * <ul>
 *   <li>カスタムタイトルバー（ドラッグ移動、最小化、閉じる）</li>
 *   <li>ドラッグ＆ドロップ + 個別削除対応ファイルリスト</li>
 *   <li>ファイル単位の状態表示（pending / success / error）</li>
 *   <li>リアルタイムプログレスバー + ステータスバー</li>
 *   <li>オートスクロールコンソール + クリアボタン</li>
 *   <li>キーボードショートカット（Ctrl+O / Ctrl+E / Delete）</li>
 *   <li>タスク実行中の終了確認ダイアログ</li>
 * </ul>
 * </p>
 */
public final class FoliaPhantomApp extends Application {

    /** ロガーインスタンス */
    private static final Logger log = LoggerFactory.getLogger(FoliaPhantomApp.class);

    /** タイムスタンプフォーマッター */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** デフォルト出力ディレクトリ */
    private static final String DEFAULT_OUTPUT = "patched-plugins";

    /** 並列スレッド数 */
    private static final int PARALLELISM = 4;

    // ---- ファイル状態 ----
    private enum FileState { PENDING, PATCHING, SUCCESS, ERROR }

    // ---- ファイルエントリ ----
    private record JarEntry(Path path, ReadOnlyObjectWrapper<FileState> state) {
        JarEntry(Path path) {
            this(path, new ReadOnlyObjectWrapper<>(FileState.PENDING));
        }
        String displayName() {
            return path.getFileName().toString();
        }
    }

    // ---- UI コンポーネント ----

    private ListView<JarEntry> fileListView;
    private final ObservableList<JarEntry> jarEntries = FXCollections.observableArrayList();
    private TextField outputDirField;
    private CheckBox verboseCheckBox;
    private ProgressBar progressBar;
    private Label progressLabel;
    private TextArea consoleArea;
    private Button executeButton;
    private Button openResultsButton;
    private Button clearConsoleButton;
    private Label statusBarLabel;
    private Label fileBadge;
    private final ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
    private volatile boolean tasksRunning;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Folia Phantom");
        stage.setWidth(820);
        stage.setHeight(600);
        stage.setMinWidth(640);
        stage.setMinHeight(480);
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);

        BorderPane root = new BorderPane();
        root.setId("root");

        root.setTop(createTitleBar(stage));
        root.setCenter(createMainContent());
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/com/patch/foliaphantom/gui/style.css").toExternalForm());
        registerKeyboardShortcuts(scene);

        stage.setScene(scene);
        stage.show();

        log.info("Folia Phantom GUI started");
        appendConsole("INFO", "Folia Phantom GUI ready — drop JAR files or use Ctrl+O");
    }

    /**
     * JavaFX アプリケーションのエントリポイント。
     *
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        executor.shutdownNow();
    }

    // ========================================================================
    // タイトルバー
    // ========================================================================

    private HBox createTitleBar(Stage stage) {
        HBox bar = new HBox();
        bar.setId("title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 8, 16));
        bar.setSpacing(8);

        Label icon = new Label("\uD83D\uDC7B");
        icon.setFont(Font.font("System", 18));

        Label title = new Label("Folia Phantom");
        title.setId("title-label");

        Label version = new Label("pasta v2.0.0");
        version.setId("version-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimizeBtn = titleButton("\u2500", "Minimize");
        minimizeBtn.setOnAction(e -> stage.setIconified(true));

        Button closeBtn = titleButton("\u2715", "Close");
        closeBtn.setId("close-btn");
        closeBtn.setOnAction(e -> handleClose(stage));

        bar.getChildren().addAll(icon, title, version, spacer, minimizeBtn, closeBtn);

        // ドラッグ
        var dx = new double[1];
        var dy = new double[1];
        bar.setOnMousePressed(e -> { dx[0] = stage.getX() - e.getScreenX(); dy[0] = stage.getY() - e.getScreenY(); });
        bar.setOnMouseDragged(e -> { stage.setX(e.getScreenX() + dx[0]); stage.setY(e.getScreenY() + dy[0]); });

        return bar;
    }

    private static Button titleButton(String text, String tip) {
        Button btn = new Button(text);
        btn.getStyleClass().add("title-btn");
        btn.setTooltip(new Tooltip(tip));
        return btn;
    }

    // ========================================================================
    // 閉じる処理
    // ========================================================================

    private void handleClose(Stage stage) {
        if (tasksRunning) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm Exit");
            alert.setHeaderText("Patching tasks are still running");
            alert.setContentText("Are you sure you want to exit? Running tasks will be interrupted.");
            alert.initOwner(stage);
            alert.getDialogPane().getStylesheets().addAll(stage.getScene().getStylesheets());
            alert.getDialogPane().setId("dialog-root");
            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        executor.shutdownNow();
        stage.close();
        Platform.exit();
    }

    // ========================================================================
    // メインコンテンツ
    // ========================================================================

    private BorderPane createMainContent() {
        BorderPane content = new BorderPane();
        content.setId("main-content");
        content.setLeft(createSidebar());
        content.setCenter(createCenterPane());
        return content;
    }

    // ========================================================================
    // サイドバー
    // ========================================================================

    private VBox createSidebar() {
        VBox side = new VBox();
        side.setId("sidebar");
        side.setPrefWidth(260);
        side.setPadding(new Insets(16));
        side.setSpacing(10);

        Label heading = new Label("PLUGINS TO PATCH");
        heading.setId("sidebar-title");

        this.fileListView = new ListView<>(jarEntries);
        this.fileListView.setCellFactory(lv -> new JarCell());
        VBox.setVgrow(this.fileListView, Priority.ALWAYS);
        this.fileListView.setTooltip(new Tooltip("Drag & drop JAR files here"));

        // ドラッグ＆ドロップ
        enableDragDrop(this.fileListView);

        // 選択削除 (Delete キー)
        this.fileListView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                removeSelected();
            }
        });

        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER);

        Button addBtn = new Button("\u2795 Add");
        addBtn.setId("add-btn");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(addBtn, Priority.ALWAYS);
        addBtn.setTooltip(new Tooltip("Select JAR files (Ctrl+O)"));
        addBtn.setOnAction(e -> openFileChooser());

        Button clearBtn = new Button("\uD83D\uDDD1 Clear");
        clearBtn.setId("clear-btn");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(clearBtn, Priority.ALWAYS);
        clearBtn.setTooltip(new Tooltip("Remove all files from list"));
        clearBtn.setOnAction(e -> {
            jarEntries.clear();
            updateFileBadge();
            appendConsole("INFO", "File list cleared");
        });

        btnRow.getChildren().addAll(addBtn, clearBtn);
        side.getChildren().addAll(heading, fileListView, btnRow);
        return side;
    }

    // ---- カスタムセル ----

    private final class JarCell extends ListCell<JarEntry> {
        private final Label iconLabel = new Label();
        private final Label nameLabel = new Label();
        private final Button removeBtn = new Button("\u2715");
        private final HBox graphic = new HBox(8);

        JarCell() {
            graphic.setAlignment(Pos.CENTER_LEFT);
            graphic.getChildren().addAll(iconLabel, nameLabel);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            graphic.getChildren().add(spacer);

            removeBtn.getStyleClass().add("remove-btn");
            removeBtn.setTooltip(new Tooltip("Remove from list"));
            removeBtn.setOnAction(e -> {
                JarEntry entry = getItem();
                if (entry != null) {
                    jarEntries.remove(entry);
                    updateFileBadge();
                }
            });
            graphic.getChildren().add(removeBtn);

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(JarEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                return;
            }
            nameLabel.setText(entry.path().getFileName().toString());
            nameLabel.setId("jar-name");

            switch (entry.state().get()) {
                case PENDING  -> iconLabel.setText("\u23F3");
                case PATCHING -> iconLabel.setText("\u2699\uFE0F");
                case SUCCESS  -> iconLabel.setText("\u2705");
                case ERROR    -> iconLabel.setText("\u274C");
            }
            iconLabel.setId("state-icon-" + entry.state().get().name().toLowerCase());

            // 状態に応じた文字色
            nameLabel.getStyleClass().removeAll("text-pending", "text-success", "text-error");
            nameLabel.getStyleClass().add(
                    switch (entry.state().get()) {
                        case SUCCESS -> "text-success";
                        case ERROR   -> "text-error";
                        default      -> "text-pending";
                    });

            setGraphic(graphic);
        }
    }

    // ---- ドラッグ＆ドロップ ----

    private void enableDragDrop(ListView<JarEntry> lv) {
        lv.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) { e.acceptTransferModes(TransferMode.COPY); }
            e.consume();
        });
        lv.setOnDragDropped(e -> {
            for (File f : e.getDragboard().getFiles()) { addJarFile(f.toPath()); }
            e.setDropCompleted(true);
            e.consume();
        });
    }

    // ---- ファイル選択 ----

    private void openFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Plugin JAR Files");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR files", "*.jar"));
        List<File> selected = chooser.showOpenMultipleDialog(fileListView.getScene().getWindow());
        if (selected != null) {
            for (File f : selected) { addJarFile(f.toPath()); }
        }
    }

    private void addJarFile(Path path) {
        if (!path.toString().endsWith(".jar")) {
            appendConsole("WARN", "Skipped: " + path.getFileName() + " (not a .jar)");
            return;
        }
        if (jarEntries.stream().anyMatch(e -> e.path().equals(path))) {
            appendConsole("INFO", "Already added: " + path.getFileName());
            return;
        }
        jarEntries.add(new JarEntry(path));
        updateFileBadge();
        appendConsole("INFO", "Added: " + path.getFileName());
    }

    private void removeSelected() {
        JarEntry selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            jarEntries.remove(selected);
            updateFileBadge();
        }
    }

    // ========================================================================
    // 中央ペイン
    // ========================================================================

    private VBox createCenterPane() {
        VBox center = new VBox();
        center.setId("center-pane");
        center.setPadding(new Insets(24));
        center.setSpacing(16);

        // ヘッダー
        Label welcome = new Label("Welcome to Folia Phantom");
        welcome.setId("welcome-label");
        Label subtitle = new Label("Professional bytecode transformer for Bukkit \u2192 Folia");
        subtitle.setId("subtitle-label");

        center.getChildren().addAll(welcome, subtitle,
                createConfigSection(), createProgressSection(), createConsoleSection());
        return center;
    }

    // ---- 設定 ----

    private VBox createConfigSection() {
        VBox cfg = new VBox();
        cfg.setId("config-section");
        cfg.setPadding(new Insets(16));
        cfg.setSpacing(12);

        Label title = new Label("CONFIGURATION");
        title.setId("section-title");

        this.verboseCheckBox = new CheckBox("Verbose logging");
        this.verboseCheckBox.setId("verbose-check");
        this.verboseCheckBox.setTooltip(new Tooltip("Show detailed patch logs"));

        HBox outRow = new HBox(8);
        outRow.setAlignment(Pos.CENTER_LEFT);
        Label outLabel = new Label("Output Dir:");
        this.outputDirField = new TextField(DEFAULT_OUTPUT);
        HBox.setHgrow(this.outputDirField, Priority.ALWAYS);
        this.outputDirField.setTooltip(new Tooltip("Directory for patched JARs"));

        Button browseBtn = new Button("\uD83D\uDCC2 Browse");
        browseBtn.setId("browse-btn");
        browseBtn.setTooltip(new Tooltip("Choose output folder"));
        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Output Directory");
            File currentDir = new File(outputDirField.getText());
            if (currentDir.isDirectory()) {
                dc.setInitialDirectory(currentDir);
            }
            File sel = dc.showDialog(outputDirField.getScene().getWindow());
            if (sel != null) { outputDirField.setText(sel.getAbsolutePath()); }
        });

        outRow.getChildren().addAll(outLabel, outputDirField, browseBtn);
        cfg.getChildren().addAll(title, verboseCheckBox, outRow);
        return cfg;
    }

    // ---- プログレス ----

    private VBox createProgressSection() {
        VBox prog = new VBox();
        prog.setId("progress-section");
        prog.setPadding(new Insets(16));
        prog.setSpacing(10);

        Label title = new Label("PROCESS TASK");
        title.setId("section-title");

        this.progressBar = new ProgressBar(0);
        this.progressBar.setId("progress-bar");
        this.progressBar.setMaxWidth(Double.MAX_VALUE);

        this.progressLabel = new Label("Ready");
        this.progressLabel.setId("progress-label");

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        this.executeButton = new Button("\u26A1 EXECUTE PATCH");
        this.executeButton.setId("execute-btn");
        this.executeButton.setTooltip(new Tooltip("Start patching (Ctrl+E)"));
        this.executeButton.setOnAction(e -> executePatch());

        this.openResultsButton = new Button("\uD83D\uDCC1 Open Results");
        this.openResultsButton.setId("open-results-btn");
        this.openResultsButton.setDisable(true);
        this.openResultsButton.setTooltip(new Tooltip("Open output folder in Explorer"));
        this.openResultsButton.setOnAction(e -> openResultsFolder());

        this.fileBadge = new Label("0 files");
        this.fileBadge.setId("file-badge");

        btnRow.getChildren().addAll(executeButton, openResultsButton, fileBadge);
        prog.getChildren().addAll(title, progressBar, progressLabel, btnRow);
        return prog;
    }

    // ---- コンソール ----

    private VBox createConsoleSection() {
        VBox con = new VBox();
        con.setId("console-section");
        VBox.setVgrow(con, Priority.ALWAYS);
        con.setPadding(new Insets(16));
        con.setSpacing(8);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("CONSOLE");
        title.setId("section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        this.clearConsoleButton = new Button("Clear");
        this.clearConsoleButton.setId("console-clear-btn");
        this.clearConsoleButton.setTooltip(new Tooltip("Clear console output"));
        this.clearConsoleButton.setOnAction(e -> consoleArea.clear());

        header.getChildren().addAll(title, spacer, clearConsoleButton);

        this.consoleArea = new TextArea();
        this.consoleArea.setId("console-area");
        this.consoleArea.setEditable(false);
        this.consoleArea.setFont(Font.font("Consolas", 12));
        this.consoleArea.textProperty().addListener((obs, old, nw) -> {
            // 自動スクロール
            consoleArea.selectPositionCaret(nw.length());
            consoleArea.deselect();
        });
        VBox.setVgrow(this.consoleArea, Priority.ALWAYS);

        con.getChildren().addAll(header, consoleArea);
        return con;
    }

    // ========================================================================
    // ステータスバー
    // ========================================================================

    private HBox createStatusBar() {
        HBox bar = new HBox();
        bar.setId("status-bar");
        bar.setPadding(new Insets(4, 16, 4, 16));
        bar.setSpacing(16);
        bar.setAlignment(Pos.CENTER_LEFT);

        Label statusIcon = new Label("\uD83D\uDFE2");
        statusIcon.setId("status-icon");

        this.statusBarLabel = new Label("Ready");
        this.statusBarLabel.setId("status-bar-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label cpRight = new Label("pasta v2.0.0 | Java " + System.getProperty("java.version"));
        cpRight.setId("status-bar-right");

        bar.getChildren().addAll(statusIcon, statusBarLabel, spacer, cpRight);
        return bar;
    }

    // ========================================================================
    // ショートカット
    // ========================================================================

    private void registerKeyboardShortcuts(Scene scene) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                this::openFileChooser);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
                this::executePatch);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                () -> consoleArea.clear());
    }

    // ========================================================================
    // バッジ更新
    // ========================================================================

    private void updateFileBadge() {
        int total = jarEntries.size();
        long success = jarEntries.stream().filter(e -> e.state().get() == FileState.SUCCESS).count();
        long errors = jarEntries.stream().filter(e -> e.state().get() == FileState.ERROR).count();
        if (total == 0) {
            fileBadge.setText("0 files");
        } else if (errors > 0) {
            fileBadge.setText(total + " files (\u2714" + success + " \u274C" + errors + ")");
        } else if (success > 0 && success == total) {
            fileBadge.setText(total + " files (\u2714" + success + ")");
        } else {
            fileBadge.setText(total + " files");
        }
    }

    // ========================================================================
    // パッチ実行
    // ========================================================================

    private void executePatch() {
        if (jarEntries.isEmpty()) {
            appendConsole("WARN", "No JAR files to patch");
            return;
        }

        tasksRunning = true;
        executeButton.setDisable(true);
        openResultsButton.setDisable(true);
        progressBar.setProgress(0);
        progressLabel.setText("Starting...");
        statusBarLabel.setText("Patching in progress...");

        // 全エントリを PENDING にリセット
        jarEntries.forEach(e -> e.state().set(FileState.PENDING));
        updateFileBadge();

        Path outputDir = Paths.get(outputDirField.getText());
        boolean verbose = verboseCheckBox.isSelected();
        appendConsole("INFO", "Starting patch execution...");

        executor.submit(() -> {
            PluginPatcher patcher = new PluginPatcher(outputDir, verbose);
            AtomicInteger completed = new AtomicInteger(0);
            int total = jarEntries.size();

            for (JarEntry entry : jarEntries) {
                Platform.runLater(() -> {
                    entry.state().set(FileState.PATCHING);
                    fileListView.refresh();
                });

                try {
                    Path result = patcher.patchPlugin(entry.path());
                    int done = completed.incrementAndGet();
                    double prog = (double) done / total;
                    Platform.runLater(() -> {
                        entry.state().set(FileState.SUCCESS);
                        fileListView.refresh();
                        progressBar.setProgress(prog);
                        progressLabel.setText(String.format("Processing %d/%d...", done, total));
                        statusBarLabel.setText(String.format("Patched %d/%d files", done, total));
                        appendConsole("SUCCESS", "Patched: " + entry.path().getFileName()
                                + " \u2192 " + result.getFileName());
                        updateFileBadge();
                    });
                } catch (IOException e) {
                    int done = completed.incrementAndGet();
                    Platform.runLater(() -> {
                        entry.state().set(FileState.ERROR);
                        fileListView.refresh();
                        appendConsole("ERROR", "Failed: " + entry.path().getFileName()
                                + " - " + e.getMessage());
                        updateFileBadge();
                    });
                }
            }

            Platform.runLater(() -> {
                tasksRunning = false;
                executeButton.setDisable(false);
                openResultsButton.setDisable(false);
                progressLabel.setText("Complete!");
                statusBarLabel.setText("All patching operations finished");
                appendConsole("INFO", "All patching operations finished");
            });
        });
    }

    // ========================================================================
    // 結果フォルダを開く
    // ========================================================================

    private void openResultsFolder() {
        try {
            Path dir = Paths.get(outputDirField.getText());
            if (Files.exists(dir)) {
                Desktop.getDesktop().open(dir.toFile());
            } else {
                appendConsole("WARN", "Output directory does not exist: " + dir.toAbsolutePath());
            }
        } catch (IOException e) {
            appendConsole("ERROR", "Failed to open folder: " + e.getMessage());
        }
    }

    // ========================================================================
    // コンソール出力
    // ========================================================================

    private void appendConsole(String level, String message) {
        Platform.runLater(() -> {
            String ts = LocalTime.now().format(TIME_FORMAT);
            String tag = switch (level) {
                case "SUCCESS" -> "\u2705";
                case "WARN"    -> "\u26A0\uFE0F";
                case "ERROR"   -> "\u274C";
                default        -> "\u2139\uFE0F";
            };
            consoleArea.appendText(String.format("%s [%s] %s %s%n", ts, level, tag, message));
        });
    }
}
