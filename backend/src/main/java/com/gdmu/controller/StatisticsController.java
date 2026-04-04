package com.gdmu.controller;

import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.Major;
import com.gdmu.entity.Position;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.entity.StudentUser;
import com.gdmu.mapper.CompanyUserMapper;
import com.gdmu.mapper.MajorMapper;
import com.gdmu.mapper.PositionMapper;
import com.gdmu.mapper.StudentInternshipStatusMapper;
import com.gdmu.mapper.StudentUserMapper;
import com.gdmu.utils.DateUtils;
import com.gdmu.utils.MapUtils;
import com.gdmu.utils.StatisticsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 统计分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/statistics")
public class StatisticsController {

    @Autowired
    private StudentUserMapper studentUserMapper;

    @Autowired
    private StudentInternshipStatusMapper internshipStatusMapper;

    @Autowired
    private CompanyUserMapper companyUserMapper;

    @Autowired
    private MajorMapper majorMapper;

    @Autowired
    private PositionMapper positionMapper;

    /**
     * 获取多维度统计数据
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result getStatistics(
            @RequestParam(defaultValue = "department") String dimension,
            @RequestParam(defaultValue = "monthly") String timePeriod,
            @RequestParam(defaultValue = "") String startDate,
            @RequestParam(defaultValue = "") String endDate,
            @RequestParam(defaultValue = "") String departmentId,
            @RequestParam(defaultValue = "") String majorId,
            @RequestParam(defaultValue = "") String classId
    ) {
        log.info("获取统计数据，维度: {}, 时间周期: {}, 开始日期: {}, 结束日期: {}, 院系ID: {}, 专业ID: {}, 班级ID: {}",
                dimension, timePeriod, startDate, endDate, departmentId, majorId, classId);
        try {
            Map<String, Object> result = new HashMap<>();

            // 根据不同维度获取统计数据
            if ("department".equals(dimension)) {
                // 按院系/专业维度统计
                result.put("participation", getParticipationRateByDepartment(departmentId, majorId, classId));
                result.put("location", getLocationDistribution(departmentId, majorId, classId));
                result.put("match", getMajorMatchRate(departmentId, majorId, classId));
            } else if ("time".equals(dimension)) {
                // 按时间周期维度统计
                result.put("trend", getInternshipTrend(timePeriod, startDate, endDate));
                result.put("newInterns", getNewInternsByTime(timePeriod, startDate, endDate));
            } else if ("status".equals(dimension)) {
                // 按实习状态维度统计
                result.put("status", getStatusDistribution(departmentId, majorId, classId));
                result.put("abnormal", getAbnormalStatusAnalysis(departmentId, majorId, classId));
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取统计数据失败: {}", e.getMessage());
            return Result.error("获取统计数据失败");
        }
    }

    /**
     * 按院系/专业获取实习参与率
     */
    private List<Map<String, Object>> getParticipationRateByDepartment(String departmentId, String majorId, String classId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<StudentUser> studentList = studentUserMapper.findAll();
            Map<Long, StudentUser> studentMap = new HashMap<>();
            for (StudentUser student : studentList) {
                studentMap.put(student.getId(), student);
            }
            
            List<StudentInternshipStatus> statusList = internshipStatusMapper.selectAll();
            
            Map<Long, Integer> totalByMajorId = new HashMap<>();
            Map<Long, Integer> internByMajorId = new HashMap<>();
            Map<Long, String> majorIdToName = new HashMap<>();
            
            List<Major> majorList = majorMapper.findAll();
            Map<Long, Major> majorMap = new HashMap<>();
            for (Major major : majorList) {
                majorMap.put(major.getId(), major);
            }
            
            for (StudentUser student : studentList) {
                Long studentMajorId = student.getMajorId();
                if (studentMajorId != null) {
                    totalByMajorId.put(studentMajorId, totalByMajorId.getOrDefault(studentMajorId, 0) + 1);
                    Major major = majorMap.get(studentMajorId);
                    majorIdToName.put(studentMajorId, major != null ? major.getName() : "未知专业");
                } else {
                    totalByMajorId.put(-1L, totalByMajorId.getOrDefault(-1L, 0) + 1);
                    majorIdToName.put(-1L, "未知专业");
                }
            }
            
            for (StudentInternshipStatus status : statusList) {
                if (status.getStudentId() != null) {
                    StudentUser student = studentMap.get(status.getStudentId());
                    if (student != null) {
                        Long studentMajorId = student.getMajorId();
                        if (studentMajorId == null) {
                            studentMajorId = -1L;
                        }
                        if (status.getStatus() == 1 || status.getStatus() == 2 || status.getStatus() == 3) {
                            internByMajorId.put(studentMajorId, internByMajorId.getOrDefault(studentMajorId, 0) + 1);
                        }
                    }
                }
            }
            
            for (Map.Entry<Long, Integer> entry : totalByMajorId.entrySet()) {
                Long majorIdKey = entry.getKey();
                String majorName = majorIdToName.getOrDefault(majorIdKey, "未知专业");
                int total = entry.getValue();
                int internCount = internByMajorId.getOrDefault(majorIdKey, 0);
                double rate = total > 0 ? (double) internCount / total * 100 : 0;
                rate = StatisticsUtils.roundToOneDecimal(rate);
                result.add(MapUtils.of("name", majorName, "rate", rate));
            }
        } catch (Exception e) {
            log.error("获取实习参与率失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 获取实习地点分布
     */
    private List<Map<String, Object>> getLocationDistribution(String departmentId, String majorId, String classId) {
        // 从数据库获取真实数据
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            // 获取所有实习状态记录
            List<StudentInternshipStatus> statusList = internshipStatusMapper.selectAll();
            
            // 获取所有涉及的企业ID
            Set<Long> companyIds = new HashSet<>();
            for (StudentInternshipStatus status : statusList) {
                if (status.getCompanyId() != null) {
                    companyIds.add(status.getCompanyId());
                }
            }
            
            // 批量获取企业信息，并构建企业ID到企业对象的映射
            Map<Long, CompanyUser> companyMap = new HashMap<>();
            if (!companyIds.isEmpty()) {
                List<CompanyUser> companyList = companyUserMapper.findAll();
                for (CompanyUser company : companyList) {
                    if (companyIds.contains(company.getId())) {
                        companyMap.put(company.getId(), company);
                    }
                }
            }
            
            Map<String, Integer> locationCount = new HashMap<>();
            
            for (StudentInternshipStatus status : statusList) {
                CompanyUser company = companyMap.get(status.getCompanyId());
                if (company != null && company.getAddress() != null) {
                    String location = company.getAddress();
                    locationCount.put(location, locationCount.getOrDefault(location, 0) + 1);
                }
            }
            
            // 转换为前端需要的格式
            for (Map.Entry<String, Integer> entry : locationCount.entrySet()) {
                result.add(MapUtils.of("name", entry.getKey(), "value", entry.getValue()));
            }
            
            if (result.isEmpty()) {
                result.add(MapUtils.of("name", "其他", "value", 0));
            }
        } catch (Exception e) {
            log.error("获取实习地点分布失败: {}", e.getMessage());
            result.add(MapUtils.of("name", "其他", "value", 0));
        }
        return result;
    }

    /**
     * 获取专业对口率
     */
    private List<Map<String, Object>> getMajorMatchRate(String departmentId, String majorId, String classId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<StudentUser> studentList = studentUserMapper.findAll();
            Map<Long, StudentUser> studentMap = new HashMap<>();
            for (StudentUser student : studentList) {
                studentMap.put(student.getId(), student);
            }
            
            List<StudentInternshipStatus> statusList = internshipStatusMapper.selectAll();
            
            List<Major> majorList = majorMapper.findAll();
            Map<Long, Major> majorMap = new HashMap<>();
            for (Major major : majorList) {
                majorMap.put(major.getId(), major);
            }
            
            List<Position> positionList = positionMapper.findAll();
            Map<Long, Position> positionMap = new HashMap<>();
            for (Position position : positionList) {
                positionMap.put(position.getId(), position);
            }
            
            Map<Long, Integer> totalByMajorId = new HashMap<>();
            Map<Long, Integer> matchByMajorId = new HashMap<>();
            Map<Long, String> majorIdToName = new HashMap<>();
            
            for (StudentInternshipStatus status : statusList) {
                if (status.getStudentId() != null) {
                    StudentUser student = studentMap.get(status.getStudentId());
                    if (student != null) {
                        Long studentMajorId = student.getMajorId();
                        if (studentMajorId == null) {
                            studentMajorId = -1L;
                        }
                        if (status.getStatus() == 1 || status.getStatus() == 2 || status.getStatus() == 3) {
                            totalByMajorId.put(studentMajorId, totalByMajorId.getOrDefault(studentMajorId, 0) + 1);
                            
                            Major major = majorMap.get(studentMajorId);
                            majorIdToName.put(studentMajorId, major != null ? major.getName() : "未知专业");
                            
                            boolean isMatch = false;
                            if (status.getPositionId() != null) {
                                Position position = positionMap.get(status.getPositionId());
                                if (position != null && major != null) {
                                    isMatch = isMajorMatch(major.getName(), position.getPositionName());
                                }
                            }
                            
                            if (isMatch) {
                                matchByMajorId.put(studentMajorId, matchByMajorId.getOrDefault(studentMajorId, 0) + 1);
                            }
                        }
                    }
                }
            }
            
            for (Map.Entry<Long, Integer> entry : totalByMajorId.entrySet()) {
                Long majorIdKey = entry.getKey();
                String majorName = majorIdToName.getOrDefault(majorIdKey, "未知专业");
                int total = entry.getValue();
                int matchCount = matchByMajorId.getOrDefault(majorIdKey, 0);
                double rate = total > 0 ? (double) matchCount / total * 100 : 0;
                rate = StatisticsUtils.roundToOneDecimal(rate);
                result.add(MapUtils.of("name", majorName, "rate", rate));
            }
        } catch (Exception e) {
            log.error("获取专业对口率失败: {}", e.getMessage());
        }
        return result;
    }
    
