package com.gdmu.mapper;

import com.gdmu.entity.ClassCounselorRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ClassCounselorRelationMapper {
    
    int insert(ClassCounselorRelation relation);
    
    int batchInsert(@Param("relations") List<ClassCounselorRelation> relations);
    
    ClassCounselorRelation findById(@Param("id") Long id);
    
    List<ClassCounselorRelation> findByClassId(@Param("classId") Long classId);
    
    List<ClassCounselorRelation> findByCounselorId(@Param("counselorId") Long counselorId);
    
    int deleteById(@Param("id") Long id);
    
    int deleteByClassId(@Param("classId") Long classId);
    
    int deleteByCounselorId(@Param("counselorId") Long counselorId);
    
    int deleteByClassAndCounselor(@Param("classId") Long classId, @Param("counselorId") Long counselorId);
}
