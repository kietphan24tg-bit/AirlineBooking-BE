package com.example.moduleai.tool.core;

public record ToolPolicy(
    String tool,
    ToolOperation operation,
    ToolAccess access,
    boolean confirmationRequired,
    boolean idempotencyRequired) {}
