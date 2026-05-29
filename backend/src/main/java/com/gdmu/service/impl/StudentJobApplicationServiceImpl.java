package com.gdmu.service.impl;

import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.InternshipApplicationEntity;
import com.gdmu.entity.InternshipTimeSettings;
import com.gdmu.entity.Position;
import com.gdmu.entity.StudentJobApplication;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.CompanyUserMapper;
import com.gdmu.mapper.InternshipApplicationMapper;
import com.gdmu.mapper.PositionMapper;
import com.gdmu.mapper.StudentJobApplicationMapper;
import com.gdmu.service.InternshipTimeSettingsService;
import com.gdmu.service.PositionService;
import com.gdmu.service.StudentJobApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@Service
public class StudentJobApplicationServiceImpl implements StudentJobApplicationService {

    @Autowired
    private StudentJobApplicationMapper mapper;

    @Autowired
    private PositionMapper positionMapper;

    @Autowired
    private PositionService positionService;

    @Autowired
    private InternshipApplicationMapper internshipApplicationMapper;

    @Autowired
    private InternshipTimeSettingsService internshipTimeSettingsService;

    @Autowired
    private CompanyUserMapper companyUserMapper;

    /**
     * 校验学生申请时间是否在应聘时间范围内
     */
    private void validateApplicationTime() {
        InternshipTimeSettings settings = internshipTimeSettingsService.findLatest();
        if (settings == null) {
            throw new BusinessException("系统未设置应聘时间，请联系管理员");
        }

        LocalDate today = LocalDate.now();

        // 校验应聘开始时间
        if (settings.getApplicationStartDate() != null && !settings.getApplicationStartDate().isEmpty()) {
            LocalDate startDate = LocalDate.parse(settings.getApplicationStartDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            if (today.isBefore(startDate)) {
                throw new BusinessException("应聘尚未开始，开始时间为：" + settings.getApplicationStartDate());
            }
        }

        // 校验应聘截止时间
        if (settings.getApplicationEndDate() != null && !settings.getApplicationEndDate().isEmpty()) {
            LocalDate endDate = LocalDate.parse(settings.getApplicationEndDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            if (today.isAfter(endDate)) {
                throw new BusinessException("应聘已截止，截止时间为：" + settings.getApplicationEndDate());
            }
        }
    }

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
        // 校验应聘时间范围
        validateApplicationTime();

        // 如果 companyId 为 null 但 companyName 不为空，尝试通过公司名查找 companyId
        if (application.getCompanyId() == null && application.getCompanyName() != null && !application.getCompanyName().isEmpty()) {
            CompanyUser company = companyUserMapper.findByCompanyName(application.getCompanyName());
            if (company != null) {
                application.setCompanyId(company.getId());
            }
        }

        if (application.getStatus() == null) {
            application.setStatus("pending");
        }
        if (application.getApplyDate() == null) {
            application.setApplyDate(new Date());
        }

        // 校验岗位名额 (SELECT ... FOR UPDATE 防止并发超发)
        if (application.getPositionId() != null) {
            Position position = positionMapper.findByIdForUpdate(application.getPositionId());
            if (position == null) {
                throw new BusinessException("岗位不存在");
            }
            if (position.getRemainingQuota() == null || position.getRemainingQuota() <= 0) {
                throw new BusinessException("该岗位已无名额");
            }

            // 检查重复申请
            int existingCount = mapper.countByStudentAndPosition(application.getStudentId(), application.getPositionId());
            if (existingCount > 0) {
                throw new BusinessException("您已申请过该岗位");
            }

            // 扣减名额
            position.setRemainingQuota(position.getRemainingQuota() - 1);
            position.setRecruitedCount((position.getRecruitedCount() == null ? 0 : position.getRecruitedCount()) + 1);
            positionService.update(position);
        }

        int result = mapper.insert(application);

        // 同时创建 internship_application 记录，以便企业端可以查看
        if (result > 0 && application.getPositionId() != null && application.getCompanyId() != null) {
            try {
                InternshipApplicationEntity internshipApp = new InternshipApplicationEntity();
                internshipApp.setStudentId(application.getStudentId());
                internshipApp.setStudentName(application.getStudentName());
                internshipApp.setStudentUserId(application.getStudentNo());
                internshipApp.setPositionId(application.getPositionId());
                internshipApp.setPositionName(application.getPositionName());
                internshipApp.setCompanyId(application.getCompanyId());
                internshipApp.setStatus("pending");
                internshipApp.setViewed(false);
                internshipApp.setApplyTime(new Date());
                internshipApp.setCreateTime(new Date());
                internshipApp.setUpdateTime(new Date());
                internshipApplicationMapper.insert(internshipApp);
            } catch (Exception e) {
                // 记录日志但不阻塞主流程
                System.err.println("创建 internship_application 记录失败: " + e.getMessage());
            }
        }

        return result;
    }

    @Override
    public int update(StudentJobApplication application) {
        return mapper.update(application);
    }

    @Override
    public int deleteById(Long id, Long studentId) {
        StudentJobApplication existing = mapper.findById(id);
        if (existing == null) {
            throw new BusinessException("申请记录不存在");
        }
        if (!existing.getStudentId().equals(studentId)) {
            throw new BusinessException("无权删除此申请");
        }
        int result = mapper.deleteById(id);

        // 回退岗位名额（仅 pending 状态需要回退）
        if (result > 0 && existing.getPositionId() != null && "pending".equals(existing.getStatus())) {
            Position position = positionMapper.findByIdForUpdate(existing.getPositionId());
            if (position != null) {
                position.setRemainingQuota((position.getRemainingQuota() == null ? 0 : position.getRemainingQuota()) + 1);
                position.setRecruitedCount(Math.max(0, (position.getRecruitedCount() == null ? 0 : position.getRecruitedCount()) - 1));
                positionService.update(position);
            }
        }

        return result;
    }

    @Override
    public List<StudentJobApplication> findByCompanyId(Long companyId) {
        return mapper.findByCompanyId(companyId);
    }
}
