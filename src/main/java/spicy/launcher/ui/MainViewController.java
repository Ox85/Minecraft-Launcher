package spicy.launcher.ui;

import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import spicy.launcher.core.MicrosoftAuthManager;
import spicy.launcher.core.GameLauncher;
import spicy.launcher.core.VersionManager;
import spicy.launcher.model.DisplayVersion;
import spicy.launcher.model.LaunchContext;
import spicy.launcher.model.LaunchSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainViewController {

    @FXML
    private TextField usernameField;
    @FXML
    private ComboBox<DisplayVersion> versionCombo;
    @FXML
    private StackPane playButtonWrap;
    @FXML
    private Region playProgressFill;
    @FXML
    private Button playButton;
    @FXML
    private ToggleButton themeToggle;
    @FXML
    private Button settingsButton;
    @FXML
    private Button msAuthButton;

    private Path gameDir;
    private Path settingsFile;
    private LaunchSettings launchSettings;
    private VersionManager versionManager;
    private GameLauncher gameLauncher;
    private MicrosoftAuthManager authManager;
    private MicrosoftAuthManager.MinecraftAccount currentAccount;
    private volatile Process runningProcess;
    private DevLogWindow devLogWindow;
    private Stage mainStage;

    @FXML
    public void initialize() {
        Platform.setImplicitExit(false);

        gameDir = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", ".spicy");
        settingsFile = gameDir.resolve("launch_settings.json");
        versionManager = new VersionManager(gameDir);
        gameLauncher = new GameLauncher(gameDir);
        authManager = new MicrosoftAuthManager(gameDir);
        launchSettings = LaunchSettings.load(settingsFile);

        usernameField.setText(launchSettings.getLastUsername());
        playButton.setOnAction(e -> handlePlay());

        if (settingsButton != null) {
            settingsButton.setOnAction(e -> handleSettings());
        }

        if (msAuthButton != null) {
            msAuthButton.setOnAction(e -> handleMicrosoftAuth());
        }

        Platform.runLater(this::setupCustomTitleBar);

        versionCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DisplayVersion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.id());
                    if (item.installed()) {
                        setStyle("-fx-text-fill: rgb(255,152,0); -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        if (themeToggle != null) {
            themeToggle.setSelected(launchSettings.isDarkTheme());
            applyTheme(themeToggle.isSelected());
            themeToggle.selectedProperty().addListener((obs, oldV, isDark) -> {
                applyTheme(isDark);
                launchSettings.setDarkTheme(isDark);
                launchSettings.save(settingsFile);
            });
        }

        usernameField.textProperty().addListener((obs, oldV, newV) -> {
            if (currentAccount == null) {
                launchSettings.setLastUsername(newV);
                launchSettings.save(settingsFile);
            }
        });

        loadVersions();
        loadAccountFromCache();
    }

    private void loadAccountFromCache() {
        new Thread(() -> {
            try {
                Path cacheFile = gameDir.resolve("ms_auth_cache.json");
                if (Files.exists(cacheFile)) {
                    String json = Files.readString(cacheFile);
                    MicrosoftAuthManager.MinecraftAccount account = new Gson().fromJson(json, MicrosoftAuthManager.MinecraftAccount.class);

                    if (account != null && !account.isExpired()) {
                        currentAccount = account;
                        Platform.runLater(() -> {
                            updateAccountUI(account);
                        });
                    }
                }
            } catch (Exception e) {
                System.err.println("Cache failed to load: " + e.getMessage());
            }
        }).start();
    }

    private void handleMicrosoftAuth() {
        if (currentAccount != null && !currentAccount.isExpired()) {
            handleMicrosoftLogout();
            return;
        }

        msAuthButton.setDisable(true);


        authManager.authenticate().thenAccept(account -> Platform.runLater(() -> {
            currentAccount = account;
            updateAccountUI(account);
            msAuthButton.setDisable(false);
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                msAuthButton.setDisable(false);
            });
            return null;
        });
    }

    private void handleMicrosoftLogout() {
        currentAccount = null;
        try {
            Path cacheFile = gameDir.resolve("ms_auth_cache.json");
            Files.deleteIfExists(cacheFile);
        } catch (Exception e) {
            System.err.println("Cache cannot be deleted: " + e.getMessage());
        }
        usernameField.setDisable(false);
        usernameField.setText(launchSettings.getLastUsername());
    }

    private void updateAccountUI(MicrosoftAuthManager.MinecraftAccount account) {
        usernameField.setText(account.username());
        usernameField.setDisable(true);
    }

    @FXML
    private void handleSettings() {
        SettingsDialog dialog = new SettingsDialog(
                mainStage != null ? mainStage : (Stage) playButton.getScene().getWindow(),
                launchSettings,
                settingsFile
        );
        dialog.show();
    }

    private double xOffset = 0;
    private double yOffset = 0;

    private void setupCustomTitleBar() {
        mainStage = (Stage) playButton.getScene().getWindow();
        if (mainStage == null) return;
        StackPane root = (StackPane) playButton.getScene().getRoot();
        root.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            mainStage.setX(event.getScreenX() - xOffset);
            mainStage.setY(event.getScreenY() - yOffset);
        });
    }

    @FXML
    private void handleMinimize() {
        if (mainStage != null) {
            mainStage.setIconified(true);
        }
    }

    @FXML
    private void handleClose() {
        launchSettings.save(settingsFile);
        if (mainStage != null) {
            mainStage.close();
        }
        System.exit(0);
    }

    private void loadVersions() {
        new Thread(() -> {
            try {
                var versions = versionManager.getDisplayVersions();
                Platform.runLater(() -> {
                    versionCombo.getItems().setAll(versions);
                    String savedVersion = launchSettings.getLastVersion();
                    if (savedVersion != null) {
                        for (DisplayVersion v : versions) {
                            if (savedVersion.equals(v.id())) {
                                versionCombo.getSelectionModel().select(v);
                                return;
                            }
                        }
                    }
                    for (DisplayVersion v : versions) {
                        if ("1.21.4".equals(v.id())) {
                            versionCombo.getSelectionModel().select(v);
                            return;
                        }
                    }
                    if (!versions.isEmpty()) {
                        versionCombo.getSelectionModel().select(0);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Versions cannot be loaded: " + e.getMessage()));
            }
        }).start();

        versionCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                launchSettings.setLastVersion(newV.id());
                launchSettings.save(settingsFile);
            }
        });
    }

    private void handlePlay() {
        if (runningProcess != null && runningProcess.isAlive()) {
            boolean again = askYesNoThemed("There's already an open game",
                    "You already have a game open. Are you sure you want to open another one?");
            if (!again) return;
        }

        DisplayVersion selected = versionCombo.getValue();
        if (selected == null) {
            showError("Please select an a version!");
            return;
        }

        beginButtonProgress("Preparing...");

        new Thread(() -> {
            try {
                if (!selected.installed()) {
                    versionManager.installVersion(selected.id(), (p, stage) -> {
                        int percent = (int) Math.round(p * 100);
                        setButtonProgress(p, stage + " %" + percent);
                    });
                }

                setButtonProgress(1.0, "Starting...");

                String username;
                String uuid;
                String accessToken;

                if (currentAccount != null && !currentAccount.isExpired()) {
                    username = currentAccount.username();
                    uuid = currentAccount.uuid();
                    accessToken = currentAccount.accessToken();
                } else {
                    String user = usernameField.getText().trim();
                    if (user.isEmpty()) user = "SpicyPlayer";
                    username = user;
                    uuid = UUID.nameUUIDFromBytes(("Spicy:" + user).getBytes()).toString();
                    accessToken = "0";
                }

                Integer width = launchSettings.getWindowWidth() > 0 ? launchSettings.getWindowWidth() : null;
                Integer height = launchSettings.getWindowHeight() > 0 ? launchSettings.getWindowHeight() : null;

                LaunchContext ctx = new LaunchContext(
                        username,
                        uuid,
                        accessToken,
                        gameDir.toString(),
                        gameDir.resolve("assets").toString(),
                        getAssetIndex(selected.id()),
                        selected.id(),
                        launchSettings.getMaxMemory(),
                        width, height,
                        launchSettings.isFullscreen()
                );

                runningProcess = gameLauncher.launch(selected.id(), ctx, (progress, stage) ->
                        Platform.runLater(() -> playButton.setText(stage))
                );

                if (launchSettings.isShowDevLog()) {
                    openDevLog();
                }

                if (launchSettings.isCloseLauncherOnGameStart()) {
                    hideLauncherAndWaitForGame();
                }

            } catch (Exception e) {
                System.out.println(e.getMessage());
                Platform.runLater(() -> showError("Launch error: " + e.getMessage()));
            } finally {
                if (!launchSettings.isCloseLauncherOnGameStart()) {
                    endButtonProgress("Login");
                }
            }
        }).start();
    }

    private void hideLauncherAndWaitForGame() {
        Platform.runLater(() -> {
            if (mainStage != null) {
                mainStage.hide();
            }
        });
        new Thread(() -> {
            try {
                if (Platform.isFxApplicationThread() || !Platform.isImplicitExit()) {
                    Platform.runLater(this::showLauncherBack);
                } else {
                    showLauncherBack();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "GameProcessWatcher").start();
    }

    private void showLauncherBack() {
        try {
            if (mainStage != null) {
                mainStage.show();
                mainStage.toFront();
                mainStage.requestFocus();
            }
            endButtonProgress("Login");
            if (devLogWindow != null) {
                devLogWindow.close();
                devLogWindow = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openDevLog() {
        Platform.runLater(() -> {
            try {
                if (devLogWindow != null) {
                    devLogWindow.close();
                }
                Stage owner = mainStage != null ? mainStage : (Stage) playButton.getScene().getWindow();
                boolean isDark = launchSettings.isDarkTheme();
                devLogWindow = new DevLogWindow(owner, isDark);
                if (runningProcess != null && runningProcess.isAlive()) {
                    devLogWindow.attachToProcess(runningProcess);
                }
                devLogWindow.show();
            } catch (Exception ignored) {
            }
        });
    }

    private boolean askYesNoThemed(String title, String content) {
        AtomicBoolean result = new AtomicBoolean(false);
        Runnable show = () -> {
            Stage owner = mainStage != null ? mainStage : (Stage) playButton.getScene().getWindow();
            Stage dialog = new Stage(StageStyle.TRANSPARENT);
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
            VBox overlay = new VBox();
            overlay.setAlignment(Pos.CENTER);
            overlay.setStyle("-fx-background-color: transparent;");
            overlay.setPadding(new Insets(20));
            VBox card = new VBox(14);
            card.getStyleClass().add("dialog-card");
            card.setMaxWidth(520);
            card.setPadding(new Insets(18));
            card.setEffect(new javafx.scene.effect.DropShadow(25, Color.rgb(0, 0, 0, 0.55)));
            Label t = new Label(title);
            t.getStyleClass().add("dialog-title");
            Label c = new Label(content);
            c.getStyleClass().add("dialog-text");
            c.setWrapText(true);
            Button yes = new Button("Yes");
            yes.getStyleClass().add("dialog-yes");
            Button no = new Button("No");
            no.getStyleClass().add("dialog-no");
            HBox buttons = new HBox(12, no, yes);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            card.getChildren().addAll(t, c, new Region(), buttons);
            overlay.getChildren().add(card);
            owner.iconifiedProperty().addListener((obs, oldV, isMin) -> {
                if (isMin) dialog.close();
            });
            owner.showingProperty().addListener((obs, oldV, showing) -> {
                if (!showing) dialog.close();
            });
            overlay.setOnKeyPressed(e -> {
                switch (e.getCode()) {
                    case ESCAPE -> {
                        result.set(false);
                        dialog.close();
                    }
                    case ENTER -> {
                        result.set(true);
                        dialog.close();
                    }
                }
            });
            yes.setOnAction(e -> {
                result.set(true);
                dialog.close();
            });
            no.setOnAction(e -> {
                result.set(false);
                dialog.close();
            });
            Scene scene = new Scene(overlay);
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            dialog.setScene(scene);
            dialog.setOnShown(e -> {
                dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2.0);
                dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2.0);
                overlay.requestFocus();
            });
            dialog.showAndWait();
        };
        if (Platform.isFxApplicationThread()) show.run();
        else Platform.runLater(show);
        return result.get();
    }

    private void applyTheme(boolean dark) {
        var scene = playButton.getScene();
        if (scene == null) {
            Platform.runLater(() -> applyTheme(dark));
            return;
        }
        var root = scene.getRoot();
        root.getStyleClass().remove("light");
        if (!dark) root.getStyleClass().add("light");
        if (themeToggle != null) themeToggle.setText(dark ? "🌙" : "☀");
    }

    private void beginButtonProgress(String text) {
        Platform.runLater(() -> {
            playButton.setDisable(true);
            playButton.setText(text);
            if (playProgressFill != null) {
                playProgressFill.setVisible(true);
                playProgressFill.setManaged(true);
                playProgressFill.setPrefWidth(0);
            }
        });
    }

    private void setButtonProgress(double progress01, String text) {
        double p = Math.max(0, Math.min(1, progress01));
        Platform.runLater(() -> {
            playButton.setText(text);
            if (playProgressFill != null && playButtonWrap != null) {
                double full = playButtonWrap.getWidth();
                if (full <= 0) full = playButton.getWidth();
                if (full <= 0) full = 260;
                playProgressFill.setPrefWidth(full * p);
            }
        });
    }

    private void endButtonProgress(String text) {
        Platform.runLater(() -> {
            playButton.setDisable(false);
            playButton.setText(text);
            if (playProgressFill != null) {
                playProgressFill.setVisible(false);
                playProgressFill.setManaged(false);
                playProgressFill.setPrefWidth(0);
            }
        });
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }


    private String getAssetIndex(String versionId) {
        try {
            Path versionJsonPath = gameDir.resolve("versions").resolve(versionId).resolve(versionId + ".json");
            if (Files.exists(versionJsonPath)) {
                String json = Files.readString(versionJsonPath);
                var obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

                if (obj.has("assetIndex") && obj.getAsJsonObject("assetIndex").has("id")) {
                    return obj.getAsJsonObject("assetIndex").get("id").getAsString();
                }
                if (obj.has("inheritsFrom")) {
                    return getAssetIndex(obj.get("inheritsFrom").getAsString());
                }
            }
        } catch (Exception ignored) {
        }
        if (versionId.startsWith("fabric-loader-")) {
            versionId = versionId.substring("fabric-loader-".length());
            if (versionId.contains("-")) versionId = versionId.substring(versionId.lastIndexOf("-") + 1);
        }
        String[] p = versionId.split("\\.");
        return p.length >= 2 ? p[0] + "." + p[1] : versionId;
    }
}