package com.example.moduleai.rag;

import com.example.moduleai.embedding.EmbeddingService;
import com.example.moduleai.embedding.VectorSearchService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RagIngestionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RagIngestionService.class);

  private final RagChunker ragChunker;
  private final VectorSearchService vectorSearchService;
  private final EmbeddingService embeddingService;
  private final Path docsPath;
  private final int ingestBatchSize;
  private final Duration ingestBatchDelay;

  public RagIngestionService(
      RagChunker ragChunker,
      VectorSearchService vectorSearchService,
      EmbeddingService embeddingService,
      @Value("${app.ai.rag.docs-path:docs/specs/rag}") String docsPath,
      @Value("${app.ai.rag.ingest-batch-size:80}") int ingestBatchSize,
      @Value("${app.ai.rag.ingest-batch-delay:45s}") Duration ingestBatchDelay) {
    this.ragChunker = ragChunker;
    this.vectorSearchService = vectorSearchService;
    this.embeddingService = embeddingService;
    this.docsPath = Path.of(docsPath);
    this.ingestBatchSize = ingestBatchSize;
    this.ingestBatchDelay = ingestBatchDelay;
  }

  public RagIngestionResult ingestProductionPolicies() throws IOException {
    int dimensions = embeddingService.dimensions();
    List<Document> documents = new ArrayList<>();
    Set<String> sourceFilesToRefresh = new LinkedHashSet<>();
    int filesRead = 0;

    for (String sourceFile : RagPolicyDocumentCatalog.productionFiles()) {
      Path filePath = docsPath.resolve(sourceFile);
      if (!Files.isRegularFile(filePath)) {
        continue;
      }

      filesRead++;
      sourceFilesToRefresh.add(sourceFile);
      String markdown = Files.readString(filePath, StandardCharsets.UTF_8);
      for (RagChunk chunk : ragChunker.chunk(sourceFile, markdown)) {
        documents.add(toDocument(chunk));
      }
    }

    LOGGER.info(
        "Prepared RAG ingestion documents: filesRead={}, chunks={}", filesRead, documents.size());
    for (String sourceFile : sourceFilesToRefresh) {
      vectorSearchService.deleteBySourceFile(sourceFile);
    }
    addDocumentsWithThrottle(documents);
    return new RagIngestionResult(filesRead, documents.size(), dimensions);
  }

  private void addDocumentsWithThrottle(List<Document> documents) {
    if (documents.isEmpty()) {
      return;
    }

    int batchSize = Math.max(1, ingestBatchSize);
    int totalBatches = (int) Math.ceil((double) documents.size() / batchSize);
    for (int start = 0; start < documents.size(); start += batchSize) {
      int end = Math.min(start + batchSize, documents.size());
      int batchNumber = start / batchSize + 1;
      LOGGER.info(
          "Adding RAG vector batch {}/{}: documents {}-{} of {}",
          batchNumber,
          totalBatches,
          start + 1,
          end,
          documents.size());
      vectorSearchService.addDocuments(documents.subList(start, end));
      waitBeforeNextBatch(end, documents.size());
    }
  }

  private void waitBeforeNextBatch(int currentEnd, int totalDocuments) {
    if (currentEnd >= totalDocuments || ingestBatchDelay == null || ingestBatchDelay.isZero()) {
      return;
    }

    try {
      Thread.sleep(ingestBatchDelay.toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "RAG ingestion was interrupted while waiting between batches", ex);
    }
  }

  private Document toDocument(RagChunk chunk) {
    String content =
        """
            Source: %s
            Heading: %s

            %s
            """
            .formatted(chunk.sourceFile(), chunk.headingPath(), chunk.content())
            .trim();

    RagDocumentMetadata metadata =
        new RagDocumentMetadata(
            chunk.sourceFile(),
            chunk.headingPath(),
            RagPolicyDocumentCatalog.policyArea(chunk.sourceFile()),
            "policy",
            true,
            chunk.chunkIndex(),
            sha256(content),
            LocalDate.now().toString());

    return new Document(content, metadata.toMap());
  }

  private String sha256(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 digest is not available", ex);
    }
  }
}
