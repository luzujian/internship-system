package com.internship.service;

/** Only a newly persisted feedback record triggers an administrator reminder. */
public record ProblemFeedbackSubmittedEvent(Long feedbackId) {}
