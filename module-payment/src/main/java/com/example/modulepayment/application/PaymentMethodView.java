package com.example.modulepayment.application;

import java.util.List;

public record PaymentMethodView(String type, String label, List<PaymentMethodItemView> methods) {

  public record PaymentMethodItemView(
      String code,
      String label,
      String provider,
      String icon,
      boolean supported,
      PaymentMethodMetaView meta) {}

  public record PaymentMethodMetaView(Integer holdDurationMinutes, Integer fee) {}
}
