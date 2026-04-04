package com.example.appbootstrap.controller.identity.dto;

import com.example.moduleidentity.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "AuthenticatedUserSummary", description = "Basic user information returned by auth endpoints")
public record AuthenticatedUserSummary(
    @Schema(example = "1") Long id,
    @Schema(example = "user@example.com") String email,
    @Schema(example = "Jane") String firstName,
    @Schema(example = "Doe") String lastName,
    @Schema(example = "+84901234567", nullable = true) String phone,
    @Schema(example = "VN", nullable = true) String nationality,
    @Schema(example = "USER") Role role,
    @Schema(example = "2026-04-04T10:15:30Z") OffsetDateTime createdAt) {}
