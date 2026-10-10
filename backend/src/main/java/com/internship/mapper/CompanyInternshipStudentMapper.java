package com.internship.mapper;

import com.internship.entity.dto.CompanyInternshipStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CompanyInternshipStudentMapper {
    Long countCurrentStudents(@Param("companyId") Long companyId, @Param("keyword") String keyword);

    List<CompanyInternshipStudent> findCurrentStudents(@Param("companyId") Long companyId,
                                                     @Param("keyword") String keyword,
                                                     @Param("offset") long offset,
                                                     @Param("pageSize") int pageSize);
}
