package com.example.moduleai.chat;

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
    return new RagChatResponse(answer, sources(documents), true, null);
  }

  public RagChatResponse fallback(String fallbackAnswer, String fallbackReason) {
    return new RagChatResponse(fallbackAnswer, List.of(), false, fallbackReason);
  }

  public RagChatResponse fallback(
      String fallbackAnswer, List<Document> documents, String fallbackReason) {
    return new RagChatResponse(fallbackAnswer, sources(documents), false, fallbackReason);
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
