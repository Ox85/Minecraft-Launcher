package spicy.launcher.utils;

import java.io.File;

public class SystemUtil {

    public static String getOsName() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            return "windows";
        } else if (os.contains("mac")) {
            return "osx";
        } else if (os.contains("linux")) {
            return "linux";
        }
        
        return "unknown";
    }

    public static String getArch() {
        String arch = System.getProperty("os.arch");
        return arch.contains("64") ? "64" : "32";
    }
    

    public static String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        String os = getOsName();
        
        if (os.equals("windows")) {
            return javaHome + File.separator + "bin" + File.separator + "java.exe";
        } else {
            return javaHome + File.separator + "bin" + File.separator + "java";
        }
    }
}