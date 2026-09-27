package com.internship.mapper;

import com.internship.entity.InternshipAppraisalFile;
import com.internship.entity.dto.TeacherAppraisalClassOption;
import com.internship.entity.dto.TeacherAppraisalExportFile;
import com.internship.entity.dto.TeacherAppraisalStudent;
import com.internship.entity.dto.TeacherAppraisalScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TeacherInternshipAppraisalMapper {
    long countResponsibleStudents(@Param("scope") TeacherAppraisalScope scope,
                                  @Param("keyword") String keyword,
                                  @Param("classId") Long classId);

    long countSubmittedStudents(@Param("scope") TeacherAppraisalScope scope,
                                @Param("keyword") String keyword,
                                @Param("classId") Long classId);

    long countStudents(@Param("scope") TeacherAppraisalScope scope,
                       @Param("keyword") String keyword,
                       @Param("classId") Long classId,
                       @Param("status") String status);

    List<TeacherAppraisalStudent> findStudents(@Param("scope") TeacherAppraisalScope scope,
                                                 @Param("keyword") String keyword,
                                                 @Param("classId") Long classId,
                                                 @Param("status") String status,
                                                 @Param("offset") int offset,
                                                 @Param("pageSize") int pageSize);

    List<TeacherAppraisalClassOption> findClasses(@Param("scope") TeacherAppraisalScope scope);

    TeacherAppraisalStudent findSubmittedStudent(@Param("scope") TeacherAppraisalScope scope,
                                                    @Param("studentId") Long studentId);

    InternshipAppraisalFile findAuthorizedSubmittedFile(@Param("scope") TeacherAppraisalScope scope,
                                                         @Param("fileId") Long fileId);

    List<TeacherAppraisalExportFile> findExportFiles(@Param("scope") TeacherAppraisalScope scope,
                                                       @Param("keyword") String keyword,
                                                       @Param("classId") Long classId,
                                                       @Param("status") String status);
}
