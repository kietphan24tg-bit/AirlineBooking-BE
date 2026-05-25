package com.example.modulepayment.application;

import java.math.BigDecimal;
import java.util.List;

public record ServiceChangePreviewView(
    Long bookingId,
    String currency,
    String previewToken,
    String settlementType,
    List<ItemView> items,
    SummaryView summary) {

  public record ItemView(
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

  public record SummaryView(
      BigDecimal additionalAmount, BigDecimal refundAmount, BigDecimal netAmount) {}
}
