package com.gdmu.service.impl;

import com.gdmu.entity.InternshipTimeSettings;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.InternshipTimeSettingsMapper;
import com.gdmu.service.InternshipTimeSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class InternshipTimeSettingsServiceImpl implements InternshipTimeSettingsService {
    
    private final InternshipTimeSettingsMapper internshipTimeSettingsMapper;
    
    @Autowired
    public InternshipTimeSettingsServiceImpl(InternshipTimeSettingsMapper internshipTimeSettingsMapper) {
        this.internshipTimeSettingsMapper = internshipTimeSettingsMapper;
    }
    
    @Override
    public InternshipTimeSettings findLatest() {
        log.debug("查询最新的实习时间节点设置");
        return internshipTimeSettingsMapper.findLatest();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(InternshipTimeSettings settings) {
        log.debug("插入实习时间节点设置");
        
        if (settings == null) {
            throw new BusinessException("实习时间节点设置不能为空");
        }
        
        int result = internshipTimeSettingsMapper.insert(settings);
        log.info("实习时间节点设置插入成功，ID: {}", settings.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(InternshipTimeSettings settings) {
        log.debug("更新实习时间节点设置，ID: {}", settings.getId());
        
        if (settings == null || settings.getId() == null) {
            throw new BusinessException("实习时间节点设置或ID不能为空");
        }
        
        int result = internshipTimeSettingsMapper.update(settings);
        log.info("实习时间节点设置更新成功，ID: {}", settings.getId());
        return result;
    }
}
