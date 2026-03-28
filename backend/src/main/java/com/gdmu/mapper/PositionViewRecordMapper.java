package com.gdmu.mapper;

import com.gdmu.entity.PositionViewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PositionViewRecordMapper {

    /**
     * 检查学生是否已浏览过该岗位
     */
    int existsByPositionIdAndStudentId(@Param("positionId") Long positionId, @Param("studentId") Long studentId);

    /**
     * 插入浏览记录
     */
    int insert(PositionViewRecord record);

    /**
     * 删除浏览记录
     */
    int deleteByPositionIdAndStudentId(@Param("positionId") Long positionId, @Param("studentId") Long studentId);
}