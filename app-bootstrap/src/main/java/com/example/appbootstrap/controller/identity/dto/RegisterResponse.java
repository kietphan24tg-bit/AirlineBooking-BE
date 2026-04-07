package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RegisterResponse", description = "Result returned after a successful registration")
public record RegisterResponse(
    @Schema(description = "Registered user summary") AuthenticatedUserSummary user) {}
