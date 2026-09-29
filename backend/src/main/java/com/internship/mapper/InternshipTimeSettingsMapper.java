package com.internship.mapper;

import com.internship.entity.InternshipTimeSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InternshipTimeSettingsMapper {
    
    InternshipTimeSettings findLatest();
    
    int insert(InternshipTimeSettings settings);
    
    int update(InternshipTimeSettings settings);

    int updateCalendarIfUnchanged(@Param("updated") InternshipTimeSettings updated,
                                  @Param("current") InternshipTimeSettings current);
}
