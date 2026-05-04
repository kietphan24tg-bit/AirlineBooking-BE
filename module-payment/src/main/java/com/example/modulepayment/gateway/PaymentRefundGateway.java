package com.example.modulepayment.gateway;

import com.example.modulepayment.entity.PaymentEntity;
import java.math.BigDecimal;

public interface PaymentRefundGateway {

  String provider();

  default void preparePaymentForRefund(PaymentEntity payment) {}

  PaymentRefundResult refund(PaymentRefundRequest request);

  record PaymentRefundRequest(
      Long cancellationId,
      String idempotencyKey,
      PaymentEntity payment,
      BigDecimal amount,
      String currency,
      String reason) {}

  record PaymentRefundResult(String providerRefundId, String providerStatus) {}
}
