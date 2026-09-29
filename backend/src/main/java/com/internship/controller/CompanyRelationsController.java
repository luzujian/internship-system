package com.internship.controller;

import com.internship.anno.Log;
import com.internship.dto.CompanyLiaisonBatchRequest;
import com.internship.dto.CompanyLiaisonRequest;
import com.internship.dto.CompanyVisitRecordRequest;
import com.internship.entity.Result;
import com.internship.exception.BusinessException;
import com.internship.service.CompanyRelationsService;
import com.internship.service.CompanySchoolLiaisonService;
import com.internship.service.CompanyVisitService;
import com.internship.utils.CurrentHolder;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/companies")
@PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
public class CompanyRelationsController {
    private final CompanyVisitService companyVisitService;
    private final CompanySchoolLiaisonService liaisonService;
    private final CompanyRelationsService companyRelationsService;

    public CompanyRelationsController(CompanyVisitService companyVisitService,
                                      CompanySchoolLiaisonService liaisonService,
                                      CompanyRelationsService companyRelationsService) {
        this.companyVisitService = companyVisitService;
        this.liaisonService = liaisonService;
        this.companyRelationsService = companyRelationsService;
    }

    @GetMapping("/teacher-options")
    public Result getTeacherOptions(@RequestParam(required = false) String keyword) {
        return Result.success(companyRelationsService.findTeacherOptions(keyword));
    }

    @GetMapping("/visits")
    public Result getVisits(@RequestParam(defaultValue = "1") Integer page,
                            @RequestParam(defaultValue = "20") Integer pageSize,
                            @RequestParam(required = false) Long companyId,
                            @RequestParam(required = false) String companyName,
                            @RequestParam(required = false) Integer year,
                            @RequestParam(name = "teacherId", required = false) Long participantTeacherId) {
        return Result.success(companyVisitService.findPage(
                page, pageSize, companyId, companyName, year, participantTeacherId));
    }

    @GetMapping("/visits/{id}")
    public Result getVisit(@PathVariable Long id) {
        var record = companyVisitService.findById(id);
        return record == null ? Result.error("走访记录不存在") : Result.success(record);
    }

    @PostMapping("/visits")
    @Log(operationType = "CREATE", module = "COMPANY_MANAGEMENT", description = "新增企业走访记录", riskLevel = "LOW")
    public Result createVisit(@Valid @RequestBody CompanyVisitRecordRequest request) {
        try {
            return Result.success("走访记录新增成功",
                    companyVisitService.create(request, CurrentHolder.getUserId()));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/visits/{id}")
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "更新企业走访记录", riskLevel = "LOW")
    public Result updateVisit(@PathVariable Long id,
                              @Valid @RequestBody CompanyVisitRecordRequest request) {
        try {
            return Result.success("走访记录更新成功",
                    companyVisitService.update(id, request, CurrentHolder.getUserId()));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/visits/{id}")
    @Log(operationType = "DELETE", module = "COMPANY_MANAGEMENT", description = "删除企业走访记录", riskLevel = "MEDIUM")
    public Result deleteVisit(@PathVariable Long id) {
        try {
            companyVisitService.delete(id, CurrentHolder.getUserId());
            return Result.success("走访记录删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{companyId}/liaisons")
    public Result getLiaisons(@PathVariable Long companyId) {
        return Result.success(liaisonService.findByCompanyId(companyId));
    }

    @PostMapping("/{companyId}/liaisons")
    @Log(operationType = "CREATE", module = "COMPANY_MANAGEMENT", description = "新增企业联系人", riskLevel = "LOW")
    public Result addLiaison(@PathVariable Long companyId,
                             @Valid @RequestBody CompanyLiaisonRequest request) {
        try {
            return Result.success("校内对接人新增成功", liaisonService.add(
                    companyId, request.getTeacherId(), CurrentHolder.getUserId()));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/liaisons/{liaisonId}")
    @Log(operationType = "DELETE", module = "COMPANY_MANAGEMENT", description = "删除企业联系人", riskLevel = "MEDIUM")
    public Result deleteLiaison(@PathVariable Long liaisonId) {
        try {
            liaisonService.delete(liaisonId, CurrentHolder.getUserId());
            return Result.success("校内对接关系已解除");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }
}
