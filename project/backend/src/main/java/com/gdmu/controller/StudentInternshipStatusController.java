package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.entity.PageResult;
import com.gdmu.service.StudentInternshipStatusService;
import com.gdmu.service.UserService;
import com.gdmu.anno.Log;
import com.gdmu.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 学生实习状态控制器
 * 处理实习状态相关的所有接口请求
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/internship-status")
public class StudentInternshipStatusController {

    @Autowired
    private StudentInternshipStatusService studentInternshipStatusService;

    @Autowired
    private UserService userService;

    /**
     * 获取实习状态列表（分页）
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result getInternshipStatusList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer gender,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String companyName) {
        log.info("分页获取实习状态列表，页码: {}, 每页条数: {}, 学生ID: {}, 学生姓名: {}, 性别: {}, 状态: {}, 公司ID: {}, 公司名称: {}",
                page, pageSize, studentId, name, gender, status, companyId, companyName);
        PageResult<StudentInternshipStatus> pageResult = studentInternshipStatusService.findPage(page, pageSize, studentId, name, gender, status, companyId, companyName);
        return Result.success(pageResult);
    }

    /**
     * 获取实习状态详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getInternshipStatusById(@PathVariable Long id) {
        log.info("获取实习状态详情，ID: {}", id);
        StudentInternshipStatus status = studentInternshipStatusService.findById(id);
        if (status == null) {
            return Result.error("实习状态不存在");
        }
        return Result.success(status);
    }

    /**
     * 添加实习状态
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result addInternshipStatus(@RequestBody StudentInternshipStatus status) {
        log.info("添加学生实习状态，学生ID: {}", status.getStudentId());
        int result = studentInternshipStatusService.insert(status);
        if (result > 0) {
            return Result.success("添加成功");
        }
        return Result.error("添加失败");
    }

    /**
     * 更新实习状态
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result updateInternshipStatus(@PathVariable Long id, @RequestBody StudentInternshipStatus status) {
        log.info("更新学生实习状态，ID: {}", id);
        status.setId(id);
        int result = studentInternshipStatusService.update(status);
        if (result > 0) {
            return Result.success("更新成功");
        }
        return Result.error("更新失败");
    }

    /**
     * 删除实习状态
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result deleteInternshipStatus(@PathVariable Long id) {
        log.info("删除学生实习状态，ID: {}", id);
        int result = studentInternshipStatusService.delete(id);
        if (result > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }

    /**
     * 批量删除实习状态
     */
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result batchDeleteInternshipStatus(@RequestBody List<Long> ids) {
        log.info("批量删除学生实习状态，IDs: {}", ids);
        int result = studentInternshipStatusService.batchDelete(ids);
        if (result > 0) {
            return Result.success("批量删除成功");
        }
        return Result.error("批量删除失败");
    }

    /**
     * 导出实习状态数据
     */
    @GetMapping("/export")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result exportInternshipStatus(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer gender,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String companyName) {
        log.info("导出实习状态数据，学生ID: {}, 学生姓名: {}, 性别: {}, 状态: {}, 公司ID: {}, 公司名称: {}", 
                studentId, name, gender, status, companyId, companyName);
        List<StudentInternshipStatus> statusList = studentInternshipStatusService.list(studentId, name, gender, status, companyId, companyName);
        return Result.success(statusList);
    }

    /**
     * 根据学生ID获取实习状态
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getInternshipStatusByStudentId(@PathVariable Long studentId) {
        log.info("根据学生ID获取实习状态，学生ID: {}", studentId);
        StudentInternshipStatus status = studentInternshipStatusService.findByStudentId(studentId);
        return Result.success(status);
    }

    /**
     * 根据学生ID删除实习状态（解绑）
     */
    @DeleteMapping("/student/{studentId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result deleteInternshipStatusByStudentId(@PathVariable Long studentId) {
        log.info("根据学生ID删除实习状态，学生ID: {}", studentId);
        StudentInternshipStatus status = studentInternshipStatusService.findByStudentId(studentId);
        if (status == null) {
            return Result.error("该学生未绑定实习状态");
        }
        int result = studentInternshipStatusService.delete(status.getId());
        if (result > 0) {
            return Result.success("解绑成功");
        }
        return Result.error("解绑失败");
    }

    /**
     * 批量解绑学生实习状态
     */
    @DeleteMapping("/student/batch")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result batchDeleteInternshipStatusByStudentIds(@RequestBody List<Long> studentIds) {
        log.info("批量解绑学生实习状态，学生IDs: {}", studentIds);
        int successCount = 0;
        int failCount = 0;
        
        for (Long studentId : studentIds) {
            StudentInternshipStatus status = studentInternshipStatusService.findByStudentId(studentId);
            if (status != null) {
                int result = studentInternshipStatusService.delete(status.getId());
                if (result > 0) {
                    successCount++;
                } else {
                    failCount++;
                }
            } else {
                failCount++;
            }
        }
        
        if (successCount > 0) {
            return Result.success(String.format("批量解绑成功 %d/%d 人", successCount, studentIds.size()));
        }
        return Result.error("批量解绑失败");
    }

