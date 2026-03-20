package spicy.launcher.model;

import java.util.List;

public class VersionManifest {
    public LatestVersions latest;
    public List<VersionInfo> versions;

    public static class LatestVersions {
        public String release;
        public String snapshot;
    }
}