package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.InterviewInvitation;
import com.gdmu.entity.User;
import com.gdmu.service.InterviewInvitationService;
import com.gdmu.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student/interviews")
@PreAuthorize("hasRole('STUDENT')")
public class StudentInterviewController {

    @Autowired
    private InterviewInvitationService interviewInvitationService;

    @Autowired
    private UserService userService;

    @RequestMapping(value = {"", "/list"}, method = RequestMethod.GET)
    public Result list(@RequestParam(required = false) Integer status) {
        Long studentId = getCurrentStudentId();
        if (studentId == null) {
            return Result.success(List.of());
        }
        if (status != null) {
            // pending_interview=0, interview_passed=1, interview_failed=2
            String statusStr = switch (status) {
                case 0 -> "pending_interview";
                case 1 -> "interview_passed";
                case 2 -> "interview_failed";
                default -> null;
            };
            if (statusStr != null) {
                return Result.success(interviewInvitationService.findByStudentIdAndStatus(studentId, statusStr));
            }
        }
        return Result.success(interviewInvitationService.findByStudentId(studentId));
    }

    private Long getCurrentStudentId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        String username = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        User user = userService.findByUsername(username);
        return user != null ? user.getId() : null;
    }
}