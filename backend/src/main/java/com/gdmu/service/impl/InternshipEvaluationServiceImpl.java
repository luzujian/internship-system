package com.gdmu.service.impl;

import com.gdmu.entity.InternshipEvaluation;
import com.gdmu.entity.PageResult;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.InternshipEvaluationMapper;
import com.gdmu.service.InternshipEvaluationService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class InternshipEvaluationServiceImpl implements InternshipEvaluationService {
    
    private final InternshipEvaluationMapper internshipEvaluationMapper;
    
    @Autowired
    public InternshipEvaluationServiceImpl(InternshipEvaluationMapper internshipEvaluationMapper) {
        this.internshipEvaluationMapper = internshipEvaluationMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(InternshipEvaluation evaluation) {
        log.debug("插入实习评分，学生ID: {}", evaluation.getStudentId());

        if (evaluation.getStudentId() == null || evaluation.getStudentId() <= 0) {
            throw new BusinessException("学生ID无效");
        }

        // 如果 totalScore 已经有值（动态评分计算好的），使用它
        // 否则根据传统字段计算
        if (evaluation.getTotalScore() == null || evaluation.getTotalScore() <= 0) {
            int totalScore = (evaluation.getAttitudeScore() != null ? evaluation.getAttitudeScore() : 0) +
                             (evaluation.getPerformanceScore() != null ? evaluation.getPerformanceScore() : 0) +
                             (evaluation.getReportScore() != null ? evaluation.getReportScore() : 0) +
                             (evaluation.getCompanyEvaluationScore() != null ? evaluation.getCompanyEvaluationScore() : 0);
            evaluation.setTotalScore(totalScore);
        }

        if (evaluation.getTotalScore() > 100) {
            throw new BusinessException("总分不能超过100分");
        }

        // 如果 grade 没有设置，则根据总分计算
        if (evaluation.getGrade() == null || evaluation.getGrade().isEmpty()) {
            evaluation.setGrade(calculateGrade(evaluation.getTotalScore()));
        }

        Date now = new Date();
        evaluation.setEvaluateTime(now);
        evaluation.setUpdateTime(now);
        // 默认设置 gradePublished 为 0（未发布）
        if (evaluation.getGradePublished() == null) {
            evaluation.setGradePublished(0);
        }

        int result = internshipEvaluationMapper.insert(evaluation);
        log.info("实习评分插入成功，学生ID: {}", evaluation.getStudentId());
        return result;
    }
    
    @Override
    public InternshipEvaluation findById(Long id) {
        log.debug("根据ID查询实习评分，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("ID无效");
        }
        
        return internshipEvaluationMapper.findById(id);
    }
    
    @Override
    public InternshipEvaluation findByStudentId(Long studentId) {
        log.debug("根据学生ID查询实习评分，学生ID: {}", studentId);
        
        if (studentId == null || studentId <= 0) {
            throw new BusinessException("学生ID无效");
        }
        
        return internshipEvaluationMapper.findByStudentId(studentId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(InternshipEvaluation evaluation) {
        log.debug("更新实习评分，ID: {}", evaluation.getId());

        if (evaluation.getId() == null || evaluation.getId() <= 0) {
            throw new BusinessException("ID无效");
        }

        // 如果没有传递具体分数，则不验证和更新分数相关字段
        // 只有当至少有一个分数字段不为null时才进行验证
        boolean hasScoreFields = evaluation.getAttitudeScore() != null
                || evaluation.getPerformanceScore() != null
                || evaluation.getReportScore() != null
                || evaluation.getCompanyEvaluationScore() != null;

        if (hasScoreFields) {
            // 验证分数范围
            if (evaluation.getAttitudeScore() != null && (evaluation.getAttitudeScore() < 0 || evaluation.getAttitudeScore() > 100)) {
                throw new BusinessException("实习态度分数必须在0-100之间");
            }

            if (evaluation.getPerformanceScore() != null && (evaluation.getPerformanceScore() < 0 || evaluation.getPerformanceScore() > 100)) {
                throw new BusinessException("实习表现分数必须在0-100之间");
            }

            if (evaluation.getReportScore() != null && (evaluation.getReportScore() < 0 || evaluation.getReportScore() > 100)) {
                throw new BusinessException("实习心得分数必须在0-100之间");
            }

            if (evaluation.getCompanyEvaluationScore() != null && (evaluation.getCompanyEvaluationScore() < 0 || evaluation.getCompanyEvaluationScore() > 100)) {
                throw new BusinessException("单位评价分数必须在0-100之间");
            }

            // 如果 totalScore 已经有值（动态评分计算好的），使用它
            // 否则根据传统字段计算
            Integer totalScore = evaluation.getTotalScore();
            if (totalScore == null || totalScore <= 0) {
                // 传统方式计算总分（当动态评分没有传入时）
                int calculatedTotalScore = (evaluation.getAttitudeScore() != null ? evaluation.getAttitudeScore() : 0) +
                        (evaluation.getPerformanceScore() != null ? evaluation.getPerformanceScore() : 0) +
                        (evaluation.getReportScore() != null ? evaluation.getReportScore() : 0) +
                        (evaluation.getCompanyEvaluationScore() != null ? evaluation.getCompanyEvaluationScore() : 0);
                evaluation.setTotalScore(calculatedTotalScore);
            }

            // 如果 grade 没有设置，则根据总分计算
            if (evaluation.getGrade() == null || evaluation.getGrade().isEmpty()) {
                evaluation.setGrade(calculateGrade(evaluation.getTotalScore()));
            }
        }

        evaluation.setUpdateTime(new Date());

        int result = internshipEvaluationMapper.update(evaluation);
        log.info("实习评分更新成功，ID: {}", evaluation.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        log.debug("删除实习评分，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("ID无效");
        }
        
        int result = internshipEvaluationMapper.deleteById(id);
        log.info("实习评分删除成功，ID: {}", id);
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByStudentId(Long studentId) {
        log.debug("根据学生ID删除实习评分，学生ID: {}", studentId);
        
        if (studentId == null || studentId <= 0) {
            throw new BusinessException("学生ID无效");
        }
        
        int result = internshipEvaluationMapper.deleteByStudentId(studentId);
        log.info("实习评分删除成功，学生ID: {}", studentId);
        return result;
    }
    
    @Override
    public List<InternshipEvaluation> findAll() {
        log.debug("查询所有实习评分");
        return internshipEvaluationMapper.findAll();
    }
    
    @Override
    public List<InternshipEvaluation> findByEvaluatorId(Long evaluatorId) {
        log.debug("根据评分人ID查询实习评分，评分人ID: {}", evaluatorId);
        
        if (evaluatorId == null || evaluatorId <= 0) {
            throw new BusinessException("评分人ID无效");
        }
        
        return internshipEvaluationMapper.findByEvaluatorId(evaluatorId);
    }
    
    @Override
    public int countByEvaluatorId(Long evaluatorId) {
        log.debug("统计评分人评分数量，评分人ID: {}", evaluatorId);
        
        if (evaluatorId == null || evaluatorId <= 0) {
            throw new BusinessException("评分人ID无效");
        }
        
        return internshipEvaluationMapper.countByEvaluatorId(evaluatorId);
    }
    
    @Override
    public List<InternshipEvaluation> findByGrade(String grade) {
        log.debug("根据等级查询实习评分，等级: {}", grade);
        
        if (grade == null || grade.trim().isEmpty()) {
            throw new BusinessException("等级不能为空");
        }
        
        return internshipEvaluationMapper.findByGrade(grade);
    }
    
    @Override
    public PageResult<InternshipEvaluation> findPage(Integer page, Integer pageSize, Long evaluatorId) {
        log.debug("分页查询实习评分，页码: {}, 每页条数: {}, 评分人ID: {}", page, pageSize, evaluatorId);
        
        PageHelper.startPage(page, pageSize);
        
        List<InternshipEvaluation> list;
        if (evaluatorId != null && evaluatorId > 0) {
            list = internshipEvaluationMapper.findByEvaluatorId(evaluatorId);
        } else {
            list = internshipEvaluationMapper.findAll();
        }
        
        PageInfo<InternshipEvaluation> pageInfo = new PageInfo<>(list);
        
        return PageResult.build(
            pageInfo.getTotal(),
            list,
            pageInfo.getPages(),
            pageInfo.getPageNum(),
            pageInfo.getPageSize()
        );
    }
    
    private String calculateGrade(int totalScore) {
        if (totalScore >= 90) {
            return "优秀";
        } else if (totalScore >= 80) {
            return "良好";
        } else if (totalScore >= 70) {
            return "中等";
        } else if (totalScore >= 60) {
            return "及格";
        } else {
            return "不及格";
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publishGrades(List<Long> studentIds) {
        log.info("批量发布成绩，学生IDs: {}", studentIds);
        if (studentIds == null || studentIds.isEmpty()) {
            return 0;
        }
        return internshipEvaluationMapper.publishGrades(studentIds);
    }
}
