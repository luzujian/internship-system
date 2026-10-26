package com.internship.controller;

import com.internship.entity.*;
import com.internship.entity.Class;
import com.internship.mapper.*;
import com.internship.service.*;
import com.internship.service.impl.DataExportServiceImpl;
import com.internship.utils.CurrentHolder;
import com.internship.utils.ExcelExports;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportConsistencyTest {
    @AfterEach void clearIdentity() { CurrentHolder.remove(); }
    private XSSFWorkbook workbook(MockHttpServletResponse response) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()));
    }
    private void inject(Object controller,String field,Object service) { ReflectionTestUtils.setField(controller,field,service); }
    private StudentInternshipStatus selfPractice() {
        StudentUser student = new StudentUser(); student.setId(183L); student.setStudentUserId("023209170554");
        student.setName("测试学生"); student.setGrade(2023); student.setGender(null);
        StudentInternshipStatus row = new StudentInternshipStatus(); row.setId(187L); row.setStudentId(183L);
        row.setStudent(student); row.setStatus(3); row.setCompanyName("自主联系单位"); row.setPositionName("文字岗位");
        row.setMajorName("信息资源管理"); row.setClassName("23信资17班");
        row.setCompanyConfirmStatus(1); row.setCreateTime(new Date(1700000000000L)); return row;
    }

    @Test void internshipWorkbookRetainsTextPlacementIdentifiersPathsAndDates() throws Exception {
        var row=selfPractice(); var delayed=selfPractice(); delayed.setStatus(6);
        var response=new MockHttpServletResponse(); ExcelExports.internships(response,"学生实习状态",List.of(row,delayed));
        try(var book=workbook(response)) {
            Row r=book.getSheetAt(0).getRow(1);
            assertEquals("023209170554",r.getCell(0).getStringCellValue());
            assertEquals("",r.getCell(2).getStringCellValue());
            assertEquals("实习进行中",r.getCell(6).getStringCellValue());
            assertEquals("自主实习",r.getCell(7).getStringCellValue());
            assertEquals("自主联系单位",r.getCell(8).getStringCellValue());
            assertEquals("文字岗位",r.getCell(9).getStringCellValue());
            assertEquals("已确认",r.getCell(13).getStringCellValue());
            assertTrue(DateUtil.isCellDateFormatted(r.getCell(14)));
            assertEquals("延迟实习",book.getSheetAt(0).getRow(2).getCell(7).getStringCellValue());
        }
    }

    @Test void teacherBatchUsesAllFiltersIncludingZeroStatusAndLimitsPathWithinScope() throws Exception {
        var controller=new TeacherController(); var teachers=mock(TeacherUserService.class);
        var relations=mock(ClassCounselorRelationMapper.class); var statuses=mock(StudentInternshipStatusMapper.class);
        inject(controller,"teacherUserService",teachers); inject(controller,"classCounselorRelationMapper",relations);
        inject(controller,"studentInternshipStatusMapper",statuses); inject(controller,"jobSearchStatusService",mock(JobSearchStatusService.class));
        var actor=new User(); actor.setId(23L); actor.setUsername("T0023"); actor.setRole("ROLE_TEACHER_COUNSELOR"); CurrentHolder.setUser(actor);
        var teacher=new TeacherUser(); teacher.setId(23L); teacher.setTeacherUserId("T0023"); teacher.setTeacherType("COUNSELOR");
        when(teachers.findById(23L)).thenReturn(teacher); var relation=new ClassCounselorRelation(); relation.setClassId(10L);
        when(relations.findByCounselorId(23L)).thenReturn(List.of(relation));
        var text=selfPractice(); var platform=selfPractice(); platform.setStudentId(184L); platform.setCompanyId(3L);
        when(statuses.listByScope(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(List.of(text,platform));
        var response=new MockHttpServletResponse();
        controller.exportBatchInternshipStatus(null,"23信资17班","2023级",0,Map.of("name","测试","major","信息资源管理","companyName","自主联系单位","internshipPath","selfPractice"),response);
        verify(statuses).listByScope(isNull(),eq("测试"),isNull(),eq(0),isNull(),eq("自主联系单位"),eq("2023级"),eq("信息资源管理"),eq("23信资17班"),isNull(),isNull(),isNull(),eq(List.of(10L)));
        try(var book=workbook(response)) { assertEquals(1,book.getSheetAt(0).getLastRowNum()); assertEquals("自主联系单位",book.getSheetAt(0).getRow(1).getCell(8).getStringCellValue()); }
    }

    @Test void applicationDocxAllowsNullGenderAndUsesTheRequestedStatusRecord() throws Exception {
        var controller=new StudentInternshipStatusController(); var service=mock(StudentInternshipStatusService.class);
        inject(controller,"studentInternshipStatusService",service); when(service.findById(187L)).thenReturn(selfPractice());
        var actor=new User(); actor.setId(1L); actor.setRole("ROLE_ADMIN"); CurrentHolder.setUser(actor);
        var response=new MockHttpServletResponse(); controller.downloadInternshipApplication(187L,response);
        verify(service).findById(187L); verify(service,never()).findById(183L);
        try(var doc=new XWPFDocument(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var table=doc.getTables().get(0);
            assertEquals("023209170554",table.getRow(0).getCell(1).getText());
            assertEquals("",table.getRow(2).getCell(1).getText());
            assertEquals("自主联系单位",table.getRow(3).getCell(1).getText());
            assertEquals("文字岗位",table.getRow(4).getCell(1).getText());
        }
    }

    @Test void studentUserExportUsesListFiltersAndIncludesVisibleAccountFields() throws Exception {
        var controller=new StudentUserController(); var service=mock(StudentUserService.class);
        var majors=mock(MajorMapper.class); var classes=mock(ClassMapper.class); var departments=mock(DepartmentService.class);
        inject(controller,"studentUserService",service); inject(controller,"majorMapper",majors); inject(controller,"classMapper",classes); inject(controller,"exportDepartments",departments);
        var student=selfPractice().getStudent(); student.setMajorId(2L); student.setClassId(3L); student.setStatus(0); student.setGender(2);
        var major=new Major(); major.setId(2L); major.setName("信息资源管理"); major.setDepartmentId(1L);
        var cls=new Class(); cls.setId(3L); cls.setName("23信资17班");
        var dept=new Department(); dept.setId(1L); dept.setName("生物医学工程学院");
        when(majors.findAll()).thenReturn(List.of(major)); when(classes.findAll()).thenReturn(List.of(cls)); when(departments.findAll()).thenReturn(List.of(dept));
        when(service.findPage(1,Integer.MAX_VALUE,"023","测试",2023,"2","3",0,2,"1")).thenReturn(PageResult.build(1L,List.of(student)));
        var response=new MockHttpServletResponse(); controller.exportStudentData(response,"023","测试",2023,"2","3",0,2,"1");
        try(var book=workbook(response)) {
            Row row=book.getSheetAt(0).getRow(1); assertEquals("生物医学工程学院",row.getCell(4).getStringCellValue());
            assertEquals("禁用",row.getCell(8).getStringCellValue()); assertEquals("女",row.getCell(11).getStringCellValue());
        }
    }

    @Test void teacherUserExportUsesIdentityAndStatusFiltersAndResolvesOrganization() throws Exception {
        var controller=new TeacherUserController(); var service=mock(TeacherUserService.class); var departments=mock(DepartmentService.class); var divisions=mock(DivisionService.class);
        inject(controller,"teacherUserService",service); inject(controller,"departmentService",departments); inject(controller,"divisionService",divisions);
        var teacher=new TeacherUser(); teacher.setTeacherUserId("x001"); teacher.setDepartmentId("1"); teacher.setDivisionId("2"); teacher.setTeacherType("DEPARTMENT"); teacher.setStatus(0);
        var dept=new Department(); dept.setId(1L); dept.setName("学院"); var div=new Division(); div.setId(2L); div.setName("信息管理系");
        when(departments.findAll()).thenReturn(List.of(dept)); when(divisions.findAll()).thenReturn(List.of(div));
        when(service.findPageByCondition(eq(1),eq(Integer.MAX_VALUE),anyMap())).thenReturn(PageResult.build(1L,List.of(teacher)));
        var response=new MockHttpServletResponse(); controller.exportTeacherData(response,"x001","姓名","1",0,"DEPARTMENT");
        verify(service).findPageByCondition(1,Integer.MAX_VALUE,Map.of("teacherUserId","x001","name","姓名","departmentId","1","status",0,"teacherType","DEPARTMENT"));
        try(var book=workbook(response)) { Row row=book.getSheetAt(0).getRow(1); assertEquals("信息管理系",row.getCell(3).getStringCellValue()); assertEquals("系室教师",row.getCell(4).getStringCellValue()); assertEquals("禁用",row.getCell(7).getStringCellValue()); }
    }

    @Test void companyExportSeparatesAuditAccountStatusAndUsesCurrentTagLabels() throws Exception {
        var controller=new CompanyUserController(); var service=mock(CompanyUserService.class); inject(controller,"companyUserService",service);
        var company=new CompanyUser(); company.setCompanyName("企业1"); company.setStatus(0); company.setAuditStatus(1);
        company.setIsInternshipBase(1); company.setCooperationMode("兜底"); company.setHasTrainingBaseAgreement(1); company.setIsAlumniUnit(1);
        company.setCompanyTag("学生自主联系,额外标签，接受兜底");
        when(service.findPage(1,Integer.MAX_VALUE,"企业",0,null,"接受兜底")).thenReturn(PageResult.build(1L,List.of(company)));
        var response=new MockHttpServletResponse(); controller.exportCompanyData(response,"企业",0,"接受兜底");
        try(var book=workbook(response)) { Row row=book.getSheetAt(0).getRow(1); assertEquals("审核通过",row.getCell(1).getStringCellValue()); assertEquals("禁用",row.getCell(2).getStringCellValue()); assertEquals("校企双向选择、实习基地、接受兜底、已签协议、校友单位、额外标签",row.getCell(3).getStringCellValue()); }
    }

    @Test void classExportContainsAllPagesAndCountAsANumber() throws Exception {
        var controller=new ClassController(); var service=mock(ClassService.class); inject(controller,"classService",service);
        List<Class> classes=IntStream.range(0,19).mapToObj(i -> { var c=new Class(); c.setName("班级"+i); c.setMajorId(2L); c.setMajorName("专业"); c.setGrade(2023); c.setStudentCount(i); return c; }).toList();
        when(service.getAllClassData()).thenReturn(classes); var response=new MockHttpServletResponse(); controller.exportExcel(response,"班级","2023",2L);
        try(var book=workbook(response)) { assertEquals(19,book.getSheetAt(0).getLastRowNum()); assertEquals(CellType.NUMERIC,book.getSheetAt(0).getRow(19).getCell(3).getCellType()); assertEquals(18,book.getSheetAt(0).getRow(19).getCell(3).getNumericCellValue()); }
    }

    @Test void organizationExportRetainsDivisionAndCountsWithoutUpdatingCachedCounts() throws Exception {
        var controller=new DepartmentController(); var departments=mock(DepartmentService.class); var divisions=mock(DivisionService.class); var majors=mock(MajorService.class);
        inject(controller,"departmentService",departments); inject(controller,"exportDivisions",divisions); inject(controller,"majorService",majors);
        var d=new Department(); d.setId(1L); d.setName("学院"); var v=new Division(); v.setId(2L); v.setDepartmentId(1L); v.setName("系");
        var m=new Major(); m.setId(3L); m.setDepartmentId(1L); m.setDivisionId(2L); m.setName("专业");
        when(departments.findAll()).thenReturn(List.of(d)); when(divisions.findAll()).thenReturn(List.of(v)); when(majors.findAll()).thenReturn(List.of(m));
        when(departments.getTeacherCountByDepartmentId(1L)).thenReturn(9); when(departments.getStudentCountByDepartmentId(1L)).thenReturn(100);
        when(divisions.getStudentCountByDivisionId(2L)).thenReturn(30); when(majors.getStudentCountByMajorId(3L)).thenReturn(20);
        var response=new MockHttpServletResponse(); controller.exportMajorData(response,"学院","专业",1L,2L);
        try(var book=workbook(response)) { Row row=book.getSheetAt(0).getRow(1); assertEquals("系",row.getCell(1).getStringCellValue()); assertEquals(30,row.getCell(5).getNumericCellValue()); assertEquals(20,row.getCell(6).getNumericCellValue()); }
        verify(departments,never()).findAllWithUserCount(); verify(divisions,never()).findAllWithUserCount();
    }

    @Test void categoryExportFiltersSelectedCategoryAndUsesPositionPublicationDate() throws Exception {
        var controller=new PositionCategoryController(); var service=mock(PositionCategoryService.class); inject(controller,"positionCategoryService",service);
        var c=new PositionCategory(); c.setId(1L); c.setName("开发"); var p=new Position(); p.setCategoryId(1L); p.setPositionName("开发岗位"); p.setCreateTime(new Date(1700000000000L)); p.setUpdateTime(new Date(1700100000000L));
        when(service.findAll()).thenReturn(List.of(c)); when(service.getAllPositions()).thenReturn(List.of(p));
        var response=new MockHttpServletResponse(); controller.exportCategoryData(response,"开发","开发岗位",1L);
        try(var book=workbook(response)) { Row row=book.getSheetAt(0).getRow(1); assertEquals(1,row.getCell(1).getNumericCellValue()); assertTrue(DateUtil.isCellDateFormatted(row.getCell(5))); assertEquals(p.getCreateTime(),row.getCell(5).getDateCellValue()); }
        response=new MockHttpServletResponse(); controller.exportCategoryData(response,"开发","不存在",1L);
        try(var book=workbook(response)) { assertEquals(0,book.getSheetAt(0).getLastRowNum()); }
    }

    @Test void feedbackExportUsesEveryFilterAndPageLabels() throws Exception {
        var controller=new ProblemFeedbackController(); var mapper=mock(ProblemFeedbackMapper.class); inject(controller,"problemFeedbackMapper",mapper);
        var f=new ProblemFeedback(); f.setStatus("closed"); f.setPriority("normal"); f.setFeedbackType("bug"); f.setTitle("故障");
        when(mapper.list("student","closed","normal","bug","姓名","故障")).thenReturn(List.of(f));
        var response=new MockHttpServletResponse(); controller.exportFeedbackData(response,"student","closed","normal","bug","姓名","故障");
        try(var book=workbook(response)) { Row row=book.getSheetAt(0).getRow(1); assertEquals("感谢您的反馈",row.getCell(1).getStringCellValue()); assertEquals("程序错误",row.getCell(5).getStringCellValue()); assertEquals("中",row.getCell(6).getStringCellValue()); }
    }

    @Test void announcementAudienceIncludesChineseTypesAndTargetDetails() {
        var a=new Announcement(); a.setTargetType("[\"TEACHER_TYPE\",\"COMPANY\"]"); a.setTargetValue("{\"teacherTypes\":[\"DEPARTMENT\"]}");
        assertEquals("特定教师类别、企业用户（系室教师）",ExcelExports.announcementTargets(a,List.of()));
        a.setTargetType("[\"MAJOR\"]"); a.setTargetValue("{\"majorIds\":[\"2\"]}"); var m=new Major(); m.setId(2L); m.setName("信息资源管理");
        assertEquals("特定专业学生（信息资源管理）",ExcelExports.announcementTargets(a,List.of(m)));
        a.setTargetType("TEACHER_TYPE"); a.setTargetValue("COLLEGE"); assertEquals("特定教师类别（学院教师）",ExcelExports.announcementTargets(a,List.of()));
        a.setTargetType("null"); a.setTargetValue("null"); assertDoesNotThrow(() -> ExcelExports.announcementTargets(a,List.of()));
    }

    @Test void companyRecallExportKeepsRegistrationAuditSeparateFromAutomaticModification() throws Exception {
        var controller=new CompanyUserController(); var service=mock(CompanyUserService.class); inject(controller,"companyUserService",service);
        var c=new CompanyUser(); c.setId(2L); c.setRecallStatus(1); c.setAuditStatus(2); c.setAuditRemark("注册材料不完整"); c.setRecallAuditRemark("自动允许修改");
        when(service.findRecallRecords("企业","姓名","开始","结束")).thenReturn(List.of(c));
        var response=new MockHttpServletResponse(); controller.exportRecallRecords(response,"企业","姓名","开始","结束");
        try(var book=workbook(response)) { Row row=book.getSheetAt(0).getRow(1); assertEquals("已自动允许修改",row.getCell(7).getStringCellValue()); assertEquals("已拒绝",row.getCell(10).getStringCellValue()); assertEquals("注册材料不完整",row.getCell(12).getStringCellValue()); }
    }

    @Test void studentRecallExportRetainsTextPlacementAndLeavesUnknownGradeBlank() throws Exception {
        var controller=new StudentInternshipStatusController(); var service=mock(StudentInternshipStatusService.class);
        inject(controller,"studentInternshipStatusService",service);
        var record=selfPractice(); record.getStudent().setGrade(null); record.setRecallReason("修改实习材料");
        record.setPendingApplicationTexts(List.of("撤回申请待审核"));
        when(service.findPendingRecallAuditList(183L,"测试",null,null,"自主联系单位")).thenReturn(List.of(record));
        var response=new MockHttpServletResponse(); controller.exportRecallRecords(response,183L,"测试",null,null,"自主联系单位");
        try(var book=workbook(response)) {
            Row row=book.getSheetAt(0).getRow(1);
            assertEquals("",row.getCell(3).getStringCellValue());
            assertEquals("实习进行中 撤回申请待审核",row.getCell(6).getStringCellValue());
            assertEquals("自主联系单位",row.getCell(7).getStringCellValue());
            assertEquals("文字岗位",row.getCell(8).getStringCellValue());
        }
    }

    @Test void administratorExportReadsBeyondCurrentPageAndHonorsKeyword() throws Exception {
        var controller=new AdminUserController(); var service=mock(AdminUserService.class); inject(controller,"adminUserService",service);
        var all=IntStream.range(0,15).mapToObj(i -> { var u=new AdminUser((long)i+1); u.setUsername("admin"+i); u.setPhone("13800000000"); return u; }).toList();
        when(service.findAll()).thenReturn(all); var response=new MockHttpServletResponse(); controller.exportAdmins(response,"admin");
        try(var book=workbook(response)) { assertEquals(15,book.getSheetAt(0).getLastRowNum()); assertEquals(6,book.getSheetAt(0).getRow(0).getLastCellNum()); }
        response=new MockHttpServletResponse(); controller.exportAdmins(response,"admin14");
        try(var book=workbook(response)) { assertEquals(1,book.getSheetAt(0).getLastRowNum()); assertEquals("admin14",book.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()); }
    }

    @Test void allThreeLogExportsUsePageLabelsAndPreserveMaskingAndFormulaProtection() throws Exception {
        var controller=new LogController(); var operation=new OperateLog(); operation.setOperatorRole("ROLE_ADMIN"); operation.setModule("USER_MANAGEMENT"); operation.setOperationType("EXPORT"); operation.setOperationResult("SUCCESS"); operation.setRiskLevel("MEDIUM"); operation.setIpAddress("10.1.2.3"); operation.setDescription("=1+2");
        var response=new MockHttpServletResponse(); ReflectionTestUtils.invokeMethod(controller,"writeXlsx","operations",List.of(operation),response);
        try(var book=workbook(response)) { Row r=book.getSheetAt(0).getRow(1); assertEquals("管理员",r.getCell(3).getStringCellValue()); assertEquals("用户管理",r.getCell(4).getStringCellValue()); assertEquals("导出",r.getCell(5).getStringCellValue()); assertEquals("成功",r.getCell(6).getStringCellValue()); assertEquals("中风险",r.getCell(7).getStringCellValue()); assertEquals("10.1.*.*",r.getCell(10).getStringCellValue()); assertEquals("'=1+2",r.getCell(12).getStringCellValue()); }
        var login=new LoginLog(); login.setUserType("ROLE_TEACHER_DEPARTMENT"); login.setLoginStatus("FAILURE"); login.setRiskLevel("HIGH");
        response=new MockHttpServletResponse(); ReflectionTestUtils.invokeMethod(controller,"writeXlsx","login-events",List.of(login),response);
        try(var book=workbook(response)) { Row r=book.getSheetAt(0).getRow(1); assertEquals("系室教师",r.getCell(3).getStringCellValue()); assertEquals("失败",r.getCell(4).getStringCellValue()); }
        var event=new SecurityEventLog(); event.setUserRole("SYSTEM"); event.setEventType("REQUEST_FAILURE"); event.setResult("ATTEMPTED"); event.setRiskLevel("LOW");
        response=new MockHttpServletResponse(); ReflectionTestUtils.invokeMethod(controller,"writeXlsx","security-events",List.of(event),response);
        try(var book=workbook(response)) { Row r=book.getSheetAt(0).getRow(1); assertEquals("请求处理失败",r.getCell(4).getStringCellValue()); assertEquals("尝试",r.getCell(5).getStringCellValue()); assertEquals("低风险",r.getCell(6).getStringCellValue()); }
    }

    @Test void genericStatisticsKeepNumbersUsableAndTranslateArchiveTypes() throws Exception {
        var service=new DataExportServiceImpl(); var statuses=mock(StudentInternshipStatusMapper.class); inject(service,"internshipStatusMapper",statuses);
        when(statuses.getMajorProgressForExport()).thenReturn(List.of(new HashMap<>(Map.of("majorName","专业","totalStudents",20,"progressRate",33.6))));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(service.exportMajorProgress()))) { Row row=book.getSheetAt(0).getRow(1); assertEquals(CellType.NUMERIC,row.getCell(1).getCellType()); assertEquals(33.6,row.getCell(6).getNumericCellValue()); }
        var archives=mock(StudentArchiveMapper.class); inject(service,"studentArchiveMapper",archives);
        when(archives.findPageWithStudent(null,null,null)).thenReturn(List.of(new HashMap<>(Map.of("studentUserId","001","fileType","certificate","status",1))));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(service.exportArchives()))) { Row row=book.getSheetAt(0).getRow(1); assertEquals("证书",row.getCell(3).getStringCellValue()); assertEquals("001",row.getCell(0).getStringCellValue()); }
    }
}
