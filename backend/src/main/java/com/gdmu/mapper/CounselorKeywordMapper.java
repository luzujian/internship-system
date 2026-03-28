package com.gdmu.mapper;

import com.gdmu.entity.CounselorKeyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CounselorKeywordMapper {
    
    int insert(CounselorKeyword keyword);
    
    CounselorKeyword findById(@Param("id") Long id);
    
    List<CounselorKeyword> findByCounselorId(@Param("counselorId") Long counselorId);
    
    int update(CounselorKeyword keyword);
    
    int deleteById(@Param("id") Long id);
    
    int deleteByCounselorId(@Param("counselorId") Long counselorId);

    int countByCounselorId(@Param("counselorId") Long counselorId);
}
