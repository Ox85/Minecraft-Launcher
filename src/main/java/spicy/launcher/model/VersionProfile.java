package spicy.launcher.model;

import java.util.List;

public class VersionProfile {
    public String id;
    public String type;
    public String mainClass;
    public String minecraftArguments;
    public Arguments arguments;
    public AssetIndexInfo assetIndex;
    public String assets;
    public int complianceLevel;
    public Downloads downloads;
    public String inheritsFrom;
    public List<Library> libraries;
    public Logging logging;
    public String releaseTime;
    public String time;
    public int minimumLauncherVersion;
    
    public static class Arguments {
        public List<Object> game;
        public List<Object> jvm;
    }
    
    public static class AssetIndexInfo {
        public String id;
        public String sha1;
        public long size;
        public long totalSize;
        public String url;
    }
    
    public static class Downloads {
        public DownloadInfo client;
        public DownloadInfo client_mappings;
        public DownloadInfo server;
        public DownloadInfo server_mappings;
    }
    
    public static class DownloadInfo {
        public String sha1;
        public long size;
        public String url;
    }
    
    public static class Logging {
        public LoggingClient client;
        
        public static class LoggingClient {
            public String argument;
            public LogFile file;
            public String type;
            
            public static class LogFile {
                public String id;
                public String sha1;
                public long size;
                public String url;
            }
        }
    }
}