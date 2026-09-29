package com.internship.service;

import com.internship.dto.FreeInternshipReconciliationApplyResult;
import com.internship.dto.FreeInternshipReconciliationReport;
import com.internship.dto.FreeInternshipReconciliationRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FreeInternshipReconciliationService {

    public static final String APPLY_CONFIRMATION_TOKEN = "APPLY_FREE_INTERNSHIP_RECONCILIATION";

    private static final List<String> STUDENT_ID_UPDATE_TABLES = List.of(
            "internship_confirmation_record",
            "internship_stage_assignment",
            "interview_invitation",
            "internship_progress_record",
            "position_view_record"
    );

    private static final List<String> ORPHAN_REPORT_TABLES = List.of(
            "internship_confirmation_record",
            "internship_stage_assignment",
            "interview_invitation",
            "internship_progress_record",
            "position_view_record",
            "student_internship_status",
            "internship_appraisal_submission",
            "internship_reflection"
    );

    private final JdbcTemplate jdbcTemplate;

    public FreeInternshipReconciliationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FreeInternshipReconciliationReport preview(FreeInternshipReconciliationRequest request) {
        List<FreeInternshipReconciliationRequest.StudentIdMapping> requestedMappings =
                request == null ? List.of() : request.safeMappings();
        List<String> warnings = new ArrayList<>();
        List<FreeInternshipReconciliationReport.MappingAssessment> assessments =
                assessMappings(requestedMappings, warnings);

        Set<Long> validLegacyIds = assessments.stream()
                .filter(FreeInternshipReconciliationReport.MappingAssessment::valid)
                .map(FreeInternshipReconciliationReport.MappingAssessment::legacyStudentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Long> effectiveMappings = assessments.stream()
                .filter(FreeInternshipReconciliationReport.MappingAssessment::valid)
                .collect(Collectors.toMap(
                        FreeInternshipReconciliationReport.MappingAssessment::legacyStudentId,
                        FreeInternshipReconciliationReport.MappingAssessment::targetStudentId,
                        (left, right) -> left,
                        LinkedHashMap::new));

        Map<String, List<Long>> impacts = mappingImpacts(assessments);
        List<AssignmentRow> assignments = loadAssignments();
        List<FreeInternshipReconciliationReport.ChainMerge> chainMerges =
                planChainMerges(assignments, effectiveMappings);
        SimulatedPlacements simulated = simulatePlacements(assignments, effectiveMappings, chainMerges);

        Set<Long> targetIds = assessments.stream()
                .filter(FreeInternshipReconciliationReport.MappingAssessment::valid)
                .map(FreeInternshipReconciliationReport.MappingAssessment::targetStudentId)
                .collect(Collectors.toSet());
        for (Long targetId : targetIds) {
            List<AssignmentRow> current = simulated.currentByStudent().getOrDefault(targetId, List.of());
            if (current.size() != 1) {
                warnings.add("Target student " + targetId + " must have exactly one current confirmed placement after reconciliation, found " + current.size());
            } else if (current.get(0).companyId() == null || current.get(0).positionId() == null
                    || current.get(0).confirmationRecordId() == null) {
                warnings.add("Target student " + targetId + " has an incomplete current placement");
            }
        }

        Map<String, List<Long>> remainingOrphans = remainingOrphans(validLegacyIds);
        List<Long> invalidReflections = invalidEmptyAutoReflectionIds();
        List<Long> submittedAppraisals = submittedOrphanAppraisalIds();
        if (!submittedAppraisals.isEmpty()) {
            warnings.add("Submitted orphan appraisals are preserved and require manual identity review: " + submittedAppraisals);
        }
        remainingOrphans.forEach((table, ids) -> {
            if (!ids.isEmpty()) {
                warnings.add("Unresolved orphan rows remain in " + table + ": " + ids);
            }
        });

        boolean executable = assessments.stream().allMatch(FreeInternshipReconciliationReport.MappingAssessment::valid)
                && targetIds.stream().allMatch(id -> {
                    List<AssignmentRow> rows = simulated.currentByStudent().getOrDefault(id, List.of());
                    return rows.size() == 1 && rows.get(0).companyId() != null && rows.get(0).positionId() != null
                            && rows.get(0).confirmationRecordId() != null;
                });

        List<FreeInternshipReconciliationReport.PositionQuotaChange> quotaChanges =
                positionQuotaChanges(simulated.currentRows());
        List<Long> enabledDualPhaseIds = enabledDualPhaseSettingIds();
        Map<String, Integer> counts = new LinkedHashMap<>();
        impacts.forEach((table, ids) -> counts.put("mapping." + table, ids.size()));
        remainingOrphans.forEach((table, ids) -> counts.put("orphan." + table, ids.size()));
        counts.put("chainMerges", chainMerges.size());
        counts.put("retiredDuplicatePlacements", chainMerges.stream()
                .mapToInt(merge -> merge.retiredAssignmentIds().size()).sum());
        counts.put("positionQuotaChanges", quotaChanges.size());
        counts.put("enabledDualPhaseSettings", enabledDualPhaseIds.size());
        counts.put("invalidEmptyAutoReflections", invalidReflections.size());
        counts.put("preservedSubmittedAppraisals", submittedAppraisals.size());

        return new FreeInternshipReconciliationReport(
                executable,
                Map.copyOf(counts),
                List.copyOf(assessments),
                immutableListsMap(impacts),
                immutableListsMap(remainingOrphans),
                List.copyOf(chainMerges),
                List.copyOf(quotaChanges),
                List.copyOf(enabledDualPhaseIds),
                List.copyOf(invalidReflections),
                List.copyOf(submittedAppraisals),
                List.copyOf(warnings));
    }

    @Transactional(rollbackFor = Exception.class)
    public FreeInternshipReconciliationApplyResult apply(FreeInternshipReconciliationRequest request) {
        if (request == null || !APPLY_CONFIRMATION_TOKEN.equals(request.confirmationToken())) {
            throw new IllegalArgumentException("A valid reconciliation confirmation token is required");
        }
        if (request.safeMappings().isEmpty()) {
            throw new IllegalArgumentException("At least one explicit student ID mapping is required");
        }

        lockTargetStudents(request.safeMappings());
        FreeInternshipReconciliationReport before = preview(request);
        if (!before.executable()) {
            throw new IllegalArgumentException("Reconciliation preview is not executable: " + before.warnings());
        }

        Map<String, Integer> updatedRows = new LinkedHashMap<>();
        updateMappedStudentIds(before.mappings(), updatedRows);
        mergeContinueChains(before.chainMerges(), updatedRows);
        rebuildCurrentStatuses(before.mappings(), updatedRows);
        rebuildPositionQuotas(updatedRows);
        disableLegacyDualPhase(updatedRows);
        softDeleteInvalidEmptyAutoReflections(before.invalidEmptyAutoReflectionIds(), updatedRows);

        FreeInternshipReconciliationReport after = preview(request);
        return new FreeInternshipReconciliationApplyResult(before, after, Map.copyOf(updatedRows));
    }

    List<FreeInternshipReconciliationReport.MappingAssessment> assessMappings(
            List<FreeInternshipReconciliationRequest.StudentIdMapping> mappings,
            List<String> warnings) {
        List<FreeInternshipReconciliationReport.MappingAssessment> result = new ArrayList<>();
        Set<Long> legacyIds = new HashSet<>();
        Set<Long> targetIds = new HashSet<>();

        for (FreeInternshipReconciliationRequest.StudentIdMapping mapping : mappings) {
            Long legacyId = mapping == null ? null : mapping.legacyStudentId();
            Long targetId = mapping == null ? null : mapping.targetStudentId();
            String invalidReason = null;
            if (legacyId == null || targetId == null || legacyId <= 0 || targetId <= 0) {
                invalidReason = "Both student IDs must be positive";
            } else if (legacyId.equals(targetId)) {
                invalidReason = "Legacy and target student IDs must differ";
            } else if (!legacyIds.add(legacyId)) {
                invalidReason = "Legacy student ID is mapped more than once";
            } else if (!targetIds.add(targetId)) {
                invalidReason = "Multiple legacy IDs cannot be merged into one target automatically";
            }

            if (invalidReason != null) {
                result.add(new FreeInternshipReconciliationReport.MappingAssessment(
                        legacyId, targetId, null, false, false, invalidReason));
                warnings.add("Invalid mapping " + legacyId + " -> " + targetId + ": " + invalidReason);
                continue;
            }

            List<String> targetStudentUserIds = jdbcTemplate.queryForList(
                    "SELECT student_user_id FROM student_users WHERE id = ? AND role = 'ROLE_STUDENT' AND status = 1",
                    String.class, targetId);
            if (targetStudentUserIds.size() != 1 || isBlank(targetStudentUserIds.get(0))) {
                String reason = "Target is not one active ROLE_STUDENT row";
                result.add(new FreeInternshipReconciliationReport.MappingAssessment(
                        legacyId, targetId, null, false, false, reason));
                warnings.add("Invalid mapping " + legacyId + " -> " + targetId + ": " + reason);
                continue;
            }
            String targetStudentUserId = targetStudentUserIds.get(0).trim();
            List<String> sourceEvidence = jdbcTemplate.queryForList(
                    "SELECT DISTINCT TRIM(student_user_id) FROM internship_confirmation_record " +
                            "WHERE student_id = ? AND student_user_id IS NOT NULL AND TRIM(student_user_id) <> ''",
                    String.class, legacyId);

            if (!sourceEvidence.isEmpty()) {
                boolean matches = sourceEvidence.size() == 1 && targetStudentUserId.equals(sourceEvidence.get(0));
                String message = matches
                        ? "Identity verified by confirmation student_user_id"
                        : "Confirmation student_user_id does not uniquely match target";
                result.add(new FreeInternshipReconciliationReport.MappingAssessment(
                        legacyId, targetId, targetStudentUserId, matches, false, message));
                if (!matches) {
                    warnings.add("Invalid mapping " + legacyId + " -> " + targetId + ": " + message);
                }
                continue;
            }

            long sourceRows = countRowsForStudent(STUDENT_ID_UPDATE_TABLES, legacyId);
            List<String> targetEvidence = jdbcTemplate.queryForList(
                    "SELECT DISTINCT TRIM(student_user_id) FROM internship_confirmation_record " +
                            "WHERE student_id = ? AND student_user_id IS NOT NULL AND TRIM(student_user_id) <> ''",
                    String.class, targetId);
            boolean alreadyApplied = sourceRows == 0
                    && targetEvidence.size() == 1
                    && targetStudentUserId.equals(targetEvidence.get(0));
            String message = alreadyApplied
                    ? "Mapping was already applied"
                    : "No confirmation identity evidence exists for legacy student ID";
            result.add(new FreeInternshipReconciliationReport.MappingAssessment(
                    legacyId, targetId, targetStudentUserId, alreadyApplied, alreadyApplied, message));
            if (!alreadyApplied) {
                warnings.add("Invalid mapping " + legacyId + " -> " + targetId + ": " + message);
            }
        }
        return result;
    }

    void updateMappedStudentIds(
            List<FreeInternshipReconciliationReport.MappingAssessment> mappings,
            Map<String, Integer> updatedRows) {
        for (FreeInternshipReconciliationReport.MappingAssessment mapping : mappings) {
            if (!mapping.valid() || mapping.alreadyApplied()) {
                continue;
            }
            for (String table : STUDENT_ID_UPDATE_TABLES) {
                if ("position_view_record".equals(table)) {
                    // uk_position_student(position_id, student_id)：先删除与目标学生同岗位的源浏览行，
                    // 保留目标学生行，再执行 UPDATE 迁移。
                    int deduplicated = jdbcTemplate.update(
                            "DELETE pv FROM position_view_record pv " +
                                    "JOIN position_view_record existing " +
                                    "ON existing.position_id = pv.position_id AND existing.student_id = ? " +
                                    "WHERE pv.student_id = ?",
                            mapping.targetStudentId(), mapping.legacyStudentId());
                    addCount(updatedRows, table + ".deduplicated", deduplicated);
                }
                int changed = jdbcTemplate.update(
                        "UPDATE " + table + " SET student_id = ? WHERE student_id = ?",
                        mapping.targetStudentId(), mapping.legacyStudentId());
                addCount(updatedRows, table, changed);
            }
        }
    }

    void lockTargetStudents(List<FreeInternshipReconciliationRequest.StudentIdMapping> mappings) {
        mappings.stream()
                .filter(Objects::nonNull)
                .map(FreeInternshipReconciliationRequest.StudentIdMapping::targetStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(targetId -> jdbcTemplate.queryForList(
                        "SELECT id FROM student_users WHERE id = ? FOR UPDATE", Long.class, targetId));
    }

    void mergeContinueChains(
            List<FreeInternshipReconciliationReport.ChainMerge> chainMerges,
            Map<String, Integer> updatedRows) {
        for (FreeInternshipReconciliationReport.ChainMerge merge : chainMerges) {
            int retained = jdbcTemplate.update(
                    "UPDATE internship_stage_assignment SET internship_end_time = NULL, status = 'CONFIRMED', " +
                            "current_flag = 1, quota_released = 0, quota_released_at = NULL, " +
                            "quota_release_reason = NULL, update_time = NOW() WHERE id = ?",
                    merge.retainedAssignmentId());
            addCount(updatedRows, "retainedPlacement", retained);
            for (Long retiredId : merge.retiredAssignmentIds()) {
                int retired = jdbcTemplate.update(
                        "UPDATE internship_stage_assignment SET status = 'REPLACED', current_flag = 0, " +
                                "confirmation_record_id = NULL, " +
                                "quota_released = 1, quota_released_at = COALESCE(quota_released_at, NOW()), " +
                                "quota_release_reason = 'FREE_INTERNSHIP_CHAIN_MERGE', " +
                                "remark = CASE WHEN remark IS NULL OR TRIM(remark) = '' " +
                                "THEN 'Retired by free-internship reconciliation' ELSE remark END, update_time = NOW() WHERE id = ?",
                        retiredId);
                addCount(updatedRows, "retiredPlacement", retired);
            }
        }
    }

    void rebuildCurrentStatuses(
            List<FreeInternshipReconciliationReport.MappingAssessment> mappings,
            Map<String, Integer> updatedRows) {
        for (FreeInternshipReconciliationReport.MappingAssessment mapping : mappings) {
            if (!mapping.valid()) {
                continue;
            }
            List<AssignmentRow> current = jdbcTemplate.query(
                    "SELECT id, student_id, source_type, confirmation_record_id, company_id, position_id, company_name, position_name, " +
                            "internship_start_time, internship_end_time, status, current_flag " +
                            "FROM internship_stage_assignment WHERE student_id = ? AND status = 'CONFIRMED' " +
                            "AND current_flag = 1 ORDER BY internship_start_time DESC, id DESC",
                    FreeInternshipReconciliationService::mapAssignment,
                    mapping.targetStudentId());
            if (current.size() != 1) {
                throw new IllegalStateException("Current placement changed during reconciliation for student " + mapping.targetStudentId());
            }
            AssignmentRow placement = current.get(0);
            int confirmations = jdbcTemplate.update(
                    "UPDATE internship_confirmation_record SET internship_end_time = NULL, internship_duration = NULL, " +
                            "status = 1, update_time = NOW() WHERE id = ? AND student_id = ?",
                    placement.confirmationRecordId(), mapping.targetStudentId());
            if (confirmations != 1) {
                throw new IllegalStateException("Failed to open the retained confirmation for student " + mapping.targetStudentId());
            }
            addCount(updatedRows, "currentConfirmation", confirmations);

            int statuses = jdbcTemplate.update(
                    "INSERT INTO student_internship_status " +
                            "(student_id, student_name, status, company_confirm_status, company_id, company_name, " +
                            "position_id, position_name, internship_start_time, internship_end_time, internship_duration, " +
                            "recall_status, is_delayed, is_interrupted, create_time, update_time) " +
                            "SELECT su.id, su.name, 3, 1, ?, ?, ?, ?, ?, NULL, NULL, 0, 0, 0, NOW(), NOW() " +
                            "FROM student_users su WHERE su.id = ? " +
                            "ON DUPLICATE KEY UPDATE student_name = VALUES(student_name), status = 3, company_confirm_status = 1, " +
                            "company_id = VALUES(company_id), company_name = VALUES(company_name), position_id = VALUES(position_id), " +
                            "position_name = VALUES(position_name), internship_start_time = VALUES(internship_start_time), " +
                            "internship_end_time = NULL, internship_duration = NULL, recall_status = 0, " +
                            "is_delayed = 0, is_interrupted = 0, update_time = NOW()",
                    placement.companyId(), placement.companyName(), placement.positionId(), placement.positionName(),
                    placement.startTime(), mapping.targetStudentId());
            if (statuses < 0 || statuses > 2) {
                throw new IllegalStateException("Failed to rebuild internship status for student " + mapping.targetStudentId());
            }
            addCount(updatedRows, "student_internship_status", statuses);
        }
    }

    void rebuildPositionQuotas(Map<String, Integer> updatedRows) {
        List<Map<String, Object>> counts = jdbcTemplate.queryForList(
                "SELECT p.id, p.planned_recruit, p.recruited_count, p.remaining_quota, " +
                        "COALESCE((SELECT COUNT(DISTINCT sis.student_id) FROM student_internship_status sis " +
                        "WHERE sis.position_id = p.id AND sis.status IN (1, 2, 3) " +
                        "AND COALESCE(sis.recall_status, 0) <> 2), 0) AS actual_count " +
                        "FROM position p");
        int changed = 0;
        for (Map<String, Object> row : counts) {
            long positionId = number(row, "id").longValue();
            int planned = number(row, "planned_recruit").intValue();
            int beforeRecruited = number(row, "recruited_count").intValue();
            int beforeRemaining = number(row, "remaining_quota").intValue();
            int actual = number(row, "actual_count").intValue();
            int remaining = Math.max(planned - actual, 0);
            if (beforeRecruited != actual || beforeRemaining != remaining) {
                changed += jdbcTemplate.update(
                        "UPDATE position SET recruited_count = ?, remaining_quota = ?, update_time = NOW() WHERE id = ?",
                        actual, remaining, positionId);
            }
        }
        addCount(updatedRows, "positionQuota", changed);
    }

    void softDeleteInvalidEmptyAutoReflections(List<Long> reflectionIds, Map<String, Integer> updatedRows) {
        int changed = 0;
        for (Long reflectionId : reflectionIds) {
            changed += jdbcTemplate.update(
                    "UPDATE internship_reflection SET deleted = 1, update_time = NOW() " +
                            "WHERE id = ? AND deleted = 0 AND LOWER(task_type) = 'auto' " +
                            "AND COALESCE(TRIM(content), '') = '' " +
                            "AND NOT EXISTS (SELECT 1 FROM student_users su " +
                            "WHERE su.id = internship_reflection.student_id)",
                    reflectionId);
        }
        addCount(updatedRows, "softDeletedEmptyAutoReflections", changed);
    }

    void disableLegacyDualPhase(Map<String, Integer> updatedRows) {
        int changed = jdbcTemplate.update(
                "UPDATE system_settings SET enable_dual_phase = 0, update_time = NOW() " +
                        "WHERE COALESCE(enable_dual_phase, 0) <> 0");
        addCount(updatedRows, "disabledDualPhaseSettings", changed);
    }

    private Map<String, List<Long>> mappingImpacts(
            List<FreeInternshipReconciliationReport.MappingAssessment> assessments) {
        Map<String, List<Long>> impacts = new LinkedHashMap<>();
        for (String table : STUDENT_ID_UPDATE_TABLES) {
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            for (FreeInternshipReconciliationReport.MappingAssessment assessment : assessments) {
                if (assessment.valid() && !assessment.alreadyApplied()) {
                    ids.addAll(jdbcTemplate.queryForList(
                            "SELECT id FROM " + table + " WHERE student_id = ? ORDER BY id",
                            Long.class, assessment.legacyStudentId()));
                }
            }
            impacts.put(table, List.copyOf(ids));
        }
        return impacts;
    }

    private Map<String, List<Long>> remainingOrphans(Set<Long> mappedLegacyIds) {
        Map<String, List<Long>> orphans = new LinkedHashMap<>();
        for (String table : ORPHAN_REPORT_TABLES) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT t.id, t.student_id FROM " + table + " t " +
                            "LEFT JOIN student_users su ON su.id = t.student_id " +
                            "WHERE su.id IS NULL ORDER BY t.id");
            boolean tableWillBeMapped = STUDENT_ID_UPDATE_TABLES.contains(table);
            List<Long> ids = rows.stream()
                    .filter(row -> !tableWillBeMapped || !mappedLegacyIds.contains(number(row, "student_id").longValue()))
                    .map(row -> number(row, "id").longValue())
                    .toList();
            orphans.put(table, ids);
        }
        return orphans;
    }

    private List<Long> invalidEmptyAutoReflectionIds() {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM internship_reflection r LEFT JOIN student_users su ON su.id = r.student_id " +
                        "WHERE su.id IS NULL AND r.deleted = 0 AND LOWER(r.task_type) = 'auto' " +
                        "AND COALESCE(TRIM(r.content), '') = '' ORDER BY r.id",
                Long.class);
    }

    private List<Long> submittedOrphanAppraisalIds() {
        return jdbcTemplate.queryForList(
                "SELECT a.id FROM internship_appraisal_submission a LEFT JOIN student_users su ON su.id = a.student_id " +
                        "WHERE su.id IS NULL AND a.status = 'SUBMITTED' ORDER BY a.id",
                Long.class);
    }

    private List<Long> enabledDualPhaseSettingIds() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM system_settings WHERE COALESCE(enable_dual_phase, 0) <> 0 ORDER BY id",
                Long.class);
    }

    private List<AssignmentRow> loadAssignments() {
        return jdbcTemplate.query(
                "SELECT id, student_id, source_type, confirmation_record_id, company_id, position_id, company_name, position_name, " +
                        "internship_start_time, internship_end_time, status, current_flag " +
                        "FROM internship_stage_assignment ORDER BY student_id, internship_start_time, id",
                FreeInternshipReconciliationService::mapAssignment);
    }

    private static AssignmentRow mapAssignment(ResultSet rs, int rowNum) throws SQLException {
        return new AssignmentRow(
                rs.getLong("id"),
                rs.getLong("student_id"),
                rs.getString("source_type"),
                nullableLong(rs, "confirmation_record_id"),
                nullableLong(rs, "company_id"),
                nullableLong(rs, "position_id"),
                rs.getString("company_name"),
                rs.getString("position_name"),
                toLocalDateTime(rs.getTimestamp("internship_start_time")),
                toLocalDateTime(rs.getTimestamp("internship_end_time")),
                rs.getString("status"),
                rs.getInt("current_flag") == 1);
    }

    private List<FreeInternshipReconciliationReport.ChainMerge> planChainMerges(
            List<AssignmentRow> assignments,
            Map<Long, Long> mappings) {
        Map<PlacementKey, List<AssignmentRow>> groups = assignments.stream()
                .filter(row -> row.currentFlag() && "CONFIRMED".equals(row.status()))
                .collect(Collectors.groupingBy(
                        row -> new PlacementKey(mappings.getOrDefault(row.studentId(), row.studentId()), row.companyId(), row.positionId()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<FreeInternshipReconciliationReport.ChainMerge> result = new ArrayList<>();
        Comparator<AssignmentRow> oldest = Comparator
                .comparing(AssignmentRow::startTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AssignmentRow::id);
        for (Map.Entry<PlacementKey, List<AssignmentRow>> entry : groups.entrySet()) {
            List<AssignmentRow> rows = entry.getValue();
            if (rows.size() < 2 || rows.stream().noneMatch(row -> "CONTINUE".equals(row.sourceType()))) {
                continue;
            }
            AssignmentRow retained = rows.stream().min(oldest).orElseThrow();
            List<Long> retired = rows.stream()
                    .filter(row -> row.id() != retained.id())
                    .map(AssignmentRow::id)
                    .sorted()
                    .toList();
            PlacementKey key = entry.getKey();
            result.add(new FreeInternshipReconciliationReport.ChainMerge(
                    key.studentId(), key.companyId(), key.positionId(), retained.id(),
                    retained.confirmationRecordId(), retired, retired));
        }
        return result;
    }

    private SimulatedPlacements simulatePlacements(
            List<AssignmentRow> assignments,
            Map<Long, Long> mappings,
            List<FreeInternshipReconciliationReport.ChainMerge> chainMerges) {
        Set<Long> retained = chainMerges.stream()
                .map(FreeInternshipReconciliationReport.ChainMerge::retainedAssignmentId)
                .collect(Collectors.toSet());
        Set<Long> retired = chainMerges.stream()
                .flatMap(merge -> merge.retiredAssignmentIds().stream())
                .collect(Collectors.toSet());
        Set<Long> validStudentIds = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT id FROM student_users WHERE role = 'ROLE_STUDENT' AND status = 1", Long.class));
        List<AssignmentRow> current = new ArrayList<>();
        for (AssignmentRow row : assignments) {
            long effectiveStudentId = mappings.getOrDefault(row.studentId(), row.studentId());
            boolean effectiveCurrent = retained.contains(row.id()) || (row.currentFlag() && !retired.contains(row.id()));
            boolean effectiveConfirmed = retained.contains(row.id()) || "CONFIRMED".equals(row.status());
            if (effectiveCurrent && effectiveConfirmed && validStudentIds.contains(effectiveStudentId)) {
                current.add(row.withStudentId(effectiveStudentId));
            }
        }
        Map<Long, List<AssignmentRow>> currentByStudent = current.stream()
                .collect(Collectors.groupingBy(AssignmentRow::studentId, LinkedHashMap::new, Collectors.toList()));
        return new SimulatedPlacements(List.copyOf(current), currentByStudent);
    }

    private List<FreeInternshipReconciliationReport.PositionQuotaChange> positionQuotaChanges(
            List<AssignmentRow> simulatedCurrent) {
        Map<Long, Set<Long>> studentsByPosition = new HashMap<>();
        for (AssignmentRow row : simulatedCurrent) {
            if (row.positionId() != null) {
                studentsByPosition.computeIfAbsent(row.positionId(), ignored -> new HashSet<>()).add(row.studentId());
            }
        }
        List<Map<String, Object>> positions = jdbcTemplate.queryForList(
                "SELECT id, planned_recruit, recruited_count, remaining_quota FROM position ORDER BY id");
        List<FreeInternshipReconciliationReport.PositionQuotaChange> changes = new ArrayList<>();
        for (Map<String, Object> position : positions) {
            long id = number(position, "id").longValue();
            int planned = number(position, "planned_recruit").intValue();
            int beforeRecruited = number(position, "recruited_count").intValue();
            int beforeRemaining = number(position, "remaining_quota").intValue();
            int afterRecruited = studentsByPosition.getOrDefault(id, Set.of()).size();
            int afterRemaining = Math.max(planned - afterRecruited, 0);
            if (beforeRecruited != afterRecruited || beforeRemaining != afterRemaining) {
                changes.add(new FreeInternshipReconciliationReport.PositionQuotaChange(
                        id, beforeRecruited, afterRecruited, beforeRemaining, afterRemaining));
            }
        }
        return changes;
    }

    private long countRowsForStudent(List<String> tables, Long studentId) {
        long count = 0;
        for (String table : tables) {
            Long value = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE student_id = ?", Long.class, studentId);
            count += value == null ? 0 : value;
        }
        return count;
    }

    private static Map<String, List<Long>> immutableListsMap(Map<String, List<Long>> source) {
        Map<String, List<Long>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    private static void addCount(Map<String, Integer> counts, String key, int amount) {
        counts.merge(key, amount, Integer::sum);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Number number(Map<String, Object> row, String key) {
        Object direct = row.get(key);
        if (direct instanceof Number number) {
            return number;
        }
        Object upper = row.get(key.toUpperCase());
        if (upper instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("Missing numeric column " + key);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record PlacementKey(long studentId, Long companyId, Long positionId) {
    }

    private record SimulatedPlacements(
            List<AssignmentRow> currentRows,
            Map<Long, List<AssignmentRow>> currentByStudent) {
    }

    private record AssignmentRow(
            long id,
            long studentId,
            String sourceType,
            Long confirmationRecordId,
            Long companyId,
            Long positionId,
            String companyName,
            String positionName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status,
            boolean currentFlag) {
        AssignmentRow withStudentId(long effectiveStudentId) {
            return new AssignmentRow(id, effectiveStudentId, sourceType, confirmationRecordId, companyId, positionId, companyName,
                    positionName, startTime, endTime, status, currentFlag);
        }
    }
}
