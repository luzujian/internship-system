package com.internship.service;

import com.internship.entity.CompanyRecommendationRating;
import com.internship.entity.CompanyUser;
import com.internship.mapper.CompanyUserMapper;
import com.internship.entity.FileUpload;
import com.internship.entity.InternshipAppraisalFile;
import com.internship.entity.InternshipAppraisalSubmission;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.entity.dto.AppraisalSubmitRequest;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipAppraisalMapper;
import com.internship.service.impl.InternshipAppraisalServiceImpl;
import com.internship.utils.AliyunOSSOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternshipAppraisalRatingServiceTest {
    private static final Long STUDENT_ID = 11L;
    private static final Long SUBMISSION_ID = 21L;

    @Mock private InternshipAppraisalMapper appraisalMapper;
    @Mock private FileUploadService fileUploadService;
    @Mock private AliyunOSSOperator ossOperator;
    @Mock private InternshipCalendarService calendarService;
    @Mock private InternshipLifecycleService lifecycleService;
    @Mock private InternshipStageAssignmentService assignmentService;
    @Mock private CompanyRecommendationRatingService ratingService;
    @Mock private CompanyUserMapper companyUserMapper;

    private InternshipAppraisalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InternshipAppraisalServiceImpl(
                appraisalMapper,
                fileUploadService,
                ossOperator,
                calendarService,
                lifecycleService,
                assignmentService,
                ratingService,
                companyUserMapper);
        InternshipCalendar calendar = new InternshipCalendar();
        calendar.setEndDate(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1).toString());
        calendar.setAppraisalDeadline(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(30).toString());
        org.mockito.Mockito.lenient().when(calendarService.getCalendar()).thenReturn(calendar);
        org.mockito.Mockito.lenient().when(lifecycleService.refreshStudentLifecycle(STUDENT_ID))
                .thenReturn(status(4));
    }

    @Test
    void submitsExactlyOneRatingPerDistinctActualCompany() {
        givenEditableSubmission("DRAFT");
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                assignment(1L, 101L, "甲企业", "CONFIRMED"),
                assignment(2L, 101L, "甲企业", "REPLACED"),
                assignment(3L, 202L, "乙企业", "INTERRUPTED"),
                assignment(4L, 303L, "待确认企业", "PENDING_CONFIRMATION")));

        InternshipAppraisalSubmission result = service.submit(
                STUDENT_ID, request(Map.of(101L, 5, 202L, 4)));

        ArgumentCaptor<List<CompanyRecommendationRating>> captor = ArgumentCaptor.forClass(List.class);
        verify(ratingService).replaceSubmissionRatings(org.mockito.ArgumentMatchers.eq(SUBMISSION_ID), captor.capture());
        assertEquals(Map.of(101L, 5, 202L, 4), captor.getValue().stream()
                .collect(Collectors.toMap(
                        CompanyRecommendationRating::getCompanyId,
                        CompanyRecommendationRating::getStars)));
        assertEquals("SUBMITTED", result.getStatus());
        verify(appraisalMapper).updateSubmission(result);
    }

    @Test
    void appraisalQueryReturnsExistingStarsInsideRequiredCompanies() {
        InternshipAppraisalSubmission submission = new InternshipAppraisalSubmission();
        submission.setId(SUBMISSION_ID);
        submission.setStudentId(STUDENT_ID);
        submission.setStatus("RETURNED");
        when(appraisalMapper.findSubmissionByStudentId(STUDENT_ID)).thenReturn(submission);
        when(appraisalMapper.findFilesBySubmissionId(SUBMISSION_ID)).thenReturn(List.of());
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                assignment(1L, 101L, "甲企业", "CONFIRMED")));
        CompanyRecommendationRating existing = new CompanyRecommendationRating();
        existing.setSubmissionId(SUBMISSION_ID);
        existing.setCompanyId(101L);
        existing.setStars(4);
        when(ratingService.findBySubmissionId(SUBMISSION_ID)).thenReturn(List.of(existing));

        InternshipAppraisalSubmission result = service.getAppraisal(STUDENT_ID);

        assertEquals(1, result.getRequiredCompanyRatings().size());
        assertEquals(101L, result.getRequiredCompanyRatings().get(0).getCompanyId());
        assertEquals(4, result.getRequiredCompanyRatings().get(0).getStars());
    }

    @Test
    void endedPlacementStillRequiresCompanyRating() {
        InternshipAppraisalSubmission submission = new InternshipAppraisalSubmission();
        submission.setId(SUBMISSION_ID);
        submission.setStudentId(STUDENT_ID);
        submission.setStatus("DRAFT");
        when(appraisalMapper.findSubmissionByStudentId(STUDENT_ID)).thenReturn(submission);
        when(appraisalMapper.findFilesBySubmissionId(SUBMISSION_ID)).thenReturn(List.of());
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                assignment(9L, 909L, "历史实习企业", "ENDED")));
        when(ratingService.findBySubmissionId(SUBMISSION_ID)).thenReturn(List.of());

        InternshipAppraisalSubmission result = service.getAppraisal(STUDENT_ID);

        assertEquals(1, result.getRequiredCompanyRatings().size());
        assertEquals(909L, result.getRequiredCompanyRatings().get(0).getCompanyId());
        assertEquals("历史实习企业", result.getRequiredCompanyRatings().get(0).getCompanyName());
    }

    @Test
    void openCurrentPlacementCountsItsElapsedTimeAtAppraisal() {
        InternshipAppraisalSubmission submission = new InternshipAppraisalSubmission();
        submission.setId(SUBMISSION_ID);
        submission.setStudentId(STUDENT_ID);
        submission.setStatus("DRAFT");
        when(appraisalMapper.findSubmissionByStudentId(STUDENT_ID)).thenReturn(submission);
        when(appraisalMapper.findFilesBySubmissionId(SUBMISSION_ID)).thenReturn(List.of());
        InternshipStageAssignment current = assignment(10L, 1001L, "当前实习企业", "CONFIRMED");
        current.setInternshipStartTime(java.sql.Timestamp.valueOf(
                LocalDate.now().minusDays(10).atStartOfDay()));
        current.setInternshipEndTime(null);
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(current));
        when(ratingService.findBySubmissionId(SUBMISSION_ID)).thenReturn(List.of());

        InternshipAppraisalSubmission result = service.getAppraisal(STUDENT_ID);

        assertEquals(1, result.getRequiredCompanyRatings().size());
        assertEquals(1001L, result.getRequiredCompanyRatings().get(0).getCompanyId());
    }

    @Test
    void submissionWindowUsesTheGlobalInternshipCalendar() {
        givenEditableSubmission("DRAFT");
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                assignment(1L, 101L, "甲企业", "ENDED")));

        InternshipAppraisalSubmission result = service.submit(
                STUDENT_ID, request(Map.of(101L, 5)));

        assertEquals("SUBMITTED", result.getStatus());
    }

    @Test
    void rejectsSubmissionUntilRealInternshipStatusEnds() {
        givenEditableSubmission("DRAFT");
        when(lifecycleService.refreshStudentLifecycle(STUDENT_ID)).thenReturn(status(3));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(STUDENT_ID, request(Map.of())));

        assertEquals("实习状态尚未显示为已结束，暂不能提交实习鉴定表", error.getMessage());
        verify(assignmentService, never()).findByStudentId(any());
        verify(ratingService, never()).replaceSubmissionRatings(any(), any());
        verify(appraisalMapper, never()).updateSubmission(any());
    }

    @Test
    void appraisalQueryExposesTheRealLifecycleGate() {
        InternshipAppraisalSubmission submission = new InternshipAppraisalSubmission();
        submission.setId(SUBMISSION_ID);
        submission.setStudentId(STUDENT_ID);
        submission.setStatus("DRAFT");
        when(appraisalMapper.findSubmissionByStudentId(STUDENT_ID)).thenReturn(submission);
        when(appraisalMapper.findFilesBySubmissionId(SUBMISSION_ID)).thenReturn(List.of());
        when(lifecycleService.refreshStudentLifecycle(STUDENT_ID)).thenReturn(status(3));
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of());
        when(ratingService.findBySubmissionId(SUBMISSION_ID)).thenReturn(List.of());

        InternshipAppraisalSubmission result = service.getAppraisal(STUDENT_ID);

        assertEquals(3, result.getInternshipStatus());
        assertEquals(false, result.getInternshipEnded());
        verify(lifecycleService).refreshStudentLifecycle(STUDENT_ID);
    }

    @Test
    void storesUploadMetadataAndAppraisalFileInOneServiceOperation() {
        InternshipAppraisalSubmission submission = new InternshipAppraisalSubmission();
        submission.setId(SUBMISSION_ID);
        submission.setStudentId(STUDENT_ID);
        submission.setStatus("DRAFT");
        when(appraisalMapper.findSubmissionByStudentId(STUDENT_ID)).thenReturn(submission);
        when(appraisalMapper.findFilesBySubmissionId(SUBMISSION_ID)).thenReturn(List.of());
        doAnswer(invocation -> {
            FileUpload upload = invocation.getArgument(0);
            upload.setId(88L);
            return 1;
        }).when(fileUploadService).insert(any(FileUpload.class));

        FileUpload upload = new FileUpload();
        upload.setFileName("鉴定表.docx");
        InternshipAppraisalFile appraisalFile = new InternshipAppraisalFile();
        appraisalFile.setFileName("鉴定表.docx");
        appraisalFile.setFilePath("https://example.test/internship-appraisal/file.docx");

        InternshipAppraisalFile result =
                service.addUploadedFile(STUDENT_ID, upload, appraisalFile);

        assertEquals(88L, result.getFileUploadId());
        assertEquals(SUBMISSION_ID, result.getSubmissionId());
        verify(appraisalMapper).insertFile(result);
    }

    @Test
    void withdrawnAppraisalCanUploadReplacementFile() {
        InternshipAppraisalSubmission submission = new InternshipAppraisalSubmission();
        submission.setId(SUBMISSION_ID);
        submission.setStudentId(STUDENT_ID);
        submission.setStatus("WITHDRAWN");
        when(appraisalMapper.findSubmissionByStudentId(STUDENT_ID)).thenReturn(submission);
        when(appraisalMapper.findFilesBySubmissionId(SUBMISSION_ID)).thenReturn(List.of());
        InternshipAppraisalFile replacement = new InternshipAppraisalFile();
        replacement.setFileName("修订鉴定表.pdf");
        replacement.setFilePath("internship-appraisal/replacement.pdf");

        assertDoesNotThrow(() -> service.validateFileUploadAllowed(STUDENT_ID));
        service.addFile(STUDENT_ID, replacement);

        verify(appraisalMapper).insertFile(replacement);
    }

    @Test
    void rejectsMissingCompanyRatingWithoutWritingAnything() {
        givenEditableSubmission("RETURNED");
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                assignment(1L, 101L, "甲企业", "CONFIRMED"),
                assignment(2L, 202L, "乙企业", "CONFIRMED")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(STUDENT_ID, request(Map.of(101L, 5))));

        assertEquals("请完成所有实际实习企业的评分", error.getMessage());
        verify(ratingService, never()).replaceSubmissionRatings(any(), any());
        verify(appraisalMapper, never()).updateSubmission(any());
    }

    @Test
    void rejectsForeignCompanyEvenWhenRatingCountMatches() {
        givenEditableSubmission("DRAFT");
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                assignment(1L, 101L, "甲企业", "CONFIRMED")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(STUDENT_ID, request(Map.of(999L, 5))));

        assertEquals("评分中包含无权评价的企业", error.getMessage());
        verify(ratingService, never()).replaceSubmissionRatings(any(), any());
    }

    @Test
    void rejectsDuplicateAndOutOfRangeRatings() {
        givenEditableSubmission("DRAFT");
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                assignment(1L, 101L, "甲企业", "CONFIRMED")));

        AppraisalSubmitRequest duplicate = new AppraisalSubmitRequest();
        duplicate.setRatings(List.of(input(101L, 5), input(101L, 4)));
        assertEquals("同一家企业不能重复评分", assertThrows(BusinessException.class,
                () -> service.submit(STUDENT_ID, duplicate)).getMessage());

        AppraisalSubmitRequest outOfRange = new AppraisalSubmitRequest();
        outOfRange.setRatings(List.of(input(101L, 6)));
        assertEquals("企业推荐指数必须为1到5星的整数", assertThrows(BusinessException.class,
                () -> service.submit(STUDENT_ID, outOfRange)).getMessage());

        AppraisalSubmitRequest decimal = new AppraisalSubmitRequest();
        AppraisalSubmitRequest.RatingInput decimalInput = new AppraisalSubmitRequest.RatingInput();
        decimalInput.setCompanyId(101L);
        decimalInput.setStars(new BigDecimal("4.5"));
        decimal.setRatings(List.of(decimalInput));
        assertEquals("企业推荐指数必须为1到5星的整数", assertThrows(BusinessException.class,
                () -> service.submit(STUDENT_ID, decimal)).getMessage());
        verify(ratingService, never()).replaceSubmissionRatings(any(), any());
    }

    @Test
    void submitsWithoutCompanyRatingsWhenThereIsNoActualCompany() {
        givenEditableSubmission("DRAFT");
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                assignment(1L, 101L, "待确认企业", "PENDING_CONFIRMATION"),
                assignment(2L, null, null, "CONFIRMED")));
        when(ratingService.findBySubmissionId(SUBMISSION_ID)).thenReturn(List.of());

        InternshipAppraisalSubmission result =
                service.submit(STUDENT_ID, request(Map.of(101L, 5)));

        assertEquals("SUBMITTED", result.getStatus());
        assertTrue(result.getRequiredCompanyRatings().isEmpty());
        verify(ratingService).replaceSubmissionRatings(SUBMISSION_ID, List.of());
        verify(appraisalMapper).updateSubmission(result);
    }

    @Test
    void submittedAppraisalIsLockedBeforeRatingsAreChanged() {
        givenEditableSubmission("SUBMITTED");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(STUDENT_ID, request(Map.of(101L, 5))));

        assertEquals("当前鉴定表已提交", error.getMessage());
        verify(assignmentService, never()).findByStudentId(any());
        verify(ratingService, never()).replaceSubmissionRatings(any(), any());
    }

    @Test
    void historicalSelfPracticeShowsCooperativeCompanyAndSavedStars() {
        InternshipAppraisalSubmission submission = new InternshipAppraisalSubmission();
        submission.setId(SUBMISSION_ID);
        submission.setStatus("DRAFT");
        when(appraisalMapper.findSubmissionByStudentId(STUDENT_ID)).thenReturn(submission);
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(selfPractice("甲企业")));
        givenCooperativeCompany();
        CompanyRecommendationRating saved = new CompanyRecommendationRating();
        saved.setCompanyId(101L);
        saved.setStars(4);
        when(ratingService.findBySubmissionId(SUBMISSION_ID)).thenReturn(List.of(saved));

        InternshipAppraisalSubmission result = service.getAppraisal(STUDENT_ID);

        assertEquals(1, result.getRequiredCompanyRatings().size());
        assertEquals(101L, result.getRequiredCompanyRatings().get(0).getCompanyId());
        assertEquals(4, result.getRequiredCompanyRatings().get(0).getStars());
    }

    @Test
    void selfPracticeAndPlatformPlacementAtSameCompanySaveOneRating() {
        givenEditableSubmission("DRAFT");
        givenCooperativeCompany();
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                selfPractice("甲企业"), assignment(2L, 101L, "甲企业", "ENDED")));

        InternshipAppraisalSubmission result = service.submit(STUDENT_ID, request(Map.of(101L, 5)));

        ArgumentCaptor<List<CompanyRecommendationRating>> captor = ArgumentCaptor.forClass(List.class);
        verify(ratingService).replaceSubmissionRatings(org.mockito.ArgumentMatchers.eq(SUBMISSION_ID), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(101L, captor.getValue().get(0).getCompanyId());
        assertEquals(5, captor.getValue().get(0).getStars());
        assertEquals("SUBMITTED", result.getStatus());
    }

    @Test
    void cooperativeSelfPracticeCannotSkipRating() {
        givenEditableSubmission("DRAFT");
        givenCooperativeCompany();
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(selfPractice("甲企业")));

        assertEquals("请完成所有实际实习企业的评分", assertThrows(BusinessException.class,
                () -> service.submit(STUDENT_ID, request(Map.of()))).getMessage());
        verify(ratingService, never()).replaceSubmissionRatings(any(), any());
    }

    @Test
    void unregisteredSelfPracticeSubmitsWithoutRating() {
        givenEditableSubmission("DRAFT");
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(selfPractice("校外企业")));

        assertEquals("SUBMITTED", service.submit(STUDENT_ID, request(Map.of())).getStatus());
        verify(ratingService).replaceSubmissionRatings(SUBMISSION_ID, List.of());
    }

    @Test
    void unstartedSelfPracticeDoesNotBecomeRatableByCompanyName() {
        givenEditableSubmission("DRAFT");
        InternshipStageAssignment pending = selfPractice("甲企业");
        pending.setStatus("PENDING_CONFIRMATION");
        InternshipStageAssignment zeroDuration = selfPractice("甲企业");
        zeroDuration.setInternshipEndTime(zeroDuration.getInternshipStartTime());
        when(assignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(pending, zeroDuration));

        assertEquals("SUBMITTED", service.submit(STUDENT_ID, request(Map.of())).getStatus());
        verify(companyUserMapper, never()).findApprovedByCompanyName(any());
        verify(ratingService).replaceSubmissionRatings(SUBMISSION_ID, List.of());
    }

    private InternshipStageAssignment selfPractice(String companyName) {
        InternshipStageAssignment assignment = assignment(1L, null, companyName, "ENDED");
        assignment.setSourceType("SELF_PRACTICE");
        return assignment;
    }

    private void givenCooperativeCompany() {
        CompanyUser company = new CompanyUser();
        company.setId(101L);
        company.setCompanyName("甲企业");
        when(companyUserMapper.findApprovedByCompanyName("甲企业")).thenReturn(company);
    }

    private StudentInternshipStatus status(int code) {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setId(31L);
        status.setStudentId(STUDENT_ID);
        status.setStatus(code);
        return status;
    }

    private void givenEditableSubmission(String status) {
        InternshipAppraisalSubmission submission = new InternshipAppraisalSubmission();
        submission.setId(SUBMISSION_ID);
        submission.setStudentId(STUDENT_ID);
        submission.setStatus(status);
        when(appraisalMapper.findSubmissionByStudentIdForUpdate(STUDENT_ID)).thenReturn(submission);
        when(appraisalMapper.findFilesBySubmissionId(SUBMISSION_ID))
                .thenReturn(List.of(new InternshipAppraisalFile()));
    }

    private InternshipStageAssignment assignment(Long id, Long companyId, String companyName, String status) {
        InternshipStageAssignment assignment = new InternshipStageAssignment();
        assignment.setId(id);
        assignment.setStudentId(STUDENT_ID);
        assignment.setCompanyId(companyId);
        assignment.setCompanyName(companyName);
        assignment.setStatus(status);
        assignment.setInternshipStartTime(java.sql.Timestamp.valueOf(
                LocalDate.now().minusDays(2).atStartOfDay()));
        assignment.setInternshipEndTime(java.sql.Timestamp.valueOf(
                LocalDate.now().minusDays(1).atStartOfDay()));
        return assignment;
    }

    private AppraisalSubmitRequest request(Map<Long, Integer> ratings) {
        AppraisalSubmitRequest request = new AppraisalSubmitRequest();
        request.setRatings(ratings.entrySet().stream()
                .map(entry -> input(entry.getKey(), entry.getValue()))
                .toList());
        return request;
    }

    private AppraisalSubmitRequest.RatingInput input(Long companyId, Integer stars) {
        AppraisalSubmitRequest.RatingInput input = new AppraisalSubmitRequest.RatingInput();
        input.setCompanyId(companyId);
        input.setStars(BigDecimal.valueOf(stars));
        return input;
    }
}
