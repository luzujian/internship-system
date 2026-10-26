package com.internship.mapper;

import com.internship.dto.TeacherRecruitmentPositionDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TeacherRecruitmentMapper {
    List<TeacherRecruitmentPositionDTO> findPublishedPositions(
            @Param("companyName") String companyName,
            @Param("positionName") String positionName,
            @Param("status") String status,
            @Param("categoryId") Long categoryId);

    TeacherRecruitmentPositionDTO findPublishedPositionById(@Param("id") Long id);
}
