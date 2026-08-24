package com.example.moduleai.chat;

import com.example.moduleai.confirmation.ToolProposalView;
import com.example.moduleai.dto.AgentTraceStep;
import com.example.moduleai.dto.AnswerType;
import com.example.moduleai.dto.RagChatResponse;
import com.example.moduleai.dto.SourceCitation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Component
public class ChatResponseMapper {

  public RagChatResponse grounded(String answer, List<Document> documents) {
    return new RagChatResponse(
        answer, sources(documents), true, null, AnswerType.ANSWER, null, List.of(), 0.0);
  }

  /** Overload for when the RAG pipeline can provide a confidence score. */
  public RagChatResponse grounded(String answer, List<Document> documents, double confidence) {
    return new RagChatResponse(
        answer, sources(documents), true, null, AnswerType.ANSWER, null, List.of(), confidence);
  }

  public RagChatResponse fallback(String fallbackAnswer, String fallbackReason) {
    return new RagChatResponse(
        fallbackAnswer,
        List.of(),
        false,
        fallbackReason,
        AnswerType.FALLBACK,
        null,
        List.of(),
        0.0);
  }

  public RagChatResponse fallback(
      String fallbackAnswer, List<Document> documents, String fallbackReason) {
    return new RagChatResponse(
        fallbackAnswer,
        sources(documents),
        false,
        fallbackReason,
        AnswerType.FALLBACK,
        null,
        List.of(),
        0.0);
  }

  public RagChatResponse toolResult(String answer) {
    return new RagChatResponse(
        answer, List.of(), false, null, AnswerType.TOOL_RESULT, null, List.of(), 0.0);
  }

  /** Overload for when the Agent can provide execution trace steps. */
  public RagChatResponse toolResult(String answer, List<AgentTraceStep> trace) {
    return new RagChatResponse(
        answer, List.of(), false, null, AnswerType.TOOL_RESULT, null, trace, 0.0);
  }

  public RagChatResponse confirmationRequired(String answer, ToolProposalView proposal) {
    return new RagChatResponse(
        answer, List.of(), false, null, AnswerType.CONFIRMATION_REQUIRED, proposal, List.of(), 0.0);
  }

  public RagChatResponse authenticationRequired(String answer) {
    return new RagChatResponse(
        answer,
        List.of(),
        false,
        "AUTHENTICATION_REQUIRED",
        AnswerType.AUTHENTICATION_REQUIRED,
        null,
        List.of(),
        0.0);
  }

  public List<SourceCitation> sources(List<Document> documents) {
    if (documents == null || documents.isEmpty()) {
      return List.of();
    }

    Map<String, SourceCitation> deduplicated = new LinkedHashMap<>();
    for (Document document : documents) {
      String sourceFile = metadataValue(document, "source_file");
      String headingPath = metadataValue(document, "heading_path");
      if (sourceFile.isBlank() && headingPath.isBlank()) {
        continue;
      }
      deduplicated.putIfAbsent(
          sourceFile + "|" + headingPath, new SourceCitation(sourceFile, headingPath));
    }
    return List.copyOf(deduplicated.values());
  }

  private String metadataValue(Document document, String key) {
    Object value = document.getMetadata().get(key);
    return value == null ? "" : value.toString();
  }
}
