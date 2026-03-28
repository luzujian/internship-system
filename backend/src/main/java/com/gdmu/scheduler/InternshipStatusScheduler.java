package com.gdmu.scheduler;

import com.gdmu.entity.InternshipTimeSettings;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.mapper.StudentInternshipStatusMapper;
// 移除不存在的 InternshipStatusService 导入
import com.gdmu.service.InternshipTimeSettingsService;
import com.gdmu.service.StudentInternshipStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * 实习状态自动更新调度器
 * 1. 当实习开始时间到达时，自动将学生的实习状态从"已确定"(2)变更为"实习中"(3)
 * 2. 当应聘时间开始时，自动将学生的实习状态从"已结束"(4)变更为"无offer"(0)
 */
@Slf4j
@Component
public class InternshipStatusScheduler implements ApplicationRunner {

    @Autowired
    private StudentInternshipStatusMapper statusMapper;

    @Autowired
    private StudentInternshipStatusService statusService;

    @Autowired
    private InternshipTimeSettingsService timeSettingsService;

    /**
     * 应用启动时执行一次检查
     * 检查是否有学生的实习开始时间已到但状态还是"已确定"的
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("应用启动，开始检查实习状态是否需要自动更新...");
        checkAndUpdateInternshipStatus();
        checkAndResetFinishedStudents();
    }

    /**
     * 每天凌晨1点执行一次检查
     * 将所有实习开始时间已到且状态为"已确定"(2)的学生状态变更为"实习中"(3)
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void checkAndUpdateInternshipStatus() {
        log.info("开始执行实习状态自动更新任务...");

        try {
            // 查询所有状态为"已确定"(2)的学生实习状态记录
            List<StudentInternshipStatus> confirmedStatuses = statusMapper.list(null, null, null, 2, null, null, null, null, null, null);

            if (confirmedStatuses == null || confirmedStatuses.isEmpty()) {
                log.info("没有状态为已确定的学生，无需更新");
                return;
            }

            Date now = new Date();
            int updateCount = 0;

            for (StudentInternshipStatus status : confirmedStatuses) {
                try {
                    // 检查是否有实习开始时间，并且实习开始时间已到
                    if (status.getInternshipStartTime() != null && !status.getInternshipStartTime().after(now)) {
                        // 实习开始时间已到，自动将状态从"已确定"(2)变更为"实习中"(3)
                        status.setStatus(3);
                        status.setUpdateTime(now);
                        statusMapper.update(status);
                        updateCount++;

                        log.info("学生ID: {} 的实习状态已从已确定变更为实习中，实习开始时间: {}",
                                status.getStudentId(), status.getInternshipStartTime());
                    }
                } catch (Exception e) {
                    log.error("更新学生ID: {} 的实习状态时发生错误: {}", status.getStudentId(), e.getMessage(), e);
                }
            }

            log.info("实习状态自动更新任务执行完成，共更新了 {} 个学生的状态", updateCount);

        } catch (Exception e) {
            log.error("执行实习状态自动更新任务时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 每天凌晨1点30分执行一次检查
     * 当应聘时间开始时，自动将状态为"已结束"(4)的学生状态变更为"无offer"(0)
     */
    @Scheduled(cron = "0 30 1 * * ?")
    public void checkAndResetFinishedStudents() {
        log.info("开始执行已结束学生状态重置任务...");

        try {
            // 获取应聘时间设置
            InternshipTimeSettings settings = timeSettingsService.findLatest();
            if (settings == null || settings.getApplicationStartDate() == null) {
                log.info("未设置应聘开始时间，跳过重置任务");
                return;
            }

            LocalDate today = LocalDate.now();
            LocalDate applicationStartDate = LocalDate.parse(settings.getApplicationStartDate(), DateTimeFormatter.ISO_LOCAL_DATE);

            // 检查今天是否在应聘开始时间或之后
            if (today.isBefore(applicationStartDate)) {
                log.info("当前日期 {} 早于应聘开始时间 {}，跳过重置任务", today, applicationStartDate);
                return;
            }

            log.info("应聘时间已开始，当前日期: {}，应聘开始时间: {}，开始重置已结束状态的学生...",
                    today, applicationStartDate);

            // 查询所有状态为"已结束"(4)的学生实习状态记录
            List<StudentInternshipStatus> finishedStatuses = statusMapper.list(null, null, null, 4, null, null, null, null, null, null);

            if (finishedStatuses == null || finishedStatuses.isEmpty()) {
                log.info("没有状态为已结束的学生，无需重置");
                return;
            }

            Date now = new Date();
            int resetCount = 0;

            for (StudentInternshipStatus status : finishedStatuses) {
                try {
                    // 将状态从"已结束"(4)变更为"无offer"(0)
                    status.setStatus(0);
                    status.setUpdateTime(now);
                    statusMapper.update(status);
                    resetCount++;

                    log.info("学生ID: {} 的实习状态已从已结束重置为无offer", status.getStudentId());
                } catch (Exception e) {
                    log.error("重置学生ID: {} 的实习状态时发生错误: {}", status.getStudentId(), e.getMessage(), e);
                }
            }

            log.info("已结束学生状态重置任务执行完成，共重置了 {} 个学生的状态", resetCount);

        } catch (Exception e) {
            log.error("执行已结束学生状态重置任务时发生异常: {}", e.getMessage(), e);
        }
    }
}
