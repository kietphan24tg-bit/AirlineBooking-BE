package com.example.moduleai.tool.core;

public record ToolResult<T>(String tool, ToolResultStatus status, T data, ToolError error) {

  public static <T> ToolResult<T> success(String tool, T data) {
    return new ToolResult<>(tool, ToolResultStatus.SUCCESS, data, null);
  }

  public static <T> ToolResult<T> failure(String tool, ToolError error) {
    return new ToolResult<>(tool, ToolResultStatus.FAILED, null, error);
  }

  public static <T> ToolResult<T> confirmationRequired(String tool, String message) {
    return new ToolResult<>(
        tool,
        ToolResultStatus.CONFIRMATION_REQUIRED,
        null,
        ToolError.forbidden("CONFIRMATION_REQUIRED", message));
  }
}
