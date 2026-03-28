package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.service.SystemSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class SystemLogoController {
    
    @Autowired
    private SystemSettingsService systemSettingsService;
    
    @Autowired
    private com.gdmu.utils.AliyunOSSOperator aliyunOSSOperator;
    
    @PostMapping("/upload/logo")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_SETTINGS", operationType = "UPLOAD", description = "上传系统Logo")
    public Result uploadLogo(@RequestParam("file") MultipartFile file) {
        log.info("上传系统Logo: 文件名={}, 大小={}字节", 
                file.getOriginalFilename(), file.getSize());
        
        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|bmp)$")) {
                return Result.error("只支持图片格式：JPG、JPEG、PNG、GIF、BMP");
            }
            
            if (file.getSize() > 2 * 1024 * 1024) {
                return Result.error("Logo大小不能超过2MB");
            }
            
            String fileUrl = aliyunOSSOperator.upload(file.getBytes(), "logo/" + originalFilename);
            
            log.info("Logo上传成功: {}", fileUrl);
            return Result.success(fileUrl);
        } catch (IOException e) {
            log.error("Logo上传失败: {}", e.getMessage(), e);
            return Result.error("Logo上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Logo上传异常: {}", e.getMessage(), e);
            return Result.error("Logo上传异常: " + e.getMessage());
        }
    }
}
