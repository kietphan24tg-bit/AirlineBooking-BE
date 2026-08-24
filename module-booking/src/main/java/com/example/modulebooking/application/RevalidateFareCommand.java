package com.example.modulebooking.application;

public record RevalidateFareCommand(
    Long flightId, Long fareId, String promoCode, PassengerSummaryCommand passengerSummary) {}
