package com.gdmu.service.impl;

import com.gdmu.entity.Announcement;
import com.gdmu.entity.AnnouncementReadRecord;
import com.gdmu.entity.ClassCounselorRelation;
import com.gdmu.entity.InternshipTimeSettings;
import com.gdmu.entity.TeacherUser;
import com.gdmu.entity.dto.AnnouncementWithReadStatusDTO;
import com.gdmu.entity.dto.HomeStatsDTO;
import com.gdmu.entity.dto.InternshipStatusDTO;
import com.gdmu.mapper.AnnouncementMapper;
import com.gdmu.mapper.AnnouncementReadRecordMapper;
import com.gdmu.mapper.ClassCounselorRelationMapper;
import com.gdmu.mapper.CompanyUserMapper;
import com.gdmu.mapper.StudentApplicationMapper;
import com.gdmu.mapper.StudentInternshipStatusMapper;
import com.gdmu.mapper.TeacherUserMapper;
import com.gdmu.service.HomeService;
import com.gdmu.service.InternshipTimeSettingsService;
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
    private com.gdmu.mapper.StudentUserMapper studentUserMapper;
    
    @Autowired
    private TeacherUserMapper teacherUserMapper;
    
    @Autowired
    private ClassCounselorRelationMapper classCounselorRelationMapper;

    @Autowired
    private InternshipTimeSettingsService internshipTimeSettingsService;

    @Override
    public HomeStatsDTO getHomeStats(Long userId, String userType) {
        log.info("获取首页统计数据，用户 ID: {}, 用户类型：{}", userId, userType);

        HomeStatsDTO homeStats = new HomeStatsDTO();

        try {
            // 获取当前学期的时间范围（从应聘开始到实习结束）
            InternshipTimeSettings timeSettings = internshipTimeSettingsService.findLatest();
            String startDate = null;
            String endDate = null;
            if (timeSettings != null) {
                startDate = timeSettings.getApplicationStartDate();
                endDate = timeSettings.getApplicationEndDate();
                log.info("当前学期时间范围（应聘开始~应聘结束）: {} ~ {}", startDate, endDate);
            }

            Map<String, Object> dashboardStats;

            if ("COUNSELOR".equals(userType) && userId != null && userId > 0) {
                List<ClassCounselorRelation> relations = classCounselorRelationMapper.findByCounselorId(userId);
                if (relations != null && !relations.isEmpty()) {
                    List<Long> classIds = relations.stream()
                        .map(ClassCounselorRelation::getClassId)
                        .collect(Collectors.toList());
                    dashboardStats = studentInternshipStatusMapper.getDashboardStatsByClassIds(classIds, startDate, endDate);
                    log.info("辅导员负责班级统计：{} 个班级，{} 名学生", classIds.size(), dashboardStats != null ? dashboardStats.get("totalStudents") : 0);
                } else {
                    dashboardStats = studentInternshipStatusMapper.getDashboardStats(startDate, endDate);
                    log.info("辅导员未分配班级，使用全院统计数据");
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
            int delay = dashboardStats != null && dashboardStats.get("delay") != null 
                ? ((Number) dashboardStats.get("delay")).intValue() 
                : 0;
            
            Double internshipRate = totalStudents > 0 
                ? Math.round((confirmed * 1000.0) / totalStudents) / 10.0
                : 0.0;
            homeStats.setInternshipRate(internshipRate);
            
            List<InternshipStatusDTO> statusData = new ArrayList<>();
            
            InternshipStatusDTO confirmedStatus = new InternshipStatusDTO();
            confirmedStatus.setName("已确定实习");
            confirmedStatus.setValue(confirmed);
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
            
            Integer pendingApprovalCount = studentApplicationMapper.countPendingApproval();
            homeStats.setPendingApprovalCount(pendingApprovalCount != null ? pendingApprovalCount : 0);
            
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
