package com.internship.service.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.internship.exception.BusinessException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Typed, bounded predicates. User/model input never becomes SQL or executable code. */
public final class AutomationRules {
    private AutomationRules() {}
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final Map<String, String> FIELDS = Map.ofEntries(
        Map.entry("student.grade", "年级"), Map.entry("student.classId", "班级"),
        Map.entry("student.majorId", "专业"), Map.entry("student.username", "用户名"),
        Map.entry("student.className", "班级名称"), Map.entry("student.majorName", "专业名称"),
        Map.entry("student.divisionName", "系名称"), Map.entry("student.departmentName", "学院名称"),
        Map.entry("student.name", "姓名"), Map.entry("internship.status", "实习状态"),
        Map.entry("internship.companyId", "实习企业"), Map.entry("internship.positionId", "实习岗位"),
        Map.entry("internship.companyName", "实习企业名称"), Map.entry("internship.positionName", "实习岗位名称"),
        Map.entry("internship.source", "实习方式"), Map.entry("appraisal.status", "鉴定表状态"),
        Map.entry("appraisal.required", "鉴定表待提交"), Map.entry("appraisal.canSubmit", "鉴定表可提交"),
        Map.entry("appraisal.deadline", "鉴定表截止日期"), Map.entry("calendar.startDate", "统一开始日期"),
        Map.entry("calendar.endDate", "统一结束日期"));
    public static final Set<String> SOURCES = Set.of("applications", "interviews", "confirmations", "reflections", "requests");
    public static final Set<String> RECORD_FIELDS = Set.of("status", "type", "periodNumber", "dueDate", "date", "companyId", "positionId", "companyName", "positionName", "required", "canSubmit", "recallStatus");
    public static final Map<String,Set<String>> RECORD_SCHEMA = Map.of(
        "applications",Set.of("status","date","companyId","positionId","companyName","positionName"),
        "interviews",Set.of("status","date","companyId","positionId","companyName","positionName"),
        "confirmations",Set.of("status","date","companyId","positionId","companyName","positionName","recallStatus"),
        "reflections",Set.of("status","periodNumber","dueDate","date","required","canSubmit"),
        "requests",Set.of("status","type","date","companyName","positionName"));
    public static final Set<String> OPERATORS = Set.of("EQ", "NE", "IN", "NOT_IN", "EMPTY", "NOT_EMPTY", "LT", "LTE", "GT", "GTE", "BETWEEN", "DAYS_UNTIL", "DAYS_OVERDUE");
    private static final Set<String> DATE_FIELDS = Set.of("appraisal.deadline","calendar.startDate","calendar.endDate","dueDate","date");
    public enum Truth { TRUE, FALSE, UNKNOWN }

