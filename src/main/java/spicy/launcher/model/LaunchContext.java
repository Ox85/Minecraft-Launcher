package spicy.launcher.model;


public record LaunchContext(String username, String uuid, String accessToken, String gameDirectory, String assetsDirectory, String assetIndex, String versionName, int maxMemory, Integer width, Integer height, boolean fullscreen) {
}