package com.internship.agent.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.internship.entity.dto.JobSearchStatusDTO;
import com.internship.mapper.AgentReadMapper;
import com.internship.enums.JobSearchStatus;
import com.internship.service.JobSearchStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import static com.internship.agent.business.AgentTypes.*;
import static com.internship.agent.business.AgentIdentityService.*;

/** 教师实习情况智能简报：后端计算真实统计，模型只负责理解问题和提炼重点。 */
@Service
@RequiredArgsConstructor
public class TeacherInternshipSummaryService {
    private static final int PAGE_SIZE = 20;
    private static final Set<String> FOCUS_STATUSES = Set.of(
            "INTERRUPTED", "DELAYED", "NOT_FOUND", "NO_OFFER", "PENDING_CONFIRMATION");
    private static final Set<String> PLACED_STATUSES = Set.of("CONFIRMED", "IN_PROGRESS", "ENDED");
    private static final Set<String> SCOPES = Set.of("COLLEGE", "DIVISION", "CLASS", "COUNSELOR", "LAST");
    private static final Set<String> VIEWS = Set.of("summary", "students", "focus");

    private final AgentReadMapper reads;
    private final JobSearchStatusService jobSearchStatuses;

    @Transactional(readOnly = true)
    public ToolResult query(Actor actor, JsonNode args, Session session) {
        requireTeacher(actor);
        String requestedScopeType = text(args, "scopeType");
        if (requestedScopeType == null || !SCOPES.contains(requestedScopeType)) throw invalid();
        String requestedView = text(args, "view");
        String view = requestedView == null ? "summary" : requestedView;
        if (!VIEWS.contains(view)) throw invalid();
        String requestedStatus = text(args, "status");
        if (requestedStatus != null && !isStatus(requestedStatus)) throw invalid();
        int page = page(args);

        String scopeType = requestedScopeType;
        Long scopeId = optionalId(args, "scopeId");
        String scopeName = text(args, "scopeName");
        if ("LAST".equals(scopeType)) {
            TeacherSummaryQuery last = session == null ? null : session.getLastTeacherSummaryQuery();
            if (last == null) {
                throw new AgentException("NO_LAST_SCOPE", "本会话还没有已核验的实习简报范围，请先说明学院、系或班级。", 400);
            }
            if (scopeId != null || scopeName != null) throw invalid();
            scopeType = last.scopeType();
            scopeId = last.scopeId();
            scopeName = last.scopeName();
            if (requestedView == null) view = last.view();
            if (requestedStatus == null) requestedStatus = last.status();
        } else if ("COUNSELOR".equals(scopeType)) {
            if (scopeId != null || scopeName != null) throw invalid();
            if (!"COUNSELOR".equals(actor.teacherType())) {
                throw new AgentException("INVALID_TOOL_INPUT", "COUNSELOR范围仅适用于辅导员本人负责班级。", 400);
            }
        } else {
            if ("COUNSELOR".equals(actor.teacherType()) && ("COLLEGE".equals(scopeType) || "DIVISION".equals(scopeType))) {
                throw new AgentException("FORBIDDEN", "辅导员只能查询本人负责班级，不能查询学院或系整体。", 403);
            }
            if (!"COUNSELOR".equals(actor.teacherType()) && actor.departmentId() == null) {
                throw new AgentException("SCOPE_UNAVAILABLE", "当前教师尚未配置学院范围，不能查询。", 403);
            }
            ResolvedScope scope = resolveScope(actor, scopeType, scopeName, scopeId);
            scopeId = scope.id();
            scopeName = scope.name();
        }
        if ("DEPARTMENT".equals(actor.teacherType()) && ("COLLEGE".equals(scopeType)
                || ("DIVISION".equals(scopeType) && !Objects.equals(scopeId,actor.divisionId()))))
            throw new AgentException("FORBIDDEN","系教师只能查询本系及其班级。",403);
        if ("LAST".equals(requestedScopeType) && !"COUNSELOR".equals(scopeType))
            resolveScope(actor,scopeType,null,scopeId);
        if ("COUNSELOR".equals(scopeType)) scopeName = "本人负责班级";

        List<Map<String, Object>> rows = reads.teacherSummaryStudentRows(actor, scopeType, scopeId);
        if (rows == null) throw new AgentException("TOOL_UNAVAILABLE","暂时无法读取学生实习记录。",503);
        Map<Long, JobSearchStatusDTO> derived = jobSearchStatuses.resolveForStudentIds(
                rows.stream().map(row -> asLong(row.get("id"))).filter(Objects::nonNull).toList());
        for (Map<String, Object> row : rows) {
            var status = derived.get(asLong(row.get("id")));
            String code = status == null ? JobSearchStatus.UNKNOWN.name() : status.jobSearchStatus();
            row.put("status", code);
            row.put("statusText", status == null ? JobSearchStatus.UNKNOWN.getText() : status.jobSearchStatusText());
            row.put("pendingApplicationTexts", status == null ? List.of() : status.pendingApplicationTexts());
            row.remove("internshipStatus");
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (JobSearchStatus status : JobSearchStatus.values()) counts.put(status.name(), 0L);
        for (Map<String, Object> row : rows) counts.merge(String.valueOf(row.get("status")), 1L, Long::sum);
        long total = rows.size();
        long withoutOffer = counts.get(JobSearchStatus.NOT_FOUND.name()) + counts.get(JobSearchStatus.NO_OFFER.name());
        long focusCount = FOCUS_STATUSES.stream().mapToLong(counts::get).sum();
        long placed = PLACED_STATUSES.stream().mapToLong(counts::get).sum();

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("scopeType", scopeType);
        facts.put("scopeName", scopeName);
        if (scopeId != null) facts.put("scopeId", scopeId);
        facts.put("view", view);
        if (requestedStatus != null) facts.put("status", requestedStatus);
        facts.put("page", page);
        facts.put("pageSize", PAGE_SIZE);
        facts.put("total", total);
        facts.put("withoutOffer", withoutOffer);
        facts.put("withoutOfferRate", rate(withoutOffer, total));
        facts.put("focusCount", focusCount);
        facts.put("focusRate", rate(focusCount, total));
        facts.put("placed", placed);
        facts.put("placedRate", rate(placed, total));
        facts.put("statusCounts", new LinkedHashMap<>(counts));
        Map<String, Object> statusRates = new LinkedHashMap<>();
        counts.forEach((status, count) -> statusRates.put(status, rate(count, total)));
        facts.put("statusRates", statusRates);

        List<Link> links = new ArrayList<>();
        if ("summary".equals(view)) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (JobSearchStatus status : JobSearchStatus.values()) {
                long count = counts.get(status.name());
                if (count == 0) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", status.getText());
                item.put("status", status.name());
                item.put("count", count);
                item.put("ratio", rate(count, total));
                items.add(item);
            }
            links.add(new Link("前往学生状态监控", "OPEN_STUDENT_TRACKING", null, Map.of()));
            if (session != null) remember(session, scopeType, scopeId, scopeName, view, requestedStatus);
            return new ToolResult("teacher_internship_summary", scopeName + "实习情况简报", "OK",
                    OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString(), "当前教师授权范围：" + scopeName,
                    items, total, false, facts, links, warnings());
        }

        final String selectedView = view;
        final String selectedStatus = requestedStatus;
        List<Map<String, Object>> filtered = rows.stream()
                .filter(row -> "students".equals(selectedView) || FOCUS_STATUSES.contains(String.valueOf(row.get("status"))))
                .filter(row -> selectedStatus == null || selectedStatus.equals(String.valueOf(row.get("status"))))
                .map(this::studentItem)
                .toList();
        int from = Math.min((page - 1) * PAGE_SIZE, filtered.size());
        int to = Math.min(from + PAGE_SIZE, filtered.size());
        List<Map<String, Object>> items = filtered.subList(from, to);
        filtered.forEach(row -> links.add(new Link("查看学生", "OPEN_STUDENT_TRACKING", String.valueOf(row.get("id")), Map.of())));
        if (session != null) remember(session, scopeType, scopeId, scopeName, view, requestedStatus);
        return AgentReadTools.result("teacher_internship_summary",
                ("focus".equals(view) ? scopeName + "重点关注学生" : scopeName + "学生名单"),
                actor, items, filtered.size(), facts,
                links.isEmpty() ? List.of(new Link("前往学生状态监控", "OPEN_STUDENT_TRACKING", null, Map.of())) : links,
                warnings());
    }

