package com.gdmu.controller;

import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.Position;
import com.gdmu.entity.PositionCategory;
import com.gdmu.entity.PositionFavorite;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentJobApplication;
import com.gdmu.entity.User;
import com.gdmu.entity.dto.PositionVO;
import com.gdmu.mapper.CompanyUserMapper;
import com.gdmu.mapper.PositionCategoryMapper;
import com.gdmu.mapper.StudentJobApplicationMapper;
import com.gdmu.service.PositionFavoriteService;
import com.gdmu.service.PositionService;
import com.gdmu.service.PositionViewRecordService;
import com.gdmu.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/positions")
@PreAuthorize("hasRole('STUDENT')")
public class StudentPositionController {

    @Autowired
    private PositionService positionService;

    @Autowired
    private CompanyUserMapper companyUserMapper;

    @Autowired
    private StudentJobApplicationMapper studentJobApplicationMapper;

    @Autowired
    private PositionFavoriteService positionFavoriteService;

    @Autowired
    private PositionViewRecordService positionViewRecordService;

    @Autowired
    private PositionCategoryMapper positionCategoryMapper;

    @Autowired
    private UserService userService;

    /**
     * 获取当前登录学生的ID，支持两种格式：
     * 1. 纯数字字符串如 "1" -> 直接转换为 Long
     * 2. 学号字符串如 "s001" -> 通过用户服务查找对应的 ID
     */
    private Long getCurrentStudentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            try {
                String username = authentication.getName();
                try {
                    return Long.parseLong(username);
                } catch (NumberFormatException e) {
                    // 如果是学号格式，通过用户服务查找
                    User user = userService.findByUsername(username);
                    if (user != null) {
                        return user.getId();
                    }
                    log.debug("未找到用户: {}", username);
                }
            } catch (Exception e) {
                log.debug("获取当前学生ID失败: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 获取当前学生的申请记录
     */
    private Set<Long> getStudentAppliedPositionIds() {
        Long studentId = getCurrentStudentId();
        if (studentId == null) {
            return Collections.emptySet();
        }
        try {
            List<StudentJobApplication> applications = studentJobApplicationMapper.findByStudentId(studentId);
            return applications.stream()
                    .map(StudentJobApplication::getPositionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("获取学生申请记录失败: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 获取当前学生的收藏职位ID集合
     */
    private Set<Long> getStudentFavoritePositionIds() {
        Long studentId = getCurrentStudentId();
        if (studentId == null) {
            return Collections.emptySet();
        }
        try {
            List<Long> favoriteIds = positionFavoriteService.getStudentFavoritePositionIds(studentId);
            return new HashSet<>(favoriteIds);
        } catch (Exception e) {
            log.error("获取学生收藏记录失败: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    @GetMapping("/all")
    public Result getAll() {
        try {
            List<Position> positions = positionService.findAll();
            if (positions.isEmpty()) {
                return Result.success(Collections.emptyList());
            }

            Set<Long> appliedPositionIds = getStudentAppliedPositionIds();
            Set<Long> favoritePositionIds = getStudentFavoritePositionIds();

            List<PositionVO> voList = positions.stream()
                    .map(p -> convertToVO(p, appliedPositionIds, favoritePositionIds))
                    .collect(Collectors.toList());
            return Result.success(voList);
        } catch (Exception e) {
            log.error("获取职位列表失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    @GetMapping("/latest")
    public Result getLatest() {
        try {
            List<Position> all = positionService.findAll();
            List<Position> latest = all.stream()
                    .sorted(Comparator.comparing(p -> p.getCreateTime() == null ? 0L : -p.getCreateTime().getTime()))
                    .limit(10)
                    .collect(Collectors.toList());

            if (latest.isEmpty()) {
                return Result.success(Collections.emptyList());
            }

            Set<Long> appliedPositionIds = getStudentAppliedPositionIds();
            Set<Long> favoritePositionIds = getStudentFavoritePositionIds();

            List<PositionVO> voList = latest.stream()
                    .map(p -> convertToVO(p, appliedPositionIds, favoritePositionIds))
                    .collect(Collectors.toList());
            return Result.success(voList);
        } catch (Exception e) {
            log.error("获取最新职位失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    @GetMapping("/hot")
    public Result getHot() {
        try {
            List<Position> all = positionService.findAll();
            List<Position> hot = all.stream()
                    .sorted(Comparator.comparingInt(p -> -(p.getRecruitedCount() == null ? 0 : p.getRecruitedCount())))
                    .limit(10)
                    .collect(Collectors.toList());

            if (hot.isEmpty()) {
                return Result.success(Collections.emptyList());
            }

            Set<Long> appliedPositionIds = getStudentAppliedPositionIds();
            Set<Long> favoritePositionIds = getStudentFavoritePositionIds();

            List<PositionVO> voList = hot.stream()
                    .map(p -> convertToVO(p, appliedPositionIds, favoritePositionIds))
                    .collect(Collectors.toList());
            return Result.success(voList);
        } catch (Exception e) {
            log.error("获取热门职位失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 获取职位详情（浏览时viewCount+1，同一学生多次浏览只算一次）
     */
    @GetMapping("/detail/{positionId}")
    public Result getDetail(@PathVariable Long positionId) {
        try {
            Position position = positionService.findById(positionId);
            if (position == null) {
                return Result.error("职位不存在");
            }

            // 记录浏览（同一学生多次浏览只算一次）
            Long studentId = getCurrentStudentId();
            if (studentId != null) {
                positionViewRecordService.recordView(positionId, studentId);
            }

            // 重新查询最新数据
            position = positionService.findById(positionId);

            Set<Long> appliedPositionIds = getStudentAppliedPositionIds();
            Set<Long> favoritePositionIds = getStudentFavoritePositionIds();

            PositionVO vo = convertToVO(position, appliedPositionIds, favoritePositionIds);
            return Result.success(vo);
        } catch (Exception e) {
            log.error("获取职位详情失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 收藏/取消收藏职位
     */
    @PostMapping("/favorite/{positionId}")
    public Result toggleFavorite(@PathVariable Long positionId) {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return Result.error("请先登录");
            }

            boolean isFavorited = positionFavoriteService.isFavorite(positionId, studentId);
            if (isFavorited) {
                positionFavoriteService.removeFavorite(positionId, studentId);
                return Result.success("取消收藏成功");
            } else {
                positionFavoriteService.addFavorite(positionId, studentId);
                return Result.success("收藏成功");
            }
        } catch (Exception e) {
            log.error("收藏操作失败: {}", e.getMessage(), e);
            return Result.error("操作失败");
        }
    }

    /**
     * 获取学生的收藏列表
     */
    @GetMapping("/favorites")
    public Result getFavorites() {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return Result.error("未登录");
            }
            List<PositionFavorite> favorites = positionFavoriteService.getStudentFavoriteDetails(studentId);
            if (favorites.isEmpty()) {
                return Result.success(Collections.emptyList());
            }
            // 获取收藏职位的ID列表
            List<Long> positionIds = favorites.stream()
                    .map(PositionFavorite::getPositionId)
                    .collect(Collectors.toList());
            // 根据收藏时间排序获取职位详情
            List<Position> positions = positionService.findByIds(positionIds);
            // 构建ID到职位的映射
            Map<Long, Position> positionMap = positions.stream()
                    .collect(Collectors.toMap(Position::getId, p -> p));
            // 构建ID到收藏时间的映射
            Map<Long, Date> collectTimeMap = favorites.stream()
                    .collect(Collectors.toMap(PositionFavorite::getPositionId, PositionFavorite::getCreateTime));
            // 获取已申请职位ID
            Set<Long> appliedPositionIds = getStudentAppliedPositionIds();
            // 按收藏时间顺序组装结果
            List<Map<String, Object>> result = new ArrayList<>();
            for (PositionFavorite fav : favorites) {
                Position position = positionMap.get(fav.getPositionId());
                if (position != null) {
                    PositionVO vo = convertToVO(position, appliedPositionIds, new HashSet<>(Collections.singletonList(position.getId())));
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", vo.getId());
                    item.put("positionId", vo.getId());
                    item.put("companyName", vo.getCompany());
                    item.put("positionName", vo.getTitle());
                    item.put("salary", vo.getSalary());
                    item.put("location", vo.getLocation());
                    item.put("industryName", vo.getIndustryName());
                    item.put("duration", vo.getDuration());
                    item.put("internshipBase", vo.getInternshipBase());
                    item.put("description", vo.getDescription());
                    item.put("requirements", vo.getRequirements());
                    item.put("benefits", vo.getBenefits());
                    item.put("contactPerson", vo.getContactPerson());
                    item.put("contactPhone", vo.getContactPhone());
                    item.put("contactEmail", vo.getContactEmail());
                    item.put("publishTime", vo.getPublishTime());
                    item.put("viewCount", vo.getViewCount());
                    item.put("applyCount", vo.getApplyCount());
                    item.put("collectTime", collectTimeMap.get(position.getId()));
                    item.put("isFavorite", true);
                    result.add(item);
                }
            }
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取收藏列表失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 获取所有行业类别选项（从岗位表中获取实际存在的类别）
     */
    @GetMapping("/options/industries")
    public Result getIndustryOptions() {
        try {
            List<Long> categoryIds = positionService.findDistinctCategoryIds();
            if (categoryIds.isEmpty()) {
                return Result.success(Collections.emptyList());
            }
            List<Map<String, Object>> categories = new ArrayList<>();
            for (Long categoryId : categoryIds) {
                PositionCategory category = positionCategoryMapper.findById(categoryId);
                if (category != null) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("value", category.getId());
                    item.put("label", category.getName());
                    categories.add(item);
                }
            }
            return Result.success(categories);
        } catch (Exception e) {
            log.error("获取行业选项失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 获取所有公司选项（从岗位表中获取实际存在的公司）
     */
    @GetMapping("/options/companies")
    public Result getCompanyOptions() {
        try {
            List<Long> companyIds = positionService.findDistinctCompanyIds();
            if (companyIds.isEmpty()) {
                return Result.success(Collections.emptyList());
            }
            List<Map<String, Object>> companies = new ArrayList<>();
            for (Long companyId : companyIds) {
                CompanyUser company = companyUserMapper.findById(companyId);
                if (company != null) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("value", company.getId());
                    item.put("label", company.getCompanyName());
                    companies.add(item);
                }
            }
            return Result.success(companies);
        } catch (Exception e) {
            log.error("获取公司选项失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 获取所有省份选项（从岗位表中获取实际存在的省份）
     */
    @GetMapping("/options/regions")
    public Result getRegionOptions() {
        try {
            List<String> provinces = positionService.findDistinctProvinces();
            if (provinces.isEmpty()) {
                return Result.success(Collections.emptyList());
            }
            List<Map<String, Object>> regions = new ArrayList<>();
            for (String province : provinces) {
                Map<String, Object> item = new HashMap<>();
                item.put("value", province);
                item.put("label", province);
                regions.add(item);
            }
            return Result.success(regions);
        } catch (Exception e) {
            log.error("获取地区选项失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 将Position实体转换为PositionVO
     */
    private PositionVO convertToVO(Position position, Set<Long> appliedPositionIds, Set<Long> favoritePositionIds) {
        PositionVO vo = new PositionVO();
        vo.setId(position.getId());

        // title <- positionName
        vo.setTitle(position.getPositionName());

        // department
        vo.setDepartment(position.getDepartment());

        // type <- positionType
        vo.setType(position.getPositionType());

        // description
        vo.setDescription(position.getDescription());

        // requirements
        vo.setRequirements(position.getRequirements());

        // location <- province + city + district
        StringBuilder location = new StringBuilder();
        if (position.getProvince() != null) {
            location.append(position.getProvince());
        }
        if (position.getCity() != null) {
            if (location.length() > 0) location.append("-");
            location.append(position.getCity());
        }
        if (position.getDistrict() != null) {
            if (location.length() > 0) location.append("-");
            location.append(position.getDistrict());
        }
        if (position.getDetailAddress() != null && !position.getDetailAddress().isEmpty()) {
            if (location.length() > 0) location.append("-");
            location.append(position.getDetailAddress());
        }
        vo.setLocation(location.length() > 0 ? location.toString() : "未知");

        // salary <- salaryMin - salaryMax
        if (position.getSalaryMin() != null && position.getSalaryMax() != null) {
            vo.setSalary(position.getSalaryMin() + "-" + position.getSalaryMax() + "元/天");
        } else if (position.getSalaryMin() != null) {
            vo.setSalary(position.getSalaryMin() + "元/天以上");
        } else if (position.getSalaryMax() != null) {
            vo.setSalary(position.getSalaryMax() + "元/天以下");
        } else {
            vo.setSalary("面议");
        }

        // duration <- internshipStartDate - internshipEndDate
        if (position.getInternshipStartDate() != null && position.getInternshipEndDate() != null) {
            long diffDays = (position.getInternshipEndDate().getTime() - position.getInternshipStartDate().getTime()) / (1000 * 60 * 60 * 24);
            vo.setDuration(diffDays + "天");
        } else {
            vo.setDuration("不限");
        }

        // publishTime <- publishDate
        vo.setPublishTime(position.getPublishDate());

        // viewCount - 真实浏览次数
        vo.setViewCount(position.getViewCount() != null ? position.getViewCount() : 0);

        // applyCount <- recruitedCount
        vo.setApplyCount(position.getRecruitedCount() != null ? position.getRecruitedCount() : 0);

        // plannedRecruit
        vo.setPlannedRecruit(position.getPlannedRecruit());

        // recruitedCount
        vo.setRecruitedCount(position.getRecruitedCount());

        // remainingQuota
        vo.setRemainingQuota(position.getRemainingQuota());

        // status
        vo.setStatus(position.getStatus());

        // internshipStartDate
        vo.setInternshipStartDate(position.getInternshipStartDate());

        // internshipEndDate
        vo.setInternshipEndDate(position.getInternshipEndDate());

        // createTime
        vo.setCreateTime(position.getCreateTime());

        // industry
        vo.setIndustry(position.getCategoryId());

        // isFavorite - 从收藏表查询真实数据
        vo.setIsFavorite(favoritePositionIds.contains(position.getId()));

        // isApplied - 根据学生申请记录判断
        vo.setIsApplied(appliedPositionIds.contains(position.getId()));

        // 从公司信息中获取更多真实数据
        if (position.getCompanyId() != null) {
            CompanyUser company = companyUserMapper.findById(position.getCompanyId());
            if (company != null) {
                vo.setCompany(company.getCompanyName());
                vo.setContactPerson(company.getContactPerson());
                vo.setContactPhone(company.getContactPhone());
                vo.setContactEmail(company.getContactEmail());

                if (company.getIsInternshipBase() != null && company.getIsInternshipBase() == 1) {
                    vo.setInternshipBase("national");
                } else {
                    vo.setInternshipBase(null);
                }

                if (company.getIndustry() != null) {
                    vo.setIndustryName(company.getIndustry());
                }

                vo.setScale(company.getScale());
                vo.setCompanyIntroduction(company.getIntroduction());
                vo.setBenefits(company.getIntroduction());
                vo.setCompanyId(company.getId());

                if (company.getCompanyTag() != null && !company.getCompanyTag().isEmpty()) {
                    String[] tagArray = company.getCompanyTag().split("[,，]");
                    vo.setTags(Arrays.stream(tagArray)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList()));
                } else {
                    vo.setTags(Collections.emptyList());
                }
            } else {
                vo.setCompany("未知公司");
                vo.setTags(Collections.emptyList());
            }
        } else {
            vo.setCompany("未知公司");
            vo.setTags(Collections.emptyList());
            vo.setCompanyId(null);
        }

        return vo;
    }
}
