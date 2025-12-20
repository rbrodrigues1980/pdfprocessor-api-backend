package br.com.verticelabs.pdfprocessor.interfaces.exception;

import br.com.verticelabs.pdfprocessor.domain.exceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.validation.FieldError;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            PersonNotFoundException.class,
            DocumentNotFoundException.class,
            TenantNotFoundException.class,
            UserNotFoundException.class,
            RubricaNotFoundException.class,
            NoEntriesFoundException.class
    })
    public Mono<ResponseEntity<ApiErrorResponse>> handleNotFoundException(RuntimeException ex,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (response.isCommitted()) {
            return Mono.empty();
        }
        return Mono.just(createErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request));
    }

    @ExceptionHandler({
            InvalidCpfException.class,
            InvalidPdfException.class,
            InvalidYearException.class,
            InvalidOriginException.class,
            ExcelGenerationException.class,
            IllegalArgumentException.class
    })
    public Mono<ResponseEntity<ApiErrorResponse>> handleBadRequestException(RuntimeException ex,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (response.isCommitted()) {
            return Mono.empty();
        }
        return Mono.just(createErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler({
            InvalidCredentialsException.class,
            InvalidRefreshTokenException.class,
            Invalid2FACodeException.class
    })
    public Mono<ResponseEntity<ApiErrorResponse>> handleUnauthorizedException(RuntimeException ex,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (response.isCommitted()) {
            return Mono.empty();
        }
        return Mono.just(createErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request));
    }

    @ExceptionHandler({
            PersonDuplicadaException.class,
            DocumentoDuplicadoException.class,
            RubricaDuplicadaException.class,
            InvalidStatusTransitionException.class
    })
    public Mono<ResponseEntity<ApiErrorResponse>> handleConflictException(RuntimeException ex,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (response.isCommitted()) {
            return Mono.empty();
        }
        return Mono.just(createErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleValidationException(WebExchangeBindException ex,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (response.isCommitted()) {
            return Mono.empty();
        }
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message("Validation error")
                        .path(request.getPath().value())
                        .errors(errors)
                        .build()));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleGlobalException(Exception ex, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (response.isCommitted()) {
            log.warn("❌ Response already committed. Ignoring error: {}", ex.getMessage());
            return Mono.empty();
        }
        log.error("❌ Unexpected error occurred", ex);
        return Mono.just(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred: " + ex.getMessage(),
                request));
    }

    private ResponseEntity<ApiErrorResponse> createErrorResponse(HttpStatus status, String message,
            ServerHttpRequest request) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(status.value())
                        .error(status.getReasonPhrase())
                        .message(message)
                        .path(request.getPath().value())
                        .build());
    }
}
