package com.internship.service;

import com.internship.entity.CounselorAISettings;
import com.internship.entity.CounselorScoringRule;
import com.internship.entity.InternshipReflection;
import com.internship.entity.StudentReflectionAIAnalysis;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.CounselorAISettingsMapper;
import com.internship.mapper.CounselorScoringRuleMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.utils.CurrentHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/** Applies the evaluation ownership boundary to AI reports and counselor settings. */
@Service
public class ReflectionAiAccessPolicy {
    private final EvaluationAccessPolicy evaluations;
    private final InternshipReflectionService reflections;
    private final ClassCounselorRelationService assignments;
    private final TeacherUserMapper teachers;
    private final CounselorAISettingsMapper settings;
    private final CounselorScoringRuleMapper rules;
    private final InternshipReflectionMapper batchReflections;

    public ReflectionAiAccessPolicy(EvaluationAccessPolicy evaluations, InternshipReflectionService reflections,
                                    ClassCounselorRelationService assignments, TeacherUserMapper teachers,
                                    CounselorAISettingsMapper settings, CounselorScoringRuleMapper rules,
                                    InternshipReflectionMapper batchReflections) {
        this.evaluations = evaluations;
        this.reflections = reflections;
        this.assignments = assignments;
        this.teachers = teachers;
        this.settings = settings;
        this.rules = rules;
        this.batchReflections = batchReflections;
    }

    public void requireStudent(Long studentId, boolean allowStudentSelf) {
        evaluations.requireStudent(studentId, allowStudentSelf);
    }

    public void requireReflection(Long reflectionId, Long studentId, boolean allowStudentSelf) {
        evaluations.requireReflection(reflectionId, studentId, allowStudentSelf);
    }

    public void requireAnalysis(StudentReflectionAIAnalysis analysis, boolean allowStudentSelf) {
        if (analysis == null || analysis.getStudentId() == null) throw denied();
        evaluations.requireReflection(analysis.getReflectionId(), analysis.getStudentId(), allowStudentSelf);
    }

    public List<StudentReflectionAIAnalysis> readableAnalyses(List<StudentReflectionAIAnalysis> analyses) {
        Set<Long> students = evaluations.readableStudentIds();
        if (analyses == null || analyses.isEmpty() || (students != null && students.isEmpty())) return List.of();
        List<StudentReflectionAIAnalysis> candidates = analyses.stream()
                .filter(analysis -> analysis != null && analysis.getReflectionId() != null && analysis.getReflectionId() > 0
                        && analysis.getStudentId() != null && analysis.getStudentId() > 0
                        && (students == null || students.contains(analysis.getStudentId())))
                .toList();
        List<Long> ids = candidates.stream().map(StudentReflectionAIAnalysis::getReflectionId).distinct().toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, Long> owners = new HashMap<>();
        // Fetch ownership in bounded batches; never turn N reports into N teacher/scope/row lookups.
        for (int offset = 0; offset < ids.size(); offset += 500) {
            for (InternshipReflection reflection : batchReflections.findByIds(ids.subList(offset, Math.min(offset + 500, ids.size())))) {
                if (!Integer.valueOf(1).equals(reflection.getDeleted()) && reflection.getStudentId() != null) {
                    owners.put(reflection.getId(), reflection.getStudentId());
                }
            }
        }
        return candidates.stream().filter(analysis -> Objects.equals(analysis.getStudentId(), owners.get(analysis.getReflectionId()))).toList();
    }

    public void requireCounselor(Long counselorId) {
        User actor = CurrentHolder.getUser();
        if (counselorId == null || counselorId <= 0 || actor == null || actor.getId() == null || actor.getId() <= 0) throw denied();
        if ("ROLE_ADMIN".equals(actor.getRole())) return;
        if (!counselorId.equals(actor.getId()) || !"ROLE_TEACHER_COUNSELOR".equals(actor.getRole())) throw denied();
        TeacherUser teacher = teachers.findById(actor.getId());
        if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())
                || !"COUNSELOR".equals(teacher.getTeacherType())
                || actor.getUsername() == null || !actor.getUsername().equals(teacher.getTeacherUserId())) throw denied();
    }

    public void requireSettingsUpdate(CounselorAISettings update) {
        if (update == null || update.getId() == null) throw denied();
        CounselorAISettings stored = settings.findById(update.getId());
        if (stored == null || (update.getCounselorId() != null
                && !Objects.equals(stored.getCounselorId(), update.getCounselorId()))) throw denied();
        requireCounselor(stored.getCounselorId());
        update.setCounselorId(stored.getCounselorId());
    }

    public Long requireRule(Long ruleId, Long claimedCounselorId) {
        if (ruleId == null || ruleId <= 0) throw denied();
        CounselorScoringRule stored = rules.findById(ruleId);
        if (stored == null || Integer.valueOf(1).equals(stored.getDeleted())
                || (claimedCounselorId != null && !Objects.equals(stored.getCounselorId(), claimedCounselorId))) throw denied();
        requireCounselor(stored.getCounselorId());
        return stored.getCounselorId();
    }

    public AnalysisTarget requireAnalysisTarget(Long reflectionId) {
        evaluations.requireReflection(reflectionId, null, false);
        InternshipReflection reflection = reflections.findById(reflectionId);
        if (reflection == null || Integer.valueOf(1).equals(reflection.getDeleted())) throw denied();
        evaluations.requireStudent(reflection.getStudentId(), false);
        Long counselorId = assignments.findCounselorIdByStudentId(reflection.getStudentId());
        requireCounselor(counselorId);
        return new AnalysisTarget(reflection, counselorId);
    }

    public record AnalysisTarget(InternshipReflection reflection, Long counselorId) { }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("无权访问该心得分析或辅导员设置");
    }
}
