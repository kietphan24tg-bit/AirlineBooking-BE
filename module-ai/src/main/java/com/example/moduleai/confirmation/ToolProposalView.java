package com.example.moduleai.confirmation;

import com.example.moduleai.tool.core.ToolOperation;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ToolProposalView(
    UUID id,
    String tool,
    ToolOperation operation,
    String status,
    String summary,
    Map<String, Object> details,
    OffsetDateTime expiresAt,
    String confirmPath,
    String rejectPath) {}
