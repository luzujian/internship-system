package com.gdmu.controller;

import com.gdmu.entity.InternshipProgressRecord;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentJobApplication;
import com.gdmu.entity.User;
import com.gdmu.service.InternshipProgressRecordService;
import com.gdmu.service.StudentJobApplicationService;
import com.gdmu.service.UserService;

import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/student/job-applications")
@PreAuthorize("hasRole('STUDENT')")
public class StudentJobApplicationController {

    @Autowired
    private UserService userService;

    @Autowired
    private StudentJobApplicationService applicationService;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        String username = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        return userService.findByUsername(username);
    }

    @GetMapping
    public Result list() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            return Result.success(applicationService.findByStudentId(user.getId()));
        } catch (Exception e) {
            log.error("获取申请列表失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    @PostMapping
    public Result create(@RequestBody StudentJobApplication application) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            // 强制使用JWT中的studentId，不信任前端传入值
            application.setStudentId(user.getId());
            applicationService.create(application);

            // 同步写入进度记录
            InternshipProgressRecord record = new InternshipProgressRecord();
            record.setStudentId(user.getId());
            record.setEventType("job_application");
            record.setEventTitle("投递申请");
            record.setDescription(application.getPositionName() + " @ " + application.getCompanyName());
            record.setStatus("pending");
            record.setRelatedId(application.getId());
            record.setEventTime(new Date());
            progressRecordService.saveRecord(record);

            return Result.success("申请成功");
        } catch (Exception e) {
            log.error("创建申请失败: {}", e.getMessage(), e);
            return Result.error("申请失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            applicationService.deleteById(id, user.getId());
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除申请失败: {}", e.getMessage(), e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}
