package com.gdmu.service.impl;

import com.gdmu.entity.PositionFavorite;
import com.gdmu.mapper.PositionFavoriteMapper;
import com.gdmu.service.PositionFavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class PositionFavoriteServiceImpl implements PositionFavoriteService {

    @Autowired
    private PositionFavoriteMapper favoriteMapper;

    @Override
    @Transactional
    public void addFavorite(Long positionId, Long studentId) {
        // 检查是否已收藏
        if (favoriteMapper.countByPositionIdAndStudentId(positionId, studentId) > 0) {
            return; // 已收藏，直接返回
        }
        PositionFavorite favorite = new PositionFavorite();
        favorite.setPositionId(positionId);
        favorite.setStudentId(studentId);
        favorite.setCreateTime(new Date());
        favoriteMapper.insert(favorite);
    }

    @Override
    @Transactional
    public void removeFavorite(Long positionId, Long studentId) {
        favoriteMapper.deleteByPositionIdAndStudentId(positionId, studentId);
    }

    @Override
    public boolean isFavorite(Long positionId, Long studentId) {
        return favoriteMapper.countByPositionIdAndStudentId(positionId, studentId) > 0;
    }

    @Override
    public List<Long> getStudentFavoritePositionIds(Long studentId) {
        if (studentId == null) {
            return Collections.emptyList();
        }
        return favoriteMapper.findFavoritePositionIdsByStudentId(studentId);
    }

    @Override
    public List<Long> getStudentFavoritePositionIds(Long studentId, List<Long> positionIds) {
        if (studentId == null || positionIds == null || positionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return favoriteMapper.findFavoritePositionIds(studentId, positionIds);
    }

    @Override
    public List<PositionFavorite> getStudentFavoriteDetails(Long studentId) {
        if (studentId == null) {
            return Collections.emptyList();
        }
        return favoriteMapper.findFavoriteDetailsByStudentId(studentId);
    }
}