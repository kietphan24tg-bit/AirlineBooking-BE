package com.example.moduleai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RagChunkerTest {

  private final RagChunker chunker = new RagChunker();

  @Test
  void chunksMarkdownByH2AndH3Hierarchy() {
    String markdown =
        """
            # Seat Policy

            Intro text outside a section.

            ## Advance seat selection

            Advance seat selection content.

            ### Paid seats

            Paid seat content.

            ## Seat change

            Seat change content.
            """;

    List<RagChunk> chunks = chunker.chunk("seat-selection-policy.md", markdown);

    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0).headingPath()).isEqualTo("Seat Policy > Advance seat selection");
    assertThat(chunks.get(1).headingPath())
        .isEqualTo("Seat Policy > Advance seat selection > Paid seats");
    assertThat(chunks.get(2).headingPath()).isEqualTo("Seat Policy > Seat change");
    assertThat(chunks).extracting(RagChunk::chunkIndex).containsExactly(0, 1, 2);
  }

  @Test
  void keepsMarkdownTableInsideOneChunkWhenSectionIsSplit() {
    String markdown =
        """
            # Pricing Policy

            ## Price table

            Long opening paragraph that forces the section to split with a small max chunk size.

            | Option | Price |
            | --- | ---: |
            | A | 100.000 VND |
            | B | 200.000 VND |

            Closing paragraph.
            """;

    List<RagChunk> chunks = chunker.chunk("pricing-and-promotions-policy.md", markdown, 80);

    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks)
        .anySatisfy(
            chunk ->
                assertThat(chunk.content())
                    .contains("| Option | Price |")
                    .contains("| A | 100.000 VND |")
                    .contains("| B | 200.000 VND |"));
  }

  @Test
  void carriesPreviousBlockAsOverlapWhenSectionIsSplit() {
    String markdown =
        """
            # Baggage Policy

            ## Checked baggage

            Paragraph one gives the main checked baggage rule.

            Paragraph two should be repeated as overlap.

            Paragraph three starts the next chunk.
            """;

    List<RagChunk> chunks = chunker.chunk("fare-passenger-and-baggage-policy.md", markdown, 120);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).content())
        .contains("Paragraph one gives the main checked baggage rule.")
        .contains("Paragraph two should be repeated as overlap.")
        .doesNotContain("Paragraph three starts the next chunk.");
    assertThat(chunks.get(1).content())
        .contains("Paragraph two should be repeated as overlap.")
        .contains("Paragraph three starts the next chunk.");
  }

  @Test
  void productionCatalogIncludesOnlyApprovedPolicyFiles() {
    assertThat(RagPolicyDocumentCatalog.isProductionFile("seat-selection-policy.md")).isTrue();
    assertThat(RagPolicyDocumentCatalog.isProductionFile("airport-operations-policy.md")).isTrue();
    assertThat(RagPolicyDocumentCatalog.isProductionFile("README.md")).isFalse();
    assertThat(RagPolicyDocumentCatalog.isProductionFile("rag-validation-checklist.md")).isFalse();
  }

  @Test
  void metadataContainsRequiredFields() {
    RagDocumentMetadata metadata =
        new RagDocumentMetadata(
            "seat-selection-policy.md",
            "Seat Policy > Seat change",
            "seat",
            "policy",
            true,
            2,
            "abc",
            "2026-08-11");

    assertThat(metadata.toMap())
        .containsEntry("source_file", "seat-selection-policy.md")
        .containsEntry("heading_path", "Seat Policy > Seat change")
        .containsEntry("policy_area", "seat")
        .containsEntry("document_type", "policy")
        .containsEntry("production_embed", true)
        .containsEntry("chunk_index", 2)
        .containsEntry("content_hash", "abc")
        .containsEntry("last_verified_against_code", "2026-08-11");
  }
}
