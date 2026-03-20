package spicy.launcher.core;

import spicy.launcher.model.Library;
import spicy.launcher.model.ProgressListener;
import spicy.launcher.model.VersionProfile;
import spicy.launcher.utils.FileUtil;
import spicy.launcher.utils.SystemUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LibraryManager {
    private final Path librariesDir;
    private final DownloadManager downloadManager;

    public LibraryManager(Path librariesDir) {
        this.librariesDir = librariesDir;
        this.downloadManager = new DownloadManager();

        try {
            Files.createDirectories(librariesDir);
        } catch (IOException e) {
            throw new RuntimeException("Libraries directory cannot be created ", e);
        }
    }

    public Set<String> resolveLibraries(VersionProfile profile, ProgressListener listener) throws Exception {
        Set<String> classpaths = new LinkedHashSet<>();
        Map<String, String> versionMap = new HashMap<>();
        record LibTask(String name, Path path, String url, String sha1, long sizeHint) {}
        List<LibTask> tasks = new ArrayList<>();

        for (Library lib : profile.libraries) {
            if (lib.rules != null && !checkRules(lib.rules)) continue;
            if (hasNativeClassifier(lib)) continue;

            String mavenPath;
            String downloadUrl;
            String sha1 = null;

            Library.Artifact artifact = (lib.downloads != null ? lib.downloads.artifact : null);
            if (artifact != null) {
                mavenPath = artifact.path;
                downloadUrl = artifact.url;
                sha1 = artifact.sha1;
            }
            else if (lib.url != null && !lib.url.isEmpty()) {
                mavenPath = resolveMavenPath(lib.name);
                downloadUrl = lib.url + mavenPath;
            }
            else {
                continue;
            }

            String[] gav = lib.name.split(":");
            String groupArtifact = gav[0] + ":" + gav[1];
            String version = gav[2];

            if (versionMap.containsKey(groupArtifact)) {
                String existing = versionMap.get(groupArtifact);
                if (compareVersions(version, existing) <= 0) continue;
                String oldPath = resolveMavenPath(groupArtifact + ":" + existing);
                classpaths.remove(librariesDir.resolve(oldPath).toString());
            }
            versionMap.put(groupArtifact, version);

            Path libPath = librariesDir.resolve(mavenPath);

            boolean needsDownload = !Files.exists(libPath);
            if (!needsDownload && sha1 != null && !sha1.isEmpty()) {
                needsDownload = !FileUtil.verifySha1(libPath.toFile(), sha1);
            }

            if (!needsDownload) {
                classpaths.add(libPath.toAbsolutePath().toString());
                continue;
            }

            Files.createDirectories(libPath.getParent());
            long sizeHint = artifact != null ? extractSizeHint(artifact) : -1;
            if (sizeHint <= 0 && downloadUrl != null) {
                sizeHint = downloadManager.getRemoteSize(downloadUrl);
            }
            if (sizeHint <= 0) sizeHint = 1;

            tasks.add(new LibTask(lib.name, libPath, downloadUrl, sha1, sizeHint));
        }

        long totalBytes = tasks.stream().mapToLong(LibTask::sizeHint).sum();
        if (totalBytes <= 0) totalBytes = 1;
        long doneBytes = 0;

        if (listener != null) listener.onProgress(0.0, "Libraries Downloading");

        for (LibTask t : tasks) {
            long fileSize = t.sizeHint();
            long before = doneBytes;
            long finalTotalBytes = totalBytes;
            ProgressListener perFile = (p, stage) -> {
                long now = before + (long) (fileSize * p);
                double overall = now / (double) finalTotalBytes;
                if (listener != null) {
                    listener.onProgress(Math.min(1.0, overall), "Libraries Downloading");
                }
            };

            downloadManager.downloadFile(t.url(), t.path(), perFile, "Libraries Downloading");

            if (t.sha1() != null && !t.sha1().isEmpty()) {
                if (!FileUtil.verifySha1(t.path().toFile(), t.sha1())) {
                    throw new IOException("Library SHA1 verification failed: " + t.name());
                }
            }

            doneBytes += fileSize;
            classpaths.add(t.path().toAbsolutePath().toString());
        }

        if (listener != null) listener.onProgress(1.0, "Libraries is ready!");
        return classpaths;
    }

    private long extractSizeHint(Library.Artifact artifact) {
        try {
            var f = artifact.getClass().getDeclaredField("size");
            f.setAccessible(true);
            Object v = f.get(artifact);
            if (v instanceof Number n) return n.longValue();
        } catch (Exception ignored) {}
        return -1;
    }


    public Path extractNatives(VersionProfile profile, ProgressListener listener, Path targetNativesDir) throws Exception {
        Files.createDirectories(targetNativesDir);
        String osName = SystemUtil.getOsName();
        String arch = SystemUtil.getArch();
        if (listener != null) listener.onProgress(0.0, "Natives preparing");
        record NativeTask(String libName, Library.Artifact artifact, List<String> excludes) {}
        List<NativeTask> tasks = new ArrayList<>();
        for (Library lib : profile.libraries) {
            if (lib.rules != null && !checkRules(lib.rules)) continue;
            if (!hasNativeClassifier(lib)) continue;
            String nativeClassifier = getNativeClassifier(lib, osName, arch);
            if (nativeClassifier == null) continue;
            Library.Artifact nativeArtifact = null;
            if (lib.name.contains(":natives-") && lib.downloads != null && lib.downloads.artifact != null) {
                nativeArtifact = lib.downloads.artifact;
            }
            else if (lib.downloads != null && lib.downloads.classifiers != null) {
                nativeArtifact = lib.downloads.classifiers.get(nativeClassifier);
            }
            if (nativeArtifact == null) continue;
            List<String> excludes = (lib.extract != null ? lib.extract.exclude : null);
            tasks.add(new NativeTask(lib.name, nativeArtifact, excludes));
        }
        int total = tasks.size();
        int done = 0;
        for (NativeTask t : tasks) {
            done++;
            Path nativeJar = librariesDir.resolve(t.artifact().path);
            Files.createDirectories(nativeJar.getParent());
            if (!Files.exists(nativeJar)) {
                int finalDone = done;
                ProgressListener perFile = (p, stage) -> {
                    if (listener == null) return;
                    double overall = ((finalDone - 1) / (double) total) + (p / (double) total);
                    listener.onProgress(Math.min(1.0, overall), "Natives Downloading");
                };
                downloadManager.downloadFile(t.artifact().url, nativeJar, perFile, "Natives Downloading");
            }
            if (listener != null) {
                double overall = done / (double) Math.max(1, total);
                int percent = (int) Math.round(overall * 100);
                listener.onProgress(Math.min(1.0, overall), "Natives is being processed %" + percent);
            }
            FileUtil.extractZip(nativeJar, targetNativesDir, t.excludes());
        }
        if (listener != null) listener.onProgress(1.0, "Natives is ready!");
        return targetNativesDir;
    }

    private boolean hasNativeClassifier(Library lib) {
        if (lib.name.contains(":natives-")) return true;
        if (lib.natives != null) return true;
        if (lib.downloads != null && lib.downloads.classifiers != null) {
            for (String key : lib.downloads.classifiers.keySet()) {
                if (key.startsWith("natives-")) return true;
            }
        }
        return false;
    }

    private String getNativeClassifier(Library lib, String osName, String arch) {
        if (lib.name.contains(":natives-")) {
            String[] parts = lib.name.split(":");
            if (parts.length >= 4) {
                String classifier = parts[3];
                if (classifier.contains(osName)) return classifier;
            }
            return null;
        }
        if (lib.natives != null) {
            String classifier = null;
            if (osName.equals("windows") && lib.natives.windows != null) classifier = lib.natives.windows;
            else if (osName.equals("linux") && lib.natives.linux != null) classifier = lib.natives.linux;
            else if (osName.equals("osx") && lib.natives.osx != null) classifier = lib.natives.osx;
            if (classifier != null) return classifier.replace("${arch}", arch);
        }
        if (lib.downloads != null && lib.downloads.classifiers != null) {
            String osPrefix = "natives-" + osName;
            if (lib.downloads.classifiers.containsKey(osPrefix)) return osPrefix;
            String archSuffix = "-" + (arch.equals("64") ? "x86_64" : "x86");
            String fullClassifier = osPrefix + archSuffix;
            if (lib.downloads.classifiers.containsKey(fullClassifier)) return fullClassifier;
            if (osName.equals("osx") && lib.downloads.classifiers.containsKey("natives-macos")) {
                return "natives-macos";
            }
        }
        return null;
    }

    private boolean checkRules(List<Library.Rule> rules) {
        String osName = SystemUtil.getOsName();
        boolean allowed = false;
        for (Library.Rule rule : rules) {
            if (rule.os != null) {
                boolean matches = rule.os.name != null && rule.os.name.equals(osName);

                if (matches && "allow".equals(rule.action)) allowed = true;
                else if (matches && "disallow".equals(rule.action)) allowed = false;
            } else {
                if ("allow".equals(rule.action)) allowed = true;
                else if ("disallow".equals(rule.action)) allowed = false;
            }
        }

        return allowed;
    }

    private String resolveMavenPath(String gav) {
        String[] parts = gav.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? parts[3] : null;

        String filename = artifact + "-" + version;
        if (classifier != null && !classifier.isEmpty()) {
            filename += "-" + classifier;
        }
        filename += ".jar";

        return group + "/" + artifact + "/" + version + "/" + filename;
    }

    private int compareVersions(String v1, String v2) {
        String[] p1 = v1.split("\\.");
        String[] p2 = v2.split("\\.");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length ? parseVersionPart(p1[i]) : 0;
            int n2 = i < p2.length ? parseVersionPart(p2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}