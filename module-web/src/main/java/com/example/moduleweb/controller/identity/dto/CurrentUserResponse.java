package com.example.moduleweb.controller.identity.dto;

import com.example.moduleidentity.entity.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(name = "CurrentUserResponse", description = "Current authenticated user profile")
public record CurrentUserResponse(
    @Schema(example = "1") Long id,
    @Schema(example = "user@example.com") String email,
    @Schema(example = "Jane") String firstName,
    @Schema(example = "Doe") String lastName,
    @Schema(example = "+84901234567", nullable = true) String phone,
    @Schema(example = "1999-01-15", nullable = true) LocalDate dob,
    @Schema(example = "VN", nullable = true) String nationality,
    @Schema(example = "012345678901", nullable = true) String identifyNumber,
    @Schema(example = "USER") String role,
    @Schema(example = "2026-04-04T10:15:30Z") OffsetDateTime createdAt) {

  public static CurrentUserResponse from(UserEntity user) {
    return new CurrentUserResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getPhone(),
        user.getDob() == null
            ? null
            : user.getDob().atZoneSameInstant(ZoneOffset.UTC).toLocalDate(),
        user.getNationality(),
        user.getIdentifyNumber(),
        user.getRole() == null ? null : user.getRole().name(),
        user.getCreatedAt());
  }
}
