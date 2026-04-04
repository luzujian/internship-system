package com.gdmu.service.impl;

import com.gdmu.entity.CounselorKeyword;
import com.gdmu.entity.KeywordLibrary;
import com.gdmu.mapper.CounselorKeywordMapper;
import com.gdmu.mapper.KeywordLibraryMapper;
import com.gdmu.service.CounselorKeywordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class CounselorKeywordServiceImpl implements CounselorKeywordService {

    @Autowired
    private CounselorKeywordMapper counselorKeywordMapper;

    @Autowired
    private KeywordLibraryMapper keywordLibraryMapper;

    @Override
    public List<CounselorKeyword> getKeywordsByCounselorId(Long counselorId) {
        initDefaultKeywordsIfNeeded(counselorId);
        return counselorKeywordMapper.findByCounselorId(counselorId);
    }

    /**
     * 初始化默认关键词：如果辅导员没有关键词，则从系统关键词库复制默认关键词
     */
    private void initDefaultKeywordsIfNeeded(Long counselorId) {
        int count = counselorKeywordMapper.countByCounselorId(counselorId);
        if (count > 0) {
            return;
        }
        List<KeywordLibrary> defaultKeywords = keywordLibraryMapper.findByUsageType("internship");
        if (defaultKeywords == null || defaultKeywords.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (KeywordLibrary kw : defaultKeywords) {
            if (kw.getStatus() != null && kw.getStatus() == 1) {
                CounselorKeyword counselorKeyword = new CounselorKeyword();
                counselorKeyword.setCounselorId(counselorId);
                counselorKeyword.setKeyword(kw.getKeyword());
                counselorKeyword.setStatus(1);
                counselorKeyword.setCreateTime(now);
                counselorKeyword.setUpdateTime(now);
                counselorKeywordMapper.insert(counselorKeyword);
            }
        }
    }

    @Override
    @Transactional
    public void addKeyword(CounselorKeyword keyword) {
        keyword.setCreateTime(new Date());
        keyword.setUpdateTime(new Date());
        if (keyword.getStatus() == null) {
            keyword.setStatus(1);
        }
        counselorKeywordMapper.insert(keyword);
    }

    @Override
    @Transactional
    public void updateKeyword(CounselorKeyword keyword) {
        keyword.setUpdateTime(new Date());
        counselorKeywordMapper.update(keyword);
    }

    @Override
    @Transactional
    public void deleteKeyword(Long id) {
        counselorKeywordMapper.deleteById(id);
    }
}
