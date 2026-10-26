package com.internship.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.internship.entity.OperateLog;
import com.internship.entity.Result;
import com.internship.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

/** Only bounded diagnostics enter the audit payload, before its integrity hash is computed. */
public final class AuditFailureContext {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String UPLOAD_STAGE = AuditFailureContext.class.getName() + ".uploadStage";
    private static final Pattern INTERNAL = Pattern.compile(
            "(?is)(jdbc:|SQLSyntax|SQLGrammar|SQLIntegrity|Unknown column|SQL syntax|###|\\b(?:select|insert|update|delete|alter)\\s+.{0,100}\\b(?:from|into|set|table)\\b|\\bat\\s+[\\w.$]+\\([^)]*\\.java:\\d+\\)|(?:java|com|org)\\.[\\w.$]*Exception)");

    private AuditFailureContext() { }

    private static final String AUTH_CONTEXT = AuditFailureContext.class.getName() + ".authentication";
    private static final String RECORDED = AuditFailureContext.class.getName() + ".recorded";

    public static void beginAuthentication(String stage) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("validationStage", stage);
            attributes.getRequest().setAttribute(AUTH_CONTEXT, context);
        }
    }

    public static void authenticationFailure(String code, String reason) {
        authenticationFailure(code, reason, null);
    }

    public static void authenticationFailure(String code, String reason, Throwable cause) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(AUTH_CONTEXT) instanceof Map<?, ?> existing) {
            Map<String, Object> context = new LinkedHashMap<>();
            existing.forEach((key, value) -> context.put(String.valueOf(key), value));
            context.put("reasonCode", code);
            context.put("reason", sanitizeReason(reason));
            if (cause != null) context.put("exceptionType", exceptionType(cause));
            attributes.getRequest().setAttribute(AUTH_CONTEXT, context);
        }
    }

    public static boolean hasAuthenticationFailure() {
        return authenticationContext().containsKey("reasonCode");
    }

    public static String authenticationReasonCode() {
        Object code = authenticationContext().get("reasonCode");
        return code instanceof String value ? value : null;
    }

    public static String authenticationDetailJson() {
        if (!hasAuthenticationFailure()) return null;
        try {
            return JSON.writeValueAsString(Map.of("failure", authenticationContext()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> authenticationContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(AUTH_CONTEXT) instanceof Map<?, ?> existing) {
            existing.forEach((key, value) -> context.put(String.valueOf(key), value));
        }
        return context;
    }

    public static String exceptionType(Throwable cause) {
        Class<?> type = cause.getClass();
        return type.isAnonymousClass() ? type.getSuperclass().getSimpleName() : type.getSimpleName();
    }

    public static void markRecorded() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            attributes.getRequest().setAttribute(RECORDED, Boolean.TRUE);
        }
    }

    public static boolean isRecorded() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && Boolean.TRUE.equals(attributes.getRequest().getAttribute(RECORDED));
    }

    public static void uploadStage(String stage) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            attributes.getRequest().setAttribute(UPLOAD_STAGE, stage);
        }
    }

    public static void apply(OperateLog entry, Object result, Throwable thrown, Object[] arguments) {
        if (!"FAILURE".equals(entry.getOperationResult())) return;
        Map<String, Object> failure = new LinkedHashMap<>();
        String reason = "操作失败，请根据请求 ID 排查";
        if (thrown != null) {
            entry.setErrorCode(thrown instanceof AccessDeniedException ? "ACCESS_DENIED"
                    : thrown instanceof BusinessException ? "BUSINESS_EXCEPTION"
                    : thrown instanceof IllegalArgumentException ? "INVALID_ARGUMENT" : "EXCEPTION");
            failure.put("exceptionType", exceptionType(thrown));
            if (thrown instanceof BusinessException || thrown instanceof IllegalArgumentException) {
                reason = thrown.getMessage();
            } else if (thrown instanceof AccessDeniedException) {
                reason = "您没有访问权限";
            }
        } else if (result instanceof Result business) {
            entry.setErrorCode(String.valueOf(business.getCode()));
            reason = business.getMessage();
        } else if (result instanceof ResponseEntity<?> response) {
            entry.setErrorCode(String.valueOf(response.getStatusCode().value()));
            if (response.getBody() instanceof Result business) reason = business.getMessage();
        }
        failure.put("reason", sanitizeReason(reason));
        if (hasAuthenticationFailure()) failure.putAll(authenticationContext());
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object stage = attributes.getRequest().getAttribute(UPLOAD_STAGE);
            if (stage instanceof String value) failure.put("validationStage", value);
        }
        List<Map<String, Object>> files = new ArrayList<>();
        if (arguments != null) {
            for (Object argument : arguments) {
                if (argument instanceof MultipartFile file) addFile(files, file);
                else if (argument instanceof MultipartFile[] batch) {
                    for (MultipartFile file : batch) addFile(files, file);
                }
            }
        }
        if (!files.isEmpty()) failure.put("files", files);
        try {
            ObjectNode detail = JSON.createObjectNode();
            if (entry.getDetailJson() != null && JSON.readTree(entry.getDetailJson()) instanceof ObjectNode existing) {
                detail.setAll(existing);
            }
            detail.set("failure", JSON.valueToTree(failure));
            entry.setDetailJson(JSON.writeValueAsString(detail));
        } catch (Exception ignored) {
            // Keep diagnostic formatting independent of the original business result.
            entry.setDetailJson("{\"failure\":{\"reason\":\"操作失败，请根据请求 ID 排查\"}}");
        }
    }

    private static void addFile(List<Map<String, Object>> files, MultipartFile file) {
        if (file == null || files.size() >= 10) return;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("size", file.getSize());
        String type = file.getContentType();
        meta.put("declaredMime", type != null && type.matches("[A-Za-z0-9.+_-]+/[A-Za-z0-9.+_-]+")
                && type.length() <= 128 ? type : "unknown");
        String name = file.getOriginalFilename();
        String extension = name != null && name.lastIndexOf('.') >= 0
                ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        meta.put("extension", extension.matches("[a-z0-9]{1,12}") ? extension : "unknown");
        files.add(meta);
    }

    public static String sanitizeReason(String message) {
        if (message == null || message.isBlank()) return "操作失败，请根据请求 ID 排查";
        String value = message.substring(0, Math.min(message.length(), 4096));
        if (INTERNAL.matcher(value).find()) return "操作失败，请根据请求 ID 排查";
        value = value.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer [已脱敏]")
                .replaceAll("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", "[令牌已脱敏]")
                .replaceAll("(?i)(password|passwd|pwd|(?:access|refresh)[_-]?token|token|secret|authorization|api[_-]?key|密码|验证码)[\\\"']?\\s*[:=：]\\s*[\\\"']?[^\\s,;\\\"'，；}&]+", "$1=[已脱敏]")
                .replaceAll("https?://[^\\s<>\\\"']+", "[链接已隐藏]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[邮箱已脱敏]")
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "[手机号已脱敏]")
                .replaceAll("(?<!\\d)\\d{17}[0-9Xx](?!\\d)", "[身份证号已脱敏]");
        return value.substring(0, Math.min(value.length(), 512));
    }
}
