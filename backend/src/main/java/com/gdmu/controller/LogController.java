package com.gdmu.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gdmu.anno.Log;
import com.gdmu.mapper.OperateLogMapper;
import com.gdmu.entity.OperateLog;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/logs")
public class LogController {
    
    private static final Logger log = LoggerFactory.getLogger(LogController.class);
    
    @Autowired
    private OperateLogMapper operateLogMapper;
    
    /**
     * 添加操作日志
     * 此接口用于前端记录操作日志
     */
    @PostMapping("/operation")
    public Result addOperationLog(@RequestBody OperateLog operateLog) {
        try {
            log.info("添加操作日志: {}", operateLog);
            
            // 设置操作时间（如果未设置）
            if (operateLog.getOperateTime() == null) {
                operateLog.setOperateTime(new java.util.Date());
            }
            
            // 保存操作日志
            operateLogMapper.insert(operateLog);
            
            log.info("操作日志添加成功");
            return Result.success("操作日志添加成功");
        } catch (Exception e) {
            log.error("添加操作日志失败: {}", e.getMessage(), e);
            return Result.error("添加操作日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取最近的操作日志
     * 用于管理员登录后预加载
     */
    @GetMapping("/recent")
    @PreAuthorize("hasAuthority('log:view')")
    public Result getRecentLogs() {
        try {
            List<OperateLog> logs = operateLogMapper.selectAll("operate_time", "desc");
            
            // 只返回前10条
            int limit = Math.min(logs.size(), 10);
            List<OperateLog> recentLogs = logs.subList(0, limit);
            
            return Result.success(recentLogs);
        } catch (Exception e) {
            log.error("获取最近的操作日志失败: {}", e.getMessage(), e);
            return Result.error("获取最近的操作日志失败: " + e.getMessage());
        }
    }

    /**
     * 查询操作日志（分页条件查询）
     * 此接口用于前端日志管理页面
     */
    @GetMapping("/operation")
    @PreAuthorize("hasAuthority('log:view')")
    public Result queryOperationLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String operatorRole,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        try {
            // 字段映射：将前端字段名映射到数据库字段名
            String dbSortField = sortField;
            if ("operation_time".equals(sortField)) {
                dbSortField = "operate_time";
            }
            
            // 安全验证：确保排序字段和顺序是有效的值，防止SQL注入
            // 允许的排序字段列表
            Set<String> allowedSortFields = new HashSet<>(Arrays.asList("id", "operate_time", "operator_name", "operation_type", "module", "ip_address", "operation_result"));
            // 允许的排序顺序
            Set<String> allowedSortOrders = new HashSet<>(Arrays.asList("asc", "desc"));
            
            // 验证并设置默认值
            String safeSortField = allowedSortFields.contains(dbSortField) ? dbSortField : "id";
            String safeSortOrder = allowedSortOrders.contains(sortOrder.toLowerCase()) ? sortOrder.toLowerCase() : "desc";
            
            log.info("查询操作日志，排序字段: {} (前端: {}), 排序顺序: {}", safeSortField, sortField, safeSortOrder);
            
            // 使用PageHelper进行分页
            // 注意：这里不设置PageHelper的排序，因为我们需要在SQL中直接控制排序逻辑
            // 确保全局按ID降序排序后再分页
            PageHelper.startPage(page, pageSize);
            
            // 根据是否有条件参数选择查询方法，传递安全的排序参数
            List<OperateLog> logs;
            if (operatorName != null || operatorRole != null || operationType != null || module != null) {
                // 有条件查询
                logs = operateLogMapper.selectByConditions(operatorName, operatorRole, operationType, module, safeSortField, safeSortOrder);
            } else {
                // 无条件查询
                logs = operateLogMapper.selectAll(safeSortField, safeSortOrder);
            }
            
            // 封装分页结果
            PageInfo<OperateLog> pageInfo = new PageInfo<>(logs);
            
            PageResult<OperateLog> pageResult = new PageResult<>();
            pageResult.setRows(pageInfo.getList());
            pageResult.setTotal(pageInfo.getTotal());
            pageResult.setPages(pageInfo.getPages());
            pageResult.setCurrent(pageInfo.getPageNum());
            pageResult.setPageSize(pageInfo.getPageSize());
            
            return Result.success(pageResult);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取操作日志失败");
        }
    }
    
    /**
     * 清理操作日志（手动清理）
     * 管理员手动触发的日志清理，不受数量限制
     * 支持按角色清理或全部清理
     */
    @Log(operationType = "CLEAN", module = "LOG_MANAGEMENT", description = "清理操作日志")
    @DeleteMapping("/operation/clean")
    @PreAuthorize("hasAuthority('log:delete')")
    public Result manualCleanOperationLogs(
            @RequestParam(required = false) String operatorRole,
            @RequestParam(required = false, defaultValue = "false") boolean deleteAll) {
        try {
            if (operatorRole != null && !operatorRole.isEmpty()) {
                int totalCount = operateLogMapper.countByRole(operatorRole);
                log.info("当前{}角色操作日志总数: {}", operatorRole, totalCount);
                
                if (totalCount == 0) {
                    String message = getRoleName(operatorRole) + "暂无日志数据，无需清理";
                    log.info(message);
                    return Result.error(message);
                }
                
                if (deleteAll) {
                    operateLogMapper.deleteAllLogsByRole(operatorRole);
                    log.info("执行{}角色日志全量清理，删除了全部 {} 条日志", operatorRole, totalCount);
                    return Result.success(getRoleName(operatorRole) + "日志全量清理成功，删除了全部" + totalCount + "条日志");
                } else {
                    final int KEEP_COUNT = getKeepCountByRole(operatorRole);
                    final int THRESHOLD = getThresholdByRole(operatorRole);
                    
                    if (totalCount < KEEP_COUNT) {
                        String message = getRoleName(operatorRole) + "日志数量不足，当前" + totalCount + "条，未达到清理阈值" + THRESHOLD + "条（保留数量" + KEEP_COUNT + "条）";
                        log.info(message);
                        return Result.error(message);
                    }
                    
                    List<Long> keepIds = operateLogMapper.getKeepLogIdsByRole(operatorRole, KEEP_COUNT);
                    int deletedCount = operateLogMapper.cleanOldLogsByRole(operatorRole, keepIds);
                    log.info("执行{}角色日志清理，删除了 {} 条旧日志，保留了最新的 {} 条日志", operatorRole, deletedCount, KEEP_COUNT);
                    
                    if (deletedCount > 0) {
                        String message = getRoleName(operatorRole) + "日志清理成功，删除了" + deletedCount + "条旧日志";
                        return Result.success(message);
                    } else {
                        String message = getRoleName(operatorRole) + "日志已清理完成，当前共" + totalCount + "条日志（未超过保留数量" + KEEP_COUNT + "条）";
                        return Result.error(message);
                    }
                }
            } else {
                int totalCount = operateLogMapper.countTotal();
                log.info("当前操作日志总数: {}", totalCount);
                
                if (totalCount == 0) {
                    String message = "暂无日志数据，无需清理";
                    log.info(message);
                    return Result.error(message);
                }
                
                if (deleteAll) {
                    operateLogMapper.deleteAllLogs();
                    log.info("执行日志全量清理，删除了全部 {} 条日志", totalCount);
                    return Result.success("日志全量清理成功，删除了全部" + totalCount + "条日志");
                } else {
                    final int KEEP_COUNT = 200;
                    
                    if (totalCount < KEEP_COUNT) {
                        String message = "日志数量不足，当前" + totalCount + "条，未达到清理阈值" + KEEP_COUNT + "条";
                        log.info(message);
                        return Result.error(message);
                    }
                    
                    int deletedCount = operateLogMapper.cleanOldLogs(KEEP_COUNT);
                    log.info("执行日志清理，删除了 {} 条旧日志，保留了最新的 {} 条日志", deletedCount, KEEP_COUNT);
                    
                    if (deletedCount > 0) {
                        String message = "日志清理成功，删除了" + deletedCount + "条旧日志";
                        return Result.success(message);
                    } else {
                        String message = "日志已清理完成，当前共" + totalCount + "条日志（未超过保留数量" + KEEP_COUNT + "条）";
                        return Result.error(message);
                    }
                }
            }
        } catch (Exception e) {
            log.error("清理操作日志失败: {}", e.getMessage(), e);
            String errorMessage = "清理操作日志失败";
            
            if (e.getMessage() != null) {
                if (e.getMessage().contains("Unknown column")) {
                    errorMessage += "：数据库字段不存在，请联系管理员检查数据库结构";
                } else if (e.getMessage().contains("syntax error")) {
                    errorMessage += "：SQL语法错误，请联系管理员检查数据库配置";
                } else if (e.getMessage().contains("connection") || e.getMessage().contains("Connection")) {
                    errorMessage += "：数据库连接失败，请检查数据库服务是否正常";
                } else if (e.getMessage().contains("timeout")) {
                    errorMessage += "：操作超时，请稍后重试";
                } else if (e.getMessage().contains("constraint")) {
                    errorMessage += "：数据约束冲突，请联系管理员";
                } else {
                    errorMessage += "：" + e.getMessage();
                }
            } else {
                errorMessage += "：未知错误，请联系管理员";
            }
            
            return Result.error(errorMessage);
        }
    }

    /**
     * 清理操作日志（自动清理逻辑）
     * 按角色自动清理，当某个角色的日志数量达到阈值时自动清理
     */
    @Log(operationType = "CLEAN", module = "LOG_MANAGEMENT", description = "按阈值自动清理操作日志")
    @DeleteMapping("/operation/clean/auto")
    @PreAuthorize("hasAuthority('log:delete')")
    public Result autoCleanOperationLogs() {
        try {
            StringBuilder result = new StringBuilder();
            String[] roles = {"STUDENT", "TEACHER", "ADMIN", "COMPANY"};
            boolean cleaned = false;
            
            for (String role : roles) {
                int totalCount = operateLogMapper.countByRole(role);
                int threshold = getThresholdByRole(role);
                int keepCount = getKeepCountByRole(role);
                
                log.info("{}角色日志统计: 当前 {} 条，阈值 {} 条，保留 {} 条", getRoleName(role), totalCount, threshold, keepCount);
                
                if (totalCount >= threshold) {
                    List<Long> keepIds = operateLogMapper.getKeepLogIdsByRole(role, keepCount);
                    int deletedCount = operateLogMapper.cleanOldLogsByRole(role, keepIds);
                    log.info("执行{}角色日志清理，删除了 {} 条旧日志，保留了最新的 {} 条日志", getRoleName(role), deletedCount, keepCount);
                    result.append(getRoleName(role)).append("日志清理成功，删除了").append(deletedCount).append("条；");
                    cleaned = true;
                }
            }
            
            if (cleaned) {
                return Result.success(result.toString());
            } else {
                return Result.success("所有角色日志数量均未达到清理阈值");
            }
        } catch (Exception e) {
            log.error("自动清理操作日志失败: {}", e.getMessage(), e);
            return Result.error("自动清理操作日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取角色对应的清理阈值
     * 学生日志：500条（学生操作频繁，日志量大）
     * 教师日志：300条（教师操作相对较少）
     * 管理员日志：200条（管理员操作较少但重要）
     * 企业日志：300条（企业操作相对较少）
     */
    private int getThresholdByRole(String role) {
        return switch (role) {
            case "STUDENT" -> 500;
            case "TEACHER" -> 300;
            case "ADMIN" -> 200;
            case "COMPANY" -> 300;
            default -> 1000;
        };
    }

    /**
     * 获取角色对应的保留数量
     * 学生日志：100条（保留最近100条）
     * 教师日志：80条（保留最近80条）
     * 管理员日志：50条（保留最近50条，重要日志）
     * 企业日志：80条（保留最近80条）
     */
    private int getKeepCountByRole(String role) {
        return switch (role) {
            case "STUDENT" -> 100;
            case "TEACHER" -> 80;
            case "ADMIN" -> 50;
            case "COMPANY" -> 80;
            default -> 200;
        };
    }

    /**
     * 获取角色中文名称
     */
    private String getRoleName(String role) {
        return switch (role) {
            case "STUDENT" -> "学生";
            case "TEACHER" -> "教师";
            case "ADMIN" -> "管理员";
            case "COMPANY" -> "企业";
            default -> role;
        };
    }
    
    /**
     * 自动清理日志
     * 当日志总数达到1000条时，自动删除靠后的800条数据（保留最新的200条）
     */
    private void autoCleanLogs() {
        try {
            // 获取当前日志总数
            int totalCount = operateLogMapper.countTotal();
            
            // 设置阈值：当日志总数达到1000条时执行清理
            int threshold = 1000;
            // 设置保留数量：保留最新的200条日志
            int keepCount = 200;
            
            if (totalCount >= threshold) {
                // 计算需要删除的日志数量
                int deleteCount = totalCount - keepCount;
                // 执行删除操作
                operateLogMapper.deleteOldLogs(deleteCount);
                System.out.println("自动清理日志成功：当前日志总数 " + totalCount + "，删除了 " + deleteCount + " 条旧日志，保留了最新的 " + keepCount + " 条日志");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("自动清理日志失败：" + e.getMessage());
        }
    }
    

}