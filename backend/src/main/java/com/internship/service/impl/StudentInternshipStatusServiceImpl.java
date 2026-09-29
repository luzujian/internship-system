package com.internship.service.impl;

import com.internship.entity.CompanyUser;
import com.internship.entity.InterviewInvitation;
import com.internship.entity.PageResult;
import com.internship.entity.Position;
import com.internship.entity.StudentInternshipStatus;
import com.internship.enums.InternshipStatusEnum;
import com.internship.enums.RecallStatusEnum;
import com.internship.exception.BusinessException;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.service.CompanyUserService;
import com.internship.service.InternshipCalendarService;
import com.internship.service.InterviewInvitationService;
import com.internship.service.JobSearchStatusService;
import com.internship.service.PositionService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.util.StudentInternshipStatusCache;
import com.internship.validator.StateTransitionValidator;
import com.internship.websocket.AnnouncementWebSocketHandler;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生实习状态服务实现类
 */
@Slf4j
@Service
public class StudentInternshipStatusServiceImpl implements StudentInternshipStatusService {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.internship.agent.business.StudentPlanRefreshService planRefresh;

    private final StudentInternshipStatusMapper studentInternshipStatusMapper;
    private final PositionService positionService;
    private final StateTransitionValidator stateTransitionValidator;
    private final StudentInternshipStatusCache statusCache;
    private final AnnouncementWebSocketHandler webSocketHandler;
    private final InterviewInvitationService interviewService;
    private final CompanyUserService companyUserService;
    private final InternshipCalendarService internshipCalendarService;

    @Autowired
    private JobSearchStatusService jobSearchStatusService;

