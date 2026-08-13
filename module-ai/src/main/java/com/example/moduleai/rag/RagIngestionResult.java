package com.example.moduleai.rag;

public record RagIngestionResult(int filesRead, int chunksStored, int embeddingDimensions) {}
