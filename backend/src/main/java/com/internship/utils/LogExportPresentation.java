package com.internship.utils;

import java.util.Map;
import java.util.Locale;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class LogExportPresentation {
    private LogExportPresentation() {}
    private static final Map<String,String> ROLE = Map.ofEntries(
            Map.entry("STUDENT","学生"),
            Map.entry("TEACHER","教师"),
            Map.entry("ADMIN","管理员"),
            Map.entry("COMPANY","企业"),
            Map.entry("SYSTEM","系统"),
            Map.entry("UNKNOWN","未知"),
            Map.entry("TEACHER_COLLEGE","学院教师"),
            Map.entry("TEACHER_DEPARTMENT","系室教师"),
            Map.entry("TEACHER_COUNSELOR","辅导员"),
            Map.entry("管理员切换","管理员切换"));
    private static final Map<String,String> RISK = Map.ofEntries(
            Map.entry("LOW","低风险"),
            Map.entry("MEDIUM","中风险"),
            Map.entry("HIGH","高风险"),
            Map.entry("CRITICAL","严重"));
    private static final Map<String,String> EVENT = Map.ofEntries(
            Map.entry("LOGIN_SUCCESS","登录成功"),
            Map.entry("LOGIN_FAILURE","登录失败"),
            Map.entry("ACCOUNT_LOCK","账号锁定"),
            Map.entry("LOGOUT","登出"),
            Map.entry("SESSION_TIMEOUT","会话超时"),
            Map.entry("ACCOUNT_DISABLED","账号禁用"),
            Map.entry("TOKEN_INVALID","无效令牌"),
            Map.entry("RETENTION_CLEAN","留存清理"),
            Map.entry("AUDIT_ARCHIVE","审计日志归档"),
            Map.entry("PASSWORD_RESET","密码重置"),
            Map.entry("AUDIT_WRITE_FAILURE","审计写入失败"),
            Map.entry("AUDIT_INTEGRITY_FAILURE","审计完整性异常"),
            Map.entry("AUDIT_RETENTION_FAILURE","日志清理失败"),
            Map.entry("REQUEST_FAILURE","请求处理失败"),
            Map.entry("AUTH_SERVICE_FAILURE","登录服务异常"));
    private static final Map<String,String> ACTION = Map.ofEntries(
            Map.entry("SEND_CODE","发送验证码"),
            Map.entry("CREATE","新增"),
            Map.entry("UPDATE","修改"),
            Map.entry("DELETE","删除"),
            Map.entry("READ","查询"),
            Map.entry("IMPORT","导入"),
            Map.entry("EXPORT","导出"),
            Map.entry("UPLOAD","上传"),
            Map.entry("AUDIT","审核"),
            Map.entry("CLEAN","清理"),
            Map.entry("LOGIN","登录"),
            Map.entry("LOGOUT","登出"),
            Map.entry("SWITCH_ROLE","切换角色"),
            Map.entry("BACKUP","备份"),
            Map.entry("RESTORE","恢复"),
            Map.entry("RESET_PASSWORD","重置密码"),
            Map.entry("ASSIGN","分配"),
            Map.entry("DOWNLOAD","下载"),
            Map.entry("VERIFY","验证"),
            Map.entry("ADD","新增"),
            Map.entry("INSERT","新增"),
            Map.entry("MODIFY","修改"),
            Map.entry("EDIT","修改"),
            Map.entry("REMOVE","删除"),
            Map.entry("QUERY","查询"),
            Map.entry("SELECT","查询"),
            Map.entry("VIEW","查看"),
            Map.entry("CREATE_","创建"));
    private static final Map<String,String> MODULE = Map.ofEntries(
            Map.entry("AUTH","认证管理"),
            Map.entry("USER_MANAGEMENT","用户管理"),
            Map.entry("STUDENT_MANAGEMENT","学生管理"),
            Map.entry("TEACHER_MANAGEMENT","教师管理"),
            Map.entry("CLASS_MANAGEMENT","班级管理"),
            Map.entry("MAJOR_MANAGEMENT","专业管理"),
            Map.entry("DEPARTMENT_MANAGEMENT","院系管理"),
            Map.entry("COMPANY_MANAGEMENT","企业管理"),
            Map.entry("POSITION_MANAGEMENT","岗位管理"),
            Map.entry("ANNOUNCEMENT_MANAGEMENT","公告管理"),
            Map.entry("INTERNSHIP_MANAGEMENT","实习管理"),
            Map.entry("STUDENT_INTERNSHIP","学生实习"),
            Map.entry("INTERNSHIP_REFLECTION","实习心得"),
            Map.entry("INTERNSHIP_REPORT","实习报告"),
            Map.entry("LOG_MANAGEMENT","日志管理"),
            Map.entry("SYSTEM_SETTINGS","系统设置"),
            Map.entry("PERMISSION","权限管理"),
            Map.entry("AI_MODEL","AI模型管理"),
            Map.entry("FILE_ACCESS","文件访问"));
    private static final Map<String,String> RESULT = Map.ofEntries(
            Map.entry("SUCCESS","成功"),
            Map.entry("FAILURE","失败"),
            Map.entry("ATTEMPTED","尝试"));
    private static final Map<String,String> WORDS = Map.ofEntries(
            Map.entry("user","用户"),
            Map.entry("student","学生"),
            Map.entry("teacher","教师"),
            Map.entry("management","管理"),
            Map.entry("company","企业"),
            Map.entry("internship","实习"),
            Map.entry("log","日志"),
            Map.entry("system","系统"),
            Map.entry("permission","权限"),
            Map.entry("announcement","公告"),
            Map.entry("position","岗位"),
            Map.entry("data","数据"),
            Map.entry("export","导出"),
            Map.entry("backup","备份"),
            Map.entry("settings","设置"),
            Map.entry("config","配置"),
            Map.entry("resource","资源"),
            Map.entry("document","文档"),
            Map.entry("application","申请"),
            Map.entry("evaluation","评分"),
            Map.entry("reflection","心得"),
            Map.entry("report","报告"),
            Map.entry("visit","走访"),
            Map.entry("plan","计划"),
            Map.entry("keyword","关键词"),
            Map.entry("library","库"),
            Map.entry("scoring","评分"),
            Map.entry("rule","规则"),
            Map.entry("chat","聊天"),
            Map.entry("model","模型"),
            Map.entry("feedback","反馈"),
            Map.entry("problem","问题"),
            Map.entry("archive","档案"),
            Map.entry("template","模板"),
            Map.entry("withdrawal","撤回"),
            Map.entry("record","记录"),
            Map.entry("chat_management","聊天"));
    public static String role(String value) {
        if (value == null || value.isEmpty()) return "未知";
        String key = value.startsWith("ROLE_") ? value.substring(5) : value;
        return ROLE.getOrDefault(key,key);
    }
    public static String risk(String value) { return value == null || value.isEmpty() ? "" : RISK.getOrDefault(value,value); }
    public static String event(String value) { return value == null || value.isEmpty() ? "" : EVENT.getOrDefault(value,value); }
    public static String action(String value) { return value == null || value.isEmpty() ? "" : ACTION.getOrDefault(value,value); }
    public static String result(String value) { return value == null || value.isEmpty() ? "未知结果" : RESULT.getOrDefault(value,value); }
    public static String module(String value) {
        if (value == null || value.isEmpty()) return "";
        if (MODULE.containsKey(value)) return MODULE.get(value);
        return Arrays.stream(value.split("_")).map(v -> v.toLowerCase(Locale.ROOT)).map(v -> WORDS.getOrDefault(v,v)).collect(Collectors.joining());
    }
}
