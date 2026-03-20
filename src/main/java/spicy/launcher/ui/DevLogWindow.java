package spicy.launcher.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;

public class DevLogWindow {

    private Stage stage;
    private TextArea logArea;
    private volatile boolean running = true;
    private Thread logReaderThread;

    public DevLogWindow(Stage owner, boolean isDarkTheme) {
        stage = new Stage();
        stage.setTitle("Developer Log");
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/spicy/launcher/ui/logo.png"))));
        BorderPane root = new BorderPane();
        root.getStyleClass().add("devlog-root");
        if (!isDarkTheme) {
            root.getStyleClass().add("light");
        }
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.getStyleClass().add("devlog-textarea");
        logArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");
        ScrollPane scrollPane = new ScrollPane(logArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10));
        buttonBox.getStyleClass().add("devlog-buttons");
        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("devlog-button");
        clearBtn.setOnAction(e -> logArea.clear());
        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("devlog-button");
        closeBtn.setOnAction(e -> close());
        buttonBox.getChildren().addAll(clearBtn, closeBtn);
        root.setCenter(scrollPane);
        root.setBottom(buttonBox);
        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("styles.css")).toExternalForm());
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> close());
        if (owner != null) {
            owner.xProperty().addListener((obs, oldVal, newVal) -> {
                if (stage.isShowing()) {
                    stage.setX(newVal.doubleValue() + owner.getWidth() + 10);
                }
            });
            owner.yProperty().addListener((obs, oldVal, newVal) -> {
                if (stage.isShowing()) {
                    stage.setY(newVal.doubleValue());
                }
            });
        }
    }

    public void show() {
        stage.show();
    }

    public void close() {
        running = false;
        if (logReaderThread != null && logReaderThread.isAlive()) {
            logReaderThread.interrupt();
        }
        stage.close();
    }

    public void attachToProcess(Process process) {
        logReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    final String logLine = line;
                    Platform.runLater(() -> {
                        logArea.appendText(logLine + "\n");
                        logArea.setScrollTop(Double.MAX_VALUE);
                    });
                }
            } catch (IOException ignored) {}
        });
        logReaderThread.setDaemon(true);
        logReaderThread.start();
    }
}