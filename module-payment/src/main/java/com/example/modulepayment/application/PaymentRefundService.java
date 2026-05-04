package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.gateway.PaymentRefundGateway;
import com.example.modulepayment.gateway.PaymentRefundGateway.PaymentRefundRequest;
import com.example.modulepayment.gateway.PaymentRefundGateway.PaymentRefundResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PaymentRefundService {

  private final Map<String, PaymentRefundGateway> gatewaysByProvider;

  public PaymentRefundService(List<PaymentRefundGateway> gateways) {
    this.gatewaysByProvider =
        gateways.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    gateway -> normalize(gateway.provider()), Function.identity()));
  }

  public void preparePaymentForRefund(PaymentEntity payment) {
    gatewayFor(payment).preparePaymentForRefund(payment);
  }

  public PaymentRefundResult refund(
      Long cancellationId,
      String idempotencyKey,
      PaymentEntity payment,
      BigDecimal amount,
      String reason) {
    return gatewayFor(payment)
        .refund(
            new PaymentRefundRequest(
                cancellationId,
                idempotencyKey,
                payment,
                amount,
                payment == null ? null : payment.getCurrency(),
                reason));
  }

  public String providerOf(PaymentEntity payment) {
    if (payment == null) {
      throw new AppException("PAYMENT_NOT_FOUND", "Payment not found", 404);
    }
    String method = normalize(payment.getPaymentMethod());
    if (method.startsWith("STRIPE")) {
      return "STRIPE";
    }
    return "PAYOS";
  }

  private PaymentRefundGateway gatewayFor(PaymentEntity payment) {
    String provider = providerOf(payment);
    PaymentRefundGateway gateway = gatewaysByProvider.get(normalize(provider));
    if (gateway == null) {
      throw new AppException(
          "REFUND_PROVIDER_UNSUPPORTED", "Refund provider is not supported", 409);
    }
    return gateway;
  }

  private static String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }
}
