package com.example.modulepayment.application;

import com.example.modulebooking.entity.BookingEntity;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.entity.PaymentWebhookEventEntity;
import com.example.modulepayment.gateway.PayosWebhookSupport;
import com.example.modulepayment.gateway.PayosWebhookSupport.NormalizedPayosWebhookEvent;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.modulepayment.repository.PaymentWebhookEventRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentWebhookService {

  private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

  private static final String BOOKING_STATUS_CONFIRMED = "CONFIRMED";
  private static final String BOOKING_STATUS_EXPIRED = "EXPIRED";
  private static final String EVENT_TYPE_FAILED = "PAYMENT_FAILED";
  private static final String OUTCOME_FAILED_EVENT = "FAILED_EVENT";
  private static final String OUTCOME_PROCESSED = "PROCESSED";
  private static final String OUTCOME_REJECTED = "REJECTED";
  private static final String PAYMENT_STATUS_EXPIRED = "EXPIRED";
  private static final String PAYMENT_STATUS_FAILED = "FAILED";
  private static final String PAYMENT_STATUS_PENDING = "PENDING";
  private static final String PAYMENT_STATUS_SUCCESS = "SUCCESS";
  private static final String PROVIDER_PAYOS = "PAYOS";

  private final PaymentRepository paymentRepository;
  private final PaymentWebhookEventRepository paymentWebhookEventRepository;
  private final PayosWebhookSupport payosWebhookSupport;

  public PaymentWebhookService(
      PaymentRepository paymentRepository,
      PaymentWebhookEventRepository paymentWebhookEventRepository,
      PayosWebhookSupport payosWebhookSupport) {
    this.paymentRepository = paymentRepository;
    this.paymentWebhookEventRepository = paymentWebhookEventRepository;
    this.payosWebhookSupport = payosWebhookSupport;
  }

  @Transactional
  public void handlePayosWebhook(String rawPayload) {
    NormalizedPayosWebhookEvent event = payosWebhookSupport.parseAndVerify(rawPayload);
    if (paymentWebhookEventRepository.existsByProviderAndIdempotencyKey(
        event.provider(), event.idempotencyKey())) {
      return;
    }

    LookupResult lookupResult = findPaymentForWebhook(event);
    if (lookupResult.conflict()) {
      saveAudit(null, null, event, OUTCOME_REJECTED, "PAYMENT_LOOKUP_MISMATCH", "Webhook lookup mismatch");
      return;
    }

    PaymentEntity payment = lookupResult.payment();
    if (payment == null) {
      saveAudit(
          null,
          null,
          event,
          OUTCOME_REJECTED,
          "PAYMENT_NOT_FOUND",
          "Payment not found for webhook");
      return;
    }

    BookingEntity booking = payment.getBooking();
    if (!isPending(payment)) {
      if (EVENT_TYPE_FAILED.equals(event.eventType())) {
        saveAudit(
            payment,
            booking,
            event,
            OUTCOME_REJECTED,
            "PAYMENT_NOT_PENDING",
            "Failure webhook ignored: payment is not pending");
      } else {
        saveAudit(
            payment,
            booking,
            event,
            OUTCOME_REJECTED,
            "PAYMENT_NOT_PENDING",
            "Success webhook rejected: payment is not pending");
      }
      return;
    }

    OffsetDateTime now = OffsetDateTime.now();
    payment.setUpdatedAt(now);

    if (EVENT_TYPE_FAILED.equals(event.eventType())) {
      payment.setPaymentStatus(PAYMENT_STATUS_FAILED);
      paymentRepository.save(payment);
      saveAudit(payment, booking, event, OUTCOME_FAILED_EVENT, null, null);
      return;
    }

    if (!matchesAmount(payment, event)) {
      payment.setPaymentStatus(PAYMENT_STATUS_FAILED);
      paymentRepository.save(payment);
      saveAudit(
          payment,
          booking,
          event,
          OUTCOME_REJECTED,
          "PAYMENT_AMOUNT_MISMATCH",
          "Webhook amount does not match payment amount");
      return;
    }

    if (!matchesCurrency(payment, event)) {
      payment.setPaymentStatus(PAYMENT_STATUS_FAILED);
      paymentRepository.save(payment);
      saveAudit(
          payment,
          booking,
          event,
          OUTCOME_REJECTED,
          "PAYMENT_CURRENCY_MISMATCH",
          "Webhook currency does not match payment currency");
      return;
    }

    if (bookingExpired(booking, event)) {
      payment.setPaymentStatus(PAYMENT_STATUS_EXPIRED);
      booking.setStatus(BOOKING_STATUS_EXPIRED);
      booking.setUpdatedAt(now);
      paymentRepository.save(payment);
      saveAudit(payment, booking, event, OUTCOME_REJECTED, "BOOKING_EXPIRED", "Booking expired");
      return;
    }

    payment.setPaymentStatus(PAYMENT_STATUS_SUCCESS);
    payment.setPaidAt(event.occurredAt());
    if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
      payment.setTransactionId(event.paymentLinkId());
    }
    booking.setStatus(BOOKING_STATUS_CONFIRMED);
    booking.setUpdatedAt(now);
    paymentRepository.save(payment);
    saveAudit(payment, booking, event, OUTCOME_PROCESSED, null, null);

    log.info(
        "PayOS webhook processed: paymentId={}, bookingId={}, transactionId={}, eventType={}, outcome={}",
        payment.getId(),
        booking.getId(),
        event.paymentLinkId(),
        event.eventType(),
        OUTCOME_PROCESSED);
  }

  private LookupResult findPaymentForWebhook(NormalizedPayosWebhookEvent event) {
    PaymentEntity byTransactionId = null;
    PaymentEntity byOrderCode = null;
    if (event.paymentLinkId() != null && !event.paymentLinkId().isBlank()) {
      byTransactionId =
          paymentRepository.findByTransactionIdForUpdate(event.paymentLinkId()).orElse(null);
    }
    if (event.orderCode() != null) {
      byOrderCode = paymentRepository.findByIdForUpdate(event.orderCode()).orElse(null);
    }

    // if both transactionId and orderCode are present in the webhook and they point to different payments, we have a mismatch and should reject the webhook to avoid potential fraud or data corruption. Otherwise, if only one of them is present, or both are present and point to the same payment, we can proceed with that payment.
    if (byTransactionId != null
        && byOrderCode != null
        && !Objects.equals(byTransactionId.getId(), byOrderCode.getId())) {
      return new LookupResult(null, true);
    }

    return new LookupResult(byTransactionId != null ? byTransactionId : byOrderCode, false);
  }

  private boolean matchesAmount(PaymentEntity payment, NormalizedPayosWebhookEvent event) {
    if (event.amount() == null) {
      return false;
    }
    BigDecimal eventAmount = BigDecimal.valueOf(event.amount());
    return payment.getAmount() != null && payment.getAmount().compareTo(eventAmount) == 0;
  }

  private boolean matchesCurrency(PaymentEntity payment, NormalizedPayosWebhookEvent event) {
    return payment.getCurrency() != null
        && event.currency() != null
        && payment.getCurrency().equalsIgnoreCase(event.currency());
  }

  private boolean bookingExpired(BookingEntity booking, NormalizedPayosWebhookEvent event) {
    return booking.getPnrExpiresAt() != null && event.occurredAt().isAfter(booking.getPnrExpiresAt());
  }

  private boolean isPending(PaymentEntity payment) {
    String status = payment.getPaymentStatus();
    return status != null && PAYMENT_STATUS_PENDING.equalsIgnoreCase(status.trim());
  }

  private void saveAudit(
      PaymentEntity payment,
      BookingEntity booking,
      NormalizedPayosWebhookEvent event,
      String processingStatus,
      String errorCode,
      String errorMessage) {
    PaymentWebhookEventEntity audit = new PaymentWebhookEventEntity();
    audit.setProvider(PROVIDER_PAYOS);
    audit.setIdempotencyKey(event.idempotencyKey());
    audit.setPayment(payment);
    audit.setBooking(booking);
    audit.setTransactionId(event.paymentLinkId());
    audit.setOrderCode(event.orderCode());
    audit.setEventType(event.eventType());
    audit.setProcessingStatus(processingStatus);
    audit.setFinal(true);
    audit.setSignature(event.signature());
    audit.setSignatureValid(true);
    audit.setRawPayload(event.rawPayload());
    audit.setErrorCode(errorCode);
    audit.setErrorMessage(errorMessage);
    audit.setReceivedAt(event.occurredAt());
    audit.setProcessedAt(OffsetDateTime.now());
    paymentWebhookEventRepository.save(audit);
  }

  private record LookupResult(PaymentEntity payment, boolean conflict) {}
}
