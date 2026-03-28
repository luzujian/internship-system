package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Position;
import com.gdmu.entity.Result;
import com.gdmu.service.PositionService;
import com.gdmu.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 招聘岗位管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/positions")
public class PositionController {

    @Autowired
    private PositionService positionService;

    /**
     * 分页查询岗位列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('recruitment:view')")
    public Result getPositions(@RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "10") Integer pageSize,
                               @RequestParam(required = false) String companyName,
                               @RequestParam(required = false) String positionName) {
        log.info("分页查询岗位列表，页码: {}, 每页: {}, 企业: {}, 岗位: {}",
                page, pageSize, companyName, positionName);
        PageResult<Map<String, Object>> pageResult = positionService.findPage(page, pageSize, companyName, positionName);
        return Result.success(pageResult);
    }

    /**
     * 获取所有岗位列表
     */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('recruitment:view')")
    public Result getAllPositions() {
        log.info("获取所有岗位列表");
        List<Position> positions = positionService.findAll();
        return Result.success(positions);
    }

    /**
     * 获取岗位统计数据
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('recruitment:view')")
    public Result getStatistics() {
        log.info("获取岗位统计数据");
        Map<String, Object> statistics = positionService.getStatistics();
        return Result.success(statistics);
    }

    /**
     * 根据ID获取岗位详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('recruitment:view')")
    public Result getPositionById(@PathVariable Long id) {
        log.info("获取岗位详情，ID: {}", id);
        Position position = positionService.findById(id);
        if (position == null) {
            return Result.error("岗位不存在");
        }
        return Result.success(position);
    }

    /**
     * 新增岗位
     */
    @Log(operationType = "ADD", module = "POSITION_MANAGEMENT", description = "新增招聘岗位")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY') or hasAuthority('recruitment:add')")
    public Result addPosition(@RequestBody @Validated Position position) {
        log.info("新增岗位: {}", position.getPositionName());
        try {
            // 计算剩余缺口
            position.setRemainingQuota(position.getPlannedRecruit() - position.getRecruitedCount());
            positionService.insert(position);
            return Result.success("添加成功");
        } catch (Exception e) {
            log.error("新增岗位失败: {}", e.getMessage());
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 更新岗位
     */
    @Log(operationType = "UPDATE", module = "POSITION_MANAGEMENT", description = "更新招聘岗位")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY') or hasAuthority('recruitment:edit')")
    public Result updatePosition(@PathVariable Long id, @RequestBody Position position) {
        log.info("更新岗位，ID: {}", id);
        try {
            position.setId(id);
            // 重新计算剩余缺口
            if (position.getPlannedRecruit() != null) {
                Integer recruitedCount = position.getRecruitedCount() != null ? position.getRecruitedCount() : 0;
                position.setRemainingQuota(position.getPlannedRecruit() - recruitedCount);
            }
            positionService.update(position);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新岗位失败: {}", e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除岗位
     */
    @Log(operationType = "DELETE", module = "POSITION_MANAGEMENT", description = "删除招聘岗位")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY') or hasAuthority('recruitment:delete')")
    public Result deletePosition(@PathVariable Long id) {
        log.info("删除岗位，ID: {}", id);
        try {
            positionService.delete(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除岗位失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取岗位已招学生列表
     */
    @GetMapping("/{id}/students")
    @PreAuthorize("hasAuthority('recruitment:view')")
    public Result getPositionStudents(@PathVariable Long id) {
        log.info("获取岗位已招学生，岗位ID: {}", id);
        List<Map<String, Object>> students = positionService.getRecruitedStudents(id);
        return Result.success(students);
    }

    /**
     * 批量删除岗位
     */
    @Log(operationType = "DELETE", module = "POSITION_MANAGEMENT", description = "批量删除招聘岗位")
    @DeleteMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY') or hasAuthority('recruitment:delete')")
    public Result batchDeletePositions(@RequestBody List<Long> ids) {
        log.info("批量删除岗位，ID列表: {}", ids);
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("请选择要删除的岗位");
            }
            int count = positionService.batchDelete(ids);
            return Result.success("成功删除" + count + "个岗位");
        } catch (Exception e) {
            log.error("批量删除岗位失败: {}", e.getMessage());
            return Result.error("批量删除失败: " + e.getMessage());
        }
    }

    /**
     * 根据企业ID获取岗位列表
     */
    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY') or hasAuthority('recruitment:view')")
    public Result getPositionsByCompanyId(@PathVariable Long companyId,
                                         @RequestParam(required = false) String positionName,
                                         @RequestParam(required = false) String department,
                                         @RequestParam(required = false) String status) {
        log.info("根据企业ID获取岗位列表，企业ID: {}, 岗位名称: {}, 部门: {}, 状态: {}", companyId, positionName, department, status);
        
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                log.error("未获取到认证信息");
                return Result.error("未获取到认证信息");
            }
            
            boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
            
            if (!isAdmin) {
                Long currentCompanyId = CurrentHolder.getUserId();
                if (currentCompanyId == null) {
                    log.error("未获取到当前登录企业 ID");
                    return Result.error("未获取到当前登录企业 ID");
                }
                
                if (!currentCompanyId.equals(companyId)) {
                    log.warn("企业用户尝试访问其他企业的数据，当前企业 ID: {}, 请求企业 ID: {}", currentCompanyId, companyId);
                    return Result.error("无权访问该企业的数据");
                }
                
                log.info("企业用户身份验证通过，企业 ID: {}", currentCompanyId);
            }
            
            List<Position> positions;
            if (positionName != null || department != null || status != null) {
                positions = positionService.findByCompanyIdWithConditions(companyId, positionName, department, status);
            } else {
                positions = positionService.findByCompanyId(companyId);
            }
            
            return Result.success(positions);
        } catch (Exception e) {
            log.error("获取企业岗位列表失败：{}", e.getMessage(), e);
            return Result.error("获取企业岗位列表失败：" + e.getMessage());
        }
    }

    @PutMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY') or hasAuthority('recruitment:edit')")
    public Result pausePosition(@PathVariable Long id) {
        log.info("暂停岗位招聘，岗位 ID: {}", id);
        try {
            positionService.pausePosition(id);
            return Result.success("暂停成功");
        } catch (Exception e) {
            log.error("暂停岗位失败：{}", e.getMessage());
            return Result.error("暂停失败：" + e.getMessage());
        }
    }

    @PutMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY') or hasAuthority('recruitment:edit')")
    public Result resumePosition(@PathVariable Long id) {
        log.info("恢复岗位招聘，岗位 ID: {}", id);
        try {
            positionService.resumePosition(id);
            return Result.success("恢复成功");
        } catch (Exception e) {
            log.error("恢复岗位失败：{}", e.getMessage());
            return Result.error("恢复失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/clear")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(operationType = "DELETE", module = "POSITION_MANAGEMENT", description = "清除所有岗位数据")
    public Result clearAllPositions() {
        log.info("清除所有岗位数据");
        try {
            int count = positionService.clearAll();
            log.info("清除所有岗位数据成功，共清除{}条记录", count);
            return Result.success("清除成功，共清除" + count + "条记录");
        } catch (Exception e) {
            log.error("清除所有岗位数据失败: {}", e.getMessage(), e);
            return Result.error("清除失败");
        }
    }
}
