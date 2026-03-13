package com.gdmu.scheduler;

import com.gdmu.mapper.OperateLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 日志清理定时任务调度器
 * 用于定期检查并清理系统操作日志，防止日志数量过多
 */
@Component
public class LogCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupScheduler.class);

    @Autowired
    private OperateLogMapper operateLogMapper;

    /**
     * 每天凌晨2点执行一次日志自动清理任务
     * 当日志总数达到1000条时，自动删除旧日志，保留最新的200条
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void autoCleanOperationLogs() {
        try {
            // 获取当前日志总数
            int totalCount = operateLogMapper.countTotal();
            log.info("开始执行定时日志清理，当前操作日志总数: {}", totalCount);

            // 设置阈值：当日志总数达到1000条时执行清理
            int threshold = 1000;
            // 设置保留数量：保留最新的200条日志
            int keepCount = 200;

            if (totalCount >= threshold) {
                // 执行删除操作，保留最新的keepCount条日志
                int deletedCount = operateLogMapper.cleanOldLogs(keepCount);
                log.info("定时日志清理完成：当前日志总数 {}, 删除了 {} 条旧日志，保留了最新的 {} 条日志", 
                        totalCount, deletedCount, keepCount);
            } else {
                // 日志数量未达到清理阈值
                int remainingCount = threshold - totalCount;
                log.info("日志数量未达到清理阈值，还需增加 {} 条日志才会触发自动清理", remainingCount);
            }
        } catch (Exception e) {
            log.error("定时日志清理失败：{}", e.getMessage(), e);
        }
    }
}