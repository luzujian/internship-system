package com.gdmu.mapper;

import com.gdmu.entity.ScoreAdjustment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ScoreAdjustmentMapper {
    
    // 根据提交ID查询调分记录
    List<ScoreAdjustment> selectBySubmissionId(@Param("submissionId") Long submissionId);
    
    // 根据教师ID查询调分记录
    List<ScoreAdjustment> selectByTeacherId(@Param("teacherId") Long teacherId);
    
    // 插入调分记录
    int insert(ScoreAdjustment scoreAdjustment);
    
    // 更新调分记录
    int update(ScoreAdjustment scoreAdjustment);
    
    // 删除调分记录
    int deleteById(@Param("id") Long id);
    
    // 根据提交ID删除调分记录
    int deleteBySubmissionId(@Param("submissionId") Long submissionId);
}