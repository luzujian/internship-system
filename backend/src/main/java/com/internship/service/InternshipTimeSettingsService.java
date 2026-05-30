package com.internship.service;

import com.internship.entity.InternshipTimeSettings;

public interface InternshipTimeSettingsService {
    
    InternshipTimeSettings findLatest();
    
    int insert(InternshipTimeSettings settings);
    
    int update(InternshipTimeSettings settings);
}
