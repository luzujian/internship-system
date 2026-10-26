package com.internship.service.impl;

import com.internship.entity.CompanyUser;
import com.internship.entity.InternshipApplicationEntity;
import com.internship.entity.Position;
import com.internship.entity.StudentJobApplication;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.InternshipApplicationMapper;
import com.internship.mapper.PositionMapper;
import com.internship.mapper.StudentJobApplicationMapper;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.InternshipFlowRules;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.StudentJobApplicationStatuses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static com.internship.service.InternshipProgressEvents.append;

@Service
public class StudentJobApplicationServiceImpl implements StudentJobApplicationService {

    @Autowired
    private StudentJobApplicationMapper mapper;

    @Autowired
    private PositionMapper positionMapper;

    @Autowired
    private InternshipApplicationMapper internshipApplicationMapper;

    @Autowired
    private CompanyUserMapper companyUserMapper;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Override
    public List<StudentJobApplication> findByStudentId(Long studentId) {
        return mapper.findByStudentId(studentId);
    }

    @Override
    public StudentJobApplication findById(Long id) {
        return mapper.findById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int create(StudentJobApplication application) {
        application.setStageNo(1);
        application.setStageAssignmentId(null);
        application.setSourceType(InternshipFlowRules.SOURCE_NEW_APPLICATION);

        // 如果 companyId 为 null 但 companyName 不为空，尝试通过公司名查找 companyId
        if (application.getCompanyId() == null && application.getCompanyName() != null && !application.getCompanyName().isEmpty()) {
            CompanyUser company = companyUserMapper.findByCompanyName(application.getCompanyName());
            if (company != null) {
                application.setCompanyId(company.getId());
            }
        }

        application.setStatus("pending");
        application.setRejectReason(null);

        // 岗位锁仅用于串行化同岗位防重检查；申请不会占用最终名额。
        if (application.getPositionId() != null) {
            Position position = positionMapper.findByIdForUpdate(application.getPositionId());
            if (position == null) {
                throw new BusinessException("岗位不存在");
            }

            application.setCompanyId(position.getCompanyId());
            application.setPositionName(position.getPositionName());
            CompanyUser owner = companyUserMapper.findById(position.getCompanyId());
            application.setCompanyName(owner == null ? null : owner.getCompanyName());

            StudentJobApplication latest = mapper.findLatestByStudentAndPositionForUpdate(
                    application.getStudentId(), application.getPositionId());
            if (latest != null && StudentJobApplicationStatuses.blocksRetry(latest.getStatus())) {
                throw new BusinessException("您已申请过该岗位");
            }
        }

        // Share one timestamp with the mirror; apply_date alone only stores the calendar date.
        Date submittedAt = new Date();
        application.setApplyDate(submittedAt);
        application.setCreateTime(submittedAt);
        application.setUpdateTime(submittedAt);
        int result = mapper.insert(application);

        if (result > 0) {
            append(progressRecordService,
                    application.getStudentId(),
                    "job_applied",
                    "已投递岗位",
                    application.getCompanyName() + " · " + application.getPositionName(),
                    "pending",
                    application.getId(),
                    application.getApplyDate());
        }

        // 同时创建 internship_application 记录，以便企业端可以查看。
        // 两张申请表与动态必须在同一事务内成功，不能吞掉异常后留下半完成状态。
        if (result > 0 && application.getPositionId() != null && application.getCompanyId() != null) {
            InternshipApplicationEntity internshipApp = new InternshipApplicationEntity();
            internshipApp.setStudentId(application.getStudentId());
            internshipApp.setStudentName(application.getStudentName());
            internshipApp.setStudentUserId(application.getStudentNo());
            internshipApp.setPhone(application.getPhone());
            internshipApp.setEmail(application.getEmail());
            internshipApp.setMajor(application.getMajor());
            internshipApp.setGrade(application.getGrade());
            internshipApp.setPositionId(application.getPositionId());
            internshipApp.setPositionName(application.getPositionName());
            internshipApp.setCompanyId(application.getCompanyId());
            internshipApp.setStatus("pending");
            internshipApp.setViewed(false);
            internshipApp.setApplyTime(submittedAt);
            internshipApp.setCreateTime(submittedAt);
            internshipApp.setUpdateTime(submittedAt);
            internshipApp.setStageNo(application.getStageNo());
            internshipApp.setStageAssignmentId(application.getStageAssignmentId());
            internshipApp.setSourceType(application.getSourceType());
            if (internshipApplicationMapper.insert(internshipApp) <= 0) {
                throw new BusinessException("岗位申请同步失败");
            }
        }

        return result;
    }

    @Override
    public int update(StudentJobApplication application) {
        return mapper.update(application);
    }

    @Override
    public int updateStatusIfActive(Long id, String status, String rejectReason) {
        return mapper.updateStatusIfActive(id, status, rejectReason);
    }

    @Override
    public int updateLatestStatusIfActive(Long studentId, Long positionId, String status, String rejectReason) {
        return mapper.updateLatestStatusIfActive(studentId, positionId, status, rejectReason);
    }

    @Override
    public int claimPendingForDirectHire(Long id, Long companyId) {
        return mapper.claimPendingForDirectHire(id, companyId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id, Long studentId) {
        StudentJobApplication existing = mapper.findById(id);
        if (existing == null) {
            throw new BusinessException("申请记录不存在");
        }
        if (!existing.getStudentId().equals(studentId)) {
            throw new BusinessException("无权删除此申请");
        }
        if (!"pending".equals(existing.getStatus())) {
            throw new BusinessException("只有待审核申请可以撤回");
        }
        int result = mapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException("申请状态已变化，请刷新后重试");
        }

        if (result > 0) {
            if (existing.getPositionId() != null) {
                internshipApplicationMapper.updateLatestStatusByStudentAndPosition(
                        existing.getStudentId(), existing.getPositionId(), "withdrawn");
            }
            append(progressRecordService,
                    existing.getStudentId(),
                    "job_withdrawn",
                    "已撤回岗位申请",
                    existing.getCompanyName() + " · " + existing.getPositionName(),
                    "cancelled",
                    existing.getId(),
                    new Date());
        }

        return result;
    }

    @Override
    public List<StudentJobApplication> findByCompanyId(Long companyId) {
        return mapper.findByCompanyId(companyId);
    }

}
