package com.internship.service;

import com.internship.entity.StudentApplication;
import com.internship.entity.StudentUser;
import com.internship.entity.CompanyUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.StudentApplicationMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.service.impl.StudentApplicationServiceImpl;
import com.internship.util.StudentInternshipStatusCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentApplicationSelfPracticeInfoTest {
    @Mock private StudentApplicationMapper applicationMapper;
    @Mock private CompanyUserMapper companyUserMapper;
    @Mock private InternshipTimeSettingsService timeSettingsService;
    @Mock private StudentInternshipStatusMapper statusMapper;
    @Mock private InternshipProgressRecordService progressRecordService;
    @Mock private InternshipConfirmationRecordService confirmationRecordService;
    @Mock private InternshipStageAssignmentService stageAssignmentService;
    @Mock private StudentInternshipStatusCache statusCache;
    @Mock private com.internship.service.InternshipCalendarService internshipCalendarService;
    @Mock private com.internship.service.InternshipLifecycleService lifecycleService;
    @Mock private StudentUserService studentUserService;
    @Mock private EmailService emailService;
    @Mock private InternshipAgreementService agreementService;

    private StudentApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentApplicationServiceImpl(
                applicationMapper, companyUserMapper, timeSettingsService, statusMapper,
                progressRecordService, confirmationRecordService, stageAssignmentService, statusCache,
                internshipCalendarService, lifecycleService, studentUserService, emailService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "agreementService", agreementService);
    }

    @Test
    void selfPracticeRequiresCityAndHrContact() {
        assertAll(
                () -> assertRejected(app -> app.setCompanyCity(" "), "单位所在城市不能为空"),
                () -> assertRejected(app -> app.setHrManagerName(" "), "人事经理姓名不能为空"),
                () -> assertRejected(app -> app.setHrManagerPhone(" "), "人事经理联系电话不能为空")
        );

        verify(applicationMapper, never()).insert(any(StudentApplication.class));
    }

    @Test
    void completeSelfPracticeInformationCanBeInserted() {
        StudentApplication application = completeApplication();
        when(applicationMapper.insert(application)).thenReturn(1);

        assertEquals(1, service.insert(application));
        verify(applicationMapper).lockStudent(1L);
        verify(applicationMapper).insert(application);
    }

    @Test
    void approvedSystemCompanyCanBeSubmittedAsSelfPractice() {
        StudentApplication application = completeApplication();
        CompanyUser company = new CompanyUser();
        company.setId(10L);
        company.setCompanyName(application.getCompany());
        when(companyUserMapper.findApprovedByCompanyName(application.getCompany())).thenReturn(company);
        when(applicationMapper.insert(application)).thenReturn(1);

        assertEquals(1, service.insert(application));
        verify(applicationMapper).insert(application);
    }

    @Test
    void similarCompanyNameMustUseRegisteredStandardName() {
        StudentApplication application = completeApplication();
        CompanyUser similar = new CompanyUser();
        similar.setId(10L);
        similar.setCompanyName("测试单位科技有限公司");
        when(companyUserMapper.findApprovedByCompanyName(application.getCompany())).thenReturn(null);
        when(companyUserMapper.findApprovedCompanyMatches(application.getCompany().trim()))
                .thenReturn(java.util.List.of(similar));

        BusinessException error = assertThrows(BusinessException.class, () -> service.insert(application));

        assertEquals("系统企业库存在名称相近的校企合作单位（测试单位科技有限公司），请从下拉候选中选择系统登记的标准企业名称；若该企业确实不在系统中，请核对名称后重新提交", error.getMessage());
        verify(applicationMapper, never()).insert(any(StudentApplication.class));
    }

    @Test
    void findByIdMarksCooperativeCompanyForSelfPractice() {
        StudentApplication stored = completeApplication();
        stored.setId(20L);
        when(applicationMapper.findById(20L)).thenReturn(stored);
        CompanyUser company = new CompanyUser();
        company.setId(10L);
        company.setCompanyName("测试单位");
        when(companyUserMapper.findAllApprovedCompanies()).thenReturn(java.util.List.of(company));

        StudentApplication result = service.findById(20L);

        assertEquals(Boolean.TRUE, result.getCooperativeCompany());
        assertEquals(10L, result.getCooperativeCompanyId());
    }

    @Test
    void pendingSelfPracticeCanBeWithdrawnBeforeTeacherReview() {
        StudentApplication pending = completeApplication();
        pending.setId(9L);
        pending.setStatus("pending");
        when(applicationMapper.findByIdForUpdate(9L)).thenReturn(pending);
        when(applicationMapper.update(pending)).thenReturn(1);

        assertEquals(1, service.withdrawApplication(9L, 1L));
        assertEquals("withdrawn", pending.getStatus());
    }

    @Test
    void reviewedSelfPracticeCannotBeWithdrawn() {
        StudentApplication reviewed = completeApplication();
        reviewed.setId(9L);
        reviewed.setStatus("approved");
        reviewed.setReviewerId(22L);
        reviewed.setReviewTime(new java.util.Date());
        when(applicationMapper.findByIdForUpdate(9L)).thenReturn(reviewed);

        BusinessException error = assertThrows(BusinessException.class, () -> service.withdrawApplication(9L, 1L));
        assertEquals("教师已处理该申请，无法撤回", error.getMessage());
        verify(applicationMapper, never()).update(any(StudentApplication.class));
    }

    @Test
    void rejectedSelfPracticeCanBeResubmittedWithUnreplacedMaterialsRetained() {
        StudentApplication existing = completeApplication();
        existing.setId(15L);
        existing.setStatus("rejected");
        existing.setReviewerId(22L);
        existing.setReviewTime(new java.util.Date());
        existing.setRejectReason("请补充实习安排");
        existing.setMaterials(new java.util.HashMap<>(java.util.Map.of("个人申请书", "uploaded.pdf")));
        when(applicationMapper.findByIdForUpdate(15L)).thenReturn(existing);
        when(applicationMapper.findPendingByStudentAndTypeForUpdate(1L, "selfPractice")).thenReturn(null);

        StudentApplication changed = completeApplication();
        changed.setReason("已补充详细实习安排");
        changed.setMaterials(new java.util.HashMap<>(java.util.Map.of("实习接收函", "new-acceptance.pdf")));
        when(applicationMapper.resetForResubmit(changed)).thenReturn(1);

        assertEquals(1, service.resubmit(15L, 1L, changed));
        assertEquals("pending", changed.getStatus());
        assertNull(changed.getRejectReason());
        assertNull(changed.getReviewerId());
        assertNull(changed.getReviewTime());
        assertEquals("uploaded.pdf", changed.getMaterials().get("个人申请书"));
        assertEquals("new-acceptance.pdf", changed.getMaterials().get("实习接收函"));
    }

    @Test
    void approvedSelfPracticeEmailTellsStudentItPassed() {
        StudentApplication pending = completeApplication();
        pending.setId(11L);
        pending.setStatus("pending");
        when(applicationMapper.findByIdForUpdate(11L)).thenReturn(pending);
        when(applicationMapper.update(pending)).thenReturn(1);
        when(confirmationRecordService.insert(any())).thenReturn(1);
        when(stageAssignmentService.createFromApplication(any()))
                .thenReturn(new com.internship.entity.InternshipStageAssignment());
        StudentUser student = new StudentUser();
        student.setEmail("student@example.com");
        when(studentUserService.findById(1L)).thenReturn(student);
        when(emailService.sendEmail(any(), any(), any())).thenReturn(true);

        service.approve(11L, 22L);

        verify(emailService).sendEmail(eq("student@example.com"),
                eq("自主实习申请已通过通知"), contains("你的自主实习申请已通过"));
    }

    @Test
    void rejectedSelfPracticeEmailShowsTeacherReason() {
        StudentApplication pending = completeApplication();
        pending.setId(12L);
        pending.setStatus("pending");
        when(applicationMapper.findByIdForUpdate(12L)).thenReturn(pending);
        when(applicationMapper.update(pending)).thenReturn(1);
        StudentUser student = new StudentUser();
        student.setEmail("student@example.com");
        when(studentUserService.findById(1L)).thenReturn(student);
        when(emailService.sendEmail(any(), any(), any())).thenReturn(true);

        service.reject(12L, 22L, "接收函信息不完整");

        verify(emailService).sendEmail(eq("student@example.com"),
                eq("自主实习申请已驳回通知"), contains("驳回理由：接收函信息不完整"));
    }
    private void assertRejected(Consumer<StudentApplication> change, String expectedMessage) {
        StudentApplication application = completeApplication();
        change.accept(application);

        BusinessException error = assertThrows(BusinessException.class, () -> service.insert(application));
        assertEquals(expectedMessage, error.getMessage());
    }

    private StudentApplication completeApplication() {
        StudentApplication application = new StudentApplication();
        application.setAgreementFileIds(java.util.List.of(501L));
        application.setStudentId(1L);
        application.setStudentName("测试学生");
        application.setStudentUserId("20260001");
        application.setApplicationType("selfPractice");
        application.setReason("自主联系到实习单位");
        application.setCompany("测试单位");
        application.setPositionName("综合岗");
        application.setCompanyCity("广东省广州市");
        application.setHrManagerName("张经理");
        application.setHrManagerPhone("020-12345678");
        return application;
    }
}
