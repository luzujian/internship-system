package com.internship.service;

import com.internship.entity.CounselorKeyword;

import java.util.List;

public interface CounselorKeywordService {

    List<CounselorKeyword> getKeywordsByCounselorId(Long counselorId);

    void addKeyword(CounselorKeyword keyword);

    void updateKeyword(CounselorKeyword keyword);

    void deleteKeyword(Long id);
}
