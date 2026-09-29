package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Compatibility endpoints for clients that still probe the retired two-stage flow. */
@RestController
@RequestMapping("/api/student/internship-flow")
@PreAuthorize("hasRole('STUDENT')")
public class StudentInternshipFlowController {
    private static final String RETIRED_MESSAGE =
            "双阶段实习流程已停用，请直接申请岗位并通过实习确认表切换实习单位";

    @GetMapping
    public Result getFlow() {
        return Result.success(Map.of(
                "retired", true,
                "mode", "FREE_PLACEMENT",
                "message", RETIRED_MESSAGE));
    }

    @PostMapping("/stage-2/continue")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "继续原岗位实习", riskLevel = "MEDIUM")
    public Result continueOriginalPlacement() {
        return Result.error(RETIRED_MESSAGE);
    }

    @PostMapping("/stage-2/change")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "更换实习岗位", riskLevel = "MEDIUM")
    public Result changePlacement() {
        return Result.error(RETIRED_MESSAGE);
    }
}
