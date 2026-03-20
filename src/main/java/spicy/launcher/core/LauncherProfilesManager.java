package spicy.launcher.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class LauncherProfilesManager {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final Path gameDir;
    private final Path profilesFile;
    
    public LauncherProfilesManager(Path gameDir) {
        this.gameDir = gameDir;
        this.profilesFile = gameDir.resolve("launcher_profiles.json");
    }
    

    public void ensureProfilesFile() throws IOException {
        if (Files.exists(profilesFile)) {
            return;
        }
        JsonObject root = new JsonObject();
        JsonObject profiles = new JsonObject();
        JsonObject defaultProfile = new JsonObject();
        defaultProfile.addProperty("name", "Spicy");
        defaultProfile.addProperty("type", "custom");
        defaultProfile.addProperty("created", System.currentTimeMillis() + "");
        defaultProfile.addProperty("lastUsed", System.currentTimeMillis() + "");
        defaultProfile.addProperty("icon", "Furnace");
        defaultProfile.addProperty("lastVersionId", "latest-release");
        defaultProfile.addProperty("gameDir", gameDir.toAbsolutePath().toString());
        profiles.add(UUID.randomUUID().toString(), defaultProfile);
        root.add("profiles", profiles);
        JsonObject settings = new JsonObject();
        settings.addProperty("enableSnapshots", false);
        settings.addProperty("enableAdvanced", false);
        settings.addProperty("keepLauncherOpen", false);
        settings.addProperty("showMenu", false);
        settings.addProperty("enableHistorical", false);
        root.add("settings", settings);
        root.addProperty("version", 3);
        root.addProperty("clientToken", UUID.randomUUID().toString());
        JsonObject analyticsToken = new JsonObject();
        analyticsToken.addProperty("token", UUID.randomUUID().toString());
        root.add("analyticsToken", analyticsToken);
        String json = gson.toJson(root);
        Files.writeString(profilesFile, json, StandardCharsets.UTF_8);
    }

    public void addOrUpdateProfile(String name, String versionId) throws IOException {
        ensureProfilesFile();
        String json = Files.readString(profilesFile, StandardCharsets.UTF_8);
        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonObject profiles = root.getAsJsonObject("profiles");
        if (profiles == null) {
            profiles = new JsonObject();
            root.add("profiles", profiles);
        }
        String profileId = findProfileIdByName(profiles, name);
        if (profileId == null) {
            profileId = UUID.randomUUID().toString();
        }
        JsonObject profile = new JsonObject();
        profile.addProperty("name", name);
        profile.addProperty("type", "custom");
        profile.addProperty("created", System.currentTimeMillis() + "");
        profile.addProperty("lastUsed", System.currentTimeMillis() + "");
        profile.addProperty("icon", "Furnace");
        profile.addProperty("lastVersionId", versionId);
        profile.addProperty("gameDir", gameDir.toAbsolutePath().toString());
        profiles.add(profileId, profile);
        String updatedJson = gson.toJson(root);
        Files.writeString(profilesFile, updatedJson, StandardCharsets.UTF_8);
    }
    
    private String findProfileIdByName(JsonObject profiles, String name) {
        for (String key : profiles.keySet()) {
            JsonObject profile = profiles.getAsJsonObject(key);
            if (profile.has("name") && profile.get("name").getAsString().equals(name)) {
                return key;
            }
        }
        return null;
    }
}