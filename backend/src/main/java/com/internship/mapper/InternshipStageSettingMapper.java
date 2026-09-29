package com.internship.mapper;

import com.internship.entity.InternshipStageSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InternshipStageSettingMapper {
    List<InternshipStageSetting> findAll();
    InternshipStageSetting findByStageNo(@Param("stageNo") Integer stageNo);
    int insert(InternshipStageSetting setting);
    int updateByVersion(InternshipStageSetting setting);
}
