package com.example.moduleai.chat;

import com.example.moduleai.dto.RagChatResponse;
import java.util.List;
import org.springframework.ai.document.Document;

public record RagChatTrace(RagChatResponse response, List<Document> documents) {

  public RagChatTrace {
    documents = documents == null ? List.of() : List.copyOf(documents);
  }
}