    public static void validate(JsonNode node) { validate(node, null, 0, new int[]{0}); }
    private static void validate(JsonNode n, String record, int depth, int[] count) {
        if (n == null || !n.isObject() || depth > 6 || ++count[0] > 50) fail("条件结构无效或过于复杂，请缩小条件。");
        int kinds = (n.has("all") ? 1 : 0) + (n.has("any") ? 1 : 0) + (n.has("not") ? 1 : 0)
                + (n.has("exists") ? 1 : 0) + (n.has("field") ? 1 : 0) + (n.has("always") ? 1 : 0);
        if (kinds != 1) fail("每个条件必须有且只有一种判断方式。");
        if (n.has("always")) { if (!n.path("always").isBoolean()) fail("条件格式不正确。"); return; }
        for (String group : List.of("all", "any")) if (n.has(group)) {
            if (!n.path(group).isArray() || n.path(group).isEmpty()) fail("组合条件不能为空。");
            n.path(group).forEach(child -> validate(child, record, depth + 1, count)); return;
        }
        if (n.has("not")) { validate(n.path("not"), record, depth + 1, count); return; }
        if (n.has("exists")) {
            if (record != null || !SOURCES.contains(n.path("exists").asText())) fail("不支持该业务数据条件。");
            validate(n.path("where"), n.path("exists").asText(), depth + 1, count); return;
        }
        String field = n.path("field").asText(), op = n.path("op").asText();
        if (!(record != null ? RECORD_SCHEMA.get(record).contains(field) : FIELDS.containsKey(field)) || !OPERATORS.contains(op)) fail("条件字段或比较方式尚未接入。");
        if (!Set.of("EMPTY", "NOT_EMPTY").contains(op) && (!n.has("value") || n.path("value").isNull())) fail("请补充条件的比较值。");
        if (Set.of("IN", "NOT_IN", "BETWEEN", "DAYS_UNTIL", "DAYS_OVERDUE").contains(op)) {
            if (!n.path("value").isArray() || n.path("value").isEmpty()) fail("条件需要有效的取值范围。");
            if (!Set.of("IN", "NOT_IN").contains(op) && n.path("value").size() != 2) fail("日期或数值范围需要起点和终点。");
        }
        boolean dateField=DATE_FIELDS.contains(field);
        boolean booleanField=Set.of("appraisal.required","appraisal.canSubmit","required","canSubmit").contains(field);
        boolean numericField=field.endsWith("Id")||field.equals("periodNumber")||field.equals("student.grade");
        if(booleanField&&!Set.of("EQ","NE","IN","NOT_IN","EMPTY","NOT_EMPTY").contains(op))fail("是否条件只支持明确的是或否。");
        if(Set.of("DAYS_UNTIL","DAYS_OVERDUE").contains(op)) {
            if(!dateField||!n.path("value").get(0).isIntegralNumber()||!n.path("value").get(1).isIntegralNumber()||n.path("value").get(0).asLong()>n.path("value").get(1).asLong())fail("临期或逾期条件需要日期字段及有效天数范围。");
        } else if(!Set.of("EMPTY","NOT_EMPTY").contains(op)) {
            List<JsonNode> values=new ArrayList<>();if(n.path("value").isArray())n.path("value").forEach(values::add);else values.add(n.path("value"));
            for(JsonNode value:values) {
                if(booleanField&&!value.isBoolean())fail("是否条件需要明确的是或否。");
                if(numericField&&(!value.isIntegralNumber()||value.asLong()<0))fail("组织、岗位或期次条件需要核实后的编号。");
                if(dateField)try{LocalDate.parse(value.asText().substring(0,10));}catch(Exception e){fail("日期条件必须是有效日期。");}
                if(field.equals("internship.status")&&!Set.of("NOT_FOUND","NO_OFFER","PENDING_CONFIRMATION","CONFIRMED","IN_PROGRESS","ENDED","INTERRUPTED","DELAYED").contains(value.asText()))fail("该实习状态尚未接入，不能作为自动判断条件。");
            }
        }
    }
    @SuppressWarnings("unchecked")
    public static Truth evaluate(JsonNode n, Map<String, Object> facts, LocalDate today) {
        if (n.has("always")) return truth(n.path("always").asBoolean());
        for (String group : List.of("all", "any")) if (n.has(group)) {
            boolean unknown = false, all = group.equals("all");
            for (JsonNode child : n.path(group)) {
                Truth t = evaluate(child, facts, today);
                if (all && t == Truth.FALSE) return Truth.FALSE;
                if (!all && t == Truth.TRUE) return Truth.TRUE;
                unknown |= t == Truth.UNKNOWN;
            }
            return unknown ? Truth.UNKNOWN : truth(all);
        }
        if (n.has("not")) { Truth t = evaluate(n.path("not"), facts, today); return t == Truth.UNKNOWN ? t : truth(t == Truth.FALSE); }
        if (n.has("exists")) {
            Object records = facts.get(n.path("exists").asText());
            if (!(records instanceof List<?> values)) return Truth.UNKNOWN;
            boolean unknown = false;
            for (Object value : values) if (value instanceof Map<?, ?> row) {
                Truth t = evaluate(n.path("where"), (Map<String,Object>) row, today);
                if (t == Truth.TRUE) return t;
                unknown |= t == Truth.UNKNOWN;
            }
            return unknown ? Truth.UNKNOWN : Truth.FALSE;
        }
        String field = n.path("field").asText(), op = n.path("op").asText();
        if (!facts.containsKey(field)) return Truth.UNKNOWN;
        Object actual = facts.get(field); JsonNode expected = n.path("value");
        if (op.equals("EMPTY")) return truth(actual == null || actual.toString().isBlank());
        if (op.equals("NOT_EMPTY")) return truth(actual != null && !actual.toString().isBlank());
        if (actual == null || "UNKNOWN".equals(actual)) return Truth.UNKNOWN;
        try {
            if (DATE_FIELDS.contains(field)) actual = date(actual);
            if (op.equals("EQ") || op.equals("NE")) { boolean eq = same(actual, expected); return truth(op.equals("EQ") == eq); }
            if (op.equals("IN") || op.equals("NOT_IN")) { boolean found = false; for (JsonNode v : expected) found |= same(actual, v); return truth(op.equals("IN") == found); }
            if (op.equals("DAYS_UNTIL") || op.equals("DAYS_OVERDUE")) {
                long days = ChronoUnit.DAYS.between(today, date(actual)); if (op.equals("DAYS_OVERDUE")) days = -days;
                return truth(days >= expected.get(0).asLong() && days <= expected.get(1).asLong());
            }
            if (op.equals("BETWEEN")) return truth(compare(actual, expected.get(0)) >= 0 && compare(actual, expected.get(1)) <= 0);
            int cmp = compare(actual, expected);
            return truth(switch (op) { case "LT" -> cmp < 0; case "LTE" -> cmp <= 0; case "GT" -> cmp > 0; case "GTE" -> cmp >= 0; default -> false; });
        } catch (RuntimeException invalid) { return Truth.UNKNOWN; }
    }
    private static boolean same(Object value, JsonNode expected) {
        if (value instanceof LocalDate) return compare(value, expected) == 0;
        if (value instanceof Number && expected.isNumber()) return compare(value, expected) == 0;
        return String.valueOf(value).equalsIgnoreCase(expected.asText());
    }
    private static int compare(Object value, JsonNode expected) {
        if (value instanceof LocalDate d) return d.compareTo(date(expected.asText()));
        if (value instanceof Number || expected.isNumber()) return new java.math.BigDecimal(value.toString()).compareTo(new java.math.BigDecimal(expected.asText()));
        return value.toString().compareTo(expected.asText());
    }
    public static LocalDate date(Object value) {
        if (value instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
        if (value instanceof LocalDate d) return d;
        if (value instanceof LocalDateTime d) return d.toLocalDate();
        return LocalDate.parse(value.toString().substring(0, 10));
    }
    private static Truth truth(boolean b) { return b ? Truth.TRUE : Truth.FALSE; }
    public static void fail(String message) { throw new BusinessException(message); }
    public static ObjectNode leaf(ObjectMapper json, String field, String op, Object value) {
        ObjectNode n = json.createObjectNode().put("field", field).put("op", op); n.set("value", json.valueToTree(value)); return n;
    }
    public static String describe(JsonNode n) {
        if(n.path("op").asText().equals("EQ")&&n.path("value").isBoolean()) {
            String text=Map.of("appraisal.required","尚未提交鉴定表","appraisal.canSubmit","具备鉴定表提交资格","required","尚未提交","canSubmit","具备提交资格").get(n.path("field").asText());
            if(text!=null)return n.path("value").asBoolean()?text:"排除"+text+"的学生";
        }
        if (n.has("always")) return n.path("always").asBoolean() ? "所选学生" : "暂无符合对象";
        if (n.has("all") || n.has("any")) {
            List<String> parts = new ArrayList<>(); n.path(n.has("all") ? "all" : "any").forEach(c -> parts.add(describe(c)));
            return String.join(n.has("all") ? "，且" : "，或", parts);
        }
        if (n.has("not")) return "排除：" + describe(n.path("not"));
        if (n.has("exists")) return Map.of("applications","岗位申请","interviews","面试","confirmations","实习确认","reflections","心得","requests","业务申请").get(n.path("exists").asText()) + "中有" + describe(n.path("where"));
        String opName = n.path("op").asText();
        String field=n.path("field").asText();
        String label=FIELDS.getOrDefault(field,Map.ofEntries(Map.entry("dueDate","截止日期"),Map.entry("date","业务日期"),Map.entry("periodNumber","期次"),Map.entry("status","状态"),Map.entry("type","申请类型"),Map.entry("companyId","企业"),Map.entry("positionId","岗位"),Map.entry("companyName","企业名称"),Map.entry("positionName","岗位名称"),Map.entry("recallStatus","撤回状态")).getOrDefault(field,field));

        if (opName.equals("DAYS_UNTIL") || opName.equals("DAYS_OVERDUE")) {
            JsonNode val = n.path("value");
            long min = val.isArray() && val.size() > 0 ? val.get(0).asLong() : 0;
            long max = val.isArray() && val.size() > 1 ? val.get(1).asLong() : min;
            String range = min == max ? min + " 天" : min + " ~ " + max + " 天";
            return opName.equals("DAYS_UNTIL")
                    ? "距离" + label + "还剩 " + range
                    : "距离" + label + "已逾期 " + range;
        }
        if (opName.equals("BETWEEN")) {
            JsonNode val = n.path("value");
            String v0 = val.isArray() && val.size() > 0 ? val.get(0).asText() : "";
            String v1 = val.isArray() && val.size() > 1 ? val.get(1).asText() : "";
            return label + "范围为 " + v0 + " ~ " + v1;
        }

        String value = n.path("value").isArray() ? n.path("value").toString().replaceAll("[\\[\\]\"]","").replace(",","、") : n.path("value").asText();
        value = value.replace("NOT_FOUND", "暂无有效投递").replace("NO_OFFER", "已投递未录用").replace("PENDING_CONFIRMATION", "已录用待确认")
                .replace("IN_PROGRESS", "实习进行中").replace("CONFIRMED", "已定单位待实习").replace("ENDED", "实习已结束").replace("DELAYED", "已延期实习").replace("INTERRUPTED", "实习已中断").replace("true", "是").replace("false", "否");
        String op = switch (opName) { case "EQ" -> "为"; case "NE", "NOT_IN" -> "不为"; case "IN" -> "属于"; case "LT" -> "早于或小于"; case "LTE" -> "不晚于或不超过"; case "GT" -> "晚于或大于"; case "GTE" -> "不早于或不少于"; case "EMPTY" -> "未填写"; case "NOT_EMPTY" -> "已填写"; default -> "待完善"; };
        return label + op + value;
    }
}
