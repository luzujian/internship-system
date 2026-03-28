package com.gdmu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdmu.entity.InternshipProgressRecord;
import com.gdmu.mapper.InternshipProgressRecordMapper;
import com.gdmu.service.InternshipProgressRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
public class InternshipProgressRecordServiceImpl implements InternshipProgressRecordService {

    @Autowired
    private InternshipProgressRecordMapper mapper;

    @Override
    public void saveRecord(InternshipProgressRecord record) {
        try {
            log.info("开始保存实习进展记录: studentId={}, eventType={}, eventTitle={}",
                    record.getStudentId(), record.getEventType(), record.getEventTitle());
            int result = mapper.insert(record);
            log.info("保存实习进展记录结果: id={}, affectedRows={}", record.getId(), result);
        } catch (Exception e) {
            log.error("保存实习进展记录失败: studentId={}, eventType={}, error={}",
                    record.getStudentId(), record.getEventType(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<InternshipProgressRecord> getByStudentId(Long studentId) {
        LambdaQueryWrapper<InternshipProgressRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InternshipProgressRecord::getStudentId, studentId)
               .orderByDesc(InternshipProgressRecord::getEventTime);
        return mapper.selectList(wrapper);
    }

    @Override
    public void updateStatusByRelatedId(Long relatedId, String eventType, String status) {
        try {
            log.info("更新实习进展记录状态: relatedId={}, eventType={}, newStatus={}", relatedId, eventType, status);
            LambdaQueryWrapper<InternshipProgressRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(InternshipProgressRecord::getRelatedId, relatedId)
                   .eq(InternshipProgressRecord::getEventType, eventType);
            List<InternshipProgressRecord> records = mapper.selectList(wrapper);
            if (records != null && !records.isEmpty()) {
                for (InternshipProgressRecord record : records) {
                    record.setStatus(status);
                    mapper.updateById(record);
                }
                log.info("更新实习进展记录状态成功: affectedRecords={}", records.size());
            } else {
                log.warn("未找到需要更新的实习进展记录: relatedId={}, eventType={}", relatedId, eventType);
            }
        } catch (Exception e) {
            log.error("更新实习进展记录状态失败: relatedId={}, eventType={}, error={}", relatedId, eventType, e.getMessage(), e);
            throw e;
        }
    }
}
