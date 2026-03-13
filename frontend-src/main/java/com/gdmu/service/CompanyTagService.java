package com.gdmu.service;

public interface CompanyTagService {
    
    String determineCompanyTag(java.util.Date registerTime, Integer acceptBackup);
    
    void updateCompanyTags();
    
    void updateCompanyTag(Long companyId);
}
