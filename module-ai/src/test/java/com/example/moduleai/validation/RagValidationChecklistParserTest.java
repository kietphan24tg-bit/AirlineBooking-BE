package com.example.moduleai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.moduleai.validation.RagValidationChecklistParser.ValidationCase;
import com.example.moduleai.validation.RagValidationChecklistParser.ValidationSection;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagValidationChecklistParserTest {

  @Test
  void parsesAllValidationSectionsFromTheChecklist() throws Exception {
    Path checklist =
        Path.of("..", "docs", "specs", "rag", "rag-validation-checklist.md")
            .toAbsolutePath()
            .normalize();

    List<ValidationCase> cases = new RagValidationChecklistParser().parse(checklist);

    assertThat(cases).hasSize(135);
    assertThat(cases.stream().filter(c -> c.section() == ValidationSection.RAG)).hasSize(49);
    assertThat(cases.stream().filter(c -> c.section() == ValidationSection.API)).hasSize(31);
    assertThat(cases.stream().filter(c -> c.section() == ValidationSection.FALLBACK)).hasSize(15);
    assertThat(cases.stream().filter(c -> c.section() == ValidationSection.EDGE)).hasSize(28);
    assertThat(cases.stream().filter(c -> c.section() == ValidationSection.LANGUAGE)).hasSize(12);
    assertThat(cases.getFirst().question()).contains("Check-in online");
  }
}
