package com.gdmu.controller;

import com.gdmu.entity.CounselorKeyword;
import com.gdmu.entity.Result;
import com.gdmu.service.CounselorKeywordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/counselor/keyword-library")
public class CounselorKeywordController {

    @Autowired
    private CounselorKeywordService counselorKeywordService;

    @GetMapping("/{counselorId}")
    public Result getKeywordsByCounselorId(@PathVariable Long counselorId) {
        List<CounselorKeyword> keywords = counselorKeywordService.getKeywordsByCounselorId(counselorId);
        return Result.success(keywords);
    }

    @PostMapping
    public Result addKeyword(@RequestBody CounselorKeyword keyword) {
        counselorKeywordService.addKeyword(keyword);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result updateKeyword(@PathVariable Long id, @RequestBody CounselorKeyword keyword) {
        keyword.setId(id);
        counselorKeywordService.updateKeyword(keyword);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result deleteKeyword(@PathVariable Long id) {
        counselorKeywordService.deleteKeyword(id);
        return Result.success();
    }
}
