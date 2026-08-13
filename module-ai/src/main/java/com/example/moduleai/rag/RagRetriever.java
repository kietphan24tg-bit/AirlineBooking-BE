package com.example.moduleai.rag;

import com.example.moduleai.embedding.VectorSearchService;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Service
public class RagRetriever {

  private static final int DEFAULT_TOP_K = 5;

  private final VectorSearchService vectorSearchService;

  public RagRetriever(VectorSearchService vectorSearchService) {
    this.vectorSearchService = vectorSearchService;
  }

  public List<Document> retrieve(String question) {
    return retrieve(question, DEFAULT_TOP_K);
  }

  public List<Document> retrieve(String question, int topK) {
    if (question == null || question.isBlank()) {
      return List.of();
    }
    return vectorSearchService.similaritySearch(question, topK);
  }
}
