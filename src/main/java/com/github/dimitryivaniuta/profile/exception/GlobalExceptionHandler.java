package com.github.dimitryivaniuta.profile.exception;

import com.github.dimitryivaniuta.profile.api.ErrorResponse;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

/**
 * Centralized exception mapping for API responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles missing resources.
     *
     * @param exception not-found exception
     * @param exchange current request exchange
     * @return 404 error response
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception, ServerWebExchange exchange) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), exchange);
    }

    /**
     * Handles state conflicts.
     *
     * @param exception conflict exception
     * @param exchange current request exchange
     * @return 409 error response
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception, ServerWebExchange exchange) {
        return error(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), exchange);
    }

    /**
     * Handles write attempts in non-primary regions.
     *
     * @param exception write-not-allowed exception
     * @param exchange current request exchange
     * @return 409 error response
     */
    @ExceptionHandler(WriteNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleWriteNotAllowed(WriteNotAllowedException exception, ServerWebExchange exchange) {
        return error(HttpStatus.CONFLICT, "WRITE_NOT_ALLOWED", exception.getMessage(), exchange);
    }

    /**
     * Handles a regional read model that has not reached the caller's requested version yet.
     *
     * @param exception consistency-lag exception
     * @param exchange current request exchange
     * @return 409 error response
     */
    @ExceptionHandler(ConsistencyLagException.class)
    public ResponseEntity<ErrorResponse> handleConsistencyLag(ConsistencyLagException exception, ServerWebExchange exchange) {
        return error(HttpStatus.CONFLICT, "READ_MODEL_BEHIND", exception.getMessage(), exchange);
    }

    /**
     * Handles bean validation failures.
     *
     * @param exception validation exception
     * @param exchange current request exchange
     * @return 400 error response
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(WebExchangeBindException exception, ServerWebExchange exchange) {
        String message = exception.getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, exchange);
    }

    /**
     * Handles unexpected failures with a safe generic message.
     *
     * @param exception exception
     * @param exchange current request exchange
     * @return 500 error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, ServerWebExchange exchange) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected service error", exchange);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, ServerWebExchange exchange) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, exchange.getRequest().getPath().value(), Instant.now()));
    }
}
