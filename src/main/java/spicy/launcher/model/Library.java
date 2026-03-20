package spicy.launcher.model;

import java.util.List;
import java.util.Map;

public class Library {
    public String name;
    public String url;
    public Downloads downloads;
    public Natives natives;
    public List<Rule> rules;
    public Extract extract;

    public static class Downloads {
        public Artifact artifact;
        public Map<String, Artifact> classifiers;
    }

    public static class Artifact {
        public String path;
        public String sha1;
        public long size;
        public String url;
    }

    public static class Natives {
        public String linux;
        public String osx;
        public String windows;
    }

    public static class Rule {
        public String action;
        public Os os;
        public Map<String, Boolean> features;

        public static class Os {
            public String name;
            public String version;
            public String arch;
        }
    }

    public static class Extract {
        public List<String> exclude;
    }
}