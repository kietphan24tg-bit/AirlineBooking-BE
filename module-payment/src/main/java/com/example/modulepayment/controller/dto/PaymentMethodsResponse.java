package com.example.modulepayment.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PaymentMethodsResponse(
    List<PaymentMethodGroupResponse> groups, ActivePayLaterResponse activePayLater) {

  public record ActivePayLaterResponse(Long paymentId, OffsetDateTime expireAt) {}

  public record PaymentMethodGroupResponse(
      String type, String label, List<PaymentMethodItemResponse> methods) {}

  public record PaymentMethodItemResponse(
      String code,
      String label,
      String provider,
      String icon,
      boolean supported,
      PaymentMethodMetaResponse meta) {}

  public record PaymentMethodMetaResponse(Integer holdDurationMinutes, Integer fee) {}
}
