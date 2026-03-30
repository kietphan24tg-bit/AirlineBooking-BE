package com.example.appbootstrap.exception;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
    boolean success,
    ApiError error,
    int statusCode,
    OffsetDateTime timestamp,
    String path,
    String requestId) {}