    private ResolvedScope resolveScope(Actor actor, String scopeType, String scopeName, Long scopeId) {
        List<Map<String, Object>> candidates = reads.teacherSummaryScopes(actor, scopeType, scopeName, scopeId);
        if (candidates != null && scopeName != null) {
            List<Map<String, Object>> exact = candidates.stream()
                    .filter(item -> scopeName.equals(String.valueOf(item.get("name")))).toList();
            if (exact.size() == 1) candidates = exact;
            else if (exact.isEmpty() && candidates.size() == 1) candidates = candidates;
        }
        if (candidates == null || candidates.isEmpty()) {
            throw new AgentException("SCOPE_NOT_FOUND", "指定的学院、系或班级不存在，或不在当前教师可查询范围内。", 404);
        }
        if (candidates.size() > 1) {
            String names = candidates.stream().limit(5).map(item -> String.valueOf(item.get("name")))
                    .reduce((left, right) -> left + "、" + right).orElse("");
            throw new AgentException("AMBIGUOUS_SCOPE", "范围名称存在多个匹配，请确认：" + names + "。", 400);
        }
        Map<String, Object> selected = candidates.getFirst();
        Long id = asLong(selected.get("id"));
        if (id == null) throw unavailableScope();
        return new ResolvedScope(id, String.valueOf(selected.get("name")));
    }