    /**
     * 判断专业是否对口
     */
    private boolean isMajorMatch(String major, String position) {
        if (major == null || position == null) {
            return false;
        }
        
        String lowerMajor = major.toLowerCase();
        String lowerPosition = position.toLowerCase();
        
        return (lowerMajor.contains("计算机") && (lowerPosition.contains("计算机") || lowerPosition.contains("软件") || lowerPosition.contains("开发") || lowerPosition.contains("编程") || lowerPosition.contains("测试"))) ||
               (lowerMajor.contains("电子") && (lowerPosition.contains("电子") || lowerPosition.contains("电路") || lowerPosition.contains("硬件") || lowerPosition.contains("嵌入式"))) ||
               (lowerMajor.contains("机械") && (lowerPosition.contains("机械") || lowerPosition.contains("制造") || lowerPosition.contains("自动化") || lowerPosition.contains("工程"))) ||
               (lowerMajor.contains("经济") && (lowerPosition.contains("经济") || lowerPosition.contains("金融") || lowerPosition.contains("贸易") || lowerPosition.contains("市场"))) ||
               (lowerMajor.contains("管理") && (lowerPosition.contains("管理") || lowerPosition.contains("行政") || lowerPosition.contains("人力") || lowerPosition.contains("财务"))) ||
               (lowerMajor.contains("软件") && (lowerPosition.contains("软件") || lowerPosition.contains("开发") || lowerPosition.contains("编程") || lowerPosition.contains("测试"))) ||
               (lowerMajor.contains("网络") && (lowerPosition.contains("网络") || lowerPosition.contains("运维") || lowerPosition.contains("通信"))) ||
               (lowerMajor.contains("物联网") && (lowerPosition.contains("物联网") || lowerPosition.contains("嵌入式") || lowerPosition.contains("智能") || lowerPosition.contains("硬件"))) ||
               (lowerMajor.contains("数据") && (lowerPosition.contains("数据") || lowerPosition.contains("分析") || lowerPosition.contains("大数据") || lowerPosition.contains("统计")));
    }

