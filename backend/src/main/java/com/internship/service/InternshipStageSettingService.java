package com.internship.service;

import com.internship.entity.InternshipStageSetting;

import java.util.List;

public interface InternshipStageSettingService {
    List<InternshipStageSetting> findAll();
    InternshipStageSetting findByStageNo(Integer stageNo);
    void saveAll(List<InternshipStageSetting> settings, Long updaterId, String updaterName, String teacherType);
}
