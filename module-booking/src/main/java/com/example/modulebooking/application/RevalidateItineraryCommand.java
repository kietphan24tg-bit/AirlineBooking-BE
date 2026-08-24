package com.example.modulebooking.application;

import java.util.List;

public record RevalidateItineraryCommand(
    String tripType,
    String promoCode,
    PassengerSummaryCommand passengerSummary,
    List<SegmentCommand> segments) {

  public record SegmentCommand(String direction, Long flightId, Long fareId) {}
}
