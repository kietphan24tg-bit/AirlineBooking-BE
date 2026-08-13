package com.example.moduleai.dto;

import java.util.List;

public record RagChatResponse(
    String answer, List<SourceCitation> sources, boolean grounded, String fallbackReason) {}
