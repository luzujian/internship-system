package com.internship.mapper;

import com.internship.entity.dto.JobSearchStatusEvidenceDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface JobSearchStatusMapper {
    List<JobSearchStatusEvidenceDTO> findEvidenceByStudentIds(@Param("studentIds") Collection<Long> studentIds);

    long countByDisplayFilter(@Param("statusFilter") int statusFilter,
                              @Param("classIds") Collection<Long> classIds);

    long countWithoutOfferByDepartmentId(@Param("departmentId") Long departmentId);
}
