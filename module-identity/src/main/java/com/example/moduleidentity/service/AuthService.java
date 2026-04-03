package com.example.moduleidentity.service;

import com.example.common.exception.AppException;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.repository.UserRepository;
import java.time.OffsetDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public UserEntity register(String email, String password, String firstName, String lastName) {
    if (userRepository.existsByEmail(email)) {
      throw new AppException("EMAIL_ALREADY_EXISTS", "Email is already registered", 409);
    }

    UserEntity user = new UserEntity();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setVerify(true); // Mặc định true, sau có thể thêm email verification
    user.setCreatedAt(OffsetDateTime.now());
    user.setUpdatedAt(OffsetDateTime.now());

    return userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public UserEntity findByEmail(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(
            () -> new AppException("USER_NOT_FOUND", "User not found with email: " + email, 404));
  }

  @Transactional(readOnly = true)
  public UserEntity findById(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(
            () -> new AppException("USER_NOT_FOUND", "User not found with id: " + id, 404));
  }
}
