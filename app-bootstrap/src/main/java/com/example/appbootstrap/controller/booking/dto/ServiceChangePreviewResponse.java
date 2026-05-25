package com.example.appbootstrap.controller.booking.dto;

import java.math.BigDecimal;
import java.util.List;

public record ServiceChangePreviewResponse(
    Long bookingId,
    String currency,
    String previewToken,
    String settlementType,
    List<ItemResponse> items,
    SummaryResponse summary) {

  public record ItemResponse(
      Long passengerId,
      String passengerName,
      Long serviceId,
      String serviceType,
      String serviceName,
      Long optionId,
      String optionName,
      Integer quantity,
      BigDecimal oldAmount,
      BigDecimal newAmount,
      BigDecimal delta,
      String status,
      String message) {}

  public record SummaryResponse(
      BigDecimal additionalAmount, BigDecimal refundAmount, BigDecimal netAmount) {}
}
