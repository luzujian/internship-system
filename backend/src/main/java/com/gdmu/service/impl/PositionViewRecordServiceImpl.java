package com.gdmu.service.impl;

import com.gdmu.entity.PositionViewRecord;
import com.gdmu.mapper.PositionViewRecordMapper;
import com.gdmu.mapper.PositionMapper;
import com.gdmu.service.PositionViewRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
public class PositionViewRecordServiceImpl implements PositionViewRecordService {

    @Autowired
    private PositionViewRecordMapper viewRecordMapper;

    @Autowired
    private PositionMapper positionMapper;

    @Override
    public boolean hasViewed(Long positionId, Long studentId) {
        return viewRecordMapper.existsByPositionIdAndStudentId(positionId, studentId) > 0;
    }

    @Override
    @Transactional
    public boolean recordView(Long positionId, Long studentId) {
        // 检查是否已浏览过
        if (hasViewed(positionId, studentId)) {
            return false;
        }

        // 记录浏览
        PositionViewRecord record = new PositionViewRecord();
        record.setPositionId(positionId);
        record.setStudentId(studentId);
        record.setViewTime(new Date());
        viewRecordMapper.insert(record);

        // 浏览次数+1
        positionMapper.incrementViewCount(positionId);

        log.debug("学生 {} 首次浏览岗位 {}，浏览次数+1", studentId, positionId);
        return true;
    }

    @Override
    public void deleteRecord(Long positionId, Long studentId) {
        viewRecordMapper.deleteByPositionIdAndStudentId(positionId, studentId);
    }
}