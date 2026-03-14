package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentArchive;
import com.gdmu.service.StudentArchiveService;
import com.gdmu.utils.AliyunOSSOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 学生材料归档管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/archives")
public class StudentArchiveController {

    @Autowired
    private StudentArchiveService studentArchiveService;

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    /**
     * 分页查询材料列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result getArchives(@RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(required = false) String studentName,
                              @RequestParam(required = false) String fileType,
                              @RequestParam(required = false) Integer status) {
        log.info("分页查询材料列表，页码: {}, 每页: {}", page, pageSize);
        PageResult<Map<String, Object>> pageResult = studentArchiveService.findPage(page, pageSize, studentName, fileType, status);
        return Result.success(pageResult);
    }

    /**
     * 获取统计数据
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getStatistics() {
        Map<String, Object> statistics = studentArchiveService.getStatistics();
        return Result.success(statistics);
    }

    /**
     * 审核材料
     */
    @Log(operationType = "UPDATE", module = "ARCHIVE_MANAGEMENT", description = "审核学生材料")
    @PutMapping("/{id}/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public Result auditArchive(@PathVariable Long id, @RequestBody Map<String, Integer> params) {
        log.info("审核材料，ID: {}, 状态: {}", id, params.get("status"));
        try {
            studentArchiveService.updateStatus(id, params.get("status"));
            return Result.success("审核成功");
        } catch (Exception e) {
            log.error("审核失败: {}", e.getMessage());
            return Result.error("审核失败: " + e.getMessage());
        }
    }

