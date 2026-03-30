package com.example.appbootstrap.exception;

import com.example.common.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice(basePackages = "com.example")
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String DEFAULT_REQUEST_ID = "N/A";

  @ExceptionHandler(AppException.class)
  ResponseEntity<ApiErrorResponse> handleAppException(
      AppException exception, HttpServletRequest request) {
    log.warn(
        "Business exception: code={}, path={}, requestId={}, message={}",
        exception.getCode(),
        request.getRequestURI(),
        currentRequestId(),
        exception.getMessage());
    return buildErrorResponse(
        HttpStatusCode.valueOf(exception.getStatusCode()),
        exception.getCode(),
        exception.getMessage(),
        exception.getDetails(),
        request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    Map<String, Object> details = new LinkedHashMap<>();
    for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
      details.put(fieldError.getField(), fieldError.getDefaultMessage());
    }

    log.warn(
        "Validation failed: path={}, requestId={}, details={}",
        request.getRequestURI(),
        currentRequestId(),
        details);
    return buildErrorResponse(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Validation failed",
        details,
        request);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(
      HandlerMethodValidationException exception, HttpServletRequest request) {
    Map<String, Object> details =
        exception.getParameterValidationResults().stream()
            .collect(
                Collectors.toMap(
                    result -> result.getMethodParameter().getParameterName(),
                    result ->
                        result.getResolvableErrors().stream()
                            .map(error -> error.getDefaultMessage())
                            .filter(message -> message != null && !message.isBlank())
                            .toList(),
                    (left, right) -> right,
                    LinkedHashMap::new));

    log.warn(
        "Method validation failed: path={}, requestId={}, details={}",
        request.getRequestURI(),
        currentRequestId(),
        details);
    return buildErrorResponse(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Validation failed",
        details,
        request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiErrorResponse> handleConstraintViolation(
      ConstraintViolationException exception, HttpServletRequest request) {
    Map<String, Object> details = new LinkedHashMap<>();
    for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
      details.put(violation.getPropertyPath().toString(), violation.getMessage());
    }

    log.warn(
        "Constraint violation: path={}, requestId={}, details={}",
        request.getRequestURI(),
        currentRequestId(),
        details);
    return buildErrorResponse(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Validation failed",
        details,
        request);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    HttpRequestMethodNotSupportedException.class
  })
  ResponseEntity<ApiErrorResponse> handleHttpRequestException(
      Exception exception, HttpServletRequest request) {
    HttpStatus status =
        exception instanceof HttpRequestMethodNotSupportedException
            ? HttpStatus.METHOD_NOT_ALLOWED
            : HttpStatus.BAD_REQUEST;

    log.warn(
        "HTTP request error: path={}, requestId={}, message={}",
        request.getRequestURI(),
        currentRequestId(),
        exception.getMessage());
    return buildErrorResponse(
        status, "BAD_REQUEST", "Request could not be processed", Map.of(), request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiErrorResponse> handleNoResourceFound(
      NoResourceFoundException exception, HttpServletRequest request) {
    log.warn(
        "Resource not found: path={}, requestId={}",
        request.getRequestURI(),
        currentRequestId());
    return buildErrorResponse(
        HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", Map.of(), request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiErrorResponse> handleAccessDenied(
      AccessDeniedException exception, HttpServletRequest request) {
    log.warn(
        "Access denied: path={}, requestId={}",
        request.getRequestURI(),
        currentRequestId());
    return buildErrorResponse(
        HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", Map.of(), request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> handleUnhandledException(
      Exception exception, HttpServletRequest request) {
    log.error(
        "Unhandled system error: path={}, requestId={}",
        request.getRequestURI(),
        currentRequestId(),
        exception);
    return buildErrorResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_SERVER_ERROR",
        "An unexpected system error occurred",
        Map.of(),
        request);
  }

  private ResponseEntity<ApiErrorResponse> buildErrorResponse(
      HttpStatusCode status,
      String code,
      String message,
      Map<String, Object> details,
      HttpServletRequest request) {
    ApiErrorResponse response =
        new ApiErrorResponse(
            false,
            new ApiError(code, message, details),
            status.value(),
            OffsetDateTime.now(),
            request.getRequestURI(),
            currentRequestId());
    return ResponseEntity.status(status).body(response);
  }

  private String currentRequestId() {
    return MDC.get("requestId") != null ? MDC.get("requestId") : DEFAULT_REQUEST_ID;
  }
}
