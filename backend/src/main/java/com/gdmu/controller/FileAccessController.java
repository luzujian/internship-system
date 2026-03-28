package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.utils.AliyunOSSOperator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URL;

@Slf4j
@RestController
@RequestMapping("/api/uploads")
public class FileAccessController {

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @GetMapping("/**")
    @Log(operationType = "VIEW", module = "FILE_ACCESS", description = "访问上传文件")
    public void accessFile(HttpServletRequest request, HttpServletResponse response) {
        try {
            String requestURI = request.getRequestURI();
            String filePath = requestURI.replace("/api/uploads/", "");
            log.info("访问文件: {}", filePath);
            
            String ossUrl = aliyunOSSOperator.getFileUrl(filePath);
            log.info("OSS URL: {}", ossUrl);
            
            URL url = new URL(ossUrl);
            try (InputStream inputStream = url.openStream()) {
                String contentType = getContentType(filePath);
                response.setContentType(contentType);
                response.setHeader("Cache-Control", "public, max-age=31536000");
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, bytesRead);
                }
                response.getOutputStream().flush();
            }
        } catch (Exception e) {
            log.error("访问文件失败", e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private String getContentType(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            case "doc", "docx" -> "application/msword";
            case "xls", "xlsx" -> "application/vnd.ms-excel";
            case "ppt", "pptx" -> "application/vnd.ms-powerpoint";
            case "txt" -> "text/plain";
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "json" -> "application/json";
            case "zip" -> "application/zip";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            default -> "application/octet-stream";
        };
    }
}
