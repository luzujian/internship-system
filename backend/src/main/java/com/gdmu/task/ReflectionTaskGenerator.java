package com.gdmu.task;

import com.gdmu.entity.*;
import com.gdmu.mapper.InternshipReflectionMapper;
import com.gdmu.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 实习心得任务生成器
 * 根据辅导员的提交周期设置，自动为学生生成待提交的实习心得任务
 */
@Slf4j
@Component
public class ReflectionTaskGenerator {

    @Autowired
    private InternshipTimeSettingsService timeSettingsService;

    @Autowired
    private StudentInternshipStatusService statusService;

    @Autowired
    private ClassCounselorRelationService classCounselorRelationService;

    @Autowired
    private CounselorAISettingsService counselorAISettingsService;

    @Autowired
    private InternshipReflectionMapper reflectionMapper;

    @Autowired
    private UserService userService;

    /**
     * 每天凌晨2点执行，检查并生成实习心得任务
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void generateReflectionTasks() {
        log.info("开始生成实习心得提交任务");

        try {
            // 1. 获取实习时间设置
            InternshipTimeSettings settings = timeSettingsService.findLatest();
            if (settings == null || settings.getReportCycle() == null || settings.getReportCycle() <= 0) {
                log.info("未设置实习心得提交周期，跳过任务生成");
                return;
            }

            Integer reportCycle = settings.getReportCycle();
            log.info("实习心得提交周期: {} 天", reportCycle);

            // 2. 获取所有实习中的学生 (status=2 表示已确认实习)
            List<StudentInternshipStatus> interningStudents = statusService.list(
                    null, null, null, 2, null, null, null, null, null, null);

            if (interningStudents == null || interningStudents.isEmpty()) {
                log.info("没有实习中的学生，跳过任务生成");
                return;
            }

            log.info("共有 {} 个实习中的学生", interningStudents.size());

            int generatedCount = 0;

            for (StudentInternshipStatus studentStatus : interningStudents) {
                try {
                    // 3. 检查学生是否有未提交的实习心得任务
                    List<InternshipReflection> pendingReflections = reflectionMapper.list(
                            studentStatus.getStudentId(), null, null, 0);

                    // 如果有未提交的任务，跳过
                    if (pendingReflections != null && !pendingReflections.isEmpty()) {
                        log.debug("学生 {} 已有未提交的实习心得任务，跳过", studentStatus.getStudentId());
                        continue;
                    }

                    // 4. 检查学生最近一次提交的实习心得距今是否超过一个周期
                    List<InternshipReflection> recentReflections = reflectionMapper.list(studentStatus.getStudentId(), null, null, null);
                    InternshipReflection lastReflection = null;
                    if (recentReflections != null && !recentReflections.isEmpty()) {
                        lastReflection = recentReflections.get(0);
                        Date lastSubmitTime = lastReflection.getSubmitTime();
                        if (lastSubmitTime != null) {
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTime(lastSubmitTime);
                            calendar.add(Calendar.DAY_OF_YEAR, reportCycle);
                            Date nextDeadline = calendar.getTime();

                            // 如果还没到下一个提交周期，跳过
                            if (new Date().before(nextDeadline)) {
                                log.debug("学生 {} 距离上次提交未超过周期，跳过", studentStatus.getStudentId());
                                continue;
                            }
                        }
                    }

                    // 5. 获取学生的辅导员
                    Long counselorId = classCounselorRelationService.findCounselorIdByStudentId(studentStatus.getStudentId());
                    if (counselorId == null) {
                        log.warn("学生 {} 没有对应的辅导员，跳过", studentStatus.getStudentId());
                        continue;
                    }

                    // 6. 检查辅导员是否开启了AI评分
                    if (!counselorAISettingsService.isAiScoringEnabled(counselorId)) {
                        log.debug("辅导员 {} 未开启AI评分，跳过", counselorId);
                        continue;
                    }

                    // 7. 获取学生信息
                    User student = userService.findById(studentStatus.getStudentId());
                    if (student == null) {
                        log.warn("学生 {} 信息不存在，跳过", studentStatus.getStudentId());
                        continue;
                    }

                    // 8. 计算第几期
                    int periodNumber = (recentReflections != null ? recentReflections.size() : 0) + 1;

                    // 9. 计算截止日期
                    Calendar deadlineCalendar = Calendar.getInstance();
                    deadlineCalendar.add(Calendar.DAY_OF_YEAR, reportCycle);
                    Date deadline = deadlineCalendar.getTime();

                    // 10. 创建待提交的实习心得任务
                    InternshipReflection reflection = new InternshipReflection();
                    reflection.setStudentId(studentStatus.getStudentId());
                    reflection.setStudentName(student.getName());
                    reflection.setStudentUserId(student.getUsername());
                    reflection.setInternshipStatusId(studentStatus.getId());
                    reflection.setPeriodNumber(periodNumber);
                    reflection.setTaskType("auto");
                    reflection.setDeadline(deadline);
                    reflection.setCounselorId(counselorId);
                    reflection.setStatus(0); // 待提交
                    reflection.setRemark("0"); // 草稿状态
                    reflection.setSubmitTime(new Date());
                    reflection.setCreateTime(new Date());
                    reflection.setUpdateTime(new Date());
                    reflection.setDeleted(0);
                    // content 在提交时才会填充，这里设置为空字符串
                    reflection.setContent("");

                    reflectionMapper.insert(reflection);
                    generatedCount++;

                    log.info("为学生 {} 生成了第 {} 期实习心得任务，截止日期: {}",
                            student.getName(), periodNumber, deadline);

                } catch (Exception e) {
                    log.error("为学生 {} 生成任务时出错: {}", studentStatus.getStudentId(), e.getMessage(), e);
                }
            }

            log.info("实习心得任务生成完成，共生成 {} 个任务", generatedCount);

        } catch (Exception e) {
            log.error("生成实习心得任务时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 学生提交实习心得后，立即生成下一次的任务
     * @param studentId 学生ID
     * @param counselorId 辅导员ID
     */
    public void generateNextTask(Long studentId, Long counselorId) {
        try {
            // 检查辅导员是否开启了AI评分
            if (!counselorAISettingsService.isAiScoringEnabled(counselorId)) {
                return;
            }

            // 获取实习时间设置
            InternshipTimeSettings settings = timeSettingsService.findLatest();
            if (settings == null || settings.getReportCycle() == null || settings.getReportCycle() <= 0) {
                return;
            }

            Integer reportCycle = settings.getReportCycle();

            // 获取学生信息
            User student = userService.findById(studentId);
            if (student == null) {
                return;
            }

            // 获取学生的实习状态
            StudentInternshipStatus studentStatus = statusService.findByStudentId(studentId);
            if (studentStatus == null || studentStatus.getStatus() != 2) {
                // 不是实习中状态，不生成任务
                return;
            }

            // 计算当前是第几期
            List<InternshipReflection> recentReflections = reflectionMapper.list(studentId, null, null, null);
            int periodNumber = (recentReflections != null ? recentReflections.size() : 0) + 1;

            // 计算截止日期
            Calendar deadlineCalendar = Calendar.getInstance();
            deadlineCalendar.add(Calendar.DAY_OF_YEAR, reportCycle);
            Date deadline = deadlineCalendar.getTime();

            // 创建待提交的实习心得任务
            InternshipReflection reflection = new InternshipReflection();
            reflection.setStudentId(studentId);
            reflection.setStudentName(student.getName());
            reflection.setStudentUserId(student.getUsername());
            reflection.setInternshipStatusId(studentStatus.getId());
            reflection.setPeriodNumber(periodNumber);
            reflection.setTaskType("auto");
            reflection.setDeadline(deadline);
            reflection.setCounselorId(counselorId);
            reflection.setStatus(0); // 待提交
            reflection.setRemark("0"); // 草稿状态
            reflection.setSubmitTime(new Date());
            reflection.setCreateTime(new Date());
            reflection.setUpdateTime(new Date());
            reflection.setDeleted(0);
            // content 在提交时才会填充，这里设置为空字符串
            reflection.setContent("");

            reflectionMapper.insert(reflection);

            log.info("为学生 {} 提前生成了第 {} 期实习心得任务，截止日期: {}",
                    student.getName(), periodNumber, deadline);

        } catch (Exception e) {
            log.error("为学生 {} 生成下一次任务时出错: {}", studentId, e.getMessage(), e);
        }
    }
}
