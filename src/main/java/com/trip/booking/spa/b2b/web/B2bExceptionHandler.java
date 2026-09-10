package com.trip.booking.spa.b2b.web;

import com.trip.booking.spa.bff.web.BffException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 只拦截 b2b 包的控制器，形状与 {@code BffExceptionHandler} 一致。
 *
 * <p>{@link BffException} 必须在此单独接住：advice 按<b>控制器</b>所在包生效，
 * 而本包的取数与下单调的是 bff 的服务，它抛的是 BffException——不接的话
 * 「订单不存在」这类 404 会被兜成 500，前端分不清是自己传错还是服务坏了。
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.trip.booking.spa.b2b")
public class B2bExceptionHandler {

    @ExceptionHandler(B2bException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(B2bException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(Map.of("error", Map.of("status", e.getHttpStatus(), "message", e.getMessage())));
    }

    @ExceptionHandler(BffException.class)
    public ResponseEntity<Map<String, Object>> handleReused(BffException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(Map.of("error", Map.of("status", e.getHttpStatus(), "message", e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("b2b 未预期异常", e);
        return ResponseEntity.status(500)
                .body(Map.of("error", Map.of("status", 500, "message", "服务内部错误，请稍后重试")));
    }
}
