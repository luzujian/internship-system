package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.InternshipApplicationEntity;
import com.gdmu.service.InternshipApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实习申请控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/applications")
public class InternshipApplicationController {

    @Autowired
    private InternshipApplicationService internshipApplicationService;

    /**
     * 根据公司ID获取申请列表
     */
    @GetMapping("/company/{companyId}")
    public Result getApplications(@PathVariable Long companyId) {
        log.info("根据公司ID获取申请列表，公司ID: {}", companyId);
        try {
            List<InternshipApplicationEntity> applications = internshipApplicationService.findByCompanyId(companyId);
            return Result.success(applications);
        } catch (Exception e) {
            log.error("获取申请列表失败: {}", e.getMessage());
            return Result.error("获取申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取申请详情
     */
    @GetMapping("/{id}")
    public Result getApplicationById(@PathVariable Long id) {
        log.info("获取申请详情，ID: {}", id);
        try {
            InternshipApplicationEntity application = internshipApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }
            return Result.success(application);
        } catch (Exception e) {
            log.error("获取申请详情失败: {}", e.getMessage());
            return Result.error("获取申请详情失败: " + e.getMessage());
        }
    }

    /**
     * 添加申请
     */
    @Log(operationType = "ADD", module = "APPLICATION_MANAGEMENT", description = "添加实习申请")
    @PostMapping
    public Result addApplication(@RequestBody InternshipApplicationEntity application) {
        log.info("添加实习申请");
        try {
            int result = internshipApplicationService.insert(application);
            if (result > 0) {
                return Result.success("添加成功");
            }
            return Result.error("添加失败");
        } catch (Exception e) {
            log.error("添加申请失败: {}", e.getMessage());
            return Result.error("添加申请失败: " + e.getMessage());
        }
    }

    /**
     * 更新申请状态
     */
    @Log(operationType = "UPDATE", module = "APPLICATION_MANAGEMENT", description = "更新申请状态")
    @PutMapping("/{id}/status")
    public Result updateApplyStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest statusRequest) {
        log.info("更新申请状态，ID: {}, 状态: {}", id, statusRequest.getStatus());
        try {
            boolean result = internshipApplicationService.updateStatus(id, statusRequest.getStatus());
            if (result) {
                return Result.success("更新成功");
            }
            return Result.error("更新失败");
        } catch (Exception e) {
            log.error("更新申请状态失败: {}", e.getMessage());
            return Result.error("更新申请状态失败: " + e.getMessage());
        }
    }

    /**
     * 标记已读
     */
    @Log(operationType = "UPDATE", module = "APPLICATION_MANAGEMENT", description = "标记申请已读")
    @PutMapping("/{id}/view")
    public Result markAsViewed(@PathVariable Long id) {
        log.info("标记申请已读，ID: {}", id);
        try {
            boolean result = internshipApplicationService.markAsViewed(id);
            if (result) {
                return Result.success("标记成功");
            }
            return Result.error("标记失败");
        } catch (Exception e) {
            log.error("标记已读失败: {}", e.getMessage());
            return Result.error("标记已读失败: " + e.getMessage());
        }
    }

    /**
     * 删除申请
     */
    @Log(operationType = "DELETE", module = "APPLICATION_MANAGEMENT", description = "删除实习申请")
    @DeleteMapping("/{id}")
    public Result deleteApplication(@PathVariable Long id) {
        log.info("删除申请，ID: {}", id);
        try {
            boolean result = internshipApplicationService.deleteById(id);
            if (result) {
                return Result.success("删除成功");
            }
            return Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除申请失败: {}", e.getMessage());
            return Result.error("删除申请失败: " + e.getMessage());
        }
    }

    /**
     * 根据岗位ID获取申请列表
     */
    @GetMapping("/position/{positionId}")
    public Result getApplicationsByPosition(@PathVariable Long positionId) {
        log.info("根据岗位ID获取申请列表，岗位ID: {}", positionId);
        try {
            List<InternshipApplicationEntity> applications = internshipApplicationService.findByPositionId(positionId);
            return Result.success(applications);
        } catch (Exception e) {
            log.error("获取岗位申请列表失败: {}", e.getMessage());
            return Result.error("获取岗位申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 按状态获取申请
     */
    @GetMapping
    public Result getApplicationsByStatus(@RequestParam(required = false) String status) {
        log.info("按状态获取申请，状态: {}", status);
        try {
            List<InternshipApplicationEntity> applications;
            if (status != null) {
                applications = internshipApplicationService.findByStatus(status);
            } else {
                applications = internshipApplicationService.findAll();
            }
            return Result.success(applications);
        } catch (Exception e) {
            log.error("按状态获取申请失败: {}", e.getMessage());
            return Result.error("按状态获取申请失败: " + e.getMessage());
        }
    }

    /**
     * 状态更新请求参数
     */
    private static class StatusUpdateRequest {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
