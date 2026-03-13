package com.gdmu.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件类型验证工具类
 * 支持PDF、DOCX、PPT、压缩文件、图片、音视频、代码文件等多种格式验证
 */
@Slf4j
@Component
public class FileTypeValidator {
    
    // 支持的文档格式
    private static final Set<String> DOCUMENT_TYPES = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt"
    ));
    
    // 支持的图片格式
    private static final Set<String> IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"
    ));
    
    // 支持的音视频格式
    private static final Set<String> MEDIA_TYPES = new HashSet<>(Arrays.asList(
            "mp3", "wav", "ogg", "mp4", "avi", "mov", "wmv", "flv", "mkv"
    ));
    
    // 支持的压缩文件格式
    private static final Set<String> ARCHIVE_TYPES = new HashSet<>(Arrays.asList(
            "zip", "rar", "7z", "tar", "gz"
    ));
    
    // 支持的代码文件格式
    private static final Set<String> CODE_TYPES = new HashSet<>(Arrays.asList(
            "java", "py", "cpp", "c", "h", "js", "ts", "html", "css", "xml", "json", "yml", "yaml"
    ));
    
    // 所有支持的文件类型
    private static final Set<String> ALL_SUPPORTED_TYPES = new HashSet<>();
    
    static {
        ALL_SUPPORTED_TYPES.addAll(DOCUMENT_TYPES);
        ALL_SUPPORTED_TYPES.addAll(IMAGE_TYPES);
        ALL_SUPPORTED_TYPES.addAll(MEDIA_TYPES);
        ALL_SUPPORTED_TYPES.addAll(ARCHIVE_TYPES);
        ALL_SUPPORTED_TYPES.addAll(CODE_TYPES);
    }
    
    /**
     * 验证文件类型是否支持
     * @param file 上传的文件
     * @param allowedTypes 允许的文件类型（逗号分隔，如：pdf,docx,zip）
     * @return 是否支持
     */
    public boolean validateFileType(MultipartFile file, String allowedTypes) {
        if (file == null || file.isEmpty()) {
            log.warn("文件为空");
            return false;
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            log.warn("文件名为空");
            return false;
        }
        
        // 获取文件扩展名
        String extension = getFileExtension(originalFilename);
        if (extension == null || extension.trim().isEmpty()) {
            log.warn("文件无扩展名: {}", originalFilename);
            return false;
        }
        
        // 转换为小写进行比较
        extension = extension.toLowerCase();
        
        // 如果没有指定允许的类型，则使用所有支持的类型
        Set<String> allowedTypeSet;
        if (allowedTypes == null || allowedTypes.trim().isEmpty()) {
            allowedTypeSet = ALL_SUPPORTED_TYPES;
        } else {
            allowedTypeSet = parseAllowedTypes(allowedTypes);
        }
        
        boolean isSupported = allowedTypeSet.contains(extension);
        
        if (!isSupported) {
            log.warn("不支持的文件类型: {}，允许的类型: {}", extension, allowedTypeSet);
        }
        
        return isSupported;
    }
    
    /**
     * 验证文件大小
     * @param file 上传的文件
     * @param maxSizeMB 最大文件大小（MB）
     * @return 是否在限制范围内
     */
    public boolean validateFileSize(MultipartFile file, int maxSizeMB) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        long maxSizeBytes = maxSizeMB * 1024L * 1024L;
        long fileSize = file.getSize();
        
        boolean isValid = fileSize <= maxSizeBytes;
        
        if (!isValid) {
            log.warn("文件大小超出限制: {}MB，实际大小: {}MB", 
                    maxSizeMB, fileSize / (1024.0 * 1024.0));
        }
        
        return isValid;
    }
    
    /**
     * 获取文件分类
     * @param filename 文件名
     * @return 文件分类（DOCUMENT, IMAGE, MEDIA, ARCHIVE, CODE, UNKNOWN）
     */
    public String getFileCategory(String filename) {
        String extension = getFileExtension(filename);
        if (extension == null) {
            return "UNKNOWN";
        }
        
        extension = extension.toLowerCase();
        
        if (DOCUMENT_TYPES.contains(extension)) {
            return "DOCUMENT";
        } else if (IMAGE_TYPES.contains(extension)) {
            return "IMAGE";
        } else if (MEDIA_TYPES.contains(extension)) {
            return "MEDIA";
        } else if (ARCHIVE_TYPES.contains(extension)) {
            return "ARCHIVE";
        } else if (CODE_TYPES.contains(extension)) {
            return "CODE";
        } else {
            return "UNKNOWN";
        }
    }
    
    /**
     * 获取所有支持的文件类型
     * @return 支持的文件类型集合
     */
    public Set<String> getAllSupportedTypes() {
        return new HashSet<>(ALL_SUPPORTED_TYPES);
    }
    
    /**
     * 获取指定分类的支持类型
     * @param category 文件分类
     * @return 该分类下的支持类型
     */
    public Set<String> getSupportedTypesByCategory(String category) {
        switch (category.toUpperCase()) {
            case "DOCUMENT":
                return new HashSet<>(DOCUMENT_TYPES);
            case "IMAGE":
                return new HashSet<>(IMAGE_TYPES);
            case "MEDIA":
                return new HashSet<>(MEDIA_TYPES);
            case "ARCHIVE":
                return new HashSet<>(ARCHIVE_TYPES);
            case "CODE":
                return new HashSet<>(CODE_TYPES);
            default:
                return new HashSet<>();
        }
    }
    
    /**
     * 解析允许的文件类型字符串
     * @param allowedTypes 逗号分隔的文件类型字符串
     * @return 文件类型集合
     */
    private Set<String> parseAllowedTypes(String allowedTypes) {
        Set<String> result = new HashSet<>();
        String[] types = allowedTypes.split(",");
        
        for (String type : types) {
            String trimmedType = type.trim().toLowerCase();
            if (!trimmedType.isEmpty()) {
                result.add(trimmedType);
            }
        }
        
        return result;
    }
    
    /**
     * 获取文件扩展名
     * @param filename 文件名
     * @return 扩展名（不含点）
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return null;
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return null;
        }
        
        return filename.substring(lastDotIndex + 1);
    }
    
    /**
     * 批量验证文件
     * @param files 文件数组
     * @param allowedTypes 允许的文件类型
     * @param maxSizeMB 最大文件大小
     * @return 验证结果
     */
    public FileValidationResult validateFiles(MultipartFile[] files, String allowedTypes, int maxSizeMB) {
        FileValidationResult result = new FileValidationResult();
        
        if (files == null || files.length == 0) {
            result.setValid(true);
            result.setMessage("无文件需要验证");
            return result;
        }
        
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                result.addInvalidFile(file.getOriginalFilename(), "文件为空");
                continue;
            }
            
            // 验证文件类型
            if (!validateFileType(file, allowedTypes)) {
                result.addInvalidFile(file.getOriginalFilename(), "不支持的文件类型");
                continue;
            }
            
            // 验证文件大小
            if (!validateFileSize(file, maxSizeMB)) {
                result.addInvalidFile(file.getOriginalFilename(), "文件大小超出限制");
                continue;
            }
            
            result.addValidFile(file.getOriginalFilename(), getFileCategory(file.getOriginalFilename()));
        }
        
        result.setValid(result.getInvalidFiles().isEmpty());
        if (result.isValid()) {
            result.setMessage("所有文件验证通过");
        } else {
            result.setMessage("部分文件验证失败");
        }
        
        return result;
    }
    
    /**
     * 文件验证结果类
     */
    public static class FileValidationResult {
        private boolean valid;
        private String message;
        private final Set<String> validFiles = new HashSet<>();
        private final Set<String> invalidFiles = new HashSet<>();
        private final Set<String> invalidReasons = new HashSet<>();
        
        public void addValidFile(String filename, String category) {
            validFiles.add(filename + " (" + category + ")");
        }
        
        public void addInvalidFile(String filename, String reason) {
            invalidFiles.add(filename);
            invalidReasons.add(reason);
        }
        
        // Getter 和 Setter 方法
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public Set<String> getValidFiles() { return new HashSet<>(validFiles); }
        public Set<String> getInvalidFiles() { return new HashSet<>(invalidFiles); }
        public Set<String> getInvalidReasons() { return new HashSet<>(invalidReasons); }
    }
}