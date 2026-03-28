package com.gdmu.mapper;

import com.gdmu.entity.PositionFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PositionFavoriteMapper {
    /**
     * 插入收藏记录
     */
    int insert(PositionFavorite favorite);

    /**
     * 删除收藏记录
     */
    int deleteByPositionIdAndStudentId(@Param("positionId") Long positionId, @Param("studentId") Long studentId);

    /**
     * 检查是否已收藏
     */
    int countByPositionIdAndStudentId(@Param("positionId") Long positionId, @Param("studentId") Long studentId);

    /**
     * 获取学生的所有收藏职位ID
     */
    List<Long> findFavoritePositionIdsByStudentId(@Param("studentId") Long studentId);

    /**
     * 批量检查职位是否被学生收藏
     */
    List<Long> findFavoritePositionIds(@Param("studentId") Long studentId, @Param("positionIds") List<Long> positionIds);

    /**
     * 删除学生的所有收藏记录
     */
    int deleteByStudentId(@Param("studentId") Long studentId);

    /**
     * 获取学生的收藏详情（包含职位信息）
     */
    List<PositionFavorite> findFavoriteDetailsByStudentId(@Param("studentId") Long studentId);
}