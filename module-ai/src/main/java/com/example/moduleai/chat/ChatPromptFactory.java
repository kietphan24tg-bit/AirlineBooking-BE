package com.example.moduleai.chat;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Component
public class ChatPromptFactory {

  public String systemPrompt() {
    return ChatPromptTemplates.SYSTEM_PROMPT;
  }

  public String userPrompt(String question, List<Document> contextDocuments) {
    return """
        Context:
        %s

        Câu hỏi của người dùng:
        %s
        """
        .formatted(context(contextDocuments), question)
        .trim();
  }

  private String context(List<Document> documents) {
    if (documents == null || documents.isEmpty()) {
      return "Không có context phù hợp.";
    }

    return documents.stream().map(this::contextEntry).collect(Collectors.joining("\n\n---\n\n"));
  }

  private String contextEntry(Document document) {
    return """
        Source: %s
        Heading: %s
        Content:
        %s
        """
        .formatted(
            metadataValue(document, "source_file"),
            metadataValue(document, "heading_path"),
            document.getText())
        .trim();
  }

  private String metadataValue(Document document, String key) {
    Object value = document.getMetadata().get(key);
    return value == null ? "" : value.toString();
  }
}
