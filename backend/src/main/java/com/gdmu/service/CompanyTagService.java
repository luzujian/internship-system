package com.gdmu.service;

public interface CompanyTagService {
    
    String determineCompanyTag(java.util.Date registerTime, Integer acceptBackup);
    
    String determineCompanyTags(java.util.Date registerTime, Integer acceptBackup, Integer isInternshipBase, String cooperationMode);
    
    void updateCompanyTags();
    
    void updateCompanyTag(Long companyId);
}
