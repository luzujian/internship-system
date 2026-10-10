package com.internship.entity.dto;

/** Identifies the interview history used by a student's application to a position. */
public record StudentPositionKey(Long studentId, Long positionId) {
}
