package com.example.moduleai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class RagIngestionRunner implements ApplicationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(RagIngestionRunner.class);

  private final RagIngestionService ragIngestionService;
  private final boolean ingestOnStartup;

  public RagIngestionRunner(
      RagIngestionService ragIngestionService,
      @Value("${app.ai.rag.ingest-on-startup:false}") boolean ingestOnStartup) {
    this.ragIngestionService = ragIngestionService;
    this.ingestOnStartup = ingestOnStartup;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (!ingestOnStartup) {
      return;
    }

    RagIngestionResult result = ragIngestionService.ingestProductionPolicies();
    LOGGER.info(
        "RAG production policy ingestion completed: filesRead={}, chunksStored={}, dimensions={}",
        result.filesRead(),
        result.chunksStored(),
        result.embeddingDimensions());
  }
}
