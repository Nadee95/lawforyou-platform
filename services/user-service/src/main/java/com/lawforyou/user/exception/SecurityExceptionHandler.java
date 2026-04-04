package com.lawforyou.user.exception;

import com.nadeex.spring.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Handles Spring Security access-control exceptions that would otherwise be
 * swallowed by the generic {@code Exception} handler in
 * {@link com.nadeex.spring.exception.handler.GlobalExceptionHandler}.
 *
 * <p>Spring MVC resolves the <em>most-specific</em> exception handler across all
 * {@code @ControllerAdvice} beans, so this handler wins over the catch-all
 * {@code handleGeneric(Exception)} for any {@code AccessDeniedException} or
 * {@code AuthorizationDeniedException} thrown by {@code @PreAuthorize}.</p>
 *
 * <p>Note: this handler does NOT interfere with the Spring Security filter chain
 * returning 401 for unauthenticated requests — that path never reaches MVC.</p>
 */
@Slf4j
@RestControllerAdvice
@Order(-1)   // higher priority than GlobalExceptionHandler (which has no explicit order → Integer.MAX_VALUE)
public class SecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied to {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse body = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "You do not have permission to perform this action",
                request.getRequestURI(),
                Instant.now(),
                "FORBIDDEN",
                null);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }
}

