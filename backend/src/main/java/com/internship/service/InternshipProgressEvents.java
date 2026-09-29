package com.internship.service;

import com.internship.entity.InternshipProgressRecord;

import java.util.Date;

/**
 * Creates immutable student activity records from completed business transitions.
 */
public final class InternshipProgressEvents {

    private InternshipProgressEvents() {
    }

    public static void append(InternshipProgressRecordService service,
                              Long studentId,
                              String eventType,
                              String title,
                              String description,
                              String status,
                              Long relatedId,
                              Date occurredAt) {
        InternshipProgressRecord record = new InternshipProgressRecord();
        record.setStudentId(studentId);
        record.setEventType(eventType);
        record.setEventTitle(title);
        record.setDescription(description);
        record.setStatus(status);
        record.setRelatedId(relatedId);
        record.setEventTime(occurredAt == null ? new Date() : occurredAt);
        service.saveRecord(record);
    }
}
