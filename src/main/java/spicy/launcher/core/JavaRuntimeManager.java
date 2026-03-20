package spicy.launcher.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import spicy.launcher.model.ProgressListener;
import spicy.launcher.utils.SystemUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class JavaRuntimeManager {
    private static final String JAVA_MANIFEST_URL = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private static final Gson gson = new Gson();

    private final Path javaDir;
    private final DownloadManager downloadManager;

    public JavaRuntimeManager(Path gameDir) {
        this.javaDir = gameDir.resolve("runtime");
        this.downloadManager = new DownloadManager();
        try {
            Files.createDirectories(javaDir);
        } catch (IOException e) {
            throw new RuntimeException("Java runtime directory cannot be created", e);
        }
    }


    public String getOrDownloadJavaRuntime(String minecraftVersion, ProgressListener listener) throws Exception {
        int requiredJavaVersion = getRequiredJavaVersion(minecraftVersion);
        String runtimeName = "java-runtime-" + (requiredJavaVersion >= 21 ? "delta" :
                requiredJavaVersion >= 17 ? "gamma" : "alpha");

        Path runtimePath = javaDir.resolve(runtimeName);
        Path javaExecutable = findJavaInRuntime(runtimePath);

        if (javaExecutable != null && Files.exists(javaExecutable)) {
            if (listener != null) listener.onProgress(1.0, "Java runtime is ready");
            return javaExecutable.toString();
        }

        if (listener != null) listener.onProgress(0.0, "Java runtime is downloading");
        downloadJavaRuntime(runtimeName, runtimePath, listener);

        javaExecutable = findJavaInRuntime(runtimePath);
        if (javaExecutable == null || !Files.exists(javaExecutable)) {
            throw new IOException("Java runtime failed to download");
        }

        return javaExecutable.toString();
    }

    private int getRequiredJavaVersion(String minecraftVersion) {
        try {
            String[] parts = minecraftVersion.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

            if (major > 1 || (major == 1 && minor >= 20)) {
                if (major == 1 && minor == 20) {
                    return 21;
                }
                return 21;
            }

            if (major == 1 && minor >= 18) {
                return 17;
            }

            return 8;
        } catch (Exception e) {
            return 21;
        }
    }

    private void downloadJavaRuntime(String runtimeName, Path targetDir, ProgressListener listener) throws Exception {
        Files.createDirectories(targetDir);

        if (listener != null) listener.onProgress(0.05, "Fetching Java Runtime");
        String manifestJson = downloadManager.downloadString(JAVA_MANIFEST_URL);
        JsonObject manifest = gson.fromJson(manifestJson, JsonObject.class);

        String osName = SystemUtil.getOsName();
        String osKey = switch(osName) {
            case "linux" -> "linux";
            case "osx" -> "mac-os";
            default -> "windows-x64";
        };

        if (!manifest.has(osKey)) {
            throw new IOException("No java version found for this OS: " + osKey);
        }

        JsonObject osData = manifest.getAsJsonObject(osKey);

        if (!osData.has(runtimeName)) {
            if (osData.has("java-runtime-delta")) {
                runtimeName = "java-runtime-delta";
            } else if (osData.has("java-runtime-gamma")) {
                runtimeName = "java-runtime-gamma";
            } else if (osData.has("java-runtime-alpha")) {
                runtimeName = "java-runtime-alpha";
            } else {
                throw new IOException("Java runtime bulunamadı");
            }
        }

        var runtimeElement = osData.get(runtimeName);
        if (runtimeElement == null || !runtimeElement.isJsonArray()) {
            throw new IOException("Runtime information is not found: " + runtimeName);
        }

        var runtimeArray = runtimeElement.getAsJsonArray();
        if (runtimeArray.isEmpty()) {
            throw new IOException("Runtime array is empty");
        }

        JsonObject runtimeInfo = runtimeArray.get(0).getAsJsonObject();
        String manifestUrl = runtimeInfo.getAsJsonObject("manifest").get("url").getAsString();

        if (listener != null) listener.onProgress(0.10, "Retrieving the Java file list");
        String runtimeManifestJson = downloadManager.downloadString(manifestUrl);
        JsonObject runtimeManifest = gson.fromJson(runtimeManifestJson, JsonObject.class);
        JsonObject files = runtimeManifest.getAsJsonObject("files");

        int totalFiles = files.size();
        int currentFile = 0;

        for (Map.Entry<String, com.google.gson.JsonElement> entry : files.entrySet()) {
            String filePath = entry.getKey();
            JsonObject fileInfo = entry.getValue().getAsJsonObject();
            String type = fileInfo.get("type").getAsString();

            Path targetFile = targetDir.resolve(filePath);

            if ("directory".equals(type)) {
                Files.createDirectories(targetFile);
            } else if ("file".equals(type)) {
                Files.createDirectories(targetFile.getParent());

                JsonObject downloads = fileInfo.getAsJsonObject("downloads");
                JsonObject raw = downloads.getAsJsonObject("raw");
                String url = raw.get("url").getAsString();

                if (!Files.exists(targetFile)) {
                    currentFile++;
                    int finalCurrentFile = currentFile;
                    ProgressListener fileListener = (p, stage) -> {
                        double overall = 0.10 + (0.85 * (finalCurrentFile - 1 + p) / totalFiles);
                        if (listener != null) {
                            listener.onProgress(Math.min(0.95, overall), "Java downloading (" + finalCurrentFile + "/" + totalFiles + ")");
                        }
                    };

                    downloadManager.downloadFile(url, targetFile, fileListener, "Java downloading");

                    if (fileInfo.has("executable") && fileInfo.get("executable").getAsBoolean()) {
                        try {
                            targetFile.toFile().setExecutable(true, false);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        if (listener != null) listener.onProgress(1.0, "Java runtime is ready!");
    }

    private Path findJavaInRuntime(Path runtimePath) {
        if (!Files.exists(runtimePath)) {
            return null;
        }

        String osName = SystemUtil.getOsName();

        if ("windows".equals(osName)) {
            Path javaExe = runtimePath.resolve("bin").resolve("java.exe");
            if (Files.exists(javaExe)) return javaExe;

            javaExe = runtimePath.resolve("java-runtime-delta").resolve("windows-x64").resolve("java-runtime-delta").resolve("bin").resolve("java.exe");
            if (Files.exists(javaExe)) return javaExe;
        }

        if ("linux".equals(osName)) {
            Path javaExe = runtimePath.resolve("bin").resolve("java");
            if (Files.exists(javaExe)) return javaExe;
        }

        if ("osx".equals(osName)) {
            Path javaExe = runtimePath.resolve("jre.bundle").resolve("Contents").resolve("Home").resolve("bin").resolve("java");
            if (Files.exists(javaExe)) return javaExe;

            javaExe = runtimePath.resolve("bin").resolve("java");
            if (Files.exists(javaExe)) return javaExe;
        }

        return null;
    }
}