    @Autowired
    public StudentInternshipStatusServiceImpl(StudentInternshipStatusMapper studentInternshipStatusMapper,
                                           PositionService positionService,
                                           StateTransitionValidator stateTransitionValidator,
                                           StudentInternshipStatusCache statusCache,
                                           AnnouncementWebSocketHandler webSocketHandler,
                                           InterviewInvitationService interviewService,
                                           CompanyUserService companyUserService,
                                           InternshipCalendarService internshipCalendarService) {
        this.studentInternshipStatusMapper = studentInternshipStatusMapper;
        this.positionService = positionService;
        this.stateTransitionValidator = stateTransitionValidator;
        this.statusCache = statusCache;
        this.webSocketHandler = webSocketHandler;
        this.interviewService = interviewService;
        this.companyUserService = companyUserService;
        this.internshipCalendarService = internshipCalendarService;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(StudentInternshipStatus status) {
        log.debug("插入学生实习状态，学生ID: {}", status.getStudentId());
        
        // 参数校验
        validateStatusInfo(status);

        // status>=2 且起止时间为空时，按教师端"重要设置"的全局日历自动填充
        populateGlobalInternshipTimesIfNeeded(status);

        // 设置创建和更新时间
        Date now = new Date();
        status.setCreateTime(now);
        status.setUpdateTime(now);

        int result = studentInternshipStatusMapper.insert(status);
        log.info("学生实习状态插入成功，学生ID: {}", status.getStudentId());
        
        // 更新岗位已招人数（仅在企业确认后(status=2)才更新，status=1为待确认不应扣减名额）
        if (status.getPositionId() != null && status.getStatus() == 2) {
            try {
                positionService.updateRecruitedCount(status.getPositionId());
                log.debug("已更新岗位已招人数，岗位ID: {}", status.getPositionId());

                // 【新增】推送岗位更新给所有学生
                Position position = positionService.findById(status.getPositionId());
                if (position != null) {
                    webSocketHandler.sendPositionUpdateToAll(position);
                    log.debug("已推送岗位更新，岗位ID: {}", status.getPositionId());

                    // 【新增】创建面试邀请记录
                    try {
                        InterviewInvitation existing = interviewService.findByStudentAndPosition(
                            status.getStudentId(), status.getPositionId());
                        if (existing == null) {
                            // 获取企业名称
                            String companyName = "";
                            if (position.getCompanyId() != null) {
                                CompanyUser company = companyUserService.findById(position.getCompanyId());
                                if (company != null) {
                                    companyName = company.getCompanyName();
                                }
                            }

                            InterviewInvitation invitation = new InterviewInvitation();
                            invitation.setStudentId(status.getStudentId());
                            invitation.setPositionId(status.getPositionId());
                            invitation.setCompanyId(status.getCompanyId());
                            invitation.setPositionName(position.getPositionName());
                            invitation.setCompanyName(companyName);
                            // Date转换为LocalDateTime
                            if (position.getInterviewTime() != null) {
                                invitation.setInterviewTime(position.getInterviewTime().toInstant()
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                            }
                            invitation.setInterviewLocation(position.getInterviewLocation());
                            invitation.setInterviewMethod(position.getInterviewMethod());
                            invitation.setRemark(position.getInterviewRemark());
                            invitation.setStatus("pending_interview");
                            interviewService.create(invitation);

                            // WebSocket推送面试邀请给学生
                            Map<String, Object> data = new HashMap<>();
                            data.put("id", invitation.getId());
                            data.put("positionId", position.getId());
                            data.put("positionName", position.getPositionName());
                            data.put("companyId", position.getCompanyId());
                            data.put("companyName", companyName);
                            data.put("interviewTime", position.getInterviewTime());
                            data.put("interviewLocation", position.getInterviewLocation());
                            data.put("interviewMethod", position.getInterviewMethod());
                            data.put("status", "pending_interview");
                            webSocketHandler.sendInterviewCreateToUser(status.getStudentId(), data);
                            log.debug("已创建面试邀请并推送WebSocket，学生ID: {}", status.getStudentId());
                        }
                    } catch (Exception e) {
                        log.warn("创建面试邀请失败，学生ID: {}, 错误: {}", status.getStudentId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("更新岗位已招人数失败，岗位ID: {}, 错误: {}", status.getPositionId(), e.getMessage());
            }
        }

        if (result > 0 && planRefresh != null) planRefresh.afterCommit(status.getStudentId());
        // 推送实时状态更新到学生端
        try {
            webSocketHandler.sendInternshipStatusUpdateToStudent(status.getStudentId(), status.getStatus());
        } catch (Exception e) {
            log.warn("推送实习状态更新失败，学生ID: {}, 错误: {}", status.getStudentId(), e.getMessage());
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rollbackPendingConfirmationForFailedApplication(Long studentId, Long positionId) {
        if (studentId == null || studentId <= 0 || positionId == null || positionId <= 0) {
            throw new BusinessException("学生ID或岗位ID无效");
        }

        int result = studentInternshipStatusMapper
                .rollbackPendingConfirmationForFailedApplication(studentId, positionId);
        if (result > 0) {
            statusCache.evictCache(studentId);
            try {
                webSocketHandler.sendInternshipStatusUpdateToStudent(studentId, 0);
            } catch (Exception e) {
                log.warn("推送实习状态回退失败，学生ID: {}, 错误: {}", studentId, e.getMessage());
            }
            log.info("已回退旧审批遗留的待确认状态，学生ID: {}, 岗位ID: {}", studentId, positionId);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByPositionId(Long positionId) {
        log.debug("根据岗位ID删除学生实习状态，岗位ID: {}", positionId);

        if (positionId == null || positionId <= 0) {
            throw new BusinessException("岗位ID无效");
        }

        int result = studentInternshipStatusMapper.deleteByPositionId(positionId);
        log.info("根据岗位ID删除学生实习状态成功，岗位ID: {}, 删除记录数: {}", positionId, result);
        return result;
    }
    
    @Override
    public StudentInternshipStatus findById(Long id) {
        log.debug("根据ID查询学生实习状态，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("状态ID无效");
        }
        
        StudentInternshipStatus status = studentInternshipStatusMapper.findById(id);
        enrichJobSearchStatus(status);
        return status;
    }
    
    @Override
    public StudentInternshipStatus findByStudentId(Long studentId) {
        log.debug("根据学生ID查询实习状态，学生ID: {}", studentId);
        
        // 参数校验
        if (studentId == null || studentId <= 0) {
            throw new BusinessException("学生ID无效");
        }
        
        // 【Redis 缓存】优先从缓存获取
        StudentInternshipStatus cached = (StudentInternshipStatus) statusCache.getCacheStatus(studentId);
        if (cached != null) {
            log.info("命中 Redis 缓存，学生 ID: {}", studentId);
            enrichJobSearchStatus(cached);
            return cached;
        }

        // 缓存未命中，查数据库
        StudentInternshipStatus status = studentInternshipStatusMapper.findByStudentId(studentId);

        // 写入缓存
        if (status != null) {
            statusCache.cacheStatus(studentId, status);
        }

        enrichJobSearchStatus(status);

        return status;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(StudentInternshipStatus status) {
        log.debug("更新学生实习状态，ID: {}", status.getId());
        
        // 参数校验
        if (status.getId() == null || status.getId() <= 0) {
            throw new BusinessException("状态ID无效");
        }
        
        // 检查状态是否存在
        StudentInternshipStatus existingStatus = studentInternshipStatusMapper.findById(status.getId());
        if (existingStatus == null) {
            throw new BusinessException("学生实习状态不存在");
        }

        // 【状态流转验证】使用状态机验证器验证状态变更的合法性
        if (status.getStatus() != null && !status.getStatus().equals(existingStatus.getStatus())) {
            stateTransitionValidator.validate(existingStatus.getStatus(), status.getStatus());
            log.info("状态流转验证通过：学生 {} 从状态 {} 变更为 {}",
                existingStatus.getStudentId(),
                InternshipStatusEnum.of(existingStatus.getStatus()).getDescription(),
                InternshipStatusEnum.of(status.getStatus()).getDescription());
        }

        // 合并生效状态后，按全局日历自动填充缺失的实习起止时间
        StudentInternshipStatus mergeForTimeFill = new StudentInternshipStatus();
        mergeForTimeFill.setStudentId(existingStatus.getStudentId());
        mergeForTimeFill.setStatus(status.getStatus() != null ? status.getStatus() : existingStatus.getStatus());
        mergeForTimeFill.setInternshipStartTime(status.getInternshipStartTime() != null
                ? status.getInternshipStartTime() : existingStatus.getInternshipStartTime());
        mergeForTimeFill.setInternshipEndTime(status.getInternshipEndTime() != null
                ? status.getInternshipEndTime() : existingStatus.getInternshipEndTime());
        mergeForTimeFill.setInternshipDuration(status.getInternshipDuration() != null
                ? status.getInternshipDuration() : existingStatus.getInternshipDuration());
        populateGlobalInternshipTimesIfNeeded(mergeForTimeFill);
        // 如果辅助方法写入了新值，回填到待更新对象
        if (status.getInternshipStartTime() == null
                && !java.util.Objects.equals(mergeForTimeFill.getInternshipStartTime(), existingStatus.getInternshipStartTime())) {
            status.setInternshipStartTime(mergeForTimeFill.getInternshipStartTime());
        }
        if (status.getInternshipEndTime() == null
                && !java.util.Objects.equals(mergeForTimeFill.getInternshipEndTime(), existingStatus.getInternshipEndTime())) {
            status.setInternshipEndTime(mergeForTimeFill.getInternshipEndTime());
        }
        if (status.getInternshipDuration() == null
                && !java.util.Objects.equals(mergeForTimeFill.getInternshipDuration(), existingStatus.getInternshipDuration())) {
            status.setInternshipDuration(mergeForTimeFill.getInternshipDuration());
        }

        // 设置更新时间
        status.setUpdateTime(new Date());
        
        int result = studentInternshipStatusMapper.update(status);
        log.info("学生实习状态更新成功，学生ID: {}", status.getStudentId());

        // 【Redis 缓存】状态变更时清除缓存
        statusCache.evictCache(existingStatus.getStudentId());
        // 更新岗位已招人数
        updatePositionRecruitedCount(existingStatus, status);

        if (result > 0 && planRefresh != null) planRefresh.afterCommit(existingStatus.getStudentId());
        // 状态变更时推送实时更新到学生端
        if (status.getStatus() != null && !status.getStatus().equals(existingStatus.getStatus())) {
            try {
                webSocketHandler.sendInternshipStatusUpdateToStudent(status.getStudentId(), status.getStatus());
            } catch (Exception e) {
                log.warn("推送实习状态更新失败，学生ID: {}, 错误: {}", status.getStudentId(), e.getMessage());
            }
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除学生实习状态，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("状态ID无效");
        }
        
        // 检查状态是否存在
        StudentInternshipStatus status = studentInternshipStatusMapper.findById(id);
        if (status == null) {
            throw new BusinessException("学生实习状态不存在");
        }
        
        Long positionId = status.getPositionId();
        Integer oldStatus = status.getStatus();
        
        int result = studentInternshipStatusMapper.deleteById(id);
        log.info("学生实习状态删除成功，学生ID: {}", status.getStudentId());
        // 【Redis 缓存】删除时清除缓存
        statusCache.evictCache(status.getStudentId());
        
        // 更新岗位已招人数（如果之前的状态是1或2）
        if (positionId != null && (oldStatus == 1 || oldStatus == 2)) {
            try {
                positionService.updateRecruitedCount(positionId);
                log.debug("已更新岗位已招人数，岗位ID: {}", positionId);
            } catch (Exception e) {
                log.warn("更新岗位已招人数失败，岗位ID: {}, 错误: {}", positionId, e.getMessage());
            }
        }
        
        return result;
    }
    
    @Override
    public List<StudentInternshipStatus> findAll() {
        log.debug("查询所有学生实习状态");
        List<StudentInternshipStatus> statuses = studentInternshipStatusMapper.findAll();
        enrichJobSearchStatuses(statuses);
        return statuses;
    }
    
    @Override
    public List<StudentInternshipStatus> list(Long studentId, String name, Integer gender, Integer status, Long companyId, String companyName, String grade, String major, String className, String studentUserId) {
        log.debug("动态条件查询学生实习状态（扩展版），学生ID: {}, 学生姓名: {}, 性别: {}, 状态: {}, 企业ID: {}, 企业名称: {}, 年级: {}, 专业: {}, 班级: {}, 学号: {}", 
                studentId, name, gender, status, companyId, companyName, grade, major, className, studentUserId);
        List<StudentInternshipStatus> statuses = studentInternshipStatusMapper.list(
                studentId, name, gender, status, companyId, companyName, grade, major, className, studentUserId);
        enrichJobSearchStatuses(statuses);
        return statuses;
    }
    
    @Override
    public List<StudentInternshipStatus> findByIds(List<Long> ids) {
        log.debug("根据ID列表查询学生实习状态，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<StudentInternshipStatus> statuses = studentInternshipStatusMapper.selectByIds(ids);
        enrichJobSearchStatuses(statuses);
        return statuses;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除学生实习状态，ID列表: {}", ids);
        
        // 参数校验
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("状态ID列表不能为空");
        }
        
        int result = studentInternshipStatusMapper.batchDeleteByIds(ids);
        log.info("批量删除学生实习状态成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public Long count() {
        log.debug("查询学生实习状态总数");
        return studentInternshipStatusMapper.count();
    }

    @Override
    public Integer countByStatus(Integer status) {
        log.debug("根据状态统计学生数量，状态: {}", status);
        if (status != null && (status == 0 || status == 1) && jobSearchStatusService != null) {
            return Math.toIntExact(jobSearchStatusService.countByDisplayFilter(status, null));
        }
        return studentInternshipStatusMapper.countByStatus(status);
    }

    @Override
    public PageResult<StudentInternshipStatus> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询学生实习状态，页码: {}, 每页大小: {}", page, pageSize);
        return findPage(page, pageSize, null, null, null, null, null, null, null, null, null, null);
    }

    @Override
    public PageResult<StudentInternshipStatus> findPage(Integer page, Integer pageSize, Long studentId, String name, Integer gender, Integer status, Long companyId, String companyName, String grade, String major, String className) {
        return findPage(page, pageSize, studentId, name, gender, status, companyId, companyName, grade, major, className, null);
    }

    @Override
    public PageResult<StudentInternshipStatus> findPage(Integer page, Integer pageSize, Long studentId, String name, Integer gender, Integer status, Long companyId, String companyName, String grade, String major, String className, String studentUserId) {
        log.debug("分页查询学生实习状态，页码: {}, 每页大小: {}, 学生ID: {}, 学生姓名: {}, 性别: {}, 状态: {}, 企业ID: {}, 企业名称: {}, 年级: {}, 专业: {}, 班级: {}, 学号: {}",
                page, pageSize, studentId, name, gender, status, companyId, companyName, grade, major, className, studentUserId);

        PageHelper.startPage(page, pageSize);
        List<StudentInternshipStatus> statuses = studentInternshipStatusMapper.list(studentId, name, gender, status, companyId, companyName, grade, major, className, studentUserId);

        PageInfo<StudentInternshipStatus> pageInfo = new PageInfo<>(statuses);
        enrichJobSearchStatuses(pageInfo.getList());
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public PageResult<StudentInternshipStatus> findPageByScope(Integer page, Integer pageSize, Long studentId, String name,
                                                                Integer gender, Integer status, Long companyId, String companyName,
                                                                String grade, String major, String className, String studentUserId,
                                                                List<Long> departmentIds, List<Long> divisionIds, List<Long> classIds) {
        log.debug("按管辖范围分页查询，页码: {}, 每页: {}, departmentIds: {}, divisionIds: {}, classIds: {}",
                page, pageSize, departmentIds, divisionIds, classIds);

        PageHelper.startPage(page, pageSize);
        List<StudentInternshipStatus> statuses = studentInternshipStatusMapper.listByScope(
                studentId, name, gender, status, companyId, companyName, grade, major, className, studentUserId,
                departmentIds, divisionIds, classIds);

        PageInfo<StudentInternshipStatus> pageInfo = new PageInfo<>(statuses);
        enrichJobSearchStatuses(pageInfo.getList());
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public PageResult<StudentInternshipStatus> findPageByStatusList(Integer page, Integer pageSize, Long studentId, String name, Integer gender, java.util.List<Integer> statusList, Long companyId, String companyName, String grade, String major, String className, String studentUserId) {
        log.debug("分页查询学生实习状态（支持状态列表），页码: {}, 每页大小: {}, 学生ID: {}, 学生姓名: {}, 性别: {}, 状态列表: {}, 企业ID: {}, 企业名称: {}, 年级: {}, 专业: {}, 班级: {}, 学号: {}",
                page, pageSize, studentId, name, gender, statusList, companyId, companyName, grade, major, className, studentUserId);

        PageHelper.startPage(page, pageSize);
        List<StudentInternshipStatus> statuses = studentInternshipStatusMapper.listByStatusList(studentId, name, gender, statusList, companyId, companyName, grade, major, className, studentUserId);

        PageInfo<StudentInternshipStatus> pageInfo = new PageInfo<>(statuses);
        enrichJobSearchStatuses(pageInfo.getList());
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    /**
     * 验证学生实习状态信息
     */
    private void validateStatusInfo(StudentInternshipStatus status) {
        if (status == null) {
            throw new BusinessException("学生实习状态信息不能为空");
        }

        if (status.getStudentId() == null || status.getStudentId() <= 0) {
            throw new BusinessException("学生ID不能为空");
        }

        if (status.getStatus() == null || status.getStatus() < 0 || status.getStatus() > 2) {
            throw new BusinessException("实习状态值无效");
        }
    }

    /**
     * 当实习状态进入"已确认"或之后（status>=2），如果实习开始/结束时间为空，
     * 自动用教师在「重要设置」中配置的全局实习日历填好，避免顶部栏状态退化为"已确认"。
     */
    private void populateGlobalInternshipTimesIfNeeded(StudentInternshipStatus status) {
        if (status == null || status.getStatus() == null || status.getStatus() < 2) {
            return;
        }
        boolean startMissing = status.getInternshipStartTime() == null;
        boolean endMissing = status.getInternshipEndTime() == null;
        boolean durationMissing = status.getInternshipDuration() == null || status.getInternshipDuration() <= 0;
        if (!startMissing && !endMissing && !durationMissing) {
            return;
        }
        InternshipCalendar cal = null;
        try {
            cal = internshipCalendarService.getCalendar();
        } catch (Exception e) {
            log.warn("读取全局实习日历失败，学生 {} 将跳过时间自动填充：{}", status.getStudentId(), e.getMessage());
            return;
        }
        if (cal == null || cal.getStartDate() == null || cal.getEndDate() == null) {
            log.warn("全局实习日历尚未配置（重要设置），学生 {} 的实习起止时间留空", status.getStudentId());
            return;
        }
        try {
            java.time.LocalDate s = java.time.LocalDate.parse(cal.getStartDate());
            java.time.LocalDate e = java.time.LocalDate.parse(cal.getEndDate());
            long days = java.time.temporal.ChronoUnit.DAYS.between(s, e);
            if (startMissing) {
                status.setInternshipStartTime(java.sql.Timestamp.valueOf(s.atStartOfDay()));
            }
            if (endMissing) {
                status.setInternshipEndTime(java.sql.Timestamp.valueOf(e.atTime(23, 59, 59)));
            }
            if (durationMissing) {
                status.setInternshipDuration((int) days);
            }
            log.info("学生 {} 状态变更为 {}，实习起止时间为空，已按全局日历自动填充：{} ~ {}（{}天）",
                    status.getStudentId(), status.getStatus(), cal.getStartDate(), cal.getEndDate(), days);
        } catch (Exception ex) {
            log.warn("解析全局实习日历日期失败（start={}, end={}）：{}", cal.getStartDate(), cal.getEndDate(), ex.getMessage());
        }
    }

    private void enrichJobSearchStatus(StudentInternshipStatus status) {
        if (jobSearchStatusService != null) {
            jobSearchStatusService.enrich(status);
        }
    }

    private void enrichJobSearchStatuses(List<StudentInternshipStatus> statuses) {
        if (jobSearchStatusService != null) {
            jobSearchStatusService.enrich(statuses);
        }
    }

    @Override
    public PageResult<StudentInternshipStatus> findPendingRecallAuditPage(Integer page, Integer pageSize, Long studentId, String name, Integer gender, Long companyId, String companyName) {
        log.debug("分页查询待审核撤回申请，页码: {}, 每页大小: {}, 学生ID: {}, 学生姓名: {}, 性别: {}, 企业ID: {}, 企业名称: {}",
                page, pageSize, studentId, name, gender, companyId, companyName);

        PageHelper.startPage(page, pageSize);
        List<StudentInternshipStatus> statuses = studentInternshipStatusMapper.findPendingRecallAuditList(studentId, name, gender, companyId, companyName);

        PageInfo<StudentInternshipStatus> pageInfo = new PageInfo<>(statuses);
        enrichJobSearchStatuses(pageInfo.getList());
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public List<StudentInternshipStatus> findPendingRecallAuditList(Long studentId, String name, Integer gender, Long companyId, String companyName) {
        log.debug("查询待审核撤回申请列表（不分页），学生ID: {}, 学生姓名: {}, 性别: {}, 企业ID: {}, 企业名称: {}",
                studentId, name, gender, companyId, companyName);
        List<StudentInternshipStatus> statuses = studentInternshipStatusMapper.findPendingRecallAuditList(
                studentId, name, gender, companyId, companyName);
        enrichJobSearchStatuses(statuses);
        return statuses;
    }

    @Override
    public Integer countByRecallStatus(Integer recallStatus) {
        log.debug("根据撤回状态统计数量，撤回状态: {}", recallStatus);
        return studentInternshipStatusMapper.countByRecallStatus(recallStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int auditRecallApplication(Long id, Integer recallStatus, String recallAuditRemark, Long reviewerId) {
        log.debug("审核撤回申请，ID: {}, 撤回状态: {}, 审核备注: {}, 审核人ID: {}", id, recallStatus, recallAuditRemark, reviewerId);
        
        if (id == null || id <= 0) {
            throw new BusinessException("状态ID无效");
        }
        
        if (recallStatus == null || (recallStatus != 2 && recallStatus != 3)) {
            throw new BusinessException("撤回审核状态无效");
        }
        
        if (reviewerId == null || reviewerId <= 0) {
            throw new BusinessException("审核人ID无效");
        }
        
        StudentInternshipStatus status = studentInternshipStatusMapper.findById(id);
        if (status == null) {
            throw new BusinessException("实习状态不存在");
        }
        
        if (status.getRecallStatus() == null || status.getRecallStatus() != 1) {
            throw new BusinessException("该记录不在待审核撤回状态");
        }
        
        int result = studentInternshipStatusMapper.auditRecallApplication(id, recallStatus, recallAuditRemark, reviewerId);
        
        if (result > 0 && recallStatus == 2) {
            StudentInternshipStatus updateStatus = new StudentInternshipStatus();
            updateStatus.setId(id);
            updateStatus.setStatus(1);
            updateStatus.setRecallStatus(2);
            studentInternshipStatusMapper.update(updateStatus);
            log.info("撤回申请已批准，实习状态已退回到有Offer状态，ID: {}", id);
        }
        
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitRecallApplication(Long id, String recallReason) {
        log.debug("提交撤回申请，ID: {}, 撤回原因: {}", id, recallReason);
        
        if (id == null || id <= 0) {
            throw new BusinessException("状态ID无效");
        }
        
        if (recallReason == null || recallReason.trim().isEmpty()) {
            throw new BusinessException("撤回原因不能为空");
        }
        
        StudentInternshipStatus status = studentInternshipStatusMapper.findById(id);
        if (status == null) {
            throw new BusinessException("实习状态不存在");
        }
        
        if (status.getStatus() == null || status.getStatus() != 2) {
            throw new BusinessException("只有已确定的实习才能申请撤回");
        }
        
        if (status.getRecallStatus() != null && status.getRecallStatus() != 0) {
            throw new BusinessException("该记录已有撤回申请");
        }
        
        int result = studentInternshipStatusMapper.submitRecallApplication(id, recallReason);
        log.info("撤回申请提交成功，ID: {}", id);
        return result;
    }

    /**
     * 更新岗位已招人数
     * 当学生实习状态变化时，更新相关岗位的已招人数
     */
    private void updatePositionRecruitedCount(StudentInternshipStatus oldStatus, StudentInternshipStatus newStatus) {
        Long oldPositionId = oldStatus != null ? oldStatus.getPositionId() : null;
        Long newPositionId = newStatus != null ? newStatus.getPositionId() : null;
        
        // 如果岗位ID发生变化，需要更新两个岗位
        if (oldPositionId != null && !oldPositionId.equals(newPositionId)) {
            if (oldPositionId != null) {
                try {
                    positionService.updateRecruitedCount(oldPositionId);
                    log.debug("已更新旧岗位已招人数，岗位ID: {}", oldPositionId);
                } catch (Exception e) {
                    log.warn("更新旧岗位已招人数失败，岗位ID: {}, 错误: {}", oldPositionId, e.getMessage());
                }
            }
            if (newPositionId != null) {
                try {
                    positionService.updateRecruitedCount(newPositionId);
                    log.debug("已更新新岗位已招人数，岗位ID: {}", newPositionId);
                } catch (Exception e) {
                    log.warn("更新新岗位已招人数失败，岗位ID: {}, 错误: {}", newPositionId, e.getMessage());
                }
            }
        } else if (oldPositionId != null) {
            // 如果岗位ID没有变化，但状态可能变化了，也需要更新
            try {
                positionService.updateRecruitedCount(oldPositionId);
                log.debug("已更新岗位已招人数，岗位ID: {}", oldPositionId);

                // 【新增】推送岗位更新给所有学生
                Position position = positionService.findById(oldPositionId);
                if (position != null) {
                    webSocketHandler.sendPositionUpdateToAll(position);
                    log.debug("已推送岗位更新，岗位ID: {}", oldPositionId);
                }
            } catch (Exception e) {
                log.warn("更新岗位已招人数失败，岗位ID: {}, 错误: {}", oldPositionId, e.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearRecallData() {
        log.info("开始清除学生实习撤回申请数据");

        try {
            int count = studentInternshipStatusMapper.clearRecallData();
            log.info("清除学生实习撤回申请数据成功，共清除{}条记录", count);
            return count;
        } catch (Exception e) {
            log.error("清除学生实习撤回申请数据失败: {}", e.getMessage(), e);
            throw new BusinessException("清除撤回申请数据失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitRecallApplicationWithRecord(Long id, String recallReason, Long applicantId, String applicantRole) {
        log.debug("提交撤回申请（自动撤回），ID: {}, 撤回原因：{}, 申请人 ID: {}, 申请人身份：{}", id, recallReason, applicantId, applicantRole);

        if (id == null || id <= 0) {
            throw new BusinessException("状态 ID 无效");
        }

        if (recallReason == null || recallReason.trim().isEmpty()) {
            throw new BusinessException("撤回原因不能为空");
        }

        if (applicantId == null || applicantId <= 0) {
            throw new BusinessException("申请人 ID 无效");
        }

        if (applicantRole == null || applicantRole.isEmpty()) {
            throw new BusinessException("申请人身份不能为空");
        }

        StudentInternshipStatus status = studentInternshipStatusMapper.findById(id);
        if (status == null) {
            throw new BusinessException("实习状态不存在");
        }

        if (status.getStatus() == null || status.getStatus() != 2) {
            throw new BusinessException("只有已确定的实习才能申请撤回");
        }

        if (status.getRecallStatus() != null && status.getRecallStatus() != 0) {
            throw new BusinessException("该记录已有撤回申请");
        }

        // 1. 更新实习状态为已撤回（recall_status = 1）
        int result = studentInternshipStatusMapper.submitRecallApplication(id, recallReason);

        log.info("学生撤回申请成功，ID: {}, 撤回原因: {}", id, recallReason);
        return result;
    }
}