    private Map<String, Object> studentItem(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        for (String key : List.of("id", "title", "studentNo", "className", "major", "division",
                "status", "statusText", "pendingApplicationTexts", "company", "position")) {
            if (row.containsKey(key)) item.put(key, row.get(key));
        }
        return item;
    }

    private static void remember(Session session, String scopeType, Long scopeId, String scopeName,
                                  String view, String status) {
        session.setLastTeacherSummaryQuery(new TeacherSummaryQuery(scopeType, scopeId, scopeName, view, status));
    }

    private static List<String> warnings() {
        return List.of("统计口径：尚无Offer=未投递+无Offer；已落实=已确定+实习中+已结束；重点关注=中断、延期、未投递、无Offer、待确认。名单只返回最小必要字段。");
    }

    private static boolean isStatus(String value) {
        try {
            JobSearchStatus.valueOf(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static double rate(long part, long total) {
        if (total <= 0) return 0.0;
        return Math.round(part * 1000.0 / total) / 10.0;
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string && string.matches("[1-9][0-9]{0,17}")) {
            try { return Long.valueOf(string); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String text(JsonNode args, String key) {
        String value = args.path(key).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Long optionalId(JsonNode args, String key) {
        String value = text(args, key);
        if (value == null) return null;
        try {
            Long id = Long.valueOf(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw invalid();
        }
    }

    private static int page(JsonNode args) {
        int value = args.path("page").asInt(1);
        if (value < 1 || value > 1000) throw invalid();
        return value;
    }

    private static AgentException invalid() {
        return new AgentException("INVALID_TOOL_INPUT", "查询参数不正确，请明确范围、视图或状态。", 400);
    }

    private static AgentException unavailableScope() {
        return new AgentException("SCOPE_UNAVAILABLE", "无法核验当前查询范围，请稍后重试。", 503);
    }

    private record ResolvedScope(Long id, String name) {}
}
