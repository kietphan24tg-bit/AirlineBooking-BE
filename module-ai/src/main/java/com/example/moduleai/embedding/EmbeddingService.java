package com.example.moduleai.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

  private final EmbeddingModel embeddingModel;

  public EmbeddingService(EmbeddingModel embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  public float[] embed(String text) {
    return embeddingModel.embed(text);
  }

  public int dimensions() {
    return embeddingModel.dimensions();
  }
}
