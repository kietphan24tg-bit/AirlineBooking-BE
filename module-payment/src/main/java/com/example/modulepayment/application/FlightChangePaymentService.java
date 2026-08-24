package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.FlightChangeSettlementEntity;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.exception.PaymentExecutionUnknownException;
import com.example.modulepayment.gateway.PaymentGateway;
import com.example.modulepayment.gateway.PayosPaymentGateway;
import com.example.modulepayment.gateway.StripePaymentGateway;
import com.example.modulepayment.repository.PaymentRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlightChangePaymentService {

  private static final String METHOD_PAYOS = "PAYOS";
  private static final String METHOD_PAYOS_QR = "PAYOS_QR";
  private static final String METHOD_STRIPE = "STRIPE";
  private static final String METHOD_STRIPE_CARD = "STRIPE_CARD";
  private static final String PAYMENT_STATUS_CANCELLED = "CANCELLED";
  private static final String PAYMENT_STATUS_PENDING = "PENDING";
  private static final String PAYMENT_STATUS_UNKNOWN = "UNKNOWN";
  private static final String PAYMENT_PURPOSE = "FLIGHT_CHANGE";

  private final FlightChangeSettlementService flightChangeSettlementService;
  private final PaymentRepository paymentRepository;
  private final PayosPaymentGateway payosPaymentGateway;
  private final StripePaymentGateway stripePaymentGateway;
  private final PaymentProperties paymentProperties;

  public FlightChangePaymentService(
      FlightChangeSettlementService flightChangeSettlementService,
      PaymentRepository paymentRepository,
      PayosPaymentGateway payosPaymentGateway,
      StripePaymentGateway stripePaymentGateway,
      PaymentProperties paymentProperties) {
    this.flightChangeSettlementService = flightChangeSettlementService;
    this.paymentRepository = paymentRepository;
    this.payosPaymentGateway = payosPaymentGateway;
    this.stripePaymentGateway = stripePaymentGateway;
    this.paymentProperties = paymentProperties;
  }

  public CreatePaymentResult createPayment(
      Long bookingId, Long settlementId, Long userId, String method) {
    return doCreatePayment(bookingId, settlementId, userId, method, null);
  }

  @Transactional(noRollbackFor = PaymentExecutionUnknownException.class)
  public CreatePaymentResult createPayment(
      Long bookingId, Long settlementId, Long userId, String method, String idempotencyKey) {
    return doCreatePayment(bookingId, settlementId, userId, method, idempotencyKey);
  }

  private CreatePaymentResult doCreatePayment(
      Long bookingId, Long settlementId, Long userId, String method, String idempotencyKey) {
    String normalizedMethod = normalizeMethod(method);
    if (!METHOD_PAYOS.equals(normalizedMethod) && !METHOD_STRIPE_CARD.equals(normalizedMethod)) {
      throw new AppException("UNSUPPORTED_PAYMENT_METHOD", "Payment method is not supported", 400);
    }
    FlightChangeSettlementEntity settlement =
        flightChangeSettlementService.requireSettlementForPayment(bookingId, settlementId, userId);
    if (settlement.getNetAmount() == null || settlement.getNetAmount().signum() <= 0) {
      throw new AppException(
          "FLIGHT_CHANGE_SETTLEMENT_NOT_PAYABLE",
          "Flight change settlement does not require payment",
          409);
    }

    String requestKey = PaymentRequestKeySupport.normalize(idempotencyKey);
    PaymentEntity existing = PaymentRequestKeySupport.findExisting(paymentRepository, requestKey);
    if (existing != null) {
      PaymentRequestKeySupport.validateExisting(
          existing,
          settlement.getBooking().getId(),
          normalizedMethod,
          existing.getFlightChangeSettlement() != null
              && settlement.getId().equals(existing.getFlightChangeSettlement().getId()));
      return PaymentRequestKeySupport.toResult(existing);
    }

    OffsetDateTime now = OffsetDateTime.now();
    List<PaymentEntity> previousPending =
        paymentRepository.findByFlightChangeSettlementIdAndPaymentStatusInForUpdateOrderByLatest(
            settlement.getId(), List.of(PAYMENT_STATUS_PENDING, PAYMENT_STATUS_UNKNOWN));
    if (previousPending.stream()
        .anyMatch(payment -> PAYMENT_STATUS_UNKNOWN.equalsIgnoreCase(payment.getPaymentStatus()))) {
      throw new PaymentExecutionUnknownException();
    }
    for (PaymentEntity pendingPayment : previousPending) {
      cancelWithProvider(pendingPayment, "Payment method changed");
      pendingPayment.setPaymentStatus(PAYMENT_STATUS_CANCELLED);
      pendingPayment.setUpdatedAt(now);
      paymentRepository.save(pendingPayment);
    }

    PaymentEntity payment = new PaymentEntity();
    payment.setBooking(settlement.getBooking());
    payment.setFlightChangeSettlement(settlement);
    payment.setAmount(settlement.getNetAmount());
    payment.setCurrency(settlement.getCurrency());
    payment.setPaymentMethod(normalizedMethod);
    payment.setPaymentStatus(PAYMENT_STATUS_PENDING);
    payment.setRequestKey(requestKey);
    payment.setType(METHOD_PAYOS.equals(normalizedMethod) ? "QR" : "CHECKOUT");
    OffsetDateTime expireAt = resolveExpireAt(normalizedMethod, now);
    payment.setSessionExpiresAt(expireAt);
    payment.setCreatedAt(now);
    payment.setUpdatedAt(now);
    paymentRepository.saveAndFlush(payment);

    PaymentGateway.PaymentGatewayResult gatewayResult;
    try {
      if (METHOD_PAYOS.equals(normalizedMethod)) {
        gatewayResult =
            payosPaymentGateway.createPaymentLink(
                buildGatewayRequest(payment, settlement, normalizedMethod, expireAt, requestKey));
      } else {
        gatewayResult =
            stripePaymentGateway.createCheckoutSession(
                buildGatewayRequest(payment, settlement, normalizedMethod, expireAt, requestKey));
      }
    } catch (RuntimeException exception) {
      boolean executionUnknown =
          requestKey != null && PaymentRequestKeySupport.isProviderResultUnknown(exception);
      payment.setPaymentStatus(executionUnknown ? "UNKNOWN" : "FAILED");
      payment.setUpdatedAt(OffsetDateTime.now());
      paymentRepository.save(payment);
      if (executionUnknown) {
        throw new PaymentExecutionUnknownException();
      }
      throw exception;
    }

    payment.setTransactionId(gatewayResult.paymentLinkId());
    payment.setQrData(gatewayResult.qrCode());
    payment.setCheckoutUrl(gatewayResult.checkoutUrl());
    payment.setUpdatedAt(OffsetDateTime.now());
    paymentRepository.save(payment);
    flightChangeSettlementService.markPaymentCreated(settlement);

    return new CreatePaymentResult(
        new CreatePaymentResult.PaymentView(
            payment.getId(),
            payment.getBooking().getId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getPaymentMethod(),
            payment.getPaymentStatus(),
            payment.getSessionExpiresAt(),
            payment.getTransactionId(),
            payment.getQrData()),
        new CreatePaymentResult.PaymentActionView(
            gatewayResult.actionType(), gatewayResult.qrCode(), null, gatewayResult.checkoutUrl()));
  }

  private PaymentGateway.PaymentGatewayRequest buildGatewayRequest(
      PaymentEntity payment,
      FlightChangeSettlementEntity settlement,
      String method,
      OffsetDateTime expireAt,
      String requestKey) {
    return new PaymentGateway.PaymentGatewayRequest(
        payment.getId(),
        payment.getId(),
        payment.getAmount(),
        payment.getCurrency(),
        "Flight change payment " + settlement.getBooking().getPnrCode(),
        buildFlightChangeReturnUrl(paymentProperties.returnUrl(), payment.getId()),
        buildFlightChangeCancelUrl(paymentProperties.cancelUrl(), payment.getId()),
        settlement.getBooking().getPnrCode(),
        settlement.getBooking().getContactEmail(),
        settlement.getBooking().getContactPhone(),
        expireAt,
        settlement.getBooking().getId(),
        METHOD_PAYOS.equals(method) ? METHOD_PAYOS : METHOD_STRIPE,
        "Flight change",
        "Flight change payment for booking " + settlement.getBooking().getPnrCode(),
        List.of(
            new PaymentGateway.PaymentLineItem(
                "Flight change",
                "Flight change payment for booking " + settlement.getBooking().getPnrCode(),
                payment.getAmount())),
        PaymentRequestKeySupport.metadata(
            Map.of(
                "purpose", PAYMENT_PURPOSE,
                "paymentId", String.valueOf(payment.getId()),
                "bookingId", String.valueOf(settlement.getBooking().getId()),
                "settlementId", String.valueOf(settlement.getId())),
            requestKey),
        requestKey);
  }

  private OffsetDateTime resolveExpireAt(String method, OffsetDateTime now) {
    return METHOD_PAYOS.equals(method) ? now.plusMinutes(15) : now.plusMinutes(30);
  }

  private void cancelWithProvider(PaymentEntity payment, String cancellationReason) {
    if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
      return;
    }
    if (METHOD_STRIPE_CARD.equalsIgnoreCase(payment.getPaymentMethod())) {
      stripePaymentGateway.expireCheckoutSession(payment.getTransactionId());
      return;
    }
    payosPaymentGateway.cancelPaymentLink(
        new PaymentGateway.CancelPaymentLinkRequest(
            payment.getId(), payment.getId(), cancellationReason));
  }

  private String appendPaymentId(String url, Long paymentId) {
    if (url == null || url.isBlank() || paymentId == null) {
      return url;
    }
    String separator = url.contains("?") ? "&" : "?";
    return url + separator + "paymentId=" + paymentId;
  }

  private String buildFlightChangeReturnUrl(String baseUrl, Long paymentId) {
    return buildFlightChangeFlowUrl(baseUrl, paymentId, true);
  }

  private String buildFlightChangeCancelUrl(String baseUrl, Long paymentId) {
    return buildFlightChangeFlowUrl(baseUrl, paymentId, false);
  }

  private String buildFlightChangeFlowUrl(String baseUrl, Long paymentId, boolean success) {
    String fallback = appendPaymentId(baseUrl, paymentId);
    if (baseUrl == null || baseUrl.isBlank()) {
      return fallback;
    }
    try {
      URI baseUri = URI.create(baseUrl);
      String existingQuery = baseUri.getQuery();
      String flowQuery =
          (success ? "flightChangeReturn=1" : "flightChangeReturn=1") + "&paymentId=" + paymentId;
      String query =
          existingQuery == null || existingQuery.isBlank()
              ? flowQuery
              : existingQuery + "&" + flowQuery;
      return new URI(
              baseUri.getScheme(),
              baseUri.getUserInfo(),
              baseUri.getHost(),
              baseUri.getPort(),
              "/manage-booking/change-flight",
              query,
              baseUri.getFragment())
          .toString();
    } catch (IllegalArgumentException | URISyntaxException exception) {
      return fallback;
    }
  }

  private String normalizeMethod(String method) {
    if (method == null || method.isBlank()) {
      return "";
    }
    if (METHOD_PAYOS_QR.equalsIgnoreCase(method)) {
      return METHOD_PAYOS;
    }
    if (METHOD_STRIPE.equalsIgnoreCase(method)) {
      return METHOD_STRIPE_CARD;
    }
    return method.trim().toUpperCase(Locale.ROOT);
  }
}
