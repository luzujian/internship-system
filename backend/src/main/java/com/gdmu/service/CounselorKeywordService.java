package com.gdmu.service;

import com.gdmu.entity.CounselorKeyword;

import java.util.List;

public interface CounselorKeywordService {

    List<CounselorKeyword> getKeywordsByCounselorId(Long counselorId);

    void addKeyword(CounselorKeyword keyword);

    void updateKeyword(CounselorKeyword keyword);

    void deleteKeyword(Long id);
}
