package com.internship.service;

import com.internship.entity.dto.InternshipCalendar;

public interface InternshipCalendarService {

    InternshipCalendar getCalendar();

    InternshipCalendar saveCalendar(InternshipCalendar calendar, Long updaterId, String updaterName);
}
