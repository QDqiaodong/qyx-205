package com.coldchain.exception;

import com.coldchain.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
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
     * 唯一约束冲突：典型场景一是两个并发请求把同一个托盘号同时落架，后到者撞 pallet_active 主键；
     * 二是两笔并发绑定系统中尚不存在的同一货位编码，后到者撞 location_code.code 唯一约束。
     * 冲突方整笔回滚，先前已提交的绑定/落架和流水保持不变，翻译为业务提示而不是 500。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ApiResponse.error(409, "并发操作冲突：该编码或托盘可能已被另一笔操作登记，本笔失败，现状保持不变");
    }

    /**
     * InnoDB 死锁（MySQL 1213）：极端竞争下被选为牺牲者的事务收到该异常，整笔回滚不留半成品。
     */
    @ExceptionHandler(DeadlockLoserDataAccessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleDeadlock(DeadlockLoserDataAccessException e) {
        log.warn("Deadlock loser: {}", e.getMostSpecificCause().getMessage());
        return ApiResponse.error(409, "并发操作冲突：同一编码正被另一笔绑定/重分配，请确认现状后重试，本笔未生效");
    }

    /**
     * 行锁等待超时（同架/同编码并发竞争激烈）。回滚后让用户确认现状并重试。
     */
    @ExceptionHandler(CannotAcquireLockException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleLockTimeout(CannotAcquireLockException e) {
        log.warn("Lock acquisition failed: {}", e.getMessage());
        return ApiResponse.error(409, "该货架/编码正有另一笔操作进行中，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected exception: ", e);
        return ApiResponse.error(500, "系统内部错误");
    }
}
