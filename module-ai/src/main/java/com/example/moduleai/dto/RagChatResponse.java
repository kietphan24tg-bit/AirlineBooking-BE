package com.example.moduleai.dto;

import com.example.moduleai.confirmation.ToolProposalView;
import java.util.List;

public record RagChatResponse(
    String answer,
    List<SourceCitation> sources,
    boolean grounded,
    String fallbackReason,
    AnswerType answerType,
    ToolProposalView proposal,
    List<AgentTraceStep> trace,
    double confidence) {}
