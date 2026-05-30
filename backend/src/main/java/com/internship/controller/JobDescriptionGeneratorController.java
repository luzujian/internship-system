package com.internship.controller;

import com.internship.service.JobDescriptionGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class JobDescriptionGeneratorController {

    private final JobDescriptionGeneratorService jobDescriptionGeneratorService;

    @Autowired
    public JobDescriptionGeneratorController(JobDescriptionGeneratorService jobDescriptionGeneratorService) {
        this.jobDescriptionGeneratorService = jobDescriptionGeneratorService;
    }

    /**
     * AI生成岗位描述和任职要求
     */
    @PostMapping("/generate-job-description")
    public ResponseEntity<Map<String, Object>> generateJobDescription(@RequestBody Map<String, Object> request) {
        try {
            String positionName = (String) request.get("positionName");
            String department = (String) request.get("department");
            String positionType = (String) request.get("positionType");
            String model = (String) request.get("model");

            if (positionName == null || positionName.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "请先输入岗位名称");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Map<String, String> result = jobDescriptionGeneratorService.generateJobDescription(
                    positionName, department, positionType, model
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", result);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "生成失败: " + e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
