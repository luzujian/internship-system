package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.service.JobApplicationMaterialService;
import com.internship.utils.CurrentHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobApplicationMaterialController {
    private final JobApplicationMaterialService materials;

    @GetMapping("/student/job-applications/{applicationId}/materials")
    @PreAuthorize("hasRole('STUDENT')")
    public Result studentList(@PathVariable Long applicationId) {
        return Result.success(materials.studentView(CurrentHolder.getUserId(), applicationId));
    }

    @GetMapping("/student/job-applications/{applicationId}/materials/{fileId}")
    @PreAuthorize("hasRole('STUDENT')")
    @Log(operationType = "DOWNLOAD", module = "APPLICATION_MANAGEMENT", description = "读取本人岗位申请材料副本", riskLevel = "MEDIUM")
    public ResponseEntity<byte[]> studentFile(@PathVariable Long applicationId, @PathVariable Long fileId) throws Exception {
        return StudentInternshipAgreementController.fileResponse(materials.studentFile(CurrentHolder.getUserId(), applicationId, fileId), false);
    }

    @GetMapping("/company/job-applications/{applicationId}/materials")
    @PreAuthorize("hasRole('COMPANY')")
    public Result companyList(@PathVariable Long applicationId) {
        return Result.success(materials.companyView(CurrentHolder.getUserId(), applicationId, false));
    }

    @GetMapping("/company/job-applications/{applicationId}/materials/{fileId}")
    @PreAuthorize("hasRole('COMPANY')")
    @Log(operationType = "DOWNLOAD", module = "APPLICATION_MANAGEMENT", description = "企业读取岗位申请材料副本", riskLevel = "MEDIUM")
    public ResponseEntity<byte[]> companyFile(@PathVariable Long applicationId, @PathVariable Long fileId) throws Exception {
        return StudentInternshipAgreementController.fileResponse(materials.companyFile(CurrentHolder.getUserId(), applicationId, fileId, false), false);
    }

    @GetMapping("/company/application-activities/{applicationId}/materials")
    @PreAuthorize("hasRole('COMPANY')")
    public Result activityList(@PathVariable Long applicationId) {
        return Result.success(materials.companyView(CurrentHolder.getUserId(), applicationId, true));
    }

    @GetMapping("/company/application-activities/{applicationId}/materials/{fileId}")
    @PreAuthorize("hasRole('COMPANY')")
    @Log(operationType = "DOWNLOAD", module = "APPLICATION_MANAGEMENT", description = "企业读取岗位申请动态材料副本", riskLevel = "MEDIUM")
    public ResponseEntity<byte[]> activityFile(@PathVariable Long applicationId, @PathVariable Long fileId) throws Exception {
        return StudentInternshipAgreementController.fileResponse(materials.companyFile(CurrentHolder.getUserId(), applicationId, fileId, true), false);
    }
}
