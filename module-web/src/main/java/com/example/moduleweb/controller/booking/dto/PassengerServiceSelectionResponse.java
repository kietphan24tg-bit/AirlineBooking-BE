package com.example.moduleweb.controller.booking.dto;

import java.math.BigDecimal;
import java.util.List;

public record PassengerServiceSelectionResponse(
    Long bookingId,
    BigDecimal ancillaryTotal,
    BigDecimal totalAmount,
    String currency,
    List<PassengerServicesResponse> passengers) {

  public record PassengerServicesResponse(
      Long passengerId, List<ServiceSelectionResponse> selections) {}

  public record ServiceSelectionResponse(
      Long serviceId,
      String serviceType,
      String serviceName,
      Long optionId,
      String optionName,
      Integer quantity,
      BigDecimal price) {}
}
