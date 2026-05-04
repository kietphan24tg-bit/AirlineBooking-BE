package com.example.modulepayment.gateway;

import com.example.common.exception.AppException;
import com.example.modulepayment.entity.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentRefundGateway implements PaymentRefundGateway {

  private final StripePaymentGateway stripePaymentGateway;

  public StripePaymentRefundGateway(StripePaymentGateway stripePaymentGateway) {
    this.stripePaymentGateway = stripePaymentGateway;
  }

  @Override
  public String provider() {
    return "STRIPE";
  }

  @Override
  public void preparePaymentForRefund(PaymentEntity payment) {
    if (payment == null) {
      throw new AppException("PAYMENT_NOT_FOUND", "Payment not found", 404);
    }
    if (payment.getProviderPaymentReference() == null
        || payment.getProviderPaymentReference().isBlank()) {
      payment.setProviderPaymentReference(
          stripePaymentGateway.resolvePaymentIntent(payment.getTransactionId()));
    }
  }

  @Override
  public PaymentRefundResult refund(PaymentRefundRequest request) {
    StripePaymentGateway.RefundResult result =
        stripePaymentGateway.createRefund(
            request.payment().getProviderPaymentReference(),
            request.amount(),
            request.currency(),
            request.reason());
    return new PaymentRefundResult(result.refundId(), result.status());
  }
}
