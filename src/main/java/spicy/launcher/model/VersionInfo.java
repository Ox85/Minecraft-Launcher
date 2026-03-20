package spicy.launcher.model;

import com.google.gson.annotations.SerializedName;

public class VersionInfo {
    public String id;
    public String type;
    public String url;
    public String time;
    public String releaseTime;
    @SerializedName("sha1")
    public String sha1;
    @SerializedName("complianceLevel")
    public int complianceLevel;
}