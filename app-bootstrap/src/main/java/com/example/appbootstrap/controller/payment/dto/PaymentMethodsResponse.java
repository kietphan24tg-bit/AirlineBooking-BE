package com.example.appbootstrap.controller.payment.dto;

import java.util.List;

public record PaymentMethodsResponse(List<PaymentMethodGroupResponse> groups) {

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
