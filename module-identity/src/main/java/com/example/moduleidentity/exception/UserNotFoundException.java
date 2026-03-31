package com.example.moduleidentity.exception;

import com.example.common.exception.AppException;
import java.util.Map;

public class UserNotFoundException extends AppException {

  public UserNotFoundException(Long userId) {
    super("USER_NOT_FOUND", "User not found", 404, Map.of("userId", userId));
  }

  public UserNotFoundException(String email) {
    super("USER_NOT_FOUND", "User not found", 404, Map.of("email", email));
  }
}
