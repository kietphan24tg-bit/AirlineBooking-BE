package com.example.moduleai.rag;

public record RagChunk(
    String sourceFile, String documentTitle, String headingPath, String content, int chunkIndex) {}
