package com.gdmu.service.impl;

import com.gdmu.entity.InterviewInvitation;
import com.gdmu.mapper.InterviewInvitationMapper;
import com.gdmu.service.InterviewInvitationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewInvitationServiceImpl implements InterviewInvitationService {

    @Autowired
    private InterviewInvitationMapper interviewInvitationMapper;

    @Override
    public void create(InterviewInvitation invitation) {
        invitation.setCreateTime(LocalDateTime.now());
        invitation.setUpdateTime(LocalDateTime.now());
        interviewInvitationMapper.insert(invitation);
    }

    @Override
    public void updateStatus(Long id, String status, String rejectReason) {
        interviewInvitationMapper.updateStatus(id, status, rejectReason);
    }

    @Override
    public List<InterviewInvitation> findByStudentId(Long studentId) {
        return interviewInvitationMapper.findByStudentId(studentId);
    }

    @Override
    public InterviewInvitation findByStudentAndPosition(Long studentId, Long positionId) {
        return interviewInvitationMapper.findByStudentAndPosition(studentId, positionId);
    }

    @Override
    public InterviewInvitation findById(Long id) {
        return interviewInvitationMapper.findById(id);
    }

    @Override
    public List<InterviewInvitation> findByStudentIdAndStatus(Long studentId, String status) {
        return interviewInvitationMapper.findByStudentIdAndStatus(studentId, status);
    }
}