package com.example.moduleai.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatConversationRef(UUID id, String status, OffsetDateTime contextExpiresAt) {}
