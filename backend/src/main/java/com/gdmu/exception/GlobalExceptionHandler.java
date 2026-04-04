package com.gdmu.exception;

import com.gdmu.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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
}