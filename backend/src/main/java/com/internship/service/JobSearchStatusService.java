package com.internship.service;

import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.dto.JobSearchStatusDTO;
import com.internship.entity.dto.JobSearchStatusEvidenceDTO;
import com.internship.enums.JobSearchStatus;
import com.internship.mapper.JobSearchStatusMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一派生学生求职进度。这里不修改 student_internship_status.status，
 * 只向接口模型补充 jobSearchStatus 和 jobSearchStatusText。
 */
@Service
public class JobSearchStatusService {
    private final JobSearchStatusMapper jobSearchStatusMapper;

    public JobSearchStatusService(JobSearchStatusMapper jobSearchStatusMapper) {
        this.jobSearchStatusMapper = jobSearchStatusMapper;
    }

    public JobSearchStatusDTO resolve(Long studentId, Integer internshipStatus) {
        if (studentId == null) {
            return toDTO(resolveStatus(internshipStatus, null));
        }
        List<JobSearchStatusEvidenceDTO> evidence =
                jobSearchStatusMapper.findEvidenceByStudentIds(List.of(studentId));
        JobSearchStatusEvidenceDTO item = evidence == null || evidence.isEmpty() ? null : evidence.get(0);
        return toDTO(resolveStatus(internshipStatus, item));
    }

    public Map<Long, JobSearchStatusDTO> resolveForStudentIds(Collection<Long> studentIds) {
        List<Long> normalizedIds = normalizeIds(studentIds);
        if (normalizedIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<JobSearchStatusEvidenceDTO> evidence = jobSearchStatusMapper.findEvidenceByStudentIds(normalizedIds);
        Map<Long, JobSearchStatusEvidenceDTO> evidenceByStudentId = evidence == null
                ? Collections.emptyMap()
                : evidence.stream()
                    .filter(item -> item.getStudentId() != null)
                    .collect(Collectors.toMap(
                            JobSearchStatusEvidenceDTO::getStudentId,
                            Function.identity(),
                            (first, ignored) -> first,
                            LinkedHashMap::new));

        Map<Long, JobSearchStatusDTO> result = new LinkedHashMap<>();
        for (Long studentId : normalizedIds) {
            JobSearchStatusEvidenceDTO item = evidenceByStudentId.get(studentId);
            Integer internshipStatus = item == null ? null : item.getInternshipStatus();
            result.put(studentId, toDTO(resolveStatus(internshipStatus, item)));
        }
        return result;
    }

    public void enrich(StudentInternshipStatus status) {
        if (status == null) {
            return;
        }
        JobSearchStatusDTO displayStatus = resolve(status.getStudentId(), status.getStatus());
        apply(status, displayStatus);
    }

    public void enrich(List<StudentInternshipStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        Map<Long, JobSearchStatusDTO> derived = resolveForStudentIds(statuses.stream()
                .map(StudentInternshipStatus::getStudentId)
                .filter(Objects::nonNull)
                .toList());
        for (StudentInternshipStatus status : statuses) {
            JobSearchStatusDTO displayStatus = status.getStatus() != null && status.getStatus() >= 2
                    ? toDTO(resolveStatus(status.getStatus(), null))
                    : derived.get(status.getStudentId());
            if (displayStatus != null) {
                apply(status, displayStatus);
            }
        }
    }

    public void enrichStudentMaps(List<Map<String, Object>> students) {
        if (students == null || students.isEmpty()) {
            return;
        }
        List<Long> studentIds = students.stream()
                .map(student -> asLong(student.get("id")))
                .filter(Objects::nonNull)
                .toList();
        Map<Long, JobSearchStatusDTO> derived = resolveForStudentIds(studentIds);
        for (Map<String, Object> student : students) {
            JobSearchStatusDTO displayStatus = derived.get(asLong(student.get("id")));
            if (displayStatus != null) {
                student.put("jobSearchStatus", displayStatus.jobSearchStatus());
                student.put("jobSearchStatusText", displayStatus.jobSearchStatusText());
            }
        }
    }

    public long countByDisplayFilter(int statusFilter, Collection<Long> classIds) {
        return jobSearchStatusMapper.countByDisplayFilter(statusFilter, classIds);
    }

    public long countWithoutOfferByDepartmentId(Long departmentId) {
        if (departmentId == null || departmentId <= 0) {
            throw new IllegalArgumentException("学院ID无效");
        }
        return jobSearchStatusMapper.countWithoutOfferByDepartmentId(departmentId);
    }

    private JobSearchStatus resolveStatus(Integer internshipStatus, JobSearchStatusEvidenceDTO evidence) {
        if (internshipStatus != null && internshipStatus >= 2) {
            return JobSearchStatus.fromEstablishedInternshipStatus(internshipStatus);
        }
        if (Integer.valueOf(1).equals(internshipStatus) && evidence == null) {
            return JobSearchStatus.PENDING_CONFIRMATION;
        }
        if (evidence != null
                && (Boolean.TRUE.equals(evidence.getHasPassedOffer())
                    || Boolean.TRUE.equals(evidence.getHasPendingConfirmation()))) {
            return JobSearchStatus.PENDING_CONFIRMATION;
        }
        if (evidence != null && Boolean.TRUE.equals(evidence.getHasInterviewOpportunity())) {
            return JobSearchStatus.NO_OFFER;
        }
        return JobSearchStatus.NOT_FOUND;
    }

    private JobSearchStatusDTO toDTO(JobSearchStatus status) {
        return new JobSearchStatusDTO(status.name(), status.getText());
    }

    private void apply(StudentInternshipStatus status, JobSearchStatusDTO displayStatus) {
        status.setInternshipStatus(status.getStatus());
        status.setJobSearchStatus(displayStatus.jobSearchStatus());
        status.setJobSearchStatusText(displayStatus.jobSearchStatusText());
    }

    private List<Long> normalizeIds(Collection<Long> studentIds) {
        if (studentIds == null) {
            return Collections.emptyList();
        }
        return studentIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.valueOf(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
