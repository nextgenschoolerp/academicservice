package com.kvs.academic.exception;

import com.kvs.core.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Maps malformed-request exceptions to 400/406/413 responses. Without this, the core
 * GlobalExceptionHandler treats them as unhandled and answers 500.
 * Runs before the core handler; domain exceptions still go to the core handler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class AcademicRequestExceptionHandler {

    private static final String TRACE_HEADER = "X-Trace-Id";

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing or contains an invalid value", request, response);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiErrorResponse> handleMissingInput(Exception ex,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), request, response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Invalid value for parameter '" + ex.getName() + "'", request, response);
    }

    /** A file endpoint (faculty timetable PDF, asset content) asked for with an Accept header it cannot produce, e.g. only application/json. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                                HttpServletRequest request,
                                                                HttpServletResponse response) {
        return build(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                "This endpoint returns " + ex.getSupportedMediaTypes() + "; send a matching Accept header", request, response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                                 HttpServletRequest request,
                                                                 HttpServletResponse response) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "Uploaded file exceeds the maximum allowed size", request, response);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipart(MultipartException ex,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request must be multipart/form-data with a file part", request, response);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String errorCode, String message,
                                                   HttpServletRequest request, HttpServletResponse response) {
        log.warn("Rejected request {} {}: {}", request.getMethod(), request.getRequestURI(), message);
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId != null && !response.containsHeader(TRACE_HEADER)) {
            response.setHeader(TRACE_HEADER, traceId);
        }
        ApiErrorResponse body = ApiErrorResponse.builder()
                .success(false)
                .status(status.value())
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
