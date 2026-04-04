package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.AiAuditRecord;
import com.gdmu.entity.Result;
import com.gdmu.mapper.AiAuditRecordMapper;
import com.gdmu.service.AIRecallAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/ai-audit")
public class AiAuditRecordController {

    @Autowired
    private AiAuditRecordMapper aiAuditRecordMapper;

    @Autowired
    private AIRecallAuditService aiRecallAuditService;

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getAuditRecords(
            @RequestParam(required = false) String auditType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String auditDecision,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("auditType", auditType);
            params.put("targetId", targetId);
            params.put("targetType", targetType);
            params.put("auditDecision", auditDecision);
            params.put("offset", (page - 1) * pageSize);
            params.put("limit", pageSize);

            List<AiAuditRecord> records = aiAuditRecordMapper.findByConditions(params);
            int total = aiAuditRecordMapper.countByConditions(params);

            Map<String, Object> data = new HashMap<>();
            data.put("rows", records);
            data.put("total", total);

            return Result.success(data);
        } catch (Exception e) {
            log.error("查询AI审核记录失败: {}", e.getMessage(), e);
            return Result.error("查询AI审核记录失败");
        }
    }

    @GetMapping("/detail/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getAuditDetail(@PathVariable Long id) {
        try {
            AiAuditRecord record = aiAuditRecordMapper.findById(id);
            if (record == null) {
                return Result.error("审核记录不存在");
            }
            return Result.success(record);
        } catch (Exception e) {
            log.error("查询AI审核详情失败: {}", e.getMessage(), e);
            return Result.error("查询AI审核详情失败");
        }
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getStatistics(
            @RequestParam(required = false) String auditType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            Map<String, Object> statistics = aiRecallAuditService.getAuditStatistics(auditType, startDate, endDate);
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("查询AI审核统计失败: {}", e.getMessage(), e);
            return Result.error("查询AI审核统计失败");
        }
    }

    @GetMapping("/target/{targetId}/{targetType}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getAuditRecordsByTarget(
            @PathVariable Long targetId,
            @PathVariable String targetType) {
        try {
            List<AiAuditRecord> records = aiAuditRecordMapper.findByTargetIdAndType(targetId, targetType);
            return Result.success(records);
        } catch (Exception e) {
            log.error("查询目标审核记录失败: {}", e.getMessage(), e);
            return Result.error("查询目标审核记录失败");
        }
    }

    @DeleteMapping("/by-type/{auditType}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(operationType = "DELETE", module = "AI_MODEL", description = "批量删除AI审核记录")
    public Result deleteByAuditType(@PathVariable String auditType) {
        try {
            int count = aiAuditRecordMapper.deleteByAuditType(auditType);
            log.info("批量删除AI审核记录成功，类型: {}, 删除数量: {}", auditType, count);
            return Result.success("删除成功，共删除" + count + "条记录");
        } catch (Exception e) {
            log.error("批量删除AI审核记录失败: {}", e.getMessage(), e);
            return Result.error("删除失败");
        }
    }

    @DeleteMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(operationType = "DELETE", module = "AI_MODEL", description = "删除所有AI审核记录")
    public Result deleteAll() {
        try {
            int count = aiAuditRecordMapper.deleteAll();
            log.info("批量删除所有AI审核记录成功，删除数量: {}", count);
            return Result.success("删除成功，共删除" + count + "条记录");
        } catch (Exception e) {
            log.error("批量删除所有AI审核记录失败: {}", e.getMessage(), e);
            return Result.error("删除失败");
        }
    }
}
