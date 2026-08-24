package com.example.moduleai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class RagValidationChecklistCoverageTest {
  @TestFactory
  Stream<DynamicTest> mustAnswerCasesHaveTraceablePolicyChunks() throws IOException {
    Map<String, String> files = normalizedFileContents();

    return Stream.of(
            new FileExpectation(
                "RAG-001", "checkin-and-boarding-policy.md", "check-in online", "24", "40"),
            new FileExpectation(
                "RAG-002", "checkin-and-boarding-policy.md", "quoc te", "48 gio", "60 phut"),
            new FileExpectation(
                "RAG-003", "booking-and-payment-policy.md", "15 phut", "pnrexpiresat"),
            new FileExpectation(
                "RAG-005", "booking-and-payment-policy.md", "pnrexpiresat", "sessionexpiresat"),
            new FileExpectation("RAG-006", "booking-and-payment-policy.md", "payos qr", "5 phut"),
            new FileExpectation(
                "RAG-007", "booking-and-payment-policy.md", "pay later", "giu booking lau hon"),
            new FileExpectation(
                "RAG-008", "booking-and-payment-policy.md", "stripe", "checkout", "30 phut"),
            new FileExpectation(
                "RAG-009",
                "booking-status-glossary.md",
                "pending_payment",
                "chua thanh toan thanh cong"),
            new FileExpectation(
                "RAG-010", "booking-status-glossary.md", "confirmed", "ticket", "check-in"),
            new FileExpectation(
                "RAG-011", "booking-status-glossary.md", "issued", "check-in", "refund"),
            new FileExpectation(
                "RAG-012", "booking-status-glossary.md", "partially_refunded", "hoan mot phan"),
            new FileExpectation(
                "RAG-013", "fare-passenger-and-baggage-policy.md", "em be", "khong co ghe rieng"),
            new FileExpectation("RAG-014", "fare-passenger-and-baggage-policy.md", "tre em", "75%"),
            new FileExpectation("RAG-015", "pricing-and-promotions-policy.md", "em be", "10%"),
            new FileExpectation("RAG-016", "pricing-and-promotions-policy.md", "nguoi lon", "100%"),
            new FileExpectation("RAG-021", "seat-selection-policy.md", "tieu chuan", "0 vnd"),
            new FileExpectation("RAG-022", "seat-selection-policy.md", "uu tien", "40,000 vnd"),
            new FileExpectation(
                "RAG-023", "seat-selection-policy.md", "hang thoat hiem", "120,000 vnd"),
            new FileExpectation("RAG-024", "seat-selection-policy.md", "rong chan", "180,000 vnd"),
            new FileExpectation("RAG-025", "seat-selection-policy.md", "ghe cua so", "20,000 vnd"),
            new FileExpectation(
                "RAG-026",
                "ancillary-services-policy.md",
                "baggage",
                "meal",
                "insurance",
                "priority_boarding",
                "lounge"),
            new FileExpectation("RAG-032", "change-cancellation-and-refund-policy.md", "180 phut"),
            new FileExpectation("RAG-033", "change-cancellation-and-refund-policy.md", "15 phut"),
            new FileExpectation("RAG-034", "change-cancellation-and-refund-policy.md", "90 phut"),
            new FileExpectation(
                "RAG-035",
                "change-cancellation-and-refund-policy.md",
                "infant",
                "it nhat mot adult"),
            new FileExpectation(
                "RAG-036",
                "change-cancellation-and-refund-policy.md",
                "phan phi ghe",
                "phan phi dich vu bo sung",
                "phi huy"),
            new FileExpectation(
                "RAG-038", "change-cancellation-and-refund-policy.md", "stripe", "5-10 ngay"),
            new FileExpectation(
                "RAG-039", "change-cancellation-and-refund-policy.md", "payos", "24/7"),
            new FileExpectation(
                "RAG-042", "travel-documents-policy.md", "nguon chinh thuc", "ho chieu", "visa"),
            new FileExpectation(
                "RAG-047",
                "travel-documents-policy.md",
                "nguoi lon",
                "quoc tich",
                "loai giay to",
                "so giay to"),
            new FileExpectation(
                "RAG-049", "travel-documents-policy.md", "giay to tuy than trong nuoc", "ho chieu"))
        .map(
            expectation ->
                DynamicTest.dynamicTest(
                    expectation.id(),
                    () -> assertContainsAll(files.get(expectation.file()), expectation.terms())));
  }

  @TestFactory
  Stream<DynamicTest> mustCallApiCasesAreMappedInRouterDocs() throws IOException {
    Map<String, String> files = normalizedFileContents();

    return Stream.of(
            new FileExpectation(
                "API-001", "chatbot-api-intent-map.md", "booking_lookup", "payment", "pnr"),
            new FileExpectation("API-003", "rag-scope-and-fallback.md", "payment qr", "goi api"),
            new FileExpectation(
                "API-006",
                "chatbot-api-intent-map.md",
                "seat_map_lookup",
                "/api/flights/{flightid}/seats"),
            new FileExpectation(
                "API-007", "chatbot-api-intent-map.md", "flight_search", "/api/flights/search"),
            new FileExpectation(
                "API-014",
                "chatbot-api-intent-map.md",
                "cancellation_preview",
                "/api/bookings/{bookingid}/cancellations/preview"),
            new FileExpectation(
                "API-016",
                "chatbot-api-intent-map.md",
                "flight_change_quote",
                "/api/bookings/{bookingid}/flight-change/quote"),
            new FileExpectation("API-019", "chatbot-api-intent-map.md", "promotion", "revalidate"),
            new FileExpectation(
                "API-023",
                "chatbot-api-intent-map.md",
                "seat_change_preview",
                "/api/bookings/{bookingid}/seat-changes/preview"),
            new FileExpectation(
                "API-025", "chatbot-api-intent-map.md", "my_trips", "/api/bookings/my-trips"),
            new FileExpectation(
                "API-030",
                "chatbot-api-intent-map.md",
                "booking_lookup",
                "trip_detail",
                "ngay sinh",
                "quoc tich"))
        .map(
            expectation ->
                DynamicTest.dynamicTest(
                    expectation.id(),
                    () -> assertContainsAll(files.get(expectation.file()), expectation.terms())));
  }

  @TestFactory
  Stream<DynamicTest> fallbackCasesHaveExplicitSafetyRules() throws IOException {
    Map<String, String> files = normalizedFileContents();

    return Stream.of(
            new FileExpectation(
                "FB-001", "rag-scope-and-fallback.md", "visa", "nguon chinh thuc", "khong bia"),
            new FileExpectation(
                "FB-002",
                "travel-documents-policy.md",
                "ho chieu can con han bao nhieu thang",
                "nguon chinh thuc"),
            new FileExpectation(
                "FB-003", "rag-scope-and-fallback.md", "gate", "business confirmation required"),
            new FileExpectation(
                "FB-004", "airport-operations-policy.md", "no-show", "online", "ticket"),
            new FileExpectation(
                "FB-005",
                "airport-operations-policy.md",
                "delay",
                "khong tu cam ket",
                "compensation"))
        .map(
            expectation ->
                DynamicTest.dynamicTest(
                    expectation.id(),
                    () -> assertContainsAll(files.get(expectation.file()), expectation.terms())));
  }

  private Map<String, String> normalizedFileContents() throws IOException {
    Map<String, String> files = new LinkedHashMap<>();
    Path docsRoot = docsRoot();
    for (String sourceFile : RagPolicyDocumentCatalog.productionFiles()) {
      files.put(
          sourceFile,
          normalize(Files.readString(docsRoot.resolve(sourceFile), StandardCharsets.UTF_8)));
    }
    files.put(
        "chatbot-api-intent-map.md",
        normalize(
            Files.readString(
                docsRoot.resolve("chatbot-api-intent-map.md"), StandardCharsets.UTF_8)));
    files.put(
        "rag-scope-and-fallback.md",
        normalize(
            Files.readString(
                docsRoot.resolve("rag-scope-and-fallback.md"), StandardCharsets.UTF_8)));
    return files;
  }

  private Path docsRoot() {
    Path current = Path.of("").toAbsolutePath();
    for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
      Path docs = candidate.resolve("docs").resolve("specs").resolve("rag");
      if (Files.isDirectory(docs)) {
        return docs;
      }
      Path nestedDocs =
          candidate.resolve("airline-booking").resolve("docs").resolve("specs").resolve("rag");
      if (Files.isDirectory(nestedDocs)) {
        return nestedDocs;
      }
    }
    throw new IllegalStateException("Could not locate docs/specs/rag for RAG checklist tests");
  }

  private void assertContainsAll(String content, List<String> terms) {
    assertThat(content).isNotNull();
    for (String term : terms) {
      assertThat(content).contains(normalize(term));
    }
  }

  private String normalize(String text) {
    return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase();
  }

  private record FileExpectation(String id, String file, List<String> terms) {
    private FileExpectation(String id, String file, String... terms) {
      this(id, file, List.of(terms));
    }
  }
}
