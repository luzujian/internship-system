package com.internship.controller;

import com.internship.dto.JobPostingParseRequest;
import com.internship.dto.JobPostingParseResult;
import com.internship.entity.Result;
import com.internship.exception.BusinessException;
import com.internship.service.JobPostingParseService;
import com.internship.utils.CurrentHolder;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/company")
@PreAuthorize("hasRole('COMPANY')")
public class JobPostingParseController {
    private static final Logger log = LoggerFactory.getLogger(JobPostingParseController.class);

    private final JobPostingParseService jobPostingParseService;

    public JobPostingParseController(JobPostingParseService jobPostingParseService) {
        this.jobPostingParseService = jobPostingParseService;
    }

    @PostMapping("/parse-job-posting")
    public Result parseJobPosting(@Valid @RequestBody JobPostingParseRequest request) {
        try {
            Long companyId = CurrentHolder.getUserId();
            if (companyId == null) {
                return Result.error("未获取到当前登录企业 ID");
            }
            return Result.success(jobPostingParseService.parse(request.getText(), companyId));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.warn("企业岗位AI识别失败: errorType={}", e.getClass().getSimpleName());
            return Result.error("AI识别失败，请稍后重试");
        }
    }
}
