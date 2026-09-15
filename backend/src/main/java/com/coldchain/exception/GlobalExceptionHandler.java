package com.coldchain.exception;

import com.coldchain.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation exception: {}", message);
        return ApiResponse.error(message);
    }

    /**
     * 唯一约束冲突：典型场景是两个并发请求把同一个托盘号同时落架，
     * 后到者撞 pallet_active 主键，整笔回滚。翻译为业务提示而不是 500。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ApiResponse.error(409, "并发操作冲突：该托盘可能已被另一笔登记落架，本笔失败，在架状态保持不变");
    }

    /**
     * 行锁等待超时（同架并发竞争激烈）。回滚后让用户稍后重试。
     */
    @ExceptionHandler(CannotAcquireLockException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleLockTimeout(CannotAcquireLockException e) {
        log.warn("Lock acquisition failed: {}", e.getMessage());
        return ApiResponse.error(409, "当前货架正有另一笔落架/下架操作，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected exception: ", e);
        return ApiResponse.error(500, "系统内部错误");
    }
}
