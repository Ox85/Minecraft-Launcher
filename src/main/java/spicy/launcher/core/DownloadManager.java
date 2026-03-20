package spicy.launcher.core;

import spicy.launcher.model.ProgressListener;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DownloadManager {
    private static final int TIMEOUT_MS = 30000;
    private static final int BUFFER_SIZE = 8192;


    public String downloadString(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "SpicyLauncher/2.0");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }


    public void downloadFile(String urlStr, Path destination, ProgressListener listener, String stage) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "SpicyLauncher/2.0");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        long total = conn.getContentLengthLong();
        long readTotal = 0;
        Files.createDirectories(destination.getParent());
        try (InputStream in = conn.getInputStream(); OutputStream out = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            if (listener != null) listener.onProgress(0.0, stage);
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                readTotal += read;
                if (listener != null && total > 0) {
                    double p = readTotal / (double) total;
                    listener.onProgress(Math.min(1.0, p), stage);
                }
            }
            if (listener != null) listener.onProgress(1.0, stage);
        } finally {
            conn.disconnect();
        }
    }

    public long getRemoteSize(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", "SpicyLauncher/2.0");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            long len = conn.getContentLengthLong();
            conn.disconnect();
            return len;
        } catch (Exception e) {
            return -1;
        }
    }
}
