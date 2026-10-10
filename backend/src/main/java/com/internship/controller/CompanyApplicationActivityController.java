package com.internship.controller;

import com.internship.entity.Result;
import com.internship.service.CompanyApplicationActivityService;
import com.internship.utils.CurrentHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company/application-activities")
@PreAuthorize("hasRole('COMPANY')")
public class CompanyApplicationActivityController {
    private final CompanyApplicationActivityService service;
    public record ReadVersion(String status, Long version) {}

    private Long companyId() {
        if (!"ROLE_COMPANY".equals(CurrentHolder.getUserRole()) || CurrentHolder.getUserId() == null) {
            throw new AccessDeniedException("企业身份无效");
        }
        return CurrentHolder.getUserId();
    }
    @GetMapping
    public Result list(@RequestParam(defaultValue = "0") int offset,
                       @RequestParam(defaultValue = "10") int limit,
                       @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return Result.success(service.list(companyId(), offset, limit, unreadOnly));
    }
    @GetMapping("/{id}")
    public Result detail(@PathVariable Long id) { return Result.success(service.detail(companyId(), id)); }

    @GetMapping("/for-application/{id}")
    public Result forApplication(@PathVariable Long id) {
        return Result.success(service.forApplication(companyId(), id));
    }
    @PutMapping("/{id}/read")
    public Result read(@PathVariable Long id, @RequestBody ReadVersion version) {
        service.read(companyId(), id, version.status(), version.version());
        return Result.success();
    }
}
