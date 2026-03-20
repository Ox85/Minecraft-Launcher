package spicy.launcher.model;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class AssetIndex {
    public boolean virtual;
    @SerializedName("map_to_resources")
    public boolean mapToResources;
    public Map<String, AssetObject> objects;
    
    public static class AssetObject {
        public String hash;
        public long size;
    }
}