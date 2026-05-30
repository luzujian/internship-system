package com.internship.service.impl;

import com.internship.entity.Announcement;
import com.internship.entity.AnnouncementReadRecord;
import com.internship.entity.ClassCounselorRelation;
import com.internship.entity.Division;
import com.internship.entity.InternshipTimeSettings;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.AnnouncementWithReadStatusDTO;
import com.internship.entity.dto.HomeStatsDTO;
import com.internship.entity.dto.InternshipStatusDTO;
import com.internship.mapper.AnnouncementMapper;
import com.internship.mapper.AnnouncementReadRecordMapper;
import com.internship.mapper.ClassCounselorRelationMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.DepartmentMapper;
import com.internship.mapper.DivisionMapper;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.mapper.StudentApplicationMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.CompanyUserService;
import com.internship.service.HomeService;
import com.internship.service.InternshipTimeSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HomeServiceImpl implements HomeService {
    
    @Autowired
    private AnnouncementMapper announcementMapper;
    
    @Autowired
    private AnnouncementReadRecordMapper announcementReadRecordMapper;
    
    @Autowired
    private StudentInternshipStatusMapper studentInternshipStatusMapper;
    
    @Autowired
    private StudentApplicationMapper studentApplicationMapper;
    
    @Autowired
    private CompanyUserMapper companyUserMapper;

    @Autowired
    private com.internship.mapper.StudentUserMapper studentUserMapper;
    
    @Autowired
    private TeacherUserMapper teacherUserMapper;
    
    @Autowired
    private ClassCounselorRelationMapper classCounselorRelationMapper;

    @Autowired
    private DivisionMapper divisionMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private InternshipTimeSettingsService internshipTimeSettingsService;

    @Autowired
    private InternshipReflectionMapper internshipReflectionMapper;

    @Autowired
    private CompanyUserService companyUserService;

    @Override
    public HomeStatsDTO getHomeStats(Long userId, String userType, String startDate, String endDate) {
        log.info("获取首页统计数据，用户 ID: {}, 用户类型：{}", userId, userType);

        HomeStatsDTO homeStats = new HomeStatsDTO();

        try {
            // 如果外部没有传入时间范围，则从配置中获取
            if (startDate == null || endDate == null) {
                InternshipTimeSettings timeSettings = internshipTimeSettingsService.findLatest();
                if (timeSettings != null) {
                    if (startDate == null) {
                        startDate = timeSettings.getApplicationStartDate();
                    }
                    if (endDate == null) {
                        endDate = timeSettings.getApplicationEndDate();
                    }
                    log.info("当前学期时间范围（应聘开始~应聘结束）: {} ~ {}", startDate, endDate);
                }
            } else {
                log.info("使用外部传入时间范围: {} ~ {}", startDate, endDate);
            }

            Map<String, Object> dashboardStats;

            // 根据teacherType判断身份：辅导员按班级、系室教师按系、学院教师显示所有
            if ("COUNSELOR".equals(userType) && userId != null && userId > 0) {
                List<ClassCounselorRelation> relations = classCounselorRelationMapper.findByCounselorId(userId);
                if (relations != null && !relations.isEmpty()) {
                    List<Long> classIds = relations.stream()
                        .map(ClassCounselorRelation::getClassId)
                        .collect(Collectors.toList());
                    dashboardStats = studentInternshipStatusMapper.getDashboardStatsByClassIds(classIds, startDate, endDate);
                    log.info("辅导员负责班级统计：{} 个班级，{} 名学生", classIds.size(), dashboardStats != null ? dashboardStats.get("totalStudents") : 0);
                    int pendingReflectionCount = internshipReflectionMapper.countPendingByClassIds(classIds);
                    homeStats.setPendingReflectionCount(pendingReflectionCount);
                } else {
                    dashboardStats = studentInternshipStatusMapper.getDashboardStats(startDate, endDate);
                    log.info("辅导员未分配班级，使用全院统计数据");
                }
            } else if ("DEPARTMENT".equals(userType) && userId != null && userId > 0) {
                // 系室教师：根据division_id获取对应系的学生数据
                TeacherUser teacher = teacherUserMapper.findById(userId);
                Long divId = null;
                if (teacher != null && teacher.getDivisionId() != null) {
                    try {
                        divId = Long.valueOf(teacher.getDivisionId());
                    } catch (NumberFormatException e) {
                        log.warn("系室教师的division_id格式异常: {}", teacher.getDivisionId());
                    }
                }
                if (divId != null) {
                    List<Long> divisionIds = java.util.Collections.singletonList(divId);
                    dashboardStats = studentInternshipStatusMapper.getDashboardStatsByDivisionIds(divisionIds, startDate, endDate);
                    // 获取系名称
                    Division division = divisionMapper.findById(divId);
                    if (division != null) {
                        homeStats.setDivisionName(division.getName());
                    }
                    log.info("系室教师统计（系ID={}）：{} 名学生", divId, dashboardStats != null ? dashboardStats.get("totalStudents") : 0);
                } else {
                    dashboardStats = studentInternshipStatusMapper.getDashboardStats(startDate, endDate);
                    log.info("系室教师未关联系，使用全院统计数据");
                }
            } else if ("COLLEGE".equals(userType) && userId != null && userId > 0) {
                // 学院教师：根据department_id获取对应学院的学生数据
                TeacherUser teacher = teacherUserMapper.findById(userId);
                Long deptId = null;
                if (teacher != null && teacher.getDepartmentId() != null) {
                    try {
                        deptId = Long.valueOf(teacher.getDepartmentId());
                    } catch (NumberFormatException e) {
                        log.warn("学院教师的department_id格式异常: {}", teacher.getDepartmentId());
                    }
                }
                if (deptId != null) {
                    List<Division> divisions = divisionMapper.findByDepartmentId(deptId);
                    if (divisions != null && !divisions.isEmpty()) {
                        List<Long> divisionIds = divisions.stream()
                            .map(Division::getId)
                            .collect(Collectors.toList());
                        dashboardStats = studentInternshipStatusMapper.getDashboardStatsByDivisionIds(divisionIds, startDate, endDate);
                        log.info("学院教师统计（学院ID={}，{} 个系）：{} 名学生", deptId, divisionIds.size(), dashboardStats != null ? dashboardStats.get("totalStudents") : 0);
                    } else {
                        dashboardStats = studentInternshipStatusMapper.getDashboardStats(startDate, endDate);
                        log.info("学院教师所属学院下无系，使用全院统计数据");
                    }
                    // 获取学院名称
                    var department = departmentMapper.findById(deptId);
                    if (department != null) {
                        homeStats.setDepartmentName(department.getName());
                    }
                } else {
                    dashboardStats = studentInternshipStatusMapper.getDashboardStats(startDate, endDate);
                    log.info("学院教师未关联学院，使用全院统计数据");
                }
            } else {
                dashboardStats = studentInternshipStatusMapper.getDashboardStats(startDate, endDate);
            }
            
            int totalStudents = dashboardStats != null && dashboardStats.get("totalStudents") != null 
                ? ((Number) dashboardStats.get("totalStudents")).intValue() 
                : 0;
            int confirmed = dashboardStats != null && dashboardStats.get("confirmed") != null
                ? ((Number) dashboardStats.get("confirmed")).intValue()
                : 0;
            int offer = dashboardStats != null && dashboardStats.get("offer") != null
                ? ((Number) dashboardStats.get("offer")).intValue()
                : 0;
            int noOffer = dashboardStats != null && dashboardStats.get("noOffer") != null
                ? ((Number) dashboardStats.get("noOffer")).intValue()
                : 0;
            int interning = dashboardStats != null && dashboardStats.get("interning") != null
                ? ((Number) dashboardStats.get("interning")).intValue()
                : 0;
            int delay = dashboardStats != null && dashboardStats.get("delay") != null
                ? ((Number) dashboardStats.get("delay")).intValue()
                : 0;

            int confirmedAndInterning = confirmed + interning;

            Double internshipRate = totalStudents > 0
                ? Math.round((confirmedAndInterning * 1000.0) / totalStudents) / 10.0
                : 0.0;
            homeStats.setInternshipRate(internshipRate);

            List<InternshipStatusDTO> statusData = new ArrayList<>();

            InternshipStatusDTO confirmedStatus = new InternshipStatusDTO();
            confirmedStatus.setName("已确定实习");
            confirmedStatus.setValue(confirmedAndInterning);
            confirmedStatus.setColor("success");
            statusData.add(confirmedStatus);
            
            InternshipStatusDTO offerStatus = new InternshipStatusDTO();
            offerStatus.setName("有offer但未确定");
            offerStatus.setValue(offer);
            offerStatus.setColor("info");
            statusData.add(offerStatus);
            
            InternshipStatusDTO noOfferStatus = new InternshipStatusDTO();
            noOfferStatus.setName("没offer");
            noOfferStatus.setValue(noOffer);
            noOfferStatus.setColor("danger");
            statusData.add(noOfferStatus);
            
            InternshipStatusDTO delayStatus = new InternshipStatusDTO();
            delayStatus.setName("考研");
            delayStatus.setValue(delay);
            delayStatus.setColor("warning");
            statusData.add(delayStatus);
            
            homeStats.setStatusData(statusData);
            
            int unreadCount = 0;
            homeStats.setUnreadCount(unreadCount);
            
            int pendingStudentApps = studentApplicationMapper.countPendingApproval();
            Long pendingCompanyApps = companyUserService.countByAuditStatus(0);
            homeStats.setPendingApprovalCount((pendingStudentApps > 0 ? pendingStudentApps : 0)
                    + (pendingCompanyApps != null ? pendingCompanyApps.intValue() : 0));
            
            Integer companyCount = companyUserMapper.countApproved();
            homeStats.setCompanyCount(companyCount != null ? companyCount : 0);
            
            List<Announcement> allAnnouncements = announcementMapper.findAll();
            
            allAnnouncements.sort((a, b) -> {
                if (a.getPublishTime() == null && b.getPublishTime() == null) {
                    return 0;
                }
                if (a.getPublishTime() == null) {
                    return 1;
                }
                if (b.getPublishTime() == null) {
                    return -1;
                }
                return b.getPublishTime().compareTo(a.getPublishTime());
            });
            
            List<Announcement> latestAnnouncements = allAnnouncements.stream()
                .limit(3)
                .collect(java.util.stream.Collectors.toList());
            
            List<AnnouncementWithReadStatusDTO> announcementsWithStatus = new ArrayList<>();
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            
            for (Announcement announcement : latestAnnouncements) {
                AnnouncementWithReadStatusDTO dto = new AnnouncementWithReadStatusDTO();
                dto.setId(announcement.getId());
                dto.setTitle(announcement.getTitle());
                dto.setContent(announcement.getContent());
                
                if (announcement.getPublishTime() != null) {
                    dto.setTime(dateFormat.format(announcement.getPublishTime()));
                } else {
                    dto.setTime("");
                }
                
                dto.setIsRead(false);
                
                announcementsWithStatus.add(dto);
            }
            
            homeStats.setAnnouncements(announcementsWithStatus);
            
            log.info("首页统计数据获取成功: {}", homeStats);
            return homeStats;
            
        } catch (Exception e) {
            log.error("获取首页统计数据失败", e);
            throw new RuntimeException("获取首页统计数据失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAnnouncementAsRead(Long announcementId, Long userId, String userType) {
        log.info("标记公告为已读，公告ID: {}, 用户ID: {}, 用户类型: {}", announcementId, userId, userType);
        
        try {
            AnnouncementReadRecord existingRecord = announcementReadRecordMapper.findByAnnouncementAndUser(
                announcementId, String.valueOf(userId), userType);
            
            if (existingRecord == null) {
                AnnouncementReadRecord record = new AnnouncementReadRecord();
                record.setAnnouncementId(announcementId);
                record.setUserId(String.valueOf(userId));
                record.setUserType(userType);
                announcementReadRecordMapper.insert(record);
                log.info("公告已读记录创建成功");
            } else {
                log.info("公告已读记录已存在，无需重复创建");
            }
        } catch (Exception e) {
            log.error("标记公告为已读失败", e);
            throw new RuntimeException("标记公告为已读失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAnnouncementAsUnread(Long announcementId, Long userId, String userType) {
        log.info("标记公告为未读，公告ID: {}, 用户ID: {}, 用户类型: {}", announcementId, userId, userType);
        
        try {
            AnnouncementReadRecord existingRecord = announcementReadRecordMapper.findByAnnouncementAndUser(
                announcementId, String.valueOf(userId), userType);
            
            if (existingRecord != null) {
                log.info("公告已读记录删除成功");
            } else {
                log.info("公告已读记录不存在，无需删除");
            }
        } catch (Exception e) {
            log.error("标记公告为未读失败", e);
            throw new RuntimeException("标记公告为未读失败: " + e.getMessage(), e);
        }
    }
}
