package com.gdmu.service;

import com.gdmu.entity.PositionFavorite;
import java.util.List;

public interface PositionFavoriteService {
    /**
     * 添加收藏
     */
    void addFavorite(Long positionId, Long studentId);

    /**
     * 取消收藏
     */
    void removeFavorite(Long positionId, Long studentId);

    /**
     * 检查是否已收藏
     */
    boolean isFavorite(Long positionId, Long studentId);

    /**
     * 获取学生的所有收藏职位ID
     */
    List<Long> getStudentFavoritePositionIds(Long studentId);

    /**
     * 批量检查职位是否被学生收藏
     */
    List<Long> getStudentFavoritePositionIds(Long studentId, List<Long> positionIds);

    /**
     * 获取学生的收藏详情列表
     */
    List<PositionFavorite> getStudentFavoriteDetails(Long studentId);
}