    /**
     * 获取实习趋势变化
     */
    private List<Map<String, Object>> getInternshipTrend(String timePeriod, String startDate, String endDate) {
        // 从数据库获取真实数据
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            // 获取所有实习状态记录
            List<StudentInternshipStatus> statusList = internshipStatusMapper.selectAll();
            
            // 根据时间周期生成日期列表
            List<String> dateList = generateDateList(timePeriod, startDate, endDate);
            
            Map<String, Map<String, Integer>> trendData = new HashMap<>();
            
            for (String date : dateList) {
                Map<String, Integer> dateMap = new HashMap<>();
                dateMap.put("current", 0);
                dateMap.put("completed", 0);
                dateMap.put("pending", 0);
                trendData.put(date, dateMap);
            }
            
            for (StudentInternshipStatus status : statusList) {
                Date statusDate = status.getCreateTime() != null ? status.getCreateTime() : new Date();
                String dateKey = formatDate(statusDate, timePeriod);
                
                if (trendData.containsKey(dateKey)) {
                    Map<String, Integer> dateData = new HashMap<>(trendData.get(dateKey));
                    
                    if (status.getStatus() == 1 || status.getStatus() == 2) {
                        dateData.put("current", dateData.get("current") + 1);
                    } else if (status.getStatus() == 3) {
                        dateData.put("completed", dateData.get("completed") + 1);
                    } else if (status.getStatus() == 0) {
                        dateData.put("pending", dateData.get("pending") + 1);
                    }
                    
                    trendData.put(dateKey, dateData);
                }
            }
            
            for (String date : dateList) {
                Map<String, Integer> data = trendData.get(date);
                result.add(MapUtils.of(
                    "date", date,
                    "current", data.get("current"),
                    "completed", data.get("completed"),
                    "pending", data.get("pending")
                ));
            }
        } catch (Exception e) {
            log.error("获取实习趋势变化失败: {}", e.getMessage());
            result.add(MapUtils.of("date", "2024-01", "current", 0, "completed", 0, "pending", 0));
        }
        return result;
    }

    /**
     * 获取新增实习人数
     */
    private List<Map<String, Object>> getNewInternsByTime(String timePeriod, String startDate, String endDate) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<StudentInternshipStatus> statusList = internshipStatusMapper.selectAll();
            
            List<String> dateList = generateDateList(timePeriod, startDate, endDate);
            
            Map<String, Integer> newInternsData = new HashMap<>();
            
            for (String date : dateList) {
                newInternsData.put(date, 0);
            }
            
            for (StudentInternshipStatus status : statusList) {
                Date createDate = status.getCreateTime() != null ? status.getCreateTime() : new Date();
                String dateKey = formatDate(createDate, timePeriod);
                
                if (newInternsData.containsKey(dateKey)) {
                    newInternsData.put(dateKey, newInternsData.get(dateKey) + 1);
                }
            }
            
            for (String date : dateList) {
                result.add(MapUtils.of(
                    "date", date,
                    "count", newInternsData.get(date)
                ));
            }
        } catch (Exception e) {
            log.error("获取新增实习人数失败: {}", e.getMessage());
            result.add(MapUtils.of("date", "2024-01", "count", 0));
        }
        return result;
    }

    /**
     * 获取实习状态分布
     */
    private List<Map<String, Object>> getStatusDistribution(String departmentId, String majorId, String classId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<StudentInternshipStatus> statusList = internshipStatusMapper.selectAll();
            
            Map<Integer, Integer> statusCount = new HashMap<>();
            for (StudentInternshipStatus status : statusList) {
                statusCount.put(status.getStatus(), statusCount.getOrDefault(status.getStatus(), 0) + 1);
            }
            
            result.add(MapUtils.of("name", "实习中", "value", statusCount.getOrDefault(1, 0) + statusCount.getOrDefault(2, 0)));
            result.add(MapUtils.of("name", "已结束", "value", statusCount.getOrDefault(3, 0)));
            result.add(MapUtils.of("name", "待实习", "value", statusCount.getOrDefault(0, 0)));
            result.add(MapUtils.of("name", "申请中", "value", statusCount.getOrDefault(4, 0)));
            result.add(MapUtils.of("name", "已中断", "value", statusCount.getOrDefault(5, 0)));
        } catch (Exception e) {
            log.error("获取实习状态分布失败: {}", e.getMessage());
            result.add(MapUtils.of("name", "实习中", "value", 0));
            result.add(MapUtils.of("name", "已结束", "value", 0));
            result.add(MapUtils.of("name", "待实习", "value", 0));
            result.add(MapUtils.of("name", "申请中", "value", 0));
            result.add(MapUtils.of("name", "已中断", "value", 0));
        }
        return result;
    }

    /**
     * 获取异常状态分析
     */
    private List<Map<String, Object>> getAbnormalStatusAnalysis(String departmentId, String majorId, String classId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<StudentInternshipStatus> statusList = internshipStatusMapper.selectAll();
            
            int interruptedCount = 0;
            int complaintCount = 0;
            int delayCount = 0;
            
            for (StudentInternshipStatus status : statusList) {
                if (Boolean.TRUE.equals(status.getIsInterrupted())) {
                    interruptedCount++;
                }
                if (Boolean.TRUE.equals(status.getHasComplaint())) {
                    complaintCount++;
                }
                if (Boolean.TRUE.equals(status.getIsDelayed())) {
                    delayCount++;
                }
            }
            
            result.add(MapUtils.of("name", "已中断", "count", interruptedCount));
            result.add(MapUtils.of("name", "投诉", "count", complaintCount));
            result.add(MapUtils.of("name", "延期", "count", delayCount));
        } catch (Exception e) {
            log.error("获取异常状态分析失败: {}", e.getMessage());
            result.add(MapUtils.of("name", "已中断", "count", 0));
            result.add(MapUtils.of("name", "投诉", "count", 0));
            result.add(MapUtils.of("name", "延期", "count", 0));
        }
        return result;
    }
    
    /**
     * 生成日期列表
     */
    private List<String> generateDateList(String timePeriod, String startDate, String endDate) {
        List<String> dateList = new ArrayList<>();
        
        // 这里根据时间周期生成日期列表
        // 暂时生成最近6个月的日期
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -5); // 从5个月前开始
        
        for (int i = 0; i < 6; i++) {
            String dateStr = String.format("%d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1);
            dateList.add(dateStr);
            calendar.add(Calendar.MONTH, 1);
        }
        
        return dateList;
    }
    
    /**
     * 格式化日期
     */
    private String formatDate(Date date, String timePeriod) {
        if ("monthly".equals(timePeriod)) {
            return DateUtils.formatMonth(date);
        } else if ("quarterly".equals(timePeriod)) {
            return DateUtils.getQuarterStr(date);
        } else if ("yearly".equals(timePeriod)) {
            return DateUtils.formatYear(date);
        } else {
            return DateUtils.formatMonth(date);
        }
    }
}