    /**
     * 删除材料
     */
    @Log(operationType = "DELETE", module = "ARCHIVE_MANAGEMENT", description = "删除学生材料")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result deleteArchive(@PathVariable Long id) {
        log.info("删除材料，ID: {}", id);
        try {
            studentArchiveService.delete(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除材料
     */
    @Log(operationType = "DELETE", module = "ARCHIVE_MANAGEMENT", description = "批量删除学生材料")
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result batchDeleteArchives(@RequestBody java.util.List<Long> ids) {
        log.info("批量删除材料，ID列表: {}", ids);
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("请选择要删除的材料");
            }
            int count = studentArchiveService.batchDelete(ids);
            return Result.success("成功删除" + count + "个材料");
        } catch (Exception e) {
            log.error("批量删除失败: {}", e.getMessage());
            return Result.error("批量删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生的材料列表
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getStudentArchives(@PathVariable Long studentId) {
        return Result.success(studentArchiveService.findByStudentId(studentId));
    }

    /**
     * 企业端获取学生的材料列表
     */
    @GetMapping("/company/student/{studentId}")
    @PreAuthorize("hasRole('COMPANY')")
    public Result getStudentArchivesForCompany(@PathVariable Long studentId) {
        return Result.success(studentArchiveService.findByStudentId(studentId));
    }

    /**
     * 下载归档文件
     */
    @GetMapping("/download/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadArchive(@PathVariable Long id) {
        log.info("下载归档文件，ID: {}", id);
        try {
            StudentArchive archive = studentArchiveService.findById(id);
            if (archive == null) {
                log.warn("归档文件不存在，ID: {}", id);
                return ResponseEntity.notFound().build();
            }

            String fileUrl = archive.getFileUrl();
            String fileName = archive.getFileName();

            log.info("开始下载文件: {}", fileName);

            byte[] fileBytes = downloadFileFromUrl(fileUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename(URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20"))
                    .build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileBytes);
        } catch (Exception e) {
            log.error("下载文件失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 企业端下载归档文件
     */
    @GetMapping("/company/download/{id}")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<byte[]> downloadArchiveForCompany(@PathVariable Long id) {
        log.info("企业端下载归档文件，ID: {}", id);
        try {
            StudentArchive archive = studentArchiveService.findById(id);
            if (archive == null) {
                log.warn("归档文件不存在，ID: {}", id);
                return ResponseEntity.notFound().build();
            }

            String fileUrl = archive.getFileUrl();
            String fileName = archive.getFileName();

            log.info("开始下载文件: {}", fileName);

            byte[] fileBytes = downloadFileFromUrl(fileUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename(URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20"))
                    .build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileBytes);
        } catch (Exception e) {
            log.error("下载文件失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 预览归档文件
     */
    @GetMapping("/preview/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> previewArchive(@PathVariable Long id) {
        log.info("预览归档文件，ID: {}", id);
        try {
            StudentArchive archive = studentArchiveService.findById(id);
            if (archive == null) {
                log.warn("归档文件不存在，ID: {}", id);
                return ResponseEntity.notFound().build();
            }

            String fileUrl = archive.getFileUrl();
            String fileName = archive.getFileName();

            log.info("开始预览文件: {}", fileName);

            byte[] fileBytes = downloadFileFromUrl(fileUrl);

            String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            MediaType mediaType = getMediaType(fileExtension);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setContentDisposition(ContentDisposition.builder("inline")
                    .build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileBytes);
        } catch (Exception e) {
            log.error("预览文件失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 企业端预览归档文件
     */
    @GetMapping("/company/preview/{id}")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<byte[]> previewArchiveForCompany(@PathVariable Long id) {
        log.info("企业端预览归档文件，ID: {}", id);
        try {
            StudentArchive archive = studentArchiveService.findById(id);
            if (archive == null) {
                log.warn("归档文件不存在，ID: {}", id);
                return ResponseEntity.notFound().build();
            }

            String fileUrl = archive.getFileUrl();
            String fileName = archive.getFileName();

            log.info("开始预览文件: {}", fileName);

            byte[] fileBytes = downloadFileFromUrl(fileUrl);

            String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            MediaType mediaType = getMediaType(fileExtension);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setContentDisposition(ContentDisposition.builder("inline")
                    .build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileBytes);
        } catch (Exception e) {
            log.error("预览文件失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 从URL下载文件（支持阿里云OSS和在线文件URL）
     */
    private byte[] downloadFileFromUrl(String fileUrl) throws Exception {
        if (fileUrl.startsWith("data:")) {
            log.info("从data URL解码文件: {}", fileUrl.substring(0, Math.min(50, fileUrl.length())));
            return decodeDataUrl(fileUrl);
        } else if (fileUrl.contains("aliyuncs.com")) {
            log.info("从阿里云OSS下载文件: {}", fileUrl);
            return aliyunOSSOperator.downloadFile(fileUrl);
        } else {
            log.info("从在线URL下载文件: {}", fileUrl);
            return downloadFromOnlineUrl(fileUrl);
        }
    }

    /**
     * 解码data URL
     */
    private byte[] decodeDataUrl(String dataUrl) throws Exception {
        String base64Data = dataUrl.substring(dataUrl.indexOf(',') + 1);
        return java.util.Base64.getDecoder().decode(base64Data);
    }

    /**
     * 从在线URL下载文件
     */
    private byte[] downloadFromOnlineUrl(String fileUrl) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP请求失败，状态码: " + responseCode);
            }

            InputStream inputStream = connection.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            return outputStream.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private MediaType getMediaType(String fileExtension) {
        switch (fileExtension) {
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG;
            case "png":
                return MediaType.IMAGE_PNG;
            case "gif":
                return MediaType.IMAGE_GIF;
            case "pdf":
                return MediaType.APPLICATION_PDF;
            case "doc":
                return MediaType.valueOf("application/msword");
            case "docx":
                return MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "xls":
                return MediaType.valueOf("application/vnd.ms-excel");
            case "xlsx":
                return MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "ppt":
                return MediaType.valueOf("application/vnd.ms-powerpoint");
            case "pptx":
                return MediaType.valueOf("application/vnd.openxmlformats-officedocument.presentationml.presentation");
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
