package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(name = "UpdateCurrentUserRequest", description = "Partial profile update payload for the current user")
public record UpdateCurrentUserRequest(
    @Schema(example = "Jane", nullable = true)
    @Pattern(regexp = "^(?!\\s*$).+", message = "First name must not be blank")
    @Size(max = 100, message = "First name must have at most 100 characters")
    String firstName,
    @Schema(example = "Doe", nullable = true)
    @Pattern(regexp = "^(?!\\s*$).+", message = "Last name must not be blank")
    @Size(max = 100, message = "Last name must have at most 100 characters")
    String lastName,
    @Schema(example = "1999-01-15", nullable = true) LocalDate dob,
    @Schema(example = "VN", nullable = true)
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Nationality must be ISO alpha-2")
    String nationality,
    @Schema(example = "012345678901", nullable = true)
    @Size(max = 100, message = "Identify number must have at most 100 characters")
    String identifyNumber) {}
