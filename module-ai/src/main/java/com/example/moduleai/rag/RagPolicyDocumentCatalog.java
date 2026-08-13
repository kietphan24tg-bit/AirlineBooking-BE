package com.example.moduleai.rag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RagPolicyDocumentCatalog {

  private static final Set<String> PRODUCTION_FILES =
      new LinkedHashSet<>(
          List.of(
              "booking-and-payment-policy.md",
              "booking-status-glossary.md",
              "fare-passenger-and-baggage-policy.md",
              "seat-selection-policy.md",
              "ancillary-services-policy.md",
              "checkin-and-boarding-policy.md",
              "change-cancellation-and-refund-policy.md",
              "travel-documents-policy.md",
              "pricing-and-promotions-policy.md",
              "airport-operations-policy.md"));

  private static final Map<String, String> POLICY_AREAS =
      Map.ofEntries(
          Map.entry("booking-and-payment-policy.md", "payment"),
          Map.entry("booking-status-glossary.md", "booking_status"),
          Map.entry("fare-passenger-and-baggage-policy.md", "fare_passenger_baggage"),
          Map.entry("seat-selection-policy.md", "seat"),
          Map.entry("ancillary-services-policy.md", "ancillary"),
          Map.entry("checkin-and-boarding-policy.md", "checkin_boarding"),
          Map.entry("change-cancellation-and-refund-policy.md", "change_refund"),
          Map.entry("travel-documents-policy.md", "travel_documents"),
          Map.entry("pricing-and-promotions-policy.md", "pricing_promotions"),
          Map.entry("airport-operations-policy.md", "airport_operations"));

  private RagPolicyDocumentCatalog() {}

  public static boolean isProductionFile(String sourceFile) {
    return PRODUCTION_FILES.contains(sourceFile);
  }

  public static Set<String> productionFiles() {
    return Collections.unmodifiableSet(PRODUCTION_FILES);
  }

  public static String policyArea(String sourceFile) {
    return POLICY_AREAS.getOrDefault(sourceFile, "general");
  }
}
