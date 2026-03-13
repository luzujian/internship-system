package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.service.DataExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 数据导出控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/export")
public class DataExportController {

    @Autowired
    private DataExportService dataExportService;

    /**
     * 导出学生实习状态汇总
     */
    @Log(operationType = "EXPORT", module = "DATA_EXPORT", description = "导出学生实习状态汇总")
    @GetMapping("/student-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportStudentStatus(@RequestParam(required = false) Integer status) {
        log.info("导出学生实习状态汇总，状态筛选: {}", status);
        try {
            byte[] data = dataExportService.exportStudentStatus(status);
            return buildExcelResponse(data, "学生实习状态汇总.xlsx");
        } catch (Exception e) {
            log.error("导出失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导出各专业实习进度
     */
    @Log(operationType = "EXPORT", module = "DATA_EXPORT", description = "导出各专业实习进度")
    @GetMapping("/major-progress")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportMajorProgress() {
        log.info("导出各专业实习进度");
        try {
            byte[] data = dataExportService.exportMajorProgress();
            return buildExcelResponse(data, "各专业实习进度.xlsx");
        } catch (Exception e) {
            log.error("导出失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导出企业招聘情况
     */
    @Log(operationType = "EXPORT", module = "DATA_EXPORT", description = "导出企业招聘情况")
    @GetMapping("/company-recruitment")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportCompanyRecruitment() {
        log.info("导出企业招聘情况");
        try {
            byte[] data = dataExportService.exportCompanyRecruitment();
            return buildExcelResponse(data, "企业招聘情况.xlsx");
        } catch (Exception e) {
            log.error("导出失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导出重点关注学生
     */
    @Log(operationType = "EXPORT", module = "DATA_EXPORT", description = "导出重点关注学生")
    @GetMapping("/focus-students")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportFocusStudents() {
        log.info("导出重点关注学生");
        try {
            byte[] data = dataExportService.exportFocusStudents();
            return buildExcelResponse(data, "重点关注学生名单.xlsx");
        } catch (Exception e) {
            log.error("导出失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导出班级实习统计
     */
    @Log(operationType = "EXPORT", module = "DATA_EXPORT", description = "导出班级实习统计")
    @GetMapping("/class-statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportClassStatistics() {
        log.info("导出班级实习统计");
        try {
            byte[] data = dataExportService.exportClassStatistics();
            return buildExcelResponse(data, "班级实习统计.xlsx");
        } catch (Exception e) {
            log.error("导出失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导出学生材料归档
     */
    @Log(operationType = "EXPORT", module = "DATA_EXPORT", description = "导出学生材料归档")
    @GetMapping("/archives")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportArchives() {
        log.info("导出学生材料归档");
        try {
            byte[] data = dataExportService.exportArchives();
            return buildExcelResponse(data, "学生材料归档.xlsx");
        } catch (Exception e) {
            log.error("导出失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 构建Excel响应
     */
    private ResponseEntity<byte[]> buildExcelResponse(byte[] data, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        headers.setContentDispositionFormData("attachment", encodedFilename);
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
