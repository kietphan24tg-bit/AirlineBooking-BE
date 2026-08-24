package com.example.modulebooking.application;

import java.math.BigDecimal;
import java.util.List;

public record PassengerServiceSelectionView(
    Long bookingId,
    BigDecimal ancillaryTotal,
    BigDecimal totalAmount,
    String currency,
    List<PassengerServicesView> passengers) {

  public record PassengerServicesView(Long passengerId, List<ServiceSelectionView> selections) {}

  public record ServiceSelectionView(
      Long serviceId,
      String serviceType,
      String serviceName,
      Long optionId,
      String optionName,
      Integer quantity,
      BigDecimal price) {}
}
