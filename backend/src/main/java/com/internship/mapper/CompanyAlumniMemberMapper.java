package com.internship.mapper;

import com.internship.entity.CompanyAlumniMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CompanyAlumniMemberMapper {
    List<CompanyAlumniMember> findByCompanyId(@Param("companyId") Long companyId);

    List<CompanyAlumniMember> findByCompanyIds(@Param("companyIds") List<Long> companyIds);

    int batchInsert(@Param("members") List<CompanyAlumniMember> members);

    int deleteByCompanyId(@Param("companyId") Long companyId);
}
