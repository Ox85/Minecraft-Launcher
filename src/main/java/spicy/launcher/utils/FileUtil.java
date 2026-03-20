package spicy.launcher.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtil {

    public static boolean verifySha1(File file, String expectedSha1) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String actualSha1 = sb.toString();
            return actualSha1.equalsIgnoreCase(expectedSha1);

        } catch (Exception e) {
            System.err.println("SHA1 verification failed: " + e.getMessage() + ".");
            return false;
        }
    }


    public static void extractZip(Path zipFile, Path targetDir, List<String> excludePatterns) throws IOException {
        Files.createDirectories(targetDir);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (excludePatterns != null && shouldExclude(entryName, excludePatterns)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(targetDir.resolve(entryName));
                    continue;
                }
                Path targetFile = targetDir.resolve(entryName);
                Files.createDirectories(targetFile.getParent());
                try (FileOutputStream fos = new FileOutputStream(targetFile.toFile())) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static boolean shouldExclude(String entryName, List<String> excludePatterns) {
        for (String pattern : excludePatterns) {
            if (entryName.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }
}