package com.internship.controller;

import com.internship.entity.Result;
import com.internship.service.CompanySchoolLiaisonService;
import com.internship.utils.CurrentHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company")
@PreAuthorize("hasRole('COMPANY')")
public class CompanySchoolLiaisonReadController {
    private final CompanySchoolLiaisonService liaisonService;

    public CompanySchoolLiaisonReadController(CompanySchoolLiaisonService liaisonService) {
        this.liaisonService = liaisonService;
    }

    @GetMapping("/school-liaisons")
    public Result getSchoolLiaisons() {
        return Result.success(liaisonService.findByCompanyId(CurrentHolder.getUserId()));
    }
}
