package com.example.modulebooking.application;

import java.time.LocalDate;
import java.util.List;

public record CreateBookingCommand(
    String revalidateId,
    String idempotencyKey,
    PassengerSummaryCommand passengerSummary,
    List<ContactCommand> contacts,
    List<PassengerCommand> passengers) {

  public record ContactCommand(String email, String phone, Boolean primary) {}

  public record PassengerCommand(
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      String gender,
      String nationality,
      String passengerType,
      String identifyType,
      String identifyNumber,
      LocalDate expiryDate) {}
}
