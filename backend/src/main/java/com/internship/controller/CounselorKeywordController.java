package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.CounselorKeyword;
import com.internship.entity.Result;
import com.internship.service.CounselorKeywordService;
import com.internship.utils.CurrentHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/counselor/keyword-library")
@PreAuthorize("hasRole('TEACHER_COUNSELOR')")
public class CounselorKeywordController {

    @Autowired
    private CounselorKeywordService counselorKeywordService;

    @GetMapping("/{counselorId}")
    public Result getKeywordsByCounselorId(@PathVariable Long counselorId) {
        requireCounselorId(counselorId);
        List<CounselorKeyword> keywords = counselorKeywordService.getKeywordsByCounselorId(counselorId);
        return Result.success(keywords);
    }

    @PostMapping
    @Log(operationType = "CREATE", module = "KEYWORD_LIBRARY", description = "新增关键词", riskLevel = "LOW")
    public Result addKeyword(@RequestBody CounselorKeyword keyword) {
        keyword.setCounselorId(requireCurrentCounselorId());
        counselorKeywordService.addKeyword(keyword);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Log(operationType = "UPDATE", module = "KEYWORD_LIBRARY", description = "更新关键词", riskLevel = "LOW")
    public Result updateKeyword(@PathVariable Long id, @RequestBody CounselorKeyword keyword) {
        Long counselorId = requireCurrentCounselorId();
        requireOwnedKeyword(id, counselorId);
        keyword.setId(id);
        keyword.setCounselorId(counselorId);
        counselorKeywordService.updateKeyword(keyword);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Log(operationType = "DELETE", module = "KEYWORD_LIBRARY", description = "删除关键词", riskLevel = "MEDIUM")
    public Result deleteKeyword(@PathVariable Long id) {
        requireOwnedKeyword(id, requireCurrentCounselorId());
        counselorKeywordService.deleteKeyword(id);
        return Result.success();
    }

    private Long requireCurrentCounselorId() {
        Long userId = CurrentHolder.getUserId();
        if (userId == null || !"ROLE_TEACHER_COUNSELOR".equals(CurrentHolder.getUserRole())) {
            throw new AccessDeniedException("当前身份不是辅导员");
        }
        return userId;
    }

    private void requireCounselorId(Long counselorId) {
        if (!requireCurrentCounselorId().equals(counselorId)) {
            throw new AccessDeniedException("无权访问其他辅导员的关键词");
        }
    }

    private void requireOwnedKeyword(Long id, Long counselorId) {
        CounselorKeyword existing = counselorKeywordService.findById(id);
        if (existing == null || !counselorId.equals(existing.getCounselorId())) {
            throw new AccessDeniedException("无权修改其他辅导员的关键词");
        }
    }
}
