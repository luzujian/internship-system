package com.internship.service;

import com.internship.entity.CompanyUser;
import com.internship.entity.InterviewInvitation;
import com.internship.entity.Position;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.dto.StudentPositionKey;
import com.internship.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.internship.service.InternshipProgressEvents.append;

@Service
public class DirectHireService {

    public static final String DIRECT_HIRE_METHOD = "direct_hire";
    private static final int LOOKUP_BATCH_SIZE = 200;

    private final StudentJobApplicationService applicationService;
    private final PositionService positionService;
    private final InterviewInvitationService invitationService;
    private final InternshipProgressRecordService progressRecordService;
    private final CompanyUserService companyUserService;

    public DirectHireService(StudentJobApplicationService applicationService,
                             PositionService positionService,
                             InterviewInvitationService invitationService,
                             InternshipProgressRecordService progressRecordService,
                             CompanyUserService companyUserService) {
        this.applicationService = applicationService;
        this.positionService = positionService;
        this.invitationService = invitationService;
        this.progressRecordService = progressRecordService;
        this.companyUserService = companyUserService;
    }

    @Transactional(rollbackFor = Exception.class)
    public DirectHireResult directHire(Long companyId, Long applicationId) {
        StudentJobApplication application = applicationService.findById(applicationId);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }
        if (!companyId.equals(application.getCompanyId())) {
            throw new BusinessException("无权操作此申请");
        }
        if (!"pending".equals(application.getStatus())) {
            throw new BusinessException("只有待审核申请可以直接录取");
        }

        Position position = positionService.findById(application.getPositionId());
        if (position == null) {
            throw new BusinessException("岗位不存在");
        }
        InterviewInvitation existingInvitation = invitationService.findByStudentAndPosition(
                application.getStudentId(), application.getPositionId());
        if (existingInvitation != null
                && !StudentJobApplicationStatuses.canRetry(existingInvitation.getStatus())) {
            throw new BusinessException("该学生已存在此岗位的面试记录");
        }

        CompanyUser company = companyUserService.findById(companyId);
        String companyName = company != null && company.getCompanyName() != null
                ? company.getCompanyName() : application.getCompanyName();

        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setStudentId(application.getStudentId());
        invitation.setCompanyId(companyId);
        invitation.setPositionId(application.getPositionId());
        invitation.setPositionName(position.getPositionName());
        invitation.setCompanyName(companyName);
        invitation.setInterviewMethod(DIRECT_HIRE_METHOD);
        invitation.setStatus("interview_passed");
        invitation.setRemark("企业直接录取，无需参加面试");
        if (company != null) {
            invitation.setCompanyAddress(company.getAddress());
            invitation.setWebsite(company.getWebsite());
        }
        invitation.setContactPerson(position.getContactPerson());
        invitation.setContactPhone(position.getContactPhone());
        invitation.setContactEmail(position.getContactEmail());
        if (applicationService.claimPendingForDirectHire(applicationId, companyId) <= 0) {
            throw new BusinessException("申请状态已变化，请刷新后重试");
        }
        application.setStatus("interview_passed");
        invitationService.create(invitation);

        saveDirectHireProgress(application, invitation, companyName);

        return new DirectHireResult(
                application.getId(),
                invitation.getId(),
                application.getStudentId(),
                companyId,
                application.getPositionId(),
                companyName,
                position.getPositionName(),
                true);
    }

    public boolean isDirectHire(Long studentId, Long positionId) {
        InterviewInvitation invitation = invitationService.findByStudentAndPosition(studentId, positionId);
        return invitation != null && DIRECT_HIRE_METHOD.equals(invitation.getInterviewMethod());
    }

    /** Read fresh flags in bounded batches; the latest invitation may differ from a displayed history row. */
    public Set<StudentPositionKey> findDirectHirePairs(Collection<StudentPositionKey> pairs) {
        Set<StudentPositionKey> uniquePairs = new LinkedHashSet<>();
        for (StudentPositionKey pair : pairs) {
            // SQL equality with a null student or position has never matched an invitation.
            if (pair != null && pair.studentId() != null && pair.positionId() != null) {
                uniquePairs.add(pair);
            }
        }
        List<StudentPositionKey> requested = new ArrayList<>(uniquePairs);
        Set<StudentPositionKey> directHirePairs = new HashSet<>();
        for (int start = 0; start < requested.size(); start += LOOKUP_BATCH_SIZE) {
            List<StudentPositionKey> batch = requested.subList(start,
                    Math.min(start + LOOKUP_BATCH_SIZE, requested.size()));
            for (InterviewInvitation invitation : invitationService.findLatestForStudentPositions(batch)) {
                if (DIRECT_HIRE_METHOD.equals(invitation.getInterviewMethod())) {
                    directHirePairs.add(new StudentPositionKey(invitation.getStudentId(), invitation.getPositionId()));
                }
            }
        }
        return directHirePairs;
    }

    private void saveDirectHireProgress(StudentJobApplication application,
                                        InterviewInvitation invitation,
                                        String companyName) {
        append(progressRecordService,
                application.getStudentId(),
                "direct_offer",
                "收到企业直接录取 Offer",
                companyName + " · " + application.getPositionName(),
                "success",
                invitation.getId(),
                new Date());
    }

    public record DirectHireResult(Long applicationId,
                                   Long invitationId,
                                   Long studentId,
                                   Long companyId,
                                   Long positionId,
                                   String companyName,
                                   String positionName,
                                   boolean directHire) {
    }
}
