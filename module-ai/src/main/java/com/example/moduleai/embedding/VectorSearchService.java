package com.example.moduleai.embedding;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class VectorSearchService {

  private final VectorStore vectorStore;

  public VectorSearchService(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
  }

  public void addDocuments(List<Document> documents) {
    if (documents == null || documents.isEmpty()) {
      return;
    }
    vectorStore.add(documents);
  }

  public void deleteBySourceFile(String sourceFile) {
    if (sourceFile == null || sourceFile.isBlank()) {
      return;
    }
    vectorStore.delete("source_file == '%s'".formatted(sourceFile));
  }

  public List<Document> similaritySearch(String query, int topK) {
    return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).build());
  }
}
