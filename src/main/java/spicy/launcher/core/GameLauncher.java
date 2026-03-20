package spicy.launcher.core;

import spicy.launcher.model.LaunchContext;
import spicy.launcher.model.ProgressListener;
import spicy.launcher.model.VersionProfile;
import spicy.launcher.utils.SystemUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GameLauncher {
    private final Path gameDir;
    private final VersionManager versionManager;
    private final LibraryManager libraryManager;
    private final JavaRuntimeManager javaRuntimeManager;

    public GameLauncher(Path gameDir) {
        this.gameDir = gameDir;
        this.versionManager = new VersionManager(gameDir);
        this.libraryManager = new LibraryManager(gameDir.resolve("libraries"));
        this.javaRuntimeManager = new JavaRuntimeManager(gameDir);
    }


    public Process launch(String versionId, LaunchContext context, ProgressListener progressListener) throws Exception {
        ProgressListener ui = (p, stage) -> {
            if (progressListener != null) progressListener.onProgress(p, stage);
        };
        ui.onProgress(0.0, "Version profile downloading");
        VersionProfile profile = versionManager.loadProfile(versionId);

        ui.onProgress(0.005, "Java runtime is being checked");
        String vanillaVersion = extractVanillaVersion(versionId, profile);
        ProgressListener javaL = (p, stage) -> {
            double overall = 0.005 + (p * 0.15);
            ui.onProgress(overall, stage);
        };
        String javaPath = javaRuntimeManager.getOrDownloadJavaRuntime(vanillaVersion, javaL);

        ui.onProgress(0.16, "Libraries are being checked");
        ProgressListener libsL = (p, stage) -> {
            double overall = 0.16 + (p * 0.30);
            ui.onProgress(overall, stage);
        };
        Set<String> classpaths = libraryManager.resolveLibraries(profile, libsL);
        Path modLoaderJar = gameDir.resolve("versions").resolve(versionId).resolve(versionId + ".jar");
        if (Files.exists(modLoaderJar)) {
            classpaths.add(modLoaderJar.toAbsolutePath().toString());
        }
        Path vanillaJar = findVanillaJar(versionId, profile);
        if (vanillaJar != null && Files.exists(vanillaJar)) {
            classpaths.add(vanillaJar.toAbsolutePath().toString());
        }
        ui.onProgress(0.46, "Native libraries are preparing");
        ProgressListener nativesL = (p, stage) -> {
            double overall = 0.46 + (p * 0.20);
            ui.onProgress(overall, stage);
        };
        Path runNativesDir = gameDir.resolve("natives-cache").resolve(SystemUtil.getOsName() + "-" + SystemUtil.getArch()).resolve("run-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID());
        Path nativesDir = libraryManager.extractNatives(profile, nativesL, runNativesDir);
        ui.onProgress(0.66, "The startup command is being prepared");
        List<String> command = buildLaunchCommand(javaPath, profile, context, classpaths, nativesDir);
        ui.onProgress(0.75, "Minecraft launching");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(context.gameDirectory()));
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        ui.onProgress(1.0, "Launched");
        return proc;
    }

    private String extractVanillaVersion(String versionId, VersionProfile profile) {
        if (profile.inheritsFrom != null && !profile.inheritsFrom.isEmpty()) {
            return profile.inheritsFrom;
        }

        if (versionId.startsWith("fabric-loader-")) {
            String[] parts = versionId.split("-");
            if (parts.length >= 3) {
                return parts[parts.length - 1];
            }
        }
        else if (versionId.contains("-forge-")) {
            return versionId.split("-forge-")[0];
        }

        return versionId;
    }

    private Path findVanillaJar(String versionId, VersionProfile profile) {
        if (profile.inheritsFrom != null && !profile.inheritsFrom.isEmpty()) {
            Path inheritedJar = gameDir.resolve("versions")
                    .resolve(profile.inheritsFrom)
                    .resolve(profile.inheritsFrom + ".jar");
            if (Files.exists(inheritedJar)) {
                return inheritedJar;
            }
        }

        String vanillaVersion = versionId;
        if (versionId.startsWith("fabric-loader-")) {
            String[] parts = versionId.split("-");
            if (parts.length >= 3) {
                vanillaVersion = parts[parts.length - 1];
            }
        }
        else if (versionId.contains("-forge-")) {
            vanillaVersion = versionId.split("-forge-")[0];
        }

        Path vanillaJar = gameDir.resolve("versions").resolve(vanillaVersion).resolve(vanillaVersion + ".jar");
        if (Files.exists(vanillaJar)) {
            return vanillaJar;
        }
        return null;
    }

    private List<String> buildLaunchCommand(String javaPath, VersionProfile profile, LaunchContext context, Set<String> classpaths, Path nativesDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaPath);
        cmd.add("-Xmx" + context.maxMemory() + "G");
        cmd.add("-XX:+UnlockExperimentalVMOptions");
        cmd.add("-XX:+UseG1GC");
        cmd.add("-XX:G1NewSizePercent=20");
        cmd.add("-XX:G1ReservePercent=20");
        cmd.add("-XX:MaxGCPauseMillis=50");
        cmd.add("-XX:G1HeapRegionSize=32M");
        cmd.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
        cmd.add("-Dminecraft.launcher.brand=spicy-launcher");
        cmd.add("-Dminecraft.launcher.version=1.0");
        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, classpaths));
        cmd.add(profile.mainClass);
        cmd.add("--username");
        cmd.add(context.username());
        cmd.add("--version");
        cmd.add(context.versionName());
        cmd.add("--gameDir");
        cmd.add(context.gameDirectory());
        cmd.add("--assetsDir");
        cmd.add(context.assetsDirectory());
        cmd.add("--assetIndex");
        cmd.add(context.assetIndex());
        cmd.add("--uuid");
        cmd.add(context.uuid());
        cmd.add("--accessToken");
        cmd.add(context.accessToken());
        cmd.add("--userType");
        cmd.add("msa");
        cmd.add("--versionType");
        cmd.add("release");
        cmd.add("--userProperties");
        cmd.add("{}");

        if (context.width() != null && context.height() != null) {
            cmd.add("--width");
            cmd.add(String.valueOf(context.width()));
            cmd.add("--height");
            cmd.add(String.valueOf(context.height()));
        }
        if (context.fullscreen()) {
            cmd.add("--fullscreen");
        }
        return cmd;
    }
}