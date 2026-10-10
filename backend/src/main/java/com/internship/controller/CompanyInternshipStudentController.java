package com.internship.controller;

import com.internship.entity.PageResult;
import com.internship.entity.Result;
import com.internship.entity.dto.CompanyInternshipStudent;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyInternshipStudentMapper;
import com.internship.utils.CurrentHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company/internship-students")
@PreAuthorize("hasRole('COMPANY')")
public class CompanyInternshipStudentController {
    private final CompanyInternshipStudentMapper mapper;

    @GetMapping
    public Result list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int pageSize,
                       @RequestParam(required = false) String keyword) {
        Long companyId = CurrentHolder.getUserId();
        if (companyId == null || !"ROLE_COMPANY".equals(CurrentHolder.getUserRole())) {
            throw new AccessDeniedException("当前身份不是企业");
        }
        int current = Math.max(1, page);
        int size = Math.max(1, Math.min(100, pageSize));
        String search = keyword == null ? null : keyword.trim();
        if (search != null && search.length() > 100) {
            throw new BusinessException("搜索关键词不能超过100个字符");
        }
        long total = mapper.countCurrentStudents(companyId, search);
        List<CompanyInternshipStudent> rows = total == 0 ? List.of()
                : mapper.findCurrentStudents(companyId, search, (current - 1L) * size, size);
        return Result.success(PageResult.build(total, rows, (int) ((total + size - 1) / size), current, size));
    }
}
