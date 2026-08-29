package com.example.moduleai.conversation;

import java.util.UUID;

public record ChatStructuredState(
    String itinerary,
    Integer adultCount,
    Integer childCount,
    Integer infantCount,
    String cabinClass,
    String selectedFlightCode,
    String selectedFareCode,
    Long verifiedBookingId,
    UUID activeProposalId) {

  public static ChatStructuredState empty() {
    return new ChatStructuredState(null, null, null, null, null, null, null, null, null);
  }
}
