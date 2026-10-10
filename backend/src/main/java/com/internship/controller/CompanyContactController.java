package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.CompanyContact;
import com.internship.entity.Result;
import com.internship.service.CompanyContactService;
import com.internship.utils.CurrentHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company/contacts")
@PreAuthorize("hasRole('COMPANY')")
public class CompanyContactController {
    private final CompanyContactService service;
    private Long companyId() {
        if (!"ROLE_COMPANY".equals(CurrentHolder.getUserRole()) || CurrentHolder.getUserId() == null) {
            throw new AccessDeniedException("企业身份无效");
        }
        return CurrentHolder.getUserId();
    }
    @GetMapping
    public Result list() { return Result.success(service.list(companyId())); }

    @PostMapping
    @Log(operationType = "INSERT", module = "COMPANY_MANAGEMENT", description = "新增企业联系人")
    public Result create(@Valid @RequestBody CompanyContact contact) {
        return Result.success(service.save(companyId(), null, contact));
    }
    @PutMapping("/{id}")
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "编辑企业联系人")
    public Result update(@PathVariable Long id, @Valid @RequestBody CompanyContact contact) {
        return Result.success(service.save(companyId(), id, contact));
    }
    @DeleteMapping("/{id}")
    @Log(operationType = "DELETE", module = "COMPANY_MANAGEMENT", description = "删除企业联系人")
    public Result delete(@PathVariable Long id) {
        service.delete(companyId(), id);
        return Result.success();
    }
}
