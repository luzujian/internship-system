package com.gdmu.mapper;

import com.gdmu.entity.InternshipTimeSettings;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InternshipTimeSettingsMapper {
    
    InternshipTimeSettings findLatest();
    
    int insert(InternshipTimeSettings settings);
    
    int update(InternshipTimeSettings settings);
}
