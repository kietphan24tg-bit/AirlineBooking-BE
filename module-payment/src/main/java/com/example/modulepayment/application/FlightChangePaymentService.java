package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.FlightChangeSettlementEntity;
import com.example.modulepayment.entity.PaymentEntity;
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

@Service
public class FlightChangePaymentService {

  private static final String METHOD_PAYOS = "PAYOS";
  private static final String METHOD_PAYOS_QR = "PAYOS_QR";
  private static final String METHOD_STRIPE = "STRIPE";
  private static final String METHOD_STRIPE_CARD = "STRIPE_CARD";
  private static final String PAYMENT_STATUS_CANCELLED = "CANCELLED";
  private static final String PAYMENT_STATUS_PENDING = "PENDING";
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

    OffsetDateTime now = OffsetDateTime.now();
    List<PaymentEntity> previousPending =
        paymentRepository.findByFlightChangeSettlementIdAndPaymentStatusInForUpdateOrderByLatest(
            settlement.getId(), List.of(PAYMENT_STATUS_PENDING));
    for (PaymentEntity existing : previousPending) {
      cancelWithProvider(existing, "Payment method changed");
      existing.setPaymentStatus(PAYMENT_STATUS_CANCELLED);
      existing.setUpdatedAt(now);
      paymentRepository.save(existing);
    }

    PaymentEntity payment = new PaymentEntity();
    payment.setBooking(settlement.getBooking());
    payment.setFlightChangeSettlement(settlement);
    payment.setAmount(settlement.getNetAmount());
    payment.setCurrency(settlement.getCurrency());
    payment.setPaymentMethod(normalizedMethod);
    payment.setPaymentStatus(PAYMENT_STATUS_PENDING);
    payment.setType(METHOD_PAYOS.equals(normalizedMethod) ? "QR" : "CHECKOUT");
    OffsetDateTime expireAt = resolveExpireAt(normalizedMethod, now);
    payment.setSessionExpiresAt(expireAt);
    payment.setCreatedAt(now);
    payment.setUpdatedAt(now);
    paymentRepository.saveAndFlush(payment);

    PaymentGateway.PaymentGatewayResult gatewayResult;
    if (METHOD_PAYOS.equals(normalizedMethod)) {
      gatewayResult =
          payosPaymentGateway.createPaymentLink(
              buildGatewayRequest(payment, settlement, normalizedMethod, expireAt));
    } else {
      gatewayResult =
          stripePaymentGateway.createCheckoutSession(
              buildGatewayRequest(payment, settlement, normalizedMethod, expireAt));
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
      OffsetDateTime expireAt) {
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
        Map.of(
            "purpose", PAYMENT_PURPOSE,
            "paymentId", String.valueOf(payment.getId()),
            "bookingId", String.valueOf(settlement.getBooking().getId()),
            "settlementId", String.valueOf(settlement.getId())));
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
