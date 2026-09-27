package com.internship.service.impl;

import com.internship.entity.InternshipStageSetting;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipStageSettingMapper;
import com.internship.service.InternshipStageSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Service
public class InternshipStageSettingServiceImpl implements InternshipStageSettingService {
    private final InternshipStageSettingMapper mapper;

    public InternshipStageSettingServiceImpl(InternshipStageSettingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<InternshipStageSetting> findAll() {
        return mapper.findAll();
    }

    @Override
    public InternshipStageSetting findByStageNo(Integer stageNo) {
        return mapper.findByStageNo(stageNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAll(List<InternshipStageSetting> settings, Long updaterId, String updaterName, String teacherType) {
        if (settings == null || settings.size() != 2
                || settings.stream().anyMatch(s -> s == null || s.getStageNo() == null
                || (s.getStageNo() != 1 && s.getStageNo() != 2))
                || settings.stream().map(InternshipStageSetting::getStageNo).distinct().count() != 2) {
            throw new BusinessException("必须同时配置第一阶段和第二阶段");
        }

        InternshipStageSetting first = settings.stream()
                .filter(s -> s.getStageNo() == 1).findFirst().orElseThrow();
        InternshipStageSetting second = settings.stream()
                .filter(s -> s.getStageNo() == 2).findFirst().orElseThrow();
        validateDates(first, second, teacherType);

        boolean canEditAppraisalDeadline = "DEPARTMENT".equals(teacherType);
        Date now = new Date();
        for (InternshipStageSetting incoming : settings.stream()
                .sorted(Comparator.comparing(InternshipStageSetting::getStageNo)).toList()) {
            incoming.setUpdaterId(updaterId);
            incoming.setUpdaterName(updaterName);
            incoming.setEnabled(1);
            incoming.setChangeDeadline(null);
            incoming.setConfirmationDeadline(null);
            incoming.setReportDeadline(null);
            // 只有系室教师(DEPARTMENT)可以配置鉴定表截止日期；其他教师保存时该字段会被还原为数据库现有值
            if (!canEditAppraisalDeadline) {
                incoming.setAppraisalDeadline(null);
            }
            if (incoming.getVersion() == null) incoming.setVersion(0);
            incoming.setUpdateTime(now);

            InternshipStageSetting existing = mapper.findByStageNo(incoming.getStageNo());
            if (existing == null) {
                mapper.insert(incoming);
            } else {
                incoming.setId(existing.getId());
                // 非系室教师保存时，保留数据库中原有的鉴定表截止日期
                if (!canEditAppraisalDeadline) {
                    incoming.setAppraisalDeadline(existing.getAppraisalDeadline());
                }
                if (incoming.getVersion() == null) incoming.setVersion(existing.getVersion());
                if (!existing.getVersion().equals(incoming.getVersion())) {
                    throw new BusinessException("阶段设置已被其他教师修改，请刷新后重试");
                }
                if (mapper.updateByVersion(incoming) != 1) {
                    throw new BusinessException("阶段设置已被其他教师修改，请刷新后重试");
                }
            }
        }
    }

    private void validateDates(InternshipStageSetting first, InternshipStageSetting second, String teacherType) {
        validateStage(first, "第一阶段");
        validateStage(second, "第二阶段");
        LocalDate firstEnd = parse(first.getInternshipEndDate(), "第一阶段实习结束日期格式不正确");
        LocalDate secondStart = parse(second.getInternshipStartDate(), "第二阶段实习开始日期格式不正确");
        if (!firstEnd.isBefore(secondStart)) {
            throw new BusinessException("第二阶段实习开始时间必须晚于第一阶段实习结束时间");
        }
        // 只有系室教师可以配置鉴定表截止日期，且必须晚于第二阶段实习结束日期
        if ("DEPARTMENT".equals(teacherType)) {
            String deadline = second.getAppraisalDeadline();
            if (deadline != null && !deadline.isBlank()) {
                LocalDate appraisalDeadline = parse(deadline, "鉴定表截止日期格式不正确");
                LocalDate secondEnd = parse(second.getInternshipEndDate(), "第二阶段实习结束日期格式不正确");
                if (!appraisalDeadline.isAfter(secondEnd)) {
                    throw new BusinessException("鉴定表截止日期必须晚于第二阶段实习结束日期");
                }
            }
        }
    }

    private void validateStage(InternshipStageSetting stage, String label) {
        if (isBlank(stage.getStageName()) || isBlank(stage.getSelectionStartDate())
                || isBlank(stage.getSelectionEndDate()) || isBlank(stage.getInternshipStartDate())
                || isBlank(stage.getInternshipEndDate())) {
            throw new BusinessException(label + "设置不完整");
        }
        LocalDate selectionStart = parse(stage.getSelectionStartDate(), label + "选择开始日期格式不正确");
        LocalDate selectionEnd = parse(stage.getSelectionEndDate(), label + "选择截止日期格式不正确");
        LocalDate internshipStart = parse(stage.getInternshipStartDate(), label + "实习开始日期格式不正确");
        LocalDate internshipEnd = parse(stage.getInternshipEndDate(), label + "实习结束日期格式不正确");
        if (selectionStart.isAfter(selectionEnd)) {
            throw new BusinessException(label + "选择开始时间不能晚于选择截止时间");
        }
        if (selectionEnd.isAfter(internshipStart)) {
            throw new BusinessException(label + "选择截止时间不能晚于实习开始时间");
        }
        if (internshipStart.isAfter(internshipEnd)) {
            throw new BusinessException(label + "实习开始时间不能晚于实习结束时间");
        }
        if (stage.getReportCycle() == null || stage.getReportCycle() < 1 || stage.getReportCycle() > 90) {
            throw new BusinessException(label + "心得周期必须为1至90天");
        }
    }

    private LocalDate parse(String value, String message) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
