package com.internship.controller;

import com.internship.entity.Result;
import com.internship.entity.InterviewInvitation;
import com.internship.entity.User;
import com.internship.entity.dto.StudentPositionKey;
import com.internship.service.DirectHireService;
import com.internship.service.InterviewInvitationService;
import com.internship.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/student/interviews")
@PreAuthorize("hasRole('STUDENT')")
public class StudentInterviewController {

    @Autowired
    private InterviewInvitationService interviewInvitationService;

    @Autowired
    private DirectHireService directHireService;

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
                List<InterviewInvitation> invitations = interviewInvitationService.findByStudentIdAndStatus(studentId, statusStr);
                markDirectHire(invitations);
                return Result.success(invitations);
            }
        }
        List<InterviewInvitation> invitations = interviewInvitationService.findByStudentId(studentId);
        markDirectHire(invitations);
        return Result.success(invitations);
    }

    private void markDirectHire(List<InterviewInvitation> invitations) {
        Set<StudentPositionKey> directHirePairs = directHireService.findDirectHirePairs(invitations.stream()
                .map(invitation -> new StudentPositionKey(invitation.getStudentId(), invitation.getPositionId())).toList());
        for (InterviewInvitation invitation : invitations) {
            invitation.setDirectHire(directHirePairs.contains(
                    new StudentPositionKey(invitation.getStudentId(), invitation.getPositionId())));
        }
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
