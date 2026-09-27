package com.internship.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TeacherMacroStatisticsMapper {
    long sumActivePositionRemainingQuota();
}
