package com.example.moduleweb.exception;

import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> details) {}
