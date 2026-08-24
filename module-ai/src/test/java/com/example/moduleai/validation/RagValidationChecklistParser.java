package com.example.moduleai.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RagValidationChecklistParser {

  private static final Pattern TABLE_ROW =
      Pattern.compile("^\\|\\s*([^|]+?)\\s*\\|\\s*([^|]+?)\\s*\\|\\s*([^|]+?)\\s*\\|$");

  public List<ValidationCase> parse(Path checklistPath) throws IOException {
    List<ValidationCase> cases = new ArrayList<>();
    ValidationSection currentSection = null;

    for (String rawLine : Files.readAllLines(checklistPath, StandardCharsets.UTF_8)) {
      String line = rawLine.trim();
      ValidationSection section = ValidationSection.fromHeading(line);
      if (section != null) {
        currentSection = section;
        continue;
      }

      if (currentSection == null || !line.startsWith("|")) {
        continue;
      }

      Matcher matcher = TABLE_ROW.matcher(line);
      if (!matcher.matches()) {
        continue;
      }

      String id = matcher.group(1).trim();
      if (!currentSection.matchesId(id)) {
        continue;
      }
      cases.add(
          new ValidationCase(
              currentSection, id, cleanCell(matcher.group(2)), cleanCell(matcher.group(3))));
    }
    return List.copyOf(cases);
  }

  private String cleanCell(String value) {
    return value.replace("\\|", "|").replace("`", "").trim();
  }

  public enum ValidationSection {
    RAG("## Must-answer bằng RAG", "RAG-"),
    API("## Must-call-API", "API-"),
    FALLBACK("## Fallback và không được bịa", "FB-"),
    EDGE("## Edge case và regression", "EDGE-"),
    LANGUAGE("## Đa ngôn ngữ và language consistency", "LANG-");

    private final String heading;
    private final String idPrefix;

    ValidationSection(String heading, String idPrefix) {
      this.heading = heading;
      this.idPrefix = idPrefix;
    }

    static ValidationSection fromHeading(String line) {
      for (ValidationSection section : values()) {
        if (line.equals(section.heading)) {
          return section;
        }
      }
      return null;
    }

    boolean matchesId(String id) {
      return id.startsWith(idPrefix);
    }
  }

  public record ValidationCase(
      ValidationSection section, String id, String question, String expectedBehavior) {}
}
