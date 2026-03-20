package spicy.launcher.core;

import com.google.gson.Gson;
import spicy.launcher.model.AssetIndex;
import spicy.launcher.model.ProgressListener;
import spicy.launcher.model.VersionProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class AssetManager {
    private static final Gson gson = new Gson();
    private static final String RESOURCES_BASE = "https://resources.download.minecraft.net/";
    
    private final Path assetsDir;
    private final DownloadManager downloadManager;
    
    public AssetManager(Path assetsDir) {
        this.assetsDir = assetsDir;
        this.downloadManager = new DownloadManager();
        try {
            Files.createDirectories(assetsDir);
        } catch (IOException e) {
            throw new RuntimeException("Assets directory cannot be created", e);
        }
    }
    

    public void downloadAssets(VersionProfile profile, ProgressListener listener) throws Exception {
        if (profile.assetIndex == null) {
            if (listener != null) listener.onProgress(1.0, "Asset index is null");
            return;
        }
        String indexId = profile.assetIndex.id;
        if (listener != null) listener.onProgress(0.0, "Asset index downloading");
        Path indexesDir = assetsDir.resolve("indexes");
        Files.createDirectories(indexesDir);
        Path indexFile = indexesDir.resolve(indexId + ".json");
        if (!Files.exists(indexFile)) {
            downloadManager.downloadFile(profile.assetIndex.url, indexFile, listener, "Asset index downloading");
        }
        String indexJson = Files.readString(indexFile, StandardCharsets.UTF_8);
        AssetIndex assetIndex = gson.fromJson(indexJson, AssetIndex.class);
        Path objectsDir = assetsDir.resolve("objects");
        Files.createDirectories(objectsDir);
        long totalBytes = 0;
        for (AssetIndex.AssetObject obj : assetIndex.objects.values()) {
            totalBytes += obj.size;
        }
        if (totalBytes <= 0) totalBytes = 1;
        long downloadedBytes = 0;
        int current = 0;
        if (listener != null) listener.onProgress(0.0, "Assets downloading");
        for (Map.Entry<String, AssetIndex.AssetObject> entry : assetIndex.objects.entrySet()) {
            current++;
            AssetIndex.AssetObject obj = entry.getValue();
            String hash = obj.hash;
            String hashPrefix = hash.substring(0, 2);
            Path hashDir = objectsDir.resolve(hashPrefix);
            Files.createDirectories(hashDir);
            Path assetFile = hashDir.resolve(hash);
            boolean needsDownload = !Files.exists(assetFile) || Files.size(assetFile) != obj.size;
            if (needsDownload) {
                String url = RESOURCES_BASE + hashPrefix + "/" + hash;
                long fileSize = obj.size;
                long before = downloadedBytes;
                long finalTotalBytes = totalBytes;
                ProgressListener perFile = (p, stage) -> {
                    long now = before + (long) (fileSize * p);
                    double overall = now / (double) finalTotalBytes;
                    if (listener != null) {
                        listener.onProgress(Math.min(1.0, overall), "Libraries downloading");
                    }
                };
                downloadManager.downloadFile(url, assetFile, perFile, "Assets downloading");
                downloadedBytes += fileSize;
            } else {
                downloadedBytes += obj.size;
                double overall = downloadedBytes / (double) totalBytes;
                if (listener != null) {
                    listener.onProgress(Math.min(1.0, overall), "Assets downloading");
                }
            }
            if (current % 200 == 0 && listener != null) {
                double overall = downloadedBytes / (double) totalBytes;
                listener.onProgress(Math.min(1.0, overall), "Assets downloading");
            }
        }
        if (listener != null) listener.onProgress(1.0, "Assets downloaded!");
        setupVirtualAssets(indexId, assetIndex);
    }


    private void setupVirtualAssets(String indexId, AssetIndex assetIndex) throws Exception {
        boolean isVirtual = assetIndex.virtual;
        boolean mapToResources = assetIndex.mapToResources;
        if (!isVirtual && !mapToResources) {
            return;
        }
        Path objectsDir = assetsDir.resolve("objects");
        if (isVirtual) {
            Path virtualDir = assetsDir.resolve("virtual").resolve(indexId);
            Files.createDirectories(virtualDir);
            for (Map.Entry<String, AssetIndex.AssetObject> entry : assetIndex.objects.entrySet()) {
                String assetPath = entry.getKey();
                String hash = entry.getValue().hash;
                String hashPrefix = hash.substring(0, 2);
                Path sourceFile = objectsDir.resolve(hashPrefix).resolve(hash);
                Path targetFile = virtualDir.resolve(assetPath);
                if (Files.exists(sourceFile) && !Files.exists(targetFile)) {
                    Files.createDirectories(targetFile.getParent());
                    Files.copy(sourceFile, targetFile);
                }
            }
        }
        if (mapToResources) {
            Path resourcesDir = assetsDir.getParent().resolve("resources");
            Files.createDirectories(resourcesDir);
            for (Map.Entry<String, AssetIndex.AssetObject> entry : assetIndex.objects.entrySet()) {
                String assetPath = entry.getKey();
                String hash = entry.getValue().hash;
                String hashPrefix = hash.substring(0, 2);
                Path sourceFile = objectsDir.resolve(hashPrefix).resolve(hash);
                Path targetFile = resourcesDir.resolve(assetPath);
                if (Files.exists(sourceFile) && !Files.exists(targetFile)) {
                    Files.createDirectories(targetFile.getParent());
                    Files.copy(sourceFile, targetFile);
                }
            }
        }
    }
}