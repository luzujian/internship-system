package com.gdmu.controller;

import com.gdmu.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件下载控制器
 * 提供测试脚本等文件的下载功能
 */
@Slf4j
@RestController
@RequestMapping("/api/download")
public class FileDownloadController {

    /**
     * 下载测试脚本
     * 访问：http://localhost:8080/api/download/test-script
     */
    @GetMapping("/test-script")
    public ResponseEntity<Resource> downloadTestScript() throws IOException {
        // 测试脚本路径
        String scriptPath = "tests/auto_test.py";
        File file = new File(scriptPath);

        if (!file.exists()) {
            log.error("文件不存在：{}", scriptPath);
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"auto_test.py\"")
                .contentType(MediaType.parseMediaType("text/x-python"))
                .contentLength(file.length())
                .body(resource);
    }

    /**
     * 下载测试报告
     * 访问：http://localhost:8080/api/download/test-report
     */
    @GetMapping("/test-report")
    public ResponseEntity<Resource> downloadTestReport() throws IOException {
        String reportPath = "docs/TEST-REPORT-SIMPLE.md";
        File file = new File(reportPath);

        if (!file.exists()) {
            log.error("文件不存在：{}", reportPath);
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"TEST-REPORT-SIMPLE.md\"")
                .contentType(MediaType.parseMediaType("text/markdown"))
                .contentLength(file.length())
                .body(resource);
    }

    /**
     * 下载提交说明
     * 访问：http://localhost:8080/api/download/submit-guide
     */
    @GetMapping("/submit-guide")
    public ResponseEntity<Resource> downloadSubmitGuide() throws IOException {
        String guidePath = "docs/提交说明.md";
        File file = new File(guidePath);

        if (!file.exists()) {
            log.error("文件不存在：{}", guidePath);
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"提交说明.md\"")
                .contentType(MediaType.parseMediaType("text/markdown"))
                .contentLength(file.length())
                .body(resource);
    }
}
