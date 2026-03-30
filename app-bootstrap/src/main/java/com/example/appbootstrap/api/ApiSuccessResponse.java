package com.example.appbootstrap.api;

public record ApiSuccessResponse<T>(boolean success, T data, String message) {

  public static <T> ApiSuccessResponse<T> of(T data, String message) {
    return new ApiSuccessResponse<>(true, data, message);
  }
}
