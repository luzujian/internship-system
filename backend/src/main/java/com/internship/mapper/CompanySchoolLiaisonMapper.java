package com.internship.mapper;

import com.internship.entity.CompanySchoolLiaison;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CompanySchoolLiaisonMapper {
    List<CompanySchoolLiaison> findByCompanyId(@Param("companyId") Long companyId);

    List<CompanySchoolLiaison> findByCompanyIds(@Param("companyIds") List<Long> companyIds);

    int exists(@Param("companyId") Long companyId, @Param("teacherId") Long teacherId);

    int insert(CompanySchoolLiaison liaison);

    int batchInsert(@Param("liaisons") List<CompanySchoolLiaison> liaisons);

    CompanySchoolLiaison findById(@Param("id") Long id);

    int delete(@Param("companyId") Long companyId, @Param("teacherId") Long teacherId);

    int deleteById(@Param("id") Long id);

    int deleteByCompanyId(@Param("companyId") Long companyId);
}
