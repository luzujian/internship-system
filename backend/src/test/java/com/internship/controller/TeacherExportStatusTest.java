package com.internship.controller;

import com.internship.entity.ClassCounselorRelation;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.entity.dto.JobSearchStatusEvidenceDTO;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
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
                assertEquals("已投递未录用 延期申请待审核", workbook.getSheetAt(0).getRow(1).getCell(6).getStringCellValue());
            }
        }
    }
}
