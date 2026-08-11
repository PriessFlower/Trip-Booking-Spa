package com.trip.booking.spa.bff.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 只拦截 bff 包的控制器，避免影响 rest 包既有行为 */
@Slf4j
@RestControllerAdvice(basePackages = "com.trip.booking.spa.bff")
public class BffExceptionHandler {

    @ExceptionHandler(BffException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BffException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(Map.of("error", Map.of("status", e.getHttpStatus(), "message", e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("bff 未预期异常", e);
        return ResponseEntity.status(500)
                .body(Map.of("error", Map.of("status", 500, "message", "服务内部错误，请稍后重试")));
    }
}
