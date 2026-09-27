package com.internship.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.internship.dto.CompanyVisitRecordRequest;
import com.internship.entity.CompanyUser;
import com.internship.entity.CompanyVisitParticipant;
import com.internship.entity.CompanyVisitRecord;
import com.internship.entity.CompanyVisitSummary;
import com.internship.entity.PageResult;
import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.CompanyVisitMapper;
import com.internship.mapper.TeacherUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompanyVisitService {
    private final CompanyVisitMapper companyVisitMapper;
    private final CompanyUserMapper companyUserMapper;
    private final TeacherUserMapper teacherUserMapper;

    public CompanyVisitService(CompanyVisitMapper companyVisitMapper,
                               CompanyUserMapper companyUserMapper,
                               TeacherUserMapper teacherUserMapper) {
        this.companyVisitMapper = companyVisitMapper;
        this.companyUserMapper = companyUserMapper;
        this.teacherUserMapper = teacherUserMapper;
    }

    public PageResult<CompanyVisitRecord> findPage(Integer page,
                                                    Integer pageSize,
                                                    Long companyId,
                                                    String companyName,
                                                    Integer year,
                                                    Long participantTeacherId) {
        PageHelper.startPage(page, pageSize);
        List<CompanyVisitRecord> records = companyVisitMapper.findList(
                companyId, companyName, year, participantTeacherId);
        PageInfo<CompanyVisitRecord> pageInfo = new PageInfo<>(records);
        attachParticipants(pageInfo.getList());
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(), pageInfo.getPages(),
                pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    public CompanyVisitRecord findById(Long id) {
        CompanyVisitRecord record = companyVisitMapper.findById(id);
        if (record != null) {
            record.setParticipants(companyVisitMapper.findParticipantsByRecordId(id));
        }
        return record;
    }

    public List<CompanyVisitSummary> summarizeByCompanyIds(List<Long> companyIds,
                                                           Integer selectedYear,
                                                           Integer currentYear) {
        if (companyIds == null || companyIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<CompanyVisitSummary> summaries = companyVisitMapper.summarizeByCompanyIds(
                companyIds, selectedYear, currentYear);
        for (CompanyVisitSummary summary : summaries) {
            summary.setSelectedYear(selectedYear);
            summary.setVisitedInSelectedYear(summary.getSelectedYearVisitCount() != null
                    && summary.getSelectedYearVisitCount() > 0);
            summary.setVisitedThisYear(summary.getCurrentYearVisitCount() != null
                    && summary.getCurrentYearVisitCount() > 0);
        }
        return summaries;
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyVisitRecord create(CompanyVisitRecordRequest request, Long creatorTeacherId) {
        requireTeacher(creatorTeacherId);
        validateCompany(request.getCompanyId());
        List<Long> participantIds = validateParticipantIds(request.getParticipantTeacherIds());

        LocalDateTime now = LocalDateTime.now();
        CompanyVisitRecord record = new CompanyVisitRecord();
        record.setCompanyId(request.getCompanyId());
        record.setVisitDate(request.getVisitDate());
        record.setRemark(normalizeRemark(request.getRemark()));
        record.setCreatorTeacherId(creatorTeacherId);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        companyVisitMapper.insertRecord(record);
        companyVisitMapper.batchInsertParticipants(buildParticipants(record.getId(), participantIds, now));
        return findById(record.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyVisitRecord update(Long id,
                                     CompanyVisitRecordRequest request,
                                     Long currentTeacherId) {
        CompanyVisitRecord existing = requireRecord(id);
        requireCanManage(existing, currentTeacherId);
        validateCompany(request.getCompanyId());
        List<Long> participantIds = validateParticipantIds(request.getParticipantTeacherIds());

        LocalDateTime now = LocalDateTime.now();
        existing.setCompanyId(request.getCompanyId());
        existing.setVisitDate(request.getVisitDate());
        existing.setRemark(normalizeRemark(request.getRemark()));
        existing.setUpdateTime(now);
        companyVisitMapper.updateRecord(existing);
        companyVisitMapper.deleteParticipantsByRecordId(id);
        companyVisitMapper.batchInsertParticipants(buildParticipants(id, participantIds, now));
        return findById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long currentTeacherId) {
        CompanyVisitRecord existing = requireRecord(id);
        requireCanManage(existing, currentTeacherId);
        companyVisitMapper.deleteParticipantsByRecordId(id);
        companyVisitMapper.deleteRecord(id);
    }

    private CompanyVisitRecord requireRecord(Long id) {
        CompanyVisitRecord record = companyVisitMapper.findById(id);
        if (record == null) {
            throw new BusinessException("走访记录不存在");
        }
        return record;
    }

    private TeacherUser requireTeacher(Long teacherId) {
        if (teacherId == null) {
            throw new BusinessException("未获取到当前教师");
        }
        TeacherUser teacher = teacherUserMapper.findById(teacherId);
        if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())) {
            throw new BusinessException("教师账号不存在或已停用");
        }
        return teacher;
    }

    private void requireCanManage(CompanyVisitRecord record, Long currentTeacherId) {
        if (currentTeacherId == null || !currentTeacherId.equals(record.getCreatorTeacherId())) {
            throw new BusinessException("只能修改或删除本人填写的走访记录");
        }
    }

    private void validateCompany(Long companyId) {
        CompanyUser company = companyUserMapper.findById(companyId);
        if (company == null) {
            throw new BusinessException("企业不存在");
        }
    }

    private List<Long> validateParticipantIds(List<Long> teacherIds) {
        if (teacherIds == null || teacherIds.isEmpty()) {
            throw new BusinessException("走访教师不能为空");
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long teacherId : teacherIds) {
            if (teacherId == null) {
                throw new BusinessException("走访教师不能为空");
            }
            distinctIds.add(teacherId);
        }
        List<Long> ids = new ArrayList<>(distinctIds);
        List<TeacherUser> teachers = teacherUserMapper.selectByIds(ids);
        Map<Long, TeacherUser> teachersById = teachers.stream()
                .collect(Collectors.toMap(TeacherUser::getId, Function.identity()));
        boolean invalid = ids.stream().anyMatch(id -> {
            TeacherUser teacher = teachersById.get(id);
            return teacher == null || !Integer.valueOf(1).equals(teacher.getStatus());
        });
        if (invalid) {
            throw new BusinessException("走访教师不存在或已停用");
        }
        return ids;
    }

    private List<CompanyVisitParticipant> buildParticipants(Long recordId,
                                                             List<Long> teacherIds,
                                                             LocalDateTime now) {
        List<CompanyVisitParticipant> participants = new ArrayList<>(teacherIds.size());
        for (Long teacherId : teacherIds) {
            CompanyVisitParticipant participant = new CompanyVisitParticipant();
            participant.setVisitRecordId(recordId);
            participant.setTeacherId(teacherId);
            participant.setCreateTime(now);
            participants.add(participant);
        }
        return participants;
    }

    private String normalizeRemark(String remark) {
        return remark == null || remark.trim().isEmpty() ? null : remark.trim();
    }

    private void attachParticipants(List<CompanyVisitRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> recordIds = records.stream().map(CompanyVisitRecord::getId).toList();
        Map<Long, List<CompanyVisitParticipant>> participantsByRecordId =
                companyVisitMapper.findParticipantsByRecordIds(recordIds).stream()
                        .collect(Collectors.groupingBy(CompanyVisitParticipant::getVisitRecordId));
        for (CompanyVisitRecord record : records) {
            record.setParticipants(participantsByRecordId.getOrDefault(record.getId(), Collections.emptyList()));
        }
    }
}
