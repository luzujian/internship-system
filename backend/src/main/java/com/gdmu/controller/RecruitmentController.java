package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Position;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.service.PositionService;
import com.gdmu.service.StudentInternshipStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/recruitment")
public class RecruitmentController {

    @Autowired
    private PositionService positionService;

    @Autowired
    private StudentInternshipStatusService studentInternshipStatusService;

    @GetMapping("/positions")
    @PreAuthorize("hasAuthority('recruitment:view')")
    public Result getRecruitmentPositions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String positionName) {
        log.info("分页查询招聘岗位列表，页码: {}, 每页: {}, 企业: {}, 岗位: {}", page, pageSize, companyName, positionName);
        PageResult<Map<String, Object>> pageResult = positionService.findPage(page, pageSize, companyName, positionName);
        log.info("招聘岗位列表查询结果，总数: {}, 数据量: {}", pageResult.getTotal(), pageResult.getRows() != null ? pageResult.getRows().size() : 0);
        return Result.success(pageResult);
    }

    @GetMapping("/applications")
    @PreAuthorize("hasAuthority('recruitment:view')")
    public Result getRecruitmentApplications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer gender,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String companyName) {
        log.info("分页查询应聘申请列表，页码: {}, 每页: {}, 学生ID: {}, 学生姓名: {}, 性别: {}, 企业ID: {}, 企业名称: {}",
                page, pageSize, studentId, name, gender, companyId, companyName);
        PageResult<StudentInternshipStatus> pageResult = studentInternshipStatusService.findPage(
                page, pageSize, studentId, name, gender, null, companyId, companyName, null, null, null);
        log.info("应聘申请列表查询结果，总数: {}, 数据量: {}", pageResult.getTotal(), pageResult.getRows() != null ? pageResult.getRows().size() : 0);
        return Result.success(pageResult);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('recruitment:view')")
    public Result getRecruitmentStatistics() {
        log.info("获取招聘统计数据");
        Map<String, Object> statistics = new HashMap<>();
        
        List<Position> allPositions = positionService.findAll();
        int totalPositions = allPositions.size();
        int totalPlannedRecruit = allPositions.stream().mapToInt(p -> p.getPlannedRecruit() != null ? p.getPlannedRecruit() : 0).sum();
        int totalRemainingQuota = allPositions.stream().mapToInt(p -> p.getRemainingQuota() != null ? p.getRemainingQuota() : 0).sum();
        
        List<StudentInternshipStatus> applicationStudents = studentInternshipStatusService.list(null, null, null, 0, null, null, null, null, null, null);
        List<StudentInternshipStatus> offerStudents = studentInternshipStatusService.list(null, null, null, 1, null, null, null, null, null, null);
        List<StudentInternshipStatus> confirmedStudents = studentInternshipStatusService.list(null, null, null, 2, null, null, null, null, null, null);
        int totalApplications = applicationStudents.size();
        int totalRecruitedCount = offerStudents.size() + confirmedStudents.size();
        
        statistics.put("totalPositions", totalPositions);
        statistics.put("totalPlannedRecruit", totalPlannedRecruit);
        statistics.put("totalRecruitedCount", totalRecruitedCount);
        statistics.put("totalRemainingQuota", totalRemainingQuota);
        statistics.put("totalApplications", totalApplications);
        
        return Result.success(statistics);
    }

    @GetMapping("/position/{id}")
    @PreAuthorize("hasAuthority('recruitment:view')")
    public Result getPositionDetail(@PathVariable Long id) {
        log.info("获取岗位详情，ID: {}", id);
        Position position = positionService.findById(id);
        if (position == null) {
            return Result.error("岗位不存在");
        }
        return Result.success(position);
    }

    @GetMapping("/position/{id}/applications")
    @PreAuthorize("hasAuthority('recruitment:view')")
    public Result getPositionApplications(@PathVariable Long id) {
        log.info("获取岗位应聘列表，岗位ID: {}", id);
        List<StudentInternshipStatus> applications = studentInternshipStatusService.list(null, null, null, null, null, null, null, null, null, null);
        log.info("查询到的所有实习状态数量: {}", applications.size());
        List<StudentInternshipStatus> filteredApplications = applications.stream()
                .filter(app -> app.getPositionId() != null && app.getPositionId().equals(id))
                .toList();
        log.info("过滤后的岗位应聘列表数量: {}, 岗位ID: {}", filteredApplications.size(), id);
        for (StudentInternshipStatus app : filteredApplications) {
            log.info("应聘学生: 学号={}, 姓名={}, 状态={}, 岗位ID={}", 
                    app.getStudent() != null ? app.getStudent().getStudentUserId() : "无",
                    app.getStudent() != null ? app.getStudent().getName() : "无",
                    app.getStatus(),
                    app.getPositionId());
        }
        return Result.success(filteredApplications);
    }

    @GetMapping("/company/{companyId}/positions")
    @PreAuthorize("hasAuthority('recruitment:view')")
    public Result getCompanyPositions(@PathVariable Long companyId) {
        log.info("获取企业岗位列表，企业ID: {}", companyId);
        List<Position> positions = positionService.findByCompanyId(companyId);
        return Result.success(positions);
    }
}
