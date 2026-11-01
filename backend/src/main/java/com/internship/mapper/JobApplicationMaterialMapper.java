package com.internship.mapper;

import com.internship.entity.dto.JobApplicationMaterialModels.MaterialFile;
import com.internship.entity.dto.JobApplicationMaterialModels.Submission;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface JobApplicationMaterialMapper {
    int insertSubmission(Submission submission);
    int insertFile(MaterialFile file);
    Submission findByJobApplicationId(Long applicationId);
    Submission findByCompanyApplicationId(Long applicationId);
    List<MaterialFile> findFiles(Long submissionId);
    MaterialFile findFile(Long fileId);
}
