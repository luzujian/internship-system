package com.gdmu.service;

import com.gdmu.entity.AlgorithmParameter;
import java.math.BigDecimal;

public interface AlgorithmParameterService {
    
    // 获取或创建算法参数
    AlgorithmParameter getOrCreateParameters(Long courseId, Long assignmentId, Long teacherId);
    
    // 保存算法参数
    boolean saveParameters(AlgorithmParameter parameters);
    
    // 根据ID获取算法参数
    AlgorithmParameter getById(Long id);
    
    // 更新算法参数
    boolean updateParameters(AlgorithmParameter parameters);
}