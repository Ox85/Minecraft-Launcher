package spicy.launcher.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LaunchSettings {
    private String lastUsername = "Player";
    private String lastVersion = "";
    private boolean darkTheme = true;
    private boolean closeLauncherOnGameStart = false;
    private boolean showDevLog = false;
    private int maxMemory = 4;
    private int windowWidth = 854;
    private int windowHeight = 480;
    private boolean fullscreen = false;
    private String gameDirectory = "";

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static LaunchSettings load(Path file) {
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                LaunchSettings settings = gson.fromJson(json, LaunchSettings.class);

                if (settings.gameDirectory == null || settings.gameDirectory.isEmpty()) {
                    settings.gameDirectory = System.getProperty("user.home") + "\\AppData\\Roaming\\.spicy";
                }

                return settings;
            } catch (Exception e) {
                return new LaunchSettings();
            }
        }
        LaunchSettings settings = new LaunchSettings();
        settings.gameDirectory = System.getProperty("user.home") + "\\AppData\\Roaming\\.spicy";
        return settings;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            String json = gson.toJson(this);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getLastUsername() { return lastUsername; }
    public String getLastVersion() { return lastVersion; }
    public boolean isDarkTheme() { return darkTheme; }
    public boolean isCloseLauncherOnGameStart() { return closeLauncherOnGameStart; }
    public boolean isShowDevLog() { return showDevLog; }
    public int getMaxMemory() { return maxMemory; }
    public int getWindowWidth() { return windowWidth; }
    public int getWindowHeight() { return windowHeight; }
    public boolean isFullscreen() { return fullscreen; }
    public String getGameDirectory() { return gameDirectory; }
    public void setLastUsername(String lastUsername) { this.lastUsername = lastUsername; }
    public void setLastVersion(String lastVersion) { this.lastVersion = lastVersion; }
    public void setDarkTheme(boolean darkTheme) { this.darkTheme = darkTheme; }
    public void setCloseLauncherOnGameStart(boolean close) { this.closeLauncherOnGameStart = close; }
    public void setShowDevLog(boolean show) { this.showDevLog = show; }
    public void setMaxMemory(int maxMemory) { this.maxMemory = maxMemory; }
    public void setWindowWidth(int width) { this.windowWidth = width; }
    public void setWindowHeight(int height) { this.windowHeight = height; }
    public void setFullscreen(boolean fullscreen) { this.fullscreen = fullscreen; }
    public void setGameDirectory(String dir) { this.gameDirectory = dir; }
}