package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.gateway.StripePaymentGateway;
import com.example.modulepayment.repository.PaymentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StripeCheckoutExpirationService {

  private static final Logger LOG = LoggerFactory.getLogger(StripeCheckoutExpirationService.class);

  private final PaymentRepository paymentRepository;
  private final StripePaymentGateway stripePaymentGateway;

  public StripeCheckoutExpirationService(
      PaymentRepository paymentRepository, StripePaymentGateway stripePaymentGateway) {
    this.paymentRepository = paymentRepository;
    this.stripePaymentGateway = stripePaymentGateway;
  }

  /**
   * Expires open Stripe Checkout sessions once the booking payment window has elapsed so customers
   * cannot complete payment after the cap.
   */
  public int expireCheckoutSessionsPastBookingCap(OffsetDateTime now) {
    List<PaymentEntity> pendingPayments =
        paymentRepository.findPendingStripeCardPaymentsPastBookingExpiry(now);
    if (pendingPayments == null || pendingPayments.isEmpty()) {
      return 0;
    }

    int expiredSessions = 0;
    for (PaymentEntity payment : pendingPayments) {
      String sessionId = payment.getTransactionId();
      if (sessionId == null || sessionId.isBlank()) {
        continue;
      }
      try {
        stripePaymentGateway.expireCheckoutSession(sessionId.trim());
        expiredSessions++;
        LOG.info(
            "Expired Stripe checkout session past booking cap: paymentId={}, bookingId={}, sessionId={}",
            payment.getId(),
            payment.getBooking() == null ? null : payment.getBooking().getId(),
            sessionId);
      } catch (AppException exception) {
        LOG.warn(
            "Unable to expire Stripe checkout session: paymentId={}, sessionId={}, code={}",
            payment.getId(),
            sessionId,
            exception.getCode());
      }
    }
    return expiredSessions;
  }
}
