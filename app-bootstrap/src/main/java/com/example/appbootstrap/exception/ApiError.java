package com.example.appbootstrap.exception;

import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> details) {}
