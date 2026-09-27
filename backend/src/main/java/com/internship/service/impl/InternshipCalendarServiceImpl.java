package com.internship.service.impl;

import com.internship.entity.InternshipTimeSettings;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipTimeSettingsMapper;
import com.internship.service.InternshipCalendarService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;

@Service
public class InternshipCalendarServiceImpl implements InternshipCalendarService {

    private final InternshipTimeSettingsMapper settingsMapper;

    public InternshipCalendarServiceImpl(InternshipTimeSettingsMapper settingsMapper) {
        this.settingsMapper = settingsMapper;
    }

    @Override
    public InternshipCalendar getCalendar() {
        InternshipTimeSettings settings = settingsMapper.findLatest();
        return settings == null ? new InternshipCalendar() : toCalendar(settings);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized InternshipCalendar saveCalendar(InternshipCalendar calendar,
                                                        Long updaterId,
                                                        String updaterName) {
        validate(calendar);

        InternshipTimeSettings current = settingsMapper.findLatest();
        if (current == null) {
            if (hasText(calendar.getVersion())) {
                throw new BusinessException("实习日历已发生变化，请刷新后重试");
            }
            InternshipTimeSettings created = toSettings(calendar);
            created.setCreateAdminId(updaterId);
            created.setUpdateAdminId(updaterId);
            created.setCreator(updaterName);
            created.setUpdater(updaterName);
            if (settingsMapper.insert(created) != 1) {
                throw new BusinessException("实习日历保存失败");
            }
            return getCalendar();
        }

        if (!Objects.equals(calendar.getVersion(), versionOf(current))) {
            throw new BusinessException("实习日历已被其他教师修改，请刷新后重试");
        }

        InternshipTimeSettings updated = toSettings(calendar);
        updated.setId(current.getId());
        updated.setUpdateAdminId(updaterId);
        updated.setUpdater(updaterName);
        if (settingsMapper.updateCalendarIfUnchanged(updated, current) != 1) {
            throw new BusinessException("实习日历已被其他教师修改，请刷新后重试");
        }
        return getCalendar();
    }

    private void validate(InternshipCalendar calendar) {
        if (calendar == null) {
            throw new BusinessException("实习日历不能为空");
        }
        LocalDate start = parseRequired(calendar.getStartDate(), "实习开始日期");
        LocalDate end = parseRequired(calendar.getEndDate(), "实习结束日期");
        LocalDate appraisalDeadline = parseRequired(calendar.getAppraisalDeadline(), "实习鉴定截止日期");
        if (end.isBefore(start)) {
            throw new BusinessException("实习结束日期不能早于开始日期");
        }
        if (!appraisalDeadline.isAfter(end)) {
            throw new BusinessException("实习鉴定截止日期必须晚于实习结束日期");
        }
        if (calendar.getReportCycle() == null
                || calendar.getReportCycle() < 1
                || calendar.getReportCycle() > 90) {
            throw new BusinessException("心得周期必须为 1 到 90 天");
        }
    }

    private LocalDate parseRequired(String value, String label) {
        if (!hasText(value)) {
            throw new BusinessException(label + "不能为空");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(label + "格式必须为 yyyy-MM-dd");
        }
    }

    private InternshipCalendar toCalendar(InternshipTimeSettings settings) {
        InternshipCalendar calendar = new InternshipCalendar();
        calendar.setStartDate(settings.getStartDate());
        calendar.setEndDate(settings.getEndDate());
        calendar.setReportCycle(settings.getReportCycle());
        calendar.setAppraisalDeadline(settings.getEvaluationDeadline());
        calendar.setVersion(versionOf(settings));
        return calendar;
    }

    private InternshipTimeSettings toSettings(InternshipCalendar calendar) {
        InternshipTimeSettings settings = new InternshipTimeSettings();
        settings.setStartDate(calendar.getStartDate());
        settings.setEndDate(calendar.getEndDate());
        settings.setReportCycle(calendar.getReportCycle());
        settings.setEvaluationDeadline(calendar.getAppraisalDeadline());
        return settings;
    }

    private String versionOf(InternshipTimeSettings settings) {
        String raw = String.join("|",
                String.valueOf(settings.getId()),
                settings.getUpdateTime() == null ? "" : String.valueOf(settings.getUpdateTime().getTime()),
                nullToEmpty(settings.getStartDate()),
                nullToEmpty(settings.getEndDate()),
                String.valueOf(settings.getReportCycle()),
                nullToEmpty(settings.getEvaluationDeadline()));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
