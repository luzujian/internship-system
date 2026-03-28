package com.gdmu.service;

import com.gdmu.entity.InternshipTimeSettings;

public interface InternshipTimeSettingsService {
    
    InternshipTimeSettings findLatest();
    
    int insert(InternshipTimeSettings settings);
    
    int update(InternshipTimeSettings settings);
}
