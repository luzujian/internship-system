package com.internship.mapper;

import com.internship.entity.InternshipAppraisalFile;
import com.internship.entity.InternshipAppraisalSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InternshipAppraisalMapper {
    InternshipAppraisalSubmission findSubmissionByStudentId(@Param("studentId") Long studentId);
    InternshipAppraisalSubmission findSubmissionByStudentIdForUpdate(@Param("studentId") Long studentId);
    InternshipAppraisalSubmission findSubmissionById(@Param("id") Long id);
    List<InternshipAppraisalFile> findFilesBySubmissionId(@Param("submissionId") Long submissionId);
    InternshipAppraisalFile findFileById(@Param("id") Long id);
    int insertSubmission(InternshipAppraisalSubmission submission);
    int updateSubmission(InternshipAppraisalSubmission submission);
    int insertFile(InternshipAppraisalFile file);
    int deleteFile(@Param("id") Long id);
}
