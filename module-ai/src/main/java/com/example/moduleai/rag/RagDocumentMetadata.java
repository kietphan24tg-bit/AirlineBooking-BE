package com.example.moduleai.rag;

import java.util.LinkedHashMap;
import java.util.Map;

public record RagDocumentMetadata(
    String sourceFile,
    String headingPath,
    String policyArea,
    String documentType,
    boolean productionEmbed,
    int chunkIndex,
    String contentHash,
    String lastVerifiedAgainstCode) {

  public Map<String, Object> toMap() {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("source_file", sourceFile);
    metadata.put("heading_path", headingPath);
    metadata.put("policy_area", policyArea);
    metadata.put("document_type", documentType);
    metadata.put("production_embed", productionEmbed);
    metadata.put("chunk_index", chunkIndex);
    metadata.put("content_hash", contentHash);
    metadata.put("last_verified_against_code", lastVerifiedAgainstCode);
    return metadata;
  }
}
