package com.internship.scheduler;

import com.internship.entity.StudentInternshipStatus;
import com.internship.mapper.PositionMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.service.InternshipLifecycleService;
import com.internship.service.PositionCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.Date;
import java.util.List;

/** Advances an accepted placement through its own lifecycle without resetting it for another stage. */
@Slf4j
@Component
@Profile("!local")
public class InternshipStatusScheduler implements ApplicationRunner {
    private final StudentInternshipStatusMapper statusMapper;
    private final InternshipLifecycleService lifecycleService;
    private final PositionMapper positionMapper;
    private final PositionCacheService positionCacheService;

    public InternshipStatusScheduler(StudentInternshipStatusMapper statusMapper,
                                     InternshipLifecycleService lifecycleService,
                                     PositionMapper positionMapper,
                                     PositionCacheService positionCacheService) {
        this.statusMapper = statusMapper;
        this.lifecycleService = lifecycleService;
        this.positionMapper = positionMapper;
        this.positionCacheService = positionCacheService;
    }

    // 到期安全阀:每 5 分钟把实习期已过的在招岗位自动暂停,避免过期岗位继续收投递
    @Scheduled(cron = "0 10/5 * * * ?")
    public void pauseExpiredActivePositions() {
        try {
            int paused = positionMapper.pauseExpiredActivePositions();
            if (paused > 0) {
                log.info("到期安全阀:自动暂停 {} 个已过实习期的在招岗位", paused);
                positionCacheService.clearPositionsCache();
            }
        } catch (Exception e) {
            log.error("自动暂停过期岗位失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        checkAndUpdateInternshipStatus();
        checkAndFinishInternships();
    }

    /** 审批等事件触发的即时检查：对单条状态做一次起止判定，避免等定时任务。 */
    public void checkOne(StudentInternshipStatus status) {
        if (status == null || status.getId() == null) return;
        Date now = new Date();
        try {
            if (Integer.valueOf(2).equals(status.getStatus())
                    && status.getInternshipStartTime() != null
                    && !status.getInternshipStartTime().after(now)) {
                lifecycleService.startConfirmedInternship(status.getId(), status.getInternshipStartTime());
            }
            if (Integer.valueOf(3).equals(status.getStatus())
                    && status.getInternshipEndTime() != null
                    && !status.getInternshipEndTime().after(now)) {
                lifecycleService.finishActiveInternship(status.getId(), status.getInternshipEndTime());
            }
        } catch (Exception e) {
            log.error("即时检查学生 {} 实习状态失败: {}", status.getStudentId(), e.getMessage(), e);
        }
    }

    // 每 5 分钟兜底扫描，保证时间边界到达后尽快流转
    @Scheduled(cron = "0 */5 * * * ?")
    public void checkAndUpdateInternshipStatus() {
        List<StudentInternshipStatus> confirmed = statusMapper.list(
                null, null, null, 2, null, null, null, null, null, null);
        if (confirmed == null || confirmed.isEmpty()) return;
        Date now = new Date();
        for (StudentInternshipStatus status : confirmed) {
            try {
                if (status.getInternshipStartTime() != null
                        && !status.getInternshipStartTime().after(now)) {
                    lifecycleService.startConfirmedInternship(
                            status.getId(), status.getInternshipStartTime());
                }
            } catch (Exception e) {
                log.error("启动学生 {} 的实习状态失败: {}",
                        status.getStudentId(), e.getMessage(), e);
            }
        }
    }

    // 每 5 分钟兜底扫描，与 checkAndUpdateInternshipStatus 错开 30 秒
    @Scheduled(cron = "30 */5 * * * ?")
    public void checkAndFinishInternships() {
        List<StudentInternshipStatus> active = statusMapper.list(
                null, null, null, 3, null, null, null, null, null, null);
        if (active == null || active.isEmpty()) return;
        Date now = new Date();
        for (StudentInternshipStatus status : active) {
            try {
                if (status.getInternshipEndTime() != null
                        && !status.getInternshipEndTime().after(now)) {
                    lifecycleService.finishActiveInternship(
                            status.getId(), status.getInternshipEndTime());
                }
            } catch (Exception e) {
                log.error("结束学生 {} 的实习状态失败: {}",
                        status.getStudentId(), e.getMessage(), e);
            }
        }
    }
}
