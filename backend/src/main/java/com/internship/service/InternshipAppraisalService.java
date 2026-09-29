package com.internship.service;

import com.internship.entity.FileUpload;
import com.internship.entity.InternshipAppraisalFile;
import com.internship.entity.InternshipAppraisalSubmission;
import com.internship.entity.dto.AppraisalSubmitRequest;

public interface InternshipAppraisalService {
    InternshipAppraisalSubmission findExistingSubmission(Long studentId);
    InternshipAppraisalSubmission findOrCreateDraft(Long studentId);
    InternshipAppraisalSubmission getAppraisal(Long studentId);
    void validateFileUploadAllowed(Long studentId);
    InternshipAppraisalFile addFile(Long studentId, InternshipAppraisalFile file);
    InternshipAppraisalFile addUploadedFile(Long studentId, FileUpload upload, InternshipAppraisalFile file);
    void deleteFile(Long studentId, Long fileId);
    InternshipAppraisalSubmission submit(Long studentId, AppraisalSubmitRequest request);
    InternshipAppraisalSubmission withdraw(Long studentId);
}
