package com.example.moduleai.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RagChunker {

  private static final int DEFAULT_MAX_CHUNK_CHARACTERS = 4_500;
  private static final int OVERLAP_BLOCKS = 1;

  public List<RagChunk> chunk(String sourceFile, String markdown) {
    return chunk(sourceFile, markdown, DEFAULT_MAX_CHUNK_CHARACTERS);
  }

  List<RagChunk> chunk(String sourceFile, String markdown, int maxChunkCharacters) {
    if (markdown == null || markdown.isBlank()) {
      return List.of();
    }

    List<RagChunk> chunks = new ArrayList<>();
    String documentTitle = sourceFile;
    String currentH2 = null;
    String currentH3 = null;
    StringBuilder section = new StringBuilder();

    for (String line : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
      if (line.startsWith("# ")) {
        documentTitle = line.substring(2).trim();
        continue;
      }

      if (line.startsWith("## ") && !line.startsWith("### ")) {
        flushSection(
            sourceFile, documentTitle, currentH2, currentH3, section, maxChunkCharacters, chunks);
        currentH2 = line.substring(3).trim();
        currentH3 = null;
        section = new StringBuilder();
        continue;
      }

      if (line.startsWith("### ")) {
        flushSection(
            sourceFile, documentTitle, currentH2, currentH3, section, maxChunkCharacters, chunks);
        currentH3 = line.substring(4).trim();
        section = new StringBuilder();
        continue;
      }

      section.append(line).append('\n');
    }

    flushSection(
        sourceFile, documentTitle, currentH2, currentH3, section, maxChunkCharacters, chunks);
    return reindex(chunks);
  }

  private void flushSection(
      String sourceFile,
      String documentTitle,
      String h2,
      String h3,
      StringBuilder section,
      int maxChunkCharacters,
      List<RagChunk> chunks) {
    String content = section.toString().trim();
    if (content.isBlank() || (h2 == null && h3 == null)) {
      return;
    }

    String headingPath = headingPath(documentTitle, h2, h3);
    for (String splitContent : splitLongSection(content, maxChunkCharacters)) {
      chunks.add(new RagChunk(sourceFile, documentTitle, headingPath, splitContent, chunks.size()));
    }
  }

  private String headingPath(String documentTitle, String h2, String h3) {
    List<String> headings = new ArrayList<>();
    if (documentTitle != null && !documentTitle.isBlank()) {
      headings.add(documentTitle);
    }
    if (h2 != null && !h2.isBlank()) {
      headings.add(h2);
    }
    if (h3 != null && !h3.isBlank()) {
      headings.add(h3);
    }
    return String.join(" > ", headings);
  }

  private List<String> splitLongSection(String content, int maxChunkCharacters) {
    if (content.length() <= maxChunkCharacters) {
      return List.of(content);
    }

    List<String> chunks = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    String previousBlock = null;
    for (String block : logicalBlocks(content)) {
      if (!current.isEmpty() && current.length() + block.length() + 2 > maxChunkCharacters) {
        chunks.add(current.toString().trim());
        current = new StringBuilder();
        if (shouldCarryOverlap(previousBlock, block, maxChunkCharacters)) {
          current.append(previousBlock);
        }
      }
      appendBlock(current, block);
      previousBlock = block;
    }

    if (!current.isEmpty()) {
      chunks.add(current.toString().trim());
    }
    return chunks;
  }

  private boolean shouldCarryOverlap(
      String previousBlock, String nextBlock, int maxChunkCharacters) {
    if (OVERLAP_BLOCKS <= 0 || previousBlock == null || previousBlock.isBlank()) {
      return false;
    }
    return previousBlock.length() <= maxChunkCharacters / 2
        && previousBlock.length() + nextBlock.length() + 2 <= maxChunkCharacters;
  }

  private void appendBlock(StringBuilder current, String block) {
    if (!current.isEmpty()) {
      current.append("\n\n");
    }
    current.append(block);
  }

  private List<String> logicalBlocks(String content) {
    List<String> blocks = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inTable = false;
    boolean inList = false;

    for (String line : content.split("\n")) {
      boolean tableLine = line.trim().startsWith("|");
      boolean listLine =
          line.trim().matches("^[-*+]\\s+.*") || line.trim().matches("^\\d+\\.\\s+.*");
      boolean blank = line.isBlank();

      if (blank && !inTable && !inList) {
        addBlock(blocks, current);
        continue;
      }

      if (!current.isEmpty()
          && !blank
          && (inTable != tableLine || inList != listLine)
          && (tableLine || listLine)) {
        addBlock(blocks, current);
      }

      current.append(line).append('\n');
      inTable = tableLine && !blank;
      inList = listLine && !blank;

      if (blank) {
        inTable = false;
        inList = false;
      }
    }

    addBlock(blocks, current);
    return blocks;
  }

  private void addBlock(List<String> blocks, StringBuilder current) {
    String block = current.toString().trim();
    if (!block.isBlank()) {
      blocks.add(block);
    }
    current.setLength(0);
  }

  private List<RagChunk> reindex(List<RagChunk> chunks) {
    List<RagChunk> indexed = new ArrayList<>(chunks.size());
    for (int index = 0; index < chunks.size(); index++) {
      RagChunk chunk = chunks.get(index);
      indexed.add(
          new RagChunk(
              chunk.sourceFile(),
              chunk.documentTitle(),
              chunk.headingPath(),
              chunk.content(),
              index));
    }
    return indexed;
  }
}
