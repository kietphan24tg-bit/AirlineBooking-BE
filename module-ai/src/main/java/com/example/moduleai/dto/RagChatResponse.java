package com.example.moduleai.dto;

import com.example.moduleai.confirmation.ToolProposalView;
import java.util.List;

public record RagChatResponse(
    String answer,
    List<SourceCitation> sources,
    boolean grounded,
    String fallbackReason,
    AnswerType answerType,
    ChatConversationRef conversation,
    ToolProposalView proposal,
    List<AgentTraceStep> trace,
    double confidence) {

  public RagChatResponse(
      String answer,
      List<SourceCitation> sources,
      boolean grounded,
      String fallbackReason,
      AnswerType answerType,
      ToolProposalView proposal,
      List<AgentTraceStep> trace,
      double confidence) {
    this(answer, sources, grounded, fallbackReason, answerType, null, proposal, trace, confidence);
  }
}
