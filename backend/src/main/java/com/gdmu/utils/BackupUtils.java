package com.gdmu.utils;

import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class BackupUtils {

    public static String calculateChecksum(String filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = new FileInputStream(filePath)) {
            byte[] byteArray = new byte[1024];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static boolean verifyChecksum(String filePath, String expectedChecksum) {
        try {
            String actualChecksum = calculateChecksum(filePath);
            boolean isValid = actualChecksum.equals(expectedChecksum);
            log.debug("文件校验: 文件={}, 期望={}, 实际={}, 结果={}", 
                filePath, expectedChecksum, actualChecksum, isValid);
            return isValid;
        } catch (Exception e) {
            log.error("文件校验失败: {}", e.getMessage());
            return false;
        }
    }

    public static void compressFile(String inputPath, String outputPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputPath);
             FileOutputStream fos = new FileOutputStream(outputPath);
             GZIPOutputStream gzipOS = new GZIPOutputStream(fos)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gzipOS.write(buffer, 0, len);
            }
        }
        log.info("文件压缩完成: {} -> {}", inputPath, outputPath);
    }

    public static void decompressFile(String inputPath, String outputPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputPath);
             GZIPInputStream gzipIS = new GZIPInputStream(fis);
             FileOutputStream fos = new FileOutputStream(outputPath)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIS.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
        log.info("文件解压完成: {} -> {}", inputPath, outputPath);
    }

    public static long getFileSize(String filePath) {
        try {
            return Files.size(Paths.get(filePath));
        } catch (IOException e) {
            log.error("获取文件大小失败: {}", e.getMessage());
            return 0;
        }
    }

    public static boolean deleteFile(String filePath) {
        try {
            return Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            log.error("删除文件失败: {}", e.getMessage());
            return false;
        }
    }

    public static String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    public static String changeFileExtension(String fileName, String newExtension) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex + 1) + newExtension;
        }
        return fileName + "." + newExtension;
    }
}
