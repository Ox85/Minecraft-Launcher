package spicy.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import spicy.launcher.model.LaunchSettings;

import java.io.File;
import java.nio.file.Path;

public class SettingsDialog {

    private final Stage owner;
    private final LaunchSettings settings;
    private final Path settingsFile;
    private Stage dialog;
    private CheckBox closeLauncherCheck;
    private CheckBox showDevLogCheck;
    private CheckBox fullscreenCheck;
    private Slider ramSlider;
    private Label ramLabel;
    private TextField widthField;
    private TextField heightField;
    private TextField gameDirField;

    public SettingsDialog(Stage owner, LaunchSettings settings, Path settingsFile) {
        this.owner = owner;
        this.settings = settings;
        this.settingsFile = settingsFile;
    }

    public void show() {
        dialog = new Stage(StageStyle.TRANSPARENT);
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        VBox overlay = new VBox();
        overlay.setAlignment(Pos.CENTER);
        overlay.setPadding(new Insets(20));
        boolean isLightTheme = owner.getScene().getRoot().getStyleClass().contains("light");
        if (isLightTheme) {
            overlay.getStyleClass().add("light");
        }
        VBox card = new VBox(16);
        card.getStyleClass().add("settings-card");
        card.setMaxWidth(550);
        card.setMaxHeight(600);
        card.setPadding(new Insets(24));
        card.setEffect(new javafx.scene.effect.DropShadow(30, Color.rgb(0,0,0,0.6)));
        Label title = new Label("Settings");
        title.getStyleClass().add("settings-title");
        VBox content = new VBox(18);
        content.setPadding(new Insets(10, 0, 10, 0));
        closeLauncherCheck = new CheckBox("Close the launcher when the game starts");
        closeLauncherCheck.getStyleClass().add("settings-checkbox");
        closeLauncherCheck.setSelected(settings.isCloseLauncherOnGameStart());
        showDevLogCheck = new CheckBox("When the game launches, open the developer log");
        showDevLogCheck.getStyleClass().add("settings-checkbox");
        showDevLogCheck.setSelected(settings.isShowDevLog());
        VBox ramBox = new VBox(8);
        Label ramTitle = new Label("RAM");
        ramTitle.getStyleClass().add("settings-label");
        ramSlider = new Slider(2, 16, settings.getMaxMemory());
        ramSlider.getStyleClass().add("settings-slider");
        ramSlider.setMajorTickUnit(2);
        ramSlider.setMinorTickCount(1);
        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickLabels(false);
        ramSlider.setShowTickMarks(true);
        ramLabel = new Label(settings.getMaxMemory() + " GB");
        ramLabel.getStyleClass().add("settings-value-label");
        ramSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            ramLabel.setText(newVal.intValue() + " GB");
        });
        HBox ramLabelBox = new HBox(8);
        ramLabelBox.setAlignment(Pos.CENTER_LEFT);
        ramLabelBox.getChildren().addAll(ramTitle, ramLabel);
        ramBox.getChildren().addAll(ramLabelBox, ramSlider);
        VBox windowBox = new VBox(8);
        Label windowTitle = new Label("Window");
        windowTitle.getStyleClass().add("settings-label");
        HBox sizeBox = new HBox(12);
        sizeBox.setAlignment(Pos.CENTER_LEFT);
        widthField = new TextField(String.valueOf(settings.getWindowWidth()));
        widthField.getStyleClass().add("settings-textfield");
        widthField.setPrefWidth(100);
        widthField.setPromptText("Height");
        Label xLabel = new Label("×");
        xLabel.getStyleClass().add("settings-label");
        heightField = new TextField(String.valueOf(settings.getWindowHeight()));
        heightField.getStyleClass().add("settings-textfield");
        heightField.setPrefWidth(100);
        heightField.setPromptText("Width");
        fullscreenCheck = new CheckBox("Fullscreen");
        fullscreenCheck.getStyleClass().add("settings-checkbox");
        fullscreenCheck.setSelected(settings.isFullscreen());
        sizeBox.getChildren().addAll(widthField, xLabel, heightField, fullscreenCheck);
        windowBox.getChildren().addAll(windowTitle, sizeBox);
        VBox gameDirBox = new VBox(8);
        Label gameDirTitle = new Label("Game Directory");
        gameDirTitle.getStyleClass().add("settings-label");
        HBox dirBox = new HBox(8);
        gameDirField = new TextField(settings.getGameDirectory());
        gameDirField.getStyleClass().add("settings-textfield");
        gameDirField.setPrefWidth(350);
        Button browseBtn = new Button("Browse");
        browseBtn.getStyleClass().add("settings-button");
        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Game Directory");
            File current = new File(gameDirField.getText());
            if (current.exists()) {
                chooser.setInitialDirectory(current);
            }
            File selected = chooser.showDialog(dialog);
            if (selected != null) {
                gameDirField.setText(selected.getAbsolutePath());
            }
        });
        Button resetBtn = new Button("Default");
        resetBtn.getStyleClass().add("settings-button");
        resetBtn.setOnAction(e -> {
            String defaultDir = System.getProperty("user.home") + "\\AppData\\Roaming\\.spicy";
            gameDirField.setText(defaultDir);
        });
        dirBox.getChildren().addAll(gameDirField, browseBtn, resetBtn);
        gameDirBox.getChildren().addAll(gameDirTitle, dirBox);
        content.getChildren().addAll(
                closeLauncherCheck,
                showDevLogCheck,
                new Separator(),
                ramBox,
                new Separator(),
                windowBox,
                new Separator(),
                gameDirBox
        );
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("settings-scroll");
        scrollPane.setPrefHeight(400);
        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("dialog-no");
        cancelBtn.setOnAction(e -> dialog.close());
        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("dialog-yes");
        saveBtn.setOnAction(e -> {
            saveSettings();
            dialog.close();
        });
        buttons.getChildren().addAll(cancelBtn, saveBtn);
        card.getChildren().addAll(title, scrollPane, buttons);
        overlay.getChildren().add(card);
        overlay.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ESCAPE -> dialog.close();
                case ENTER -> {
                    saveSettings();
                    dialog.close();
                }
            }
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
        owner.iconifiedProperty().addListener((obs, oldV, isMin) -> {
            if (isMin) dialog.close();
        });
        owner.showingProperty().addListener((obs, oldV, showing) -> {
            if (!showing) dialog.close();
        });
        dialog.show();
    }

    private void saveSettings() {
        settings.setCloseLauncherOnGameStart(closeLauncherCheck.isSelected());
        settings.setShowDevLog(showDevLogCheck.isSelected());
        settings.setMaxMemory((int) ramSlider.getValue());
        settings.setFullscreen(fullscreenCheck.isSelected());
        try {
            int width = Integer.parseInt(widthField.getText());
            int height = Integer.parseInt(heightField.getText());
            settings.setWindowWidth(width);
            settings.setWindowHeight(height);
        } catch (NumberFormatException ignored) {}
        settings.setGameDirectory(gameDirField.getText());
        settings.save(settingsFile);
    }
}