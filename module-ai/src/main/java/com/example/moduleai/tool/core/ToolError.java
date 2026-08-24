package com.example.moduleai.tool.core;

public record ToolError(String type, String code, String message) {

  public static ToolError validation(String code, String message) {
    return new ToolError("validation", code, message);
  }

  public static ToolError unauthorized(String message) {
    return new ToolError("unauthorized", "UNAUTHORIZED", message);
  }

  public static ToolError forbidden(String code, String message) {
    return new ToolError("forbidden", code, message);
  }

  public static ToolError notFound(String code, String message) {
    return new ToolError("not_found", code, message);
  }

  public static ToolError conflict(String code, String message) {
    return new ToolError("conflict", code, message);
  }

  public static ToolError unavailable(String code, String message) {
    return new ToolError("unavailable", code, message);
  }
}
