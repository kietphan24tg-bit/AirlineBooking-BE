package com.example.common.exception;

import java.util.Map;

public class AppException extends RuntimeException {

  private final String code;
  private final int statusCode;
  private final Map<String, Object> details;

  public AppException(String code, String message, int statusCode) {
    this(code, message, statusCode, Map.of());
  }

  public AppException(
      String code, String message, int statusCode, Map<String, Object> details) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
    this.details = details;
  }

  public String getCode() {
    return code;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public Map<String, Object> getDetails() {
    return details;
  }
}
