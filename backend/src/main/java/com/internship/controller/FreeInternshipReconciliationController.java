package com.internship.controller;

import com.internship.anno.Log;
import com.internship.dto.FreeInternshipReconciliationRequest;
import com.internship.entity.Result;
import com.internship.service.FreeInternshipReconciliationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/free-internship-reconciliation")
@PreAuthorize("hasRole('ADMIN')")
public class FreeInternshipReconciliationController {

    private final FreeInternshipReconciliationService reconciliationService;

    public FreeInternshipReconciliationController(FreeInternshipReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/preview")
    public Result preview(@RequestBody(required = false) FreeInternshipReconciliationRequest request) {
        return Result.success(reconciliationService.preview(request));
    }

    @PostMapping("/apply")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "提交自主实习认定申请", riskLevel = "MEDIUM")
    public Result apply(@RequestBody FreeInternshipReconciliationRequest request) {
        return Result.success("自由实习历史数据对账完成", reconciliationService.apply(request));
    }
}
