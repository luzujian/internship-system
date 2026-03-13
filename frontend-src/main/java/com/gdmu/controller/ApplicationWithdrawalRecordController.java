package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.ApplicationWithdrawalRecord;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.service.ApplicationWithdrawalRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 撤回申请记录控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/application-withdrawal-records")
@PreAuthorize("hasAnyRole('ROLE_ADMIN')")
public class ApplicationWithdrawalRecordController {

    @Autowired
    private ApplicationWithdrawalRecordService applicationWithdrawalRecordService;

    /**
     * 查询所有撤回申请记录
     */
    @GetMapping
    @PreAuthorize("hasAuthority('application:withdrawal:view')")
    public Result getAllApplicationWithdrawalRecords() {
        log.info("查询所有撤回申请记录");
        try {
            List<ApplicationWithdrawalRecord> records = applicationWithdrawalRecordService.findAll();
            return Result.success(records);
        } catch (Exception e) {
            log.error("查询撤回申请记录失败：{}", e.getMessage(), e);
            return Result.error("查询撤回申请记录失败：" + e.getMessage());
        }
    }

    /**
     * 根据 ID 查询撤回申请记录
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('application:withdrawal:view')")
    public Result getApplicationWithdrawalRecordById(@PathVariable Long id) {
        log.info("根据 ID 查询撤回申请记录：{}", id);
        try {
            ApplicationWithdrawalRecord record = applicationWithdrawalRecordService.findById(id);
            if (record == null) {
                return Result.error("撤回申请记录不存在");
            }
            return Result.success(record);
        } catch (Exception e) {
            log.error("查询撤回申请记录失败：{}", e.getMessage(), e);
            return Result.error("查询撤回申请记录失败：" + e.getMessage());
        }
    }

    /**
     * 分页查询撤回申请记录
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('application:withdrawal:view')")
    public Result getApplicationWithdrawalRecordsByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long statusId,
            @RequestParam(required = false) Long applicantId,
            @RequestParam(required = false) String applicantRole,
            @RequestParam(required = false) String applicantName,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        log.info("分页查询撤回申请记录，页码：{}, 每页大小：{}, 状态 ID: {}, 申请人 ID: {}, 申请人身份：{}, 申请人姓名：{}, 开始时间：{}, 结束时间：{}",
                page, pageSize, statusId, applicantId, applicantRole, applicantName, startTime, endTime);
        try {
            PageResult<ApplicationWithdrawalRecord> result = applicationWithdrawalRecordService.findPage(page, pageSize, statusId, applicantId, applicantRole, applicantName, startTime, endTime);
            return Result.success(result);
        } catch (Exception e) {
            log.error("分页查询撤回申请记录失败：{}", e.getMessage(), e);
            return Result.error("分页查询撤回申请记录失败：" + e.getMessage());
        }
    }

    /**
     * 根据 ID 删除撤回申请记录
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('application:withdrawal:delete')")
    @Log(operationType = "DELETE", module = "WITHDRAWAL_RECORD_MANAGEMENT", description = "删除学生撤回记录")
    public Result deleteApplicationWithdrawalRecord(@PathVariable Long id) {
        log.info("删除撤回申请记录，ID: {}", id);
        try {
            int result = applicationWithdrawalRecordService.delete(id);
            return Result.success("删除撤回申请记录成功", result);
        } catch (Exception e) {
            log.error("删除撤回申请记录失败：{}", e.getMessage(), e);
            return Result.error("删除撤回申请记录失败：" + e.getMessage());
        }
    }

    /**
     * 批量删除撤回申请记录
     */
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('application:withdrawal:delete')")
    @Log(operationType = "DELETE", module = "WITHDRAWAL_RECORD_MANAGEMENT", description = "批量删除学生撤回记录")
    public Result batchDeleteApplicationWithdrawalRecords(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        log.info("批量删除撤回申请记录，IDs: {}", ids);
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("撤回申请记录 ID 列表不能为空");
            }

            int result = applicationWithdrawalRecordService.batchDelete(ids);
            return Result.success("批量删除撤回申请记录成功", result);
        } catch (Exception e) {
            log.error("批量删除撤回申请记录失败：{}", e.getMessage(), e);
            return Result.error("批量删除撤回申请记录失败：" + e.getMessage());
        }
    }

    /**
     * 清空所有撤回申请记录
     */
    @DeleteMapping("/clear-all")
    @PreAuthorize("hasAuthority('application:withdrawal:delete')")
    @Log(operationType = "DELETE", module = "WITHDRAWAL_RECORD_MANAGEMENT", description = "清空所有撤回申请记录")
    public Result clearAllApplicationWithdrawalRecords() {
        log.info("清空所有撤回申请记录");
        try {
            int result = applicationWithdrawalRecordService.deleteAll();
            return Result.success("清空所有撤回申请记录成功", result);
        } catch (Exception e) {
            log.error("清空所有撤回申请记录失败：{}", e.getMessage(), e);
            return Result.error("清空所有撤回申请记录失败：" + e.getMessage());
        }
    }
}
