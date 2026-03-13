package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Position;
import com.gdmu.entity.Result;
import com.gdmu.service.CompanyUserService;
import com.gdmu.service.PositionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/company")
@PreAuthorize("hasRole('COMPANY')")
public class CompanyController {

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private PositionService positionService;

    @GetMapping("/info")
    public Result getCompanyInfo(@RequestParam Long companyId) {
        log.info("获取企业信息: {}", companyId);
        try {
            CompanyUser company = companyUserService.findById(companyId);
            if (company == null) {
                return Result.error("企业不存在");
            }

            Map<String, Object> companyInfo = new HashMap<>();
            companyInfo.put("id", company.getId());
            companyInfo.put("companyName", company.getCompanyName());
            companyInfo.put("contactPerson", company.getContactPerson());
            companyInfo.put("contactPhone", company.getContactPhone());
            companyInfo.put("contactEmail", company.getContactEmail());
            companyInfo.put("address", company.getAddress());
            companyInfo.put("introduction", company.getIntroduction());
            companyInfo.put("status", company.getStatus());

            return Result.success(companyInfo);
        } catch (Exception e) {
            log.error("获取企业信息失败: {}", e.getMessage(), e);
            return Result.error("获取企业信息失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    public Result getStats() {
        log.info("获取企业统计数据");
        try {
            Map<String, Object> stats = new HashMap<>();
            
            Long publishedPositions = positionService.countByCompanyId(getCurrentCompanyId());
            stats.put("publishedPositions", publishedPositions);
            
            stats.put("totalApplicants", 0);
            stats.put("hiredCount", 0);
            stats.put("pendingApplications", 0);

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/positions/recent")
    public Result getRecentPositions() {
        log.info("获取最近发布的岗位");
        try {
            PageResult result = positionService.findPageByCompanyId(getCurrentCompanyId(), 1, 5);
            return Result.success(result.getRows());
        } catch (Exception e) {
            log.error("获取最近岗位失败: {}", e.getMessage(), e);
            return Result.error("获取最近岗位失败: " + e.getMessage());
        }
    }

    @GetMapping("/positions")
    public Result getPositions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("获取企业岗位列表: page={}, pageSize={}", page, pageSize);
        try {
            PageResult result = positionService.findPageByCompanyId(getCurrentCompanyId(), page, pageSize);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取岗位列表失败: {}", e.getMessage(), e);
            return Result.error("获取岗位列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/positions")
    @Log(operationType = "INSERT", module = "POSITION_MANAGEMENT", description = "创建岗位")
    public Result createPosition(@RequestBody Position position) {
        log.info("创建岗位: {}", position.getPositionName());
        try {
            position.setCompanyId(getCurrentCompanyId());
            int result = positionService.insert(position);
            if (result > 0) {
                return Result.success("岗位创建成功");
            }
            return Result.error("岗位创建失败");
        } catch (Exception e) {
            log.error("创建岗位失败: {}", e.getMessage(), e);
            return Result.error("创建岗位失败: " + e.getMessage());
        }
    }

    @PutMapping("/positions/{id}")
    @Log(operationType = "UPDATE", module = "POSITION_MANAGEMENT", description = "更新岗位")
    public Result updatePosition(@PathVariable Long id, @RequestBody Position position) {
        log.info("更新岗位: id={}", id);
        try {
            position.setId(id);
            int result = positionService.update(position);
            if (result > 0) {
                return Result.success("岗位更新成功");
            }
            return Result.error("岗位更新失败");
        } catch (Exception e) {
            log.error("更新岗位失败: {}", e.getMessage(), e);
            return Result.error("更新岗位失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/positions/{id}")
    @Log(operationType = "DELETE", module = "POSITION_MANAGEMENT", description = "删除岗位")
    public Result deletePosition(@PathVariable Long id) {
        log.info("删除岗位: id={}", id);
        try {
            int result = positionService.delete(id);
            if (result > 0) {
                return Result.success("岗位删除成功");
            }
            return Result.error("岗位删除失败");
        } catch (Exception e) {
            log.error("删除岗位失败: {}", e.getMessage(), e);
            return Result.error("删除岗位失败: " + e.getMessage());
        }
    }

    private Long getCurrentCompanyId() {
        return 1L;
    }
}
