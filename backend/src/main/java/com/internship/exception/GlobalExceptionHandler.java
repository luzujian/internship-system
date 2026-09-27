package com.internship.exception;

import com.internship.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(com.internship.agent.business.AgentException.class)
    public ResponseEntity<java.util.Map<String,Object>> handleAgentException(com.internship.agent.business.AgentException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(java.util.Map.of(
                "code",e.getHttpStatus(),"errorCode",e.getCode(),"message",e.getMessage()));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Result> handleUsernameNotFoundException(UsernameNotFoundException e) {
        log.error("用户名不存在: {}", e.getMessage());
        Result result = Result.error("用户名或密码错误");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result> handleBadCredentialsException(BadCredentialsException e) {
        log.error("密码错误: {}", e.getMessage());
        Result result = Result.error("用户名或密码错误");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Result> handleDisabledException(DisabledException e) {
        log.error("账号已禁用: {}", e.getMessage());
        Result result = Result.error("账号已被禁用，请联系管理员");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Result> handleLockedException(LockedException e) {
        log.error("账号已锁定: {}", e.getMessage());
        Result result = Result.error("账号已被锁定，请联系管理员");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    @ExceptionHandler(BackupException.class)
    public ResponseEntity<Result> handleBackupException(BackupException e) {
        log.error("备份操作异常: {}", e.getMessage(), e);
        Result result = Result.error(e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleValidationException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.error("参数验证失败: {}", errorMsg);
        Result result = Result.error("参数验证失败: " + errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result> handleBindException(BindException e) {
        String errorMsg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.error("参数绑定失败: {}", errorMsg);
        Result result = Result.error("参数绑定失败: " + errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("非法参数异常: {}", e.getMessage());
        Result result = Result.error("非法参数: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过系统允许的大小: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error("单个文件不能超过20MB"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体不可读: {}", e.getMessage());
        Result result = Result.error("请求参数格式错误，请检查后重试");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }
    // P3：教师端 Long 内部主键（如 studentId）收到学号等非数字字符串时，
    // Spring 在方法入参前抛 MethodArgumentTypeMismatchException；必须明确 400，
    // 不得落入兜底 500。Long/Integer 等数字类型统一提示“应为数字”。
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String name = e.getName() == null ? "请求参数" : e.getName();
        Class<?> requiredType = e.getRequiredType();
        String expected = (requiredType != null && Number.class.isAssignableFrom(requiredType)) ? "数字" : "合法值";
        log.warn("请求参数类型错误: {} 应为{}，实际收到 {}", name, expected, e.getValue());
        Result result = Result.error("参数 " + name + " 格式错误：应为" + expected + "，请检查后重试");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage());
        Result result = Result.error(e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("拒绝无权限操作: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new Result(403, "您没有访问权限", null));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result> handleDuplicateKeyException(DuplicateKeyException e) {
        log.error("数据库唯一约束冲突: {}", e.getMessage());
        String msg = extractDuplicateMessage(e.getMessage());
        Result result = Result.error(msg != null ? msg : "数据已存在，请勿重复提交");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.error("数据完整性异常: {}", e.getMessage());
        // 优先识别重复键场景
        String msg = e.getMessage() == null ? null : extractDuplicateMessage(e.getMessage());
        if (msg == null) {
            msg = "数据操作失败，请检查数据完整性后重试";
        }
        Result result = Result.error(msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);
        Result result = Result.error("操作失败: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        Result result = Result.error("系统错误，请联系管理员");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 从数据库异常消息中识别唯一键冲突，返回友好的中文提示；无法识别时返回 null。
     * 典型消息（MySQL）：Duplicate entry 'xxx' for key 'xxx.uk_xxx'  /  Duplicate entry 'xxx' for key 'PRIMARY'
     */
    private static String extractDuplicateMessage(String message) {
        if (message == null) {
            return null;
        }
        String lower = message.toLowerCase();
        if (!lower.contains("duplicate entry")) {
            return null;
        }
        // 尝试提取冲突的字段名（for key 'table.keyName' 中的 keyName）
        String keyName = "";
        int forKeyIdx = lower.indexOf("for key");
        if (forKeyIdx >= 0) {
            int quote1 = message.indexOf('\'', forKeyIdx);
            if (quote1 >= 0) {
                int quote2 = message.indexOf('\'', quote1 + 1);
                if (quote2 > quote1) {
                    String rawKey = message.substring(quote1 + 1, quote2);
                    // 取最后一段作为键名（可能是 db.table.key 格式）
                    int dot = rawKey.lastIndexOf('.');
                    keyName = dot >= 0 ? rawKey.substring(dot + 1) : rawKey;
                }
            }
        }
        // 根据常见键名映射友好提示
        String km = keyName.toLowerCase();
        if (km.contains("student_id") || km.contains("studentid")) {
            return "学号已存在，请修改";
        }
        if (km.contains("teacher_user_id") || km.contains("teacheruserid") || (km.contains("teacher") && km.contains("id"))) {
            return "工号已存在，请修改";
        }
        if (km.contains("username")) {
            return "登录账号已存在，请修改";
        }
        if (km.contains("name")) {
            return "名称已存在，请修改";
        }
        if (km.contains("phone") || km.contains("mobile")) {
            return "手机号已被使用，请修改";
        }
        if (km.contains("email")) {
            return "邮箱已被使用，请修改";
        }
        if (km.isEmpty() || "primary".equals(km)) {
            return "数据已存在，请修改";
        }
        return "数据已存在（字段：" + keyName + "），请修改";
    }
}
