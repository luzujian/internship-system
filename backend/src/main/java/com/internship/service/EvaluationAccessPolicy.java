package com.internship.service;

import com.internship.entity.InternshipReflection;
import com.internship.entity.StudentReflectionEvaluation;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.utils.CurrentHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Authorizes evaluation records against the student who owns the underlying work. */
@Service
public class EvaluationAccessPolicy {
    private final TeacherUserMapper teachers;
    private final TeacherAutomationScopeMapper scope;
    private final InternshipReflectionService reflections;

    public EvaluationAccessPolicy(TeacherUserMapper teachers, TeacherAutomationScopeMapper scope,
                                  InternshipReflectionService reflections) {
        this.teachers = teachers;
        this.scope = scope;
        this.reflections = reflections;
    }

    public void requireStudent(Long studentId, boolean allowStudentSelf) {
        if (studentId == null || studentId <= 0) throw denied();
        User actor = CurrentHolder.getUser();
        if (actor == null || actor.getId() == null) throw denied();
        if ("ROLE_ADMIN".equals(actor.getRole())) return;
        if (allowStudentSelf && "ROLE_STUDENT".equals(actor.getRole())
                && studentId.equals(actor.getId())) return;
        TeacherUser teacher = requireTeacher(actor);
        if (scope.contains(teacher, studentId) <= 0) throw denied();
    }

    public Set<Long> readableStudentIds() {
        User actor = CurrentHolder.getUser();
        if (actor == null || actor.getId() == null) throw denied();
        if ("ROLE_ADMIN".equals(actor.getRole())) return null;
        return new HashSet<>(scope.studentIds(requireTeacher(actor)));
    }

    public Long requireReflection(Long reflectionId, Long claimedStudentId, boolean allowStudentSelf) {
        if (reflectionId == null || reflectionId <= 0) throw denied();
        InternshipReflection reflection = reflections.findById(reflectionId);
        if (reflection == null || Integer.valueOf(1).equals(reflection.getDeleted())
                || reflection.getStudentId() == null || reflection.getStudentId() <= 0
                || (claimedStudentId != null && !Objects.equals(claimedStudentId, reflection.getStudentId()))) {
            throw denied();
        }
        requireStudent(reflection.getStudentId(), allowStudentSelf);
        return reflection.getStudentId();
    }

    public void requireEvaluation(StudentReflectionEvaluation evaluation, boolean allowStudentSelf) {
        if (evaluation == null) throw denied();
        requireReflection(evaluation.getReflectionId(), evaluation.getStudentId(), allowStudentSelf);
    }

    public List<StudentReflectionEvaluation> readableEvaluations(List<StudentReflectionEvaluation> evaluations) {
        if (evaluations == null) return List.of();
        if ("ROLE_ADMIN".equals(CurrentHolder.getUserRole())) return evaluations;
        return evaluations.stream().filter(evaluation -> {
            try {
                requireEvaluation(evaluation, false);
                return true;
            } catch (AccessDeniedException ignored) {
                return false;
            }
        }).toList();
    }

    public Long evaluatorId(Long requestedId) {
        User actor = CurrentHolder.getUser();
        if (actor == null || actor.getId() == null) throw denied();
        if ("ROLE_ADMIN".equals(actor.getRole())) return requestedId;
        requireTeacher(actor);
        return actor.getId();
    }

    private TeacherUser requireTeacher(User actor) {
        if (actor.getUsername() == null) throw denied();
        TeacherUser teacher = teachers.findById(actor.getId());
        if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())
                || teacher.getTeacherType() == null
                || !Set.of("COUNSELOR", "DEPARTMENT", "COLLEGE").contains(teacher.getTeacherType())
                || !actor.getUsername().equals(teacher.getTeacherUserId())
                || !("ROLE_TEACHER_" + teacher.getTeacherType()).equals(actor.getRole())) {
            throw denied();
        }
        return teacher;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("无权访问该学生的评分记录");
    }
}