    /**
     * 绑定或更新学生实习状态
     * 如果学生已有实习状态记录则更新，否则插入新记录
     */
    @PostMapping("/bind")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result bindOrUpdateInternshipStatus(@RequestBody StudentInternshipStatus status) {
        log.info("绑定或更新学生实习状态，学生ID: {}, 公司ID: {}, 岗位ID: {}", 
                status.getStudentId(), status.getCompanyId(), status.getPositionId());
        
        StudentInternshipStatus existingStatus = studentInternshipStatusService.findByStudentId(status.getStudentId());
        
        if (existingStatus != null) {
            existingStatus.setStatus(status.getStatus());
            existingStatus.setCompanyId(status.getCompanyId());
            existingStatus.setPositionId(status.getPositionId());
            int result = studentInternshipStatusService.update(existingStatus);
            if (result > 0) {
                return Result.success("更新成功");
            }
            return Result.error("更新失败");
        } else {
            int result = studentInternshipStatusService.insert(status);
            if (result > 0) {
                return Result.success("绑定成功");
            }
            return Result.error("绑定失败");
        }
    }

    @GetMapping("/recall/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getPendingRecallAuditInternships(@RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "10") Integer pageSize,
                                                     @RequestParam(required = false) Long studentId,
                                                     @RequestParam(required = false) String name,
                                                     @RequestParam(required = false) Integer gender,
                                                     @RequestParam(required = false) Long companyId,
                                                     @RequestParam(required = false) String companyName) {
        log.info("获取待审核撤回申请列表，页码: {}, 每页条数: {}, 学生ID: {}, 学生姓名: {}, 性别: {}, 公司ID: {}, 公司名称: {}", 
                page, pageSize, studentId, name, gender, companyId, companyName);
        try {
            PageResult<StudentInternshipStatus> pageResult = studentInternshipStatusService
                .findPendingRecallAuditPage(page, pageSize, studentId, name, gender, companyId, companyName);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取待审核撤回申请列表失败: {}", e.getMessage(), e);
            return Result.error("获取待审核撤回申请列表失败");
        }
    }

    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "审核实习确认撤回申请")
    @PutMapping("/{id}/recall-audit")
    @PreAuthorize("hasRole('ADMIN')")
    public Result auditRecallApplication(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        log.info("审核实习确认撤回申请，ID: {}, 审核参数: {}", id, params);
        try {
            Integer recallStatus = Integer.valueOf(params.get("recallStatus").toString());
            String recallAuditRemark = params.get("recallAuditRemark") != null ? 
                params.get("recallAuditRemark").toString() : "";
            
            StudentInternshipStatus status = studentInternshipStatusService.findById(id);
            if (status == null) {
                return Result.error("实习状态不存在");
            }
            
            int result = studentInternshipStatusService.auditRecallApplication(
                id, recallStatus, recallAuditRemark, getCurrentUserId());
            
            if (result > 0) {
                if (recallStatus == 2) {
                    return Result.success("撤回申请已批准");
                } else if (recallStatus == 3) {
                    return Result.success("撤回申请已拒绝");
                } else {
                    return Result.error("无效的撤回审核状态");
                }
            } else {
                return Result.error("审核失败");
            }
        } catch (BusinessException e) {
            log.warn("审核撤回申请失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("审核撤回申请时发生异常: {}", e.getMessage(), e);
            return Result.error("审核失败: " + e.getMessage());
        }
    }

    @GetMapping("/recall/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getRecallStatistics() {
        log.info("获取撤回申请统计数据");
        try {
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("pending", studentInternshipStatusService.countByRecallStatus(1));
            statistics.put("approved", studentInternshipStatusService.countByRecallStatus(2));
            statistics.put("rejected", studentInternshipStatusService.countByRecallStatus(3));
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取撤回申请统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取撤回申请统计数据失败");
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
            String username = ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
            return userService.findByUsername(username).getId();
        }
        return null;
    }
    
    @GetMapping("/company/{companyId}")
    public Result getInternshipStatusByCompanyId(@PathVariable Long companyId) {
        log.info("根据公司ID查询实习确认申请，公司ID: {}", companyId);
        try {
            List<StudentInternshipStatus> statuses = studentInternshipStatusService.findByCompanyId(companyId);
            return Result.success(statuses);
        } catch (Exception e) {
            log.error("查询实习确认申请失败", e);
            return Result.error("查询实习确认申请失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}/approve")
    public Result approveInternshipStatus(@PathVariable Long id) {
        log.info("批准实习确认申请，ID: {}", id);
        try {
            StudentInternshipStatus status = studentInternshipStatusService.findById(id);
            if (status == null) {
                return Result.error("实习状态不存在");
            }
            
            status.setCompanyConfirmStatus(1);
            status.setStatus(2);
            int result = studentInternshipStatusService.update(status);
            
            if (result > 0) {
                return Result.success("批准成功");
            }
            return Result.error("批准失败");
        } catch (Exception e) {
            log.error("批准实习确认申请失败", e);
            return Result.error("批准失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}/reject")
    public Result rejectInternshipStatus(@PathVariable Long id) {
        log.info("拒绝实习确认申请，ID: {}", id);
        try {
            StudentInternshipStatus status = studentInternshipStatusService.findById(id);
            if (status == null) {
                return Result.error("实习状态不存在");
            }
            
            status.setCompanyConfirmStatus(2);
            int result = studentInternshipStatusService.update(status);
            
            if (result > 0) {
                return Result.success("拒绝成功");
            }
            return Result.error("拒绝失败");
        } catch (Exception e) {
            log.error("拒绝实习确认申请失败", e);
            return Result.error("拒绝失败: " + e.getMessage());
        }
    }
}
