package com.internship.service.impl;

import com.internship.entity.CompanyRecommendationRating;
import com.internship.entity.FileUpload;
import com.internship.entity.InternshipAppraisalFile;
import com.internship.entity.InternshipAppraisalSubmission;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.StudentInternshipStatus;
import com.internship.enums.InternshipStatusEnum;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.entity.dto.AppraisalSubmitRequest;
import com.internship.entity.dto.RequiredCompanyRating;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipAppraisalMapper;
import com.internship.service.CompanyRecommendationRatingService;
import com.internship.service.FileUploadService;
import com.internship.service.InternshipAppraisalService;
import com.internship.service.InternshipFlowRules;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.InternshipCalendarService;
import com.internship.service.InternshipLifecycleService;
import com.internship.utils.AliyunOSSOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class InternshipAppraisalServiceImpl implements InternshipAppraisalService {
    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "RETURNED", "WITHDRAWN");
    private static final Set<String> RATABLE_ASSIGNMENT_STATUSES =
            Set.of("CONFIRMED", "REPLACED", "INTERRUPTED", "ENDED");

    private final InternshipAppraisalMapper mapper;
    private final FileUploadService fileUploadService;
    private final AliyunOSSOperator ossOperator;
    private final InternshipCalendarService calendarService;
    private final InternshipLifecycleService lifecycleService;
    private final InternshipStageAssignmentService assignmentService;
    private final CompanyRecommendationRatingService ratingService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.internship.agent.business.StudentPlanRefreshService planRefresh;

    public InternshipAppraisalServiceImpl(InternshipAppraisalMapper mapper,
                                          FileUploadService fileUploadService,
                                          AliyunOSSOperator ossOperator,
                                          InternshipCalendarService calendarService,
                                          InternshipLifecycleService lifecycleService,
                                          InternshipStageAssignmentService assignmentService,
                                          CompanyRecommendationRatingService ratingService) {
        this.mapper = mapper;
        this.fileUploadService = fileUploadService;
        this.ossOperator = ossOperator;
        this.calendarService = calendarService;
        this.lifecycleService = lifecycleService;
        this.assignmentService = assignmentService;
        this.ratingService = ratingService;
    }

    @Override
    public InternshipAppraisalSubmission findExistingSubmission(Long studentId) {
        InternshipAppraisalSubmission submission = mapper.findSubmissionByStudentId(studentId);
        if (submission != null && submission.getId() != null) {
            submission.setFiles(mapper.findFilesBySubmissionId(submission.getId()));
        }
        return submission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InternshipAppraisalSubmission findOrCreateDraft(Long studentId) {
        InternshipAppraisalSubmission submission = mapper.findSubmissionByStudentId(studentId);
        if (submission == null) {
            submission = new InternshipAppraisalSubmission();
            submission.setStudentId(studentId);
            submission.setStatus("DRAFT");
            submission.setCreateTime(new Date());
            mapper.insertSubmission(submission);
        }
        submission.setFiles(mapper.findFilesBySubmissionId(submission.getId()));
        return submission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InternshipAppraisalSubmission getAppraisal(Long studentId) {
        InternshipAppraisalSubmission submission = findOrCreateDraft(studentId);
        StudentInternshipStatus internshipStatus = lifecycleService.refreshStudentLifecycle(studentId);
        if (internshipStatus != null) {
            submission.setInternshipStatus(internshipStatus.getStatus());
            submission.setInternshipEnded(Integer.valueOf(InternshipStatusEnum.ENDED.getCode())
                    .equals(internshipStatus.getStatus()));
        }
        hydrateRatings(submission, requiredCompanyRatings(studentId));
        return submission;
    }

    @Override
    public void validateFileUploadAllowed(Long studentId) {
        InternshipAppraisalSubmission submission = mapper.findSubmissionByStudentId(studentId);
        if (submission != null
                && !"DRAFT".equals(submission.getStatus())
                && !"RETURNED".equals(submission.getStatus())) {
            throw new BusinessException("当前鉴定表已提交，不能继续添加文件");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InternshipAppraisalFile addFile(Long studentId, InternshipAppraisalFile file) {
        if (file == null || file.getFilePath() == null || file.getFilePath().isBlank()) {
            throw new BusinessException("鉴定表文件不能为空");
        }
        InternshipAppraisalSubmission submission = findOrCreateDraft(studentId);
        if (!EDITABLE_STATUSES.contains(submission.getStatus())) {
            throw new BusinessException("当前鉴定表已提交，不能继续添加文件");
        }
        file.setSubmissionId(submission.getId());
        mapper.insertFile(file);
        return file;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InternshipAppraisalFile addUploadedFile(Long studentId,
                                                    FileUpload upload,
                                                    InternshipAppraisalFile file) {
        if (upload == null) {
            throw new BusinessException("文件上传记录不能为空");
        }
        fileUploadService.insert(upload);
        if (upload.getId() == null) {
            throw new BusinessException("文件上传记录保存失败");
        }
        file.setFileUploadId(upload.getId());
        return addFile(studentId, file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long studentId, Long fileId) {
        InternshipAppraisalFile file = mapper.findFileById(fileId);
        if (file == null) throw new BusinessException("鉴定表文件不存在");
        InternshipAppraisalSubmission submission = mapper.findSubmissionById(file.getSubmissionId());
        if (submission == null || !studentId.equals(submission.getStudentId())) {
            throw new BusinessException("无权操作该鉴定表文件");
        }
        if (!EDITABLE_STATUSES.contains(submission.getStatus())) {
            throw new BusinessException("当前鉴定表已提交，不能删除文件");
        }
        var upload = file.getFileUploadId() == null ? null : fileUploadService.findById(file.getFileUploadId());
        if (upload != null) {
            if (upload.getFilePath() != null) {
                try {
                    ossOperator.deleteFile(upload.getFilePath());
                } catch (Exception e) {
                    log.warn("删除实习鉴定表 OSS 文件失败，继续清理数据库记录: {}", e.getMessage());
                }
            }
        }
        mapper.deleteFile(fileId);
        if (upload != null) {
            fileUploadService.delete(file.getFileUploadId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InternshipAppraisalSubmission submit(Long studentId, AppraisalSubmitRequest request) {
        InternshipAppraisalSubmission submission = mapper.findSubmissionByStudentIdForUpdate(studentId);
        if (submission == null) {
            submission = findOrCreateDraft(studentId);
        } else {
            submission.setFiles(mapper.findFilesBySubmissionId(submission.getId()));
        }
        if (!EDITABLE_STATUSES.contains(submission.getStatus())) {
            throw new BusinessException("当前鉴定表已提交");
        }
        validateFinalSubmissionWindow(studentId);
        int fileCount = submission.getFiles() == null ? 0 : submission.getFiles().size();
        InternshipFlowRules.validateAppraisalFileCount(fileCount);

        List<RequiredCompanyRating> requiredRatings = requiredCompanyRatings(studentId);
        List<CompanyRecommendationRating> ratings = validateAndBuildRatings(
                submission.getId(), requiredRatings, request);
        ratingService.replaceSubmissionRatings(submission.getId(), ratings);

        submission.setStatus("SUBMITTED");
        submission.setSubmittedAt(new Date());
        mapper.updateSubmission(submission);
        submission.setFiles(mapper.findFilesBySubmissionId(submission.getId()));
        hydrateRatings(submission, requiredRatings);
        if (planRefresh != null) planRefresh.afterCommit(studentId);
        return submission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InternshipAppraisalSubmission withdraw(Long studentId) {
        InternshipAppraisalSubmission submission = mapper.findSubmissionByStudentIdForUpdate(studentId);
        if (submission == null) throw new BusinessException("实习鉴定表不存在");
        if (!"SUBMITTED".equals(submission.getStatus())) throw new BusinessException("当前鉴定表未处于已提交状态");
        validateWithdrawWindow();
        submission.setStatus("WITHDRAWN");
        submission.setSubmittedAt(null);
        mapper.updateSubmission(submission);
        submission.setFiles(mapper.findFilesBySubmissionId(submission.getId()));
        hydrateRatings(submission, requiredCompanyRatings(studentId));
        if (planRefresh != null) planRefresh.afterCommit(studentId);
        return submission;
    }

    private void validateWithdrawWindow() {
        InternshipCalendar calendar = calendarService.getCalendar();
        if (calendar == null || isBlank(calendar.getAppraisalDeadline())) {
            throw new BusinessException("实习鉴定截止日期未配置，暂不能撤回");
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate deadline = LocalDate.parse(calendar.getAppraisalDeadline());
        if (today.isAfter(deadline)) throw new BusinessException("实习鉴定表提交已截止，不能撤回");
    }
    private List<RequiredCompanyRating> requiredCompanyRatings(Long studentId) {
        List<InternshipStageAssignment> assignments = assignmentService.findByStudentId(studentId);
        if (assignments == null || assignments.isEmpty()) return Collections.emptyList();

        Date actualThrough = appraisalEvidenceBoundary();
        Map<Long, String> companies = new LinkedHashMap<>();
        for (InternshipStageAssignment assignment : assignments) {
            if (assignment == null || assignment.getCompanyId() == null
                    || !RATABLE_ASSIGNMENT_STATUSES.contains(assignment.getStatus())
                    || assignment.getInternshipStartTime() == null
                    || !hasPositiveActualDuration(assignment, actualThrough)) {
                continue;
            }
            if (!companies.containsKey(assignment.getCompanyId())
                    || isBlank(companies.get(assignment.getCompanyId()))) {
                companies.put(assignment.getCompanyId(), assignment.getCompanyName());
            }
        }

        List<RequiredCompanyRating> required = new ArrayList<>();
        companies.forEach((companyId, companyName) ->
                required.add(new RequiredCompanyRating(companyId, companyName)));
        return required;
    }

    /**
     * 当前岗位可以是开放区间；鉴定时按“今天或教学日历结束日（较早者）”计算已发生的时长。
     */
    private Date appraisalEvidenceBoundary() {
        Date now = new Date();
        InternshipCalendar calendar = calendarService.getCalendar();
        if (calendar == null || isBlank(calendar.getEndDate())) return now;
        try {
            Date calendarEnd = Date.from(LocalDate.parse(calendar.getEndDate())
                    .atTime(LocalTime.MAX)
                    .atZone(ZoneId.of("Asia/Shanghai"))
                    .toInstant());
            return calendarEnd.before(now) ? calendarEnd : now;
        } catch (RuntimeException ignored) {
            return now;
        }
    }

    private boolean hasPositiveActualDuration(InternshipStageAssignment assignment, Date actualThrough) {
        Date effectiveEnd = assignment.getInternshipEndTime();
        if (effectiveEnd == null || effectiveEnd.after(actualThrough)) effectiveEnd = actualThrough;
        return effectiveEnd.after(assignment.getInternshipStartTime());
    }

    private List<CompanyRecommendationRating> validateAndBuildRatings(
            Long submissionId,
            List<RequiredCompanyRating> requiredRatings,
            AppraisalSubmitRequest request) {
        // 自主实习学生自找的企业未收录系统，无需星星推荐评价，空评价直接放行
        if (requiredRatings.isEmpty()) {
            return new ArrayList<>();
        }

        List<AppraisalSubmitRequest.RatingInput> inputs =
                request == null ? null : request.getRatings();
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException("请完成所有实际实习企业的评分");
        }

        Map<Long, Integer> provided = new LinkedHashMap<>();
        for (AppraisalSubmitRequest.RatingInput input : inputs) {
            if (input == null || input.getCompanyId() == null || input.getStars() == null) {
                throw new BusinessException("企业评分信息不完整");
            }
            int stars;
            try {
                stars = input.getStars().intValueExact();
            } catch (ArithmeticException e) {
                throw new BusinessException("企业推荐指数必须为1到5星的整数");
            }
            if (stars < 1 || stars > 5) {
                throw new BusinessException("企业推荐指数必须为1到5星的整数");
            }
            if (provided.putIfAbsent(input.getCompanyId(), stars) != null) {
                throw new BusinessException("同一家企业不能重复评分");
            }
        }

        Set<Long> requiredCompanyIds = new java.util.LinkedHashSet<>();
        for (RequiredCompanyRating required : requiredRatings) {
            requiredCompanyIds.add(required.getCompanyId());
        }
        for (Long companyId : provided.keySet()) {
            if (!requiredCompanyIds.contains(companyId)) {
                throw new BusinessException("评分中包含无权评价的企业");
            }
        }
        if (!provided.keySet().equals(requiredCompanyIds)) {
            throw new BusinessException("请完成所有实际实习企业的评分");
        }

        List<CompanyRecommendationRating> ratings = new ArrayList<>();
        for (Long companyId : requiredCompanyIds) {
            CompanyRecommendationRating rating = new CompanyRecommendationRating();
            rating.setSubmissionId(submissionId);
            rating.setCompanyId(companyId);
            rating.setStars(provided.get(companyId));
            ratings.add(rating);
        }
        return ratings;
    }

    private void hydrateRatings(InternshipAppraisalSubmission submission,
                                List<RequiredCompanyRating> requiredRatings) {
        List<CompanyRecommendationRating> existingRatings =
                ratingService.findBySubmissionId(submission.getId());
        Map<Long, Integer> starsByCompany = new LinkedHashMap<>();
        for (CompanyRecommendationRating rating : existingRatings) {
            starsByCompany.put(rating.getCompanyId(), rating.getStars());
        }
        for (RequiredCompanyRating requiredRating : requiredRatings) {
            requiredRating.setStars(starsByCompany.get(requiredRating.getCompanyId()));
        }
        submission.setRequiredCompanyRatings(requiredRatings);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void validateFinalSubmissionWindow(Long studentId) {
        InternshipCalendar calendar = calendarService.getCalendar();
        if (calendar == null || isBlank(calendar.getEndDate())
                || isBlank(calendar.getAppraisalDeadline())) {
            throw new BusinessException("全局实习日历未完整配置");
        }
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        LocalDate endDate = LocalDate.parse(calendar.getEndDate());
        if (today.isBefore(endDate)) {
            throw new BusinessException("实习尚未结束，暂不能提交实习鉴定表");
        }
        LocalDate deadline = LocalDate.parse(calendar.getAppraisalDeadline());
        if (today.isAfter(deadline)) {
            throw new BusinessException("实习鉴定表提交已截止，请联系辅导员");
        }

        StudentInternshipStatus internshipStatus = lifecycleService.refreshStudentLifecycle(studentId);
        if (internshipStatus == null
                || !Integer.valueOf(InternshipStatusEnum.ENDED.getCode()).equals(internshipStatus.getStatus())) {
            throw new BusinessException("实习状态尚未显示为已结束，暂不能提交实习鉴定表");
        }
    }
}
