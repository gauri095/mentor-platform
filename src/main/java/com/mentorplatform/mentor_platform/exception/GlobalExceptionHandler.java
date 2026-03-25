package com.mentorplatform.mentor_platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception → HTTP response translation.
 *
 *  400  MethodArgumentNotValidException  — @Valid failure, field-level errors
 *  401  BadCredentialsException          — wrong email or password
 *  401  UsernameNotFoundException        — safety net
 *  403  SecurityException               — caller doesn't own the session
 *  404  IllegalArgumentException         — session/user not found
 *  409  IllegalStateException            — duplicate session, wrong state
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value.",
                        (first, second) -> first,
                        LinkedHashMap::new));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",    400);
        body.put("error",     "Validation failed.");
        body.put("fields",    fieldErrors);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UsernameNotFoundException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    /** SessionService throws this when a mentor tries to end a session they didn't create. */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleOwnership(SecurityException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /** Session/user not found → 404. Duplicate email → 409 (detected by message content). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("already registered")) {
            return error(HttpStatus.CONFLICT, ex.getMessage());
        }
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Duplicate open session, wrong-state transition, session already ended → 409. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status",    status.value(),
                "error",     message != null ? message : "An unexpected error occurred.",
                "timestamp", Instant.now().toString()));
    }
}