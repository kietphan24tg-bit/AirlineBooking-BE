package com.example.moduleai.conversation;

import java.time.OffsetDateTime;

public record ChatMessageView(
    long sequenceNumber, String role, String content, OffsetDateTime createdAt) {}
