package com.gdmu.service;

public interface PositionViewRecordService {

    /**
     * 检查学生是否已浏览过该岗位
     */
    boolean hasViewed(Long positionId, Long studentId);

    /**
     * 记录学生浏览岗位（仅在首次浏览时增加浏览次数）
     * @return true 表示首次浏览并增加了计数，false 表示已浏览过
     */
    boolean recordView(Long positionId, Long studentId);

    /**
     * 删除浏览记录
     */
    void deleteRecord(Long positionId, Long studentId);
}