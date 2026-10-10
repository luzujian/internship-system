package com.internship.service;

import com.internship.entity.InterviewInvitation;
import com.internship.entity.dto.StudentPositionKey;

import java.util.List;

public interface InterviewInvitationService {

    void create(InterviewInvitation invitation);

    void updateStatus(Long id, String status, String rejectReason);

    List<InterviewInvitation> findByStudentId(Long studentId);

    InterviewInvitation findByStudentAndPosition(Long studentId, Long positionId);

    List<InterviewInvitation> findLatestForStudentPositions(List<StudentPositionKey> pairs);

    InterviewInvitation findById(Long id);

    List<InterviewInvitation> findByStudentIdAndStatus(Long studentId, String status);
}
