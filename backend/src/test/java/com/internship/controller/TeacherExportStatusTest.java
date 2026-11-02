package com.internship.controller;

import com.internship.entity.ClassCounselorRelation;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.entity.StudentUser;
import com.internship.entity.dto.JobSearchStatusEvidenceDTO;
import com.internship.entity.dto.TeacherStudentDetailExtras;
import com.internship.mapper.ClassCounselorRelationMapper;
import com.internship.mapper.JobSearchStatusMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.service.JobSearchStatusService;
import com.internship.service.TeacherUserService;
import com.internship.utils.CurrentHolder;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class TeacherExportStatusTest {
    @AfterEach void clearIdentity() { CurrentHolder.remove(); }

    @Test
    void bothTeacherExcelRoutesResolveJobProgressAndPendingRequestsBeforeWritingCells() throws Exception {
        TeacherController controller = new TeacherController();
        TeacherUserService teachers = mock(TeacherUserService.class);
        ClassCounselorRelationMapper relations = mock(ClassCounselorRelationMapper.class);
        StudentInternshipStatusMapper statuses = mock(StudentInternshipStatusMapper.class);
        JobSearchStatusMapper evidenceMapper = mock(JobSearchStatusMapper.class);
        ReflectionTestUtils.setField(controller, "teacherUserService", teachers);
        ReflectionTestUtils.setField(controller, "classCounselorRelationMapper", relations);
        ReflectionTestUtils.setField(controller, "studentInternshipStatusMapper", statuses);
        ReflectionTestUtils.setField(controller, "jobSearchStatusService", new JobSearchStatusService(evidenceMapper));

        User actor = new User(); actor.setId(23L); actor.setUsername("T0023"); actor.setRole("ROLE_TEACHER_COUNSELOR");
        CurrentHolder.setUser(actor);
        TeacherUser teacher = new TeacherUser(); teacher.setId(23L); teacher.setTeacherUserId("T0023"); teacher.setTeacherType("COUNSELOR");
        when(teachers.findById(23L)).thenReturn(teacher);
        ClassCounselorRelation relation = new ClassCounselorRelation(); relation.setClassId(10L);
        when(relations.findByCounselorId(23L)).thenReturn(List.of(relation));
        StudentInternshipStatus row = new StudentInternshipStatus(); row.setId(4L); row.setStudentId(101L); row.setStatus(0);
        StudentUser student = new StudentUser(); student.setId(101L); student.setStudentUserId("001234"); student.setName("测试学生");
        student.setGrade(2023); student.setPassword("never-output-password"); row.setStudent(student);
        TeacherStudentDetailExtras extras = new TeacherStudentDetailExtras(); extras.setStudentId(101L);
        extras.setDepartment("测试学院"); extras.setPhone("013800000001"); extras.setEmail("student@example.test");
        extras.setCompanyBusiness("医疗设备研发\n信息系统服务"); extras.setCompanyCity("广州市");
        extras.setHrManagerName("单位联系人"); extras.setHrManagerPhone("020-12345678");
        when(statuses.findTeacherDetailExtrasByStudentIds(List.of(101L))).thenReturn(List.of(extras));
        when(statuses.listByScope(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any()))
                .thenReturn(List.of(row));
        JobSearchStatusEvidenceDTO evidence = new JobSearchStatusEvidenceDTO(); evidence.setStudentId(101L);
        evidence.setInternshipStatus(0); evidence.setHasInterviewOpportunity(true); evidence.setHasPendingDelay(true);
        when(evidenceMapper.findEvidenceByStudentIds(List.of(101L))).thenReturn(List.of(evidence));

        MockHttpServletResponse normal = new MockHttpServletResponse();
        controller.exportInternshipStatus(null,null,null,null,null,null,null,normal);
        MockHttpServletResponse batch = new MockHttpServletResponse();
        controller.exportBatchInternshipStatus(null,null,null,null,batch);
        for (MockHttpServletResponse response : List.of(normal,batch)) {
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                assertEquals(16, workbook.getSheetAt(0).getRow(0).getLastCellNum());
                assertEquals("已投递未录用 延期申请待审核", workbook.getSheetAt(0).getRow(1).getCell(8).getStringCellValue());
                assertEquals("双向选择", workbook.getSheetAt(0).getRow(1).getCell(9).getStringCellValue());
                assertEquals("无", workbook.getSheetAt(0).getRow(1).getCell(10).getStringCellValue());
                assertEquals("013800000001", workbook.getSheetAt(0).getRow(1).getCell(6).getStringCellValue());
                assertEquals("医疗设备研发\n信息系统服务", workbook.getSheetAt(0).getRow(1).getCell(12).getStringCellValue());
                assertEquals("广州市", workbook.getSheetAt(0).getRow(1).getCell(13).getStringCellValue());
                assertEquals("单位联系人", workbook.getSheetAt(0).getRow(1).getCell(14).getStringCellValue());
                assertEquals("020-12345678", workbook.getSheetAt(0).getRow(1).getCell(15).getStringCellValue());
            }
        }

        evidence.setHasInterviewOpportunity(false); evidence.setHasPendingDelay(false);
        row.setCompanyId(8L); row.setStatus(1); // 已失效的历史录用不能继续决定当前实习途径。
        MockHttpServletResponse selected = new MockHttpServletResponse();
        controller.exportInternshipStatus(null,null,null,null,null,null,"101",selected);
        MockHttpServletResponse single = new MockHttpServletResponse();
        controller.exportInternshipStatus(101L,null,null,null,null,null,null,single);
        var detailResult = controller.getStudentInternshipDetail(101L);
        assertEquals(200, detailResult.getCode());
        Map<?, ?> detail = (Map<?, ?>) detailResult.getData();
        List<String> detailFields = List.of("studentId","name","grade","department","major","className","phone","email",
                "internshipStatusText","internshipPathText","company","positionName","companyBusiness","companyCity","hrManagerName","hrManagerPhone");
        assertFalse(detail.containsKey("password")); assertFalse(detail.containsKey("student"));
        for (MockHttpServletResponse response : List.of(selected,single)) {
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                assertEquals(16, workbook.getSheetAt(0).getRow(0).getLastCellNum());
                assertEquals("暂无有效投递", workbook.getSheetAt(0).getRow(1).getCell(8).getStringCellValue());
                assertEquals("无", workbook.getSheetAt(0).getRow(1).getCell(9).getStringCellValue());
                for (int i=0;i<detailFields.size();i++) {
                    assertEquals(detail.get(detailFields.get(i)),workbook.getSheetAt(0).getRow(1).getCell(i).getStringCellValue(),detailFields.get(i));
                }
            }
        }
    }
}
