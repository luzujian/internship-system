package com.gdmu.service;

import com.gdmu.entity.InterviewInvitation;

import java.util.List;

public interface InterviewInvitationService {

    void create(InterviewInvitation invitation);

    void updateStatus(Long id, String status, String rejectReason);

    List<InterviewInvitation> findByStudentId(Long studentId);

    InterviewInvitation findByStudentAndPosition(Long studentId, Long positionId);

    InterviewInvitation findById(Long id);

    List<InterviewInvitation> findByStudentIdAndStatus(Long studentId, String status);
}