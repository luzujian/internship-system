package com.internship.scheduler;

import com.internship.service.TeacherAutoTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 教师自动化任务定时触发器：默认每分钟检查任务是否到达执行时间。
 * 执行动作仍受 AI 能力开关（auto_task / auto_task_notify）约束，关闭即跳过并记日志。
 */
@Slf4j
@Component
public class TeacherAutoTaskScheduler {

    private final TeacherAutoTaskService autoTasks;

    public TeacherAutoTaskScheduler(TeacherAutoTaskService autoTasks) {
        this.autoTasks = autoTasks;
    }

    @Scheduled(cron = "${teacher.auto-task.cron:0 * * * * ?}",
            zone = "${teacher.auto-task.zone:Asia/Shanghai}")
    public void runDueTasks() {
        try {
            autoTasks.runEnabledTasks();
        } catch (Exception e) {
            log.error("教师自动化任务定时触发失败: {}", e.getMessage(), e);
        }
    }
}
