package spicy.launcher.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import spicy.launcher.model.*;
import spicy.launcher.utils.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class VersionManager {
    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path gameDir;
    private final Path versionsDir;
    private final DownloadManager downloadManager;
    private final LibraryManager libraryManager;
    private final AssetManager assetManager;
    private final LauncherProfilesManager profilesManager;
    private VersionManifest manifest;

    public VersionManager(Path gameDir) {
        this.gameDir = gameDir;
        this.versionsDir = gameDir.resolve("versions");
        this.downloadManager = new DownloadManager();
        this.libraryManager = new LibraryManager(gameDir.resolve("libraries"));
        this.assetManager = new AssetManager(gameDir.resolve("assets"));
        this.profilesManager = new LauncherProfilesManager(gameDir);
        try {
            Files.createDirectories(versionsDir);
            profilesManager.ensureProfilesFile();
        } catch (IOException e) {
            throw new RuntimeException("Versions directory cannot be created", e);
        }
    }

    public VersionManifest getManifest() throws Exception {
        if (manifest != null) {
            return manifest;
        }
        Path manifestCache = gameDir.resolve("version_manifest.json");
        if (Files.exists(manifestCache)) {
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(manifestCache).toMillis();
            if (age < 3600_000) {
                String json = Files.readString(manifestCache, StandardCharsets.UTF_8);
                manifest = gson.fromJson(json, VersionManifest.class);
                return manifest;
            }
        }
        String json = downloadManager.downloadString(MANIFEST_URL);
        Files.writeString(manifestCache, json, StandardCharsets.UTF_8);
        manifest = gson.fromJson(json, VersionManifest.class);
        return manifest;
    }

    public List<String> getInstalledVersions() {
        List<String> installed = new ArrayList<>();
        File[] versionDirs = versionsDir.toFile().listFiles(File::isDirectory);
        if (versionDirs == null) {
            return installed;
        }
        for (File dir : versionDirs) {
            String versionId = dir.getName();
            Path jsonFile = dir.toPath().resolve(versionId + ".json");
            if (Files.exists(jsonFile)) {
                installed.add(versionId);
            }
        }

        return installed;
    }

    public List<DisplayVersion> getDisplayVersions() throws Exception {
        Set<String> installed = new HashSet<>(getInstalledVersions());
        List<DisplayVersion> result = new ArrayList<>();
        for (String id : installed) {
            boolean isFabric = id.startsWith("fabric-loader-");
            String type = isFabric ? "modded" : "release";
            result.add(new DisplayVersion(id, type, true, isFabric));
        }
        VersionManifest manifest = getManifest();
        for (VersionInfo info : manifest.versions) {
            if (!"release".equals(info.type)) continue;
            if (!installed.contains(info.id)) {
                result.add(new DisplayVersion(info.id, "release", false, false));
            }
        }
        result.sort((a, b) -> {
            if (a.installed() != b.installed()) {
                return a.installed() ? -1 : 1;
            }
            return compareVersions(b.id(), a.id());
        });

        return result;
    }

    public VersionProfile loadProfile(String versionId) throws Exception {
        Path jsonFile = versionsDir.resolve(versionId).resolve(versionId + ".json");
        if (!Files.exists(jsonFile)) {
            throw new IOException("Unknown version profile: " + versionId + ".");
        }
        String json = Files.readString(jsonFile, StandardCharsets.UTF_8);
        VersionProfile profile = gson.fromJson(json, VersionProfile.class);
        if (profile.inheritsFrom != null && !profile.inheritsFrom.isEmpty()) {
            VersionProfile parentProfile = loadProfile(profile.inheritsFrom);
            profile = mergeProfiles(profile, parentProfile);
        }
        return profile;
    }

    private VersionProfile mergeProfiles(VersionProfile child, VersionProfile parent) {
        VersionProfile merged = new VersionProfile();
        merged.id = child.id;
        merged.mainClass = child.mainClass != null ? child.mainClass : parent.mainClass;
        merged.minecraftArguments = child.minecraftArguments != null ? child.minecraftArguments : parent.minecraftArguments;
        merged.type = child.type != null ? child.type : parent.type;
        merged.inheritsFrom = null;
        merged.downloads = parent.downloads;
        merged.assetIndex = parent.assetIndex;
        merged.libraries = new ArrayList<>();
        if (parent.libraries != null) {
            merged.libraries.addAll(parent.libraries);
        }
        if (child.libraries != null) {
            merged.libraries.addAll(child.libraries);
        }
        return merged;
    }

    public void installVersion(String versionId, ProgressListener listener) throws Exception {
        if (listener != null) listener.onProgress(0.0, "Downloading version information");
        VersionManifest manifest = getManifest();
        VersionInfo versionInfo = manifest.versions.stream().filter(v -> v.id.equals(versionId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown version: " + versionId + "."));
        Path versionDir = versionsDir.resolve(versionId);
        Files.createDirectories(versionDir);
        Path jsonFile = versionDir.resolve(versionId + ".json");
        ProgressListener jsonL = (p, stage) -> {
            double overall = 0.00 + p * 0.05;
            if (listener != null) listener.onProgress(overall, "Downloading version information");
        };
        downloadManager.downloadFile(versionInfo.url, jsonFile, jsonL, "Downloading version information");
        String jsonContent = Files.readString(jsonFile, StandardCharsets.UTF_8);
        VersionProfile profile = gson.fromJson(jsonContent, VersionProfile.class);
        Path jarFile = versionDir.resolve(versionId + ".jar");
        ProgressListener jarL = (p, stage) -> {
            double overall = 0.05 + p * 0.30;
            if (listener != null) {
                listener.onProgress(Math.min(1.0, overall), "Downloading version");
            }
        };
        downloadManager.downloadFile(profile.downloads.client.url, jarFile, jarL, "Downloading version");
        if (!FileUtil.verifySha1(jarFile.toFile(), profile.downloads.client.sha1)) {
            throw new IOException("Client JAR SHA1 verification failed.");
        }
        ProgressListener libsL = (p, stage) -> {
            double overall = 0.35 + p * 0.30;
            if (listener != null) {
                listener.onProgress(Math.min(1.0, overall), "Libraries");
            }
        };
        libraryManager.resolveLibraries(profile, libsL);
        ProgressListener assetsL = (p, stage) -> {
            double overall = 0.65 + p * 0.35;
            if (listener != null) {
                listener.onProgress(Math.min(1.0, overall), "Assets");
            }
        };
        assetManager.downloadAssets(profile, assetsL);
        profilesManager.addOrUpdateProfile(versionId, versionId);
        if (listener != null) listener.onProgress(1.0, "Installation complete!");
    }

    private int compareVersions(String v1, String v2) {
        String[] p1 = v1.replaceAll("[^0-9.]", "").split("\\.");
        String[] p2 = v2.replaceAll("[^0-9.]", "").split("\\.");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length ? Integer.parseInt(p1[i]) : 0;
            int n2 = i < p2.length ? Integer.parseInt(p2[i]) : 0;
            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }
        return 0;
    }
}