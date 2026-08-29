package com.example.moduleai.conversation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatConversationView(
    UUID id,
    String status,
    String title,
    String summary,
    OffsetDateTime lastActiveAt,
    OffsetDateTime contextExpiresAt,
    OffsetDateTime archivedAt,
    String archiveReason) {}
