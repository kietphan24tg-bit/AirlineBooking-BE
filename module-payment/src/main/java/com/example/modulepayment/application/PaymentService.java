package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.exception.BookingNotFoundException;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.exception.PaymentNotFoundException;
import com.example.modulepayment.gateway.PaymentGateway;
import com.example.modulepayment.gateway.PaymentGateway.PaymentGatewayRequest;
import com.example.modulepayment.gateway.PaymentGateway.PaymentGatewayResult;
import com.example.modulepayment.gateway.PaymentGateway.PaymentLineItem;
import com.example.modulepayment.gateway.StripePaymentGateway;
import com.example.modulepayment.repository.BookingRepository;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PaymentService {
  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private static final String METHOD_PAYOS = "PAYOS";
  private static final String METHOD_PAYOS_QR = "PAYOS_QR";
  private static final String METHOD_STRIPE = "STRIPE";
  private static final String METHOD_STRIPE_CARD = "STRIPE_CARD";
  private static final String METHOD_VNPAY = "VNPAY";
  private static final String METHOD_MOMO = "MOMO";
  private static final String METHOD_PAY_LATER = "PAY_LATER";
  private static final String PAYMENT_STATUS_FAILED = "FAILED";
  private static final String PAYMENT_STATUS_PENDING = "PENDING";
  private static final String PAYMENT_STATUS_CANCELLED = "CANCELLED";
  private static final String PAYMENT_ACTION_QR = "QR";
  private static final String PAYMENT_ACTION_CHECKOUT = "CHECKOUT";
  private static final String BOOKING_STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
  private static final long PAYOS_PAYMENT_WINDOW_MINUTES = 5;
  private static final long STRIPE_PAYMENT_WINDOW_MINUTES = 30;
  private static final long PAY_LATER_HOLD_MINUTES = 60;
  private static final long PAYMENT_CREATION_COOLDOWN_SECONDS = 15;
  private static final long PAYMENT_CREATION_WINDOW_MINUTES = 10;
  private static final int PAYMENT_CREATION_LIMIT_PER_WINDOW = 5;
  private static final String DEFAULT_CANCELLATION_REASON = "User requested cancellation";

  private final BookingRepository bookingRepository;
  private final PaymentRepository paymentRepository;
  private final PaymentGateway payosPaymentGateway;
  private final StripePaymentGateway stripePaymentGateway;
  private final PaymentProperties paymentProperties;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final FlightSeatRepository flightSeatRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;

  public PaymentService(
      BookingRepository bookingRepository,
      PaymentRepository paymentRepository,
      @Qualifier("payosPaymentGateway") PaymentGateway payosPaymentGateway,
      StripePaymentGateway stripePaymentGateway,
      PaymentProperties paymentProperties,
      BookingFareHoldRepository bookingFareHoldRepository,
      FlightSeatRepository flightSeatRepository,
      BookingServiceRepository bookingServiceRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository) {
    this.bookingRepository = bookingRepository;
    this.paymentRepository = paymentRepository;
    this.payosPaymentGateway = payosPaymentGateway;
    this.stripePaymentGateway = stripePaymentGateway;
    this.paymentProperties = paymentProperties;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.flightSeatRepository = flightSeatRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
  }

  @Transactional(readOnly = true)
  public List<PaymentMethodView> getPaymentMethods(
      Long bookingId, Long userId, boolean onlySupported) {
    BookingEntity booking = requirePayableBooking(bookingId, userId);
    return buildPaymentMethodsForBooking(booking, onlySupported);
  }

  @Transactional(readOnly = true)
  public List<PaymentMethodView> getPaymentMethodsPublic(Long bookingId, boolean onlySupported) {
    BookingEntity booking = requirePayableBookingPublic(bookingId);
    return buildPaymentMethodsForBooking(booking, onlySupported);
  }

  private List<PaymentMethodView> buildPaymentMethodsForBooking(
      BookingEntity booking, boolean onlySupported) {
    OffsetDateTime now = OffsetDateTime.now();
    boolean payosWindowEligible = hasValidEffectivePaymentWindow(booking, METHOD_PAYOS, now);
    boolean stripeWindowEligible = hasValidEffectivePaymentWindow(booking, METHOD_STRIPE_CARD, now);
    boolean payosSupported =
        paymentProperties.isPayosConfigured()
            && supportsCurrency(booking.getCurrency())
            && payosWindowEligible;
    boolean stripeSupported =
        paymentProperties.isStripeConfigured()
            && supportsCurrency(booking.getCurrency())
            && stripeWindowEligible;
    boolean payLaterSupported =
        paymentProperties.isPayosConfigured() && supportsCurrency(booking.getCurrency());
    String vietqrIcon = iconUrl("/assets/payment-icons/vietqr.svg");
    String stripeIcon = iconUrl("/assets/payment-icons/stripe.svg");
    String vnpayIcon = iconUrl("/assets/payment-icons/vnpay.svg");
    String momoIcon = iconUrl("/assets/payment-icons/momo.svg");
    List<PaymentMethodView> groups =
        List.of(
            new PaymentMethodView(
                "CARD",
                "Thẻ & ngân hàng",
                List.of(
                    new PaymentMethodView.PaymentMethodItemView(
                        METHOD_STRIPE_CARD,
                        "Thẻ tín dụng / ghi nợ quốc tế",
                        "STRIPE",
                        stripeIcon,
                        stripeSupported,
                        null),
                    new PaymentMethodView.PaymentMethodItemView(
                        METHOD_PAYOS_QR,
                        "Mobile Banking (VietQR)",
                        "PAYOS",
                        vietqrIcon,
                        payosSupported,
                        null),
                    new PaymentMethodView.PaymentMethodItemView(
                        METHOD_VNPAY, "Thanh toán VNPay", "VNPAY", vnpayIcon, false, null))),
            new PaymentMethodView(
                "EWALLET",
                "Ví điện tử",
                List.of(
                    new PaymentMethodView.PaymentMethodItemView(
                        METHOD_MOMO, "Ví MoMo", "MOMO", momoIcon, false, null))),
            new PaymentMethodView(
                "PAY_LATER",
                "Thanh toán sau",
                List.of(
                    new PaymentMethodView.PaymentMethodItemView(
                        METHOD_PAY_LATER,
                        "Giữ chỗ, thanh toán sau",
                        "PAYOS",
                        vietqrIcon,
                        payLaterSupported,
                        new PaymentMethodView.PaymentMethodMetaView(
                            (int) PAY_LATER_HOLD_MINUTES, 0)))));
    return onlySupported ? filterSupportedOnly(groups) : groups;
  }

  private String iconUrl(String path) {
    String base = paymentProperties.iconBaseUrl();
    if (base == null || base.isBlank()) {
      return path;
    }
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return normalizedBase + path;
  }

  /**
   * Returns payment status for the authenticated owner of the related booking.
   *
   * <p>Wrong owner returns {@link PaymentNotFoundException} (404) to avoid leaking existence.
   */
  @Transactional(readOnly = true)
  public PaymentStatusView getPaymentForUser(Long paymentId, Long userId) {
    PaymentEntity payment =
        paymentRepository
            .findByIdAndBookingUserId(paymentId, userId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    BookingEntity booking = payment.getBooking();
    return new PaymentStatusView(
        payment.getId(),
        booking.getId(),
        payment.getAmount(),
        payment.getCurrency(),
        payment.getPaymentMethod(),
        payment.getPaymentStatus(),
        payment.getPaidAt(),
        booking.getPnrExpiresAt(),
        payment.getTransactionId(),
        payment.getQrData(),
        payment.getQrImageUrl(),
        payment.getCheckoutUrl(),
        buildPaymentDetails(booking));
  }

  @Transactional(readOnly = true)
  public PaymentStatusView getPaymentPublic(Long paymentId) {
    PaymentEntity payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    BookingEntity booking = payment.getBooking();
    return new PaymentStatusView(
        payment.getId(),
        booking.getId(),
        payment.getAmount(),
        payment.getCurrency(),
        payment.getPaymentMethod(),
        payment.getPaymentStatus(),
        payment.getPaidAt(),
        booking.getPnrExpiresAt(),
        payment.getTransactionId(),
        payment.getQrData(),
        payment.getQrImageUrl(),
        payment.getCheckoutUrl(),
        buildPaymentDetails(booking));
  }

  @Transactional
  public PaymentStatusView cancelPaymentForUser(Long paymentId, Long userId, String reason) {
    PaymentEntity payment =
        paymentRepository
            .findByIdAndBookingUserIdForUpdate(paymentId, userId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));

    String status = payment.getPaymentStatus() == null ? "" : payment.getPaymentStatus().trim();
    if (PAYMENT_STATUS_CANCELLED.equalsIgnoreCase(status)) {
      return buildStatusView(payment);
    }
    if (!PAYMENT_STATUS_PENDING.equalsIgnoreCase(status)) {
      throw new AppException(
          "PAYMENT_NOT_CANCELLABLE", "Only pending payments can be cancelled", 409);
    }

    String cancellationReason =
        (reason == null || reason.isBlank()) ? DEFAULT_CANCELLATION_REASON : reason.trim();

    cancelWithProvider(payment, cancellationReason);

    OffsetDateTime now = OffsetDateTime.now();
    payment.setPaymentStatus(PAYMENT_STATUS_CANCELLED);
    payment.setUpdatedAt(now);
    paymentRepository.save(payment);

    return buildStatusView(payment);
  }

  @Transactional
  public PaymentStatusView cancelPaymentPublic(Long paymentId, String reason) {
    PaymentEntity payment =
        paymentRepository
            .findByIdForUpdate(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));

    String status = payment.getPaymentStatus() == null ? "" : payment.getPaymentStatus().trim();
    if (PAYMENT_STATUS_CANCELLED.equalsIgnoreCase(status)) {
      return buildStatusView(payment);
    }
    if (!PAYMENT_STATUS_PENDING.equalsIgnoreCase(status)) {
      throw new AppException(
          "PAYMENT_NOT_CANCELLABLE", "Only pending payments can be cancelled", 409);
    }

    String cancellationReason =
        (reason == null || reason.isBlank()) ? DEFAULT_CANCELLATION_REASON : reason.trim();

    cancelWithProvider(payment, cancellationReason);

    OffsetDateTime now = OffsetDateTime.now();
    payment.setPaymentStatus(PAYMENT_STATUS_CANCELLED);
    payment.setUpdatedAt(now);
    paymentRepository.save(payment);

    return buildStatusView(payment);
  }

  private PaymentStatusView buildStatusView(PaymentEntity payment) {
    BookingEntity booking = payment.getBooking();
    return new PaymentStatusView(
        payment.getId(),
        booking == null ? null : booking.getId(),
        payment.getAmount(),
        payment.getCurrency(),
        payment.getPaymentMethod(),
        payment.getPaymentStatus(),
        payment.getPaidAt(),
        booking == null ? null : booking.getPnrExpiresAt(),
        payment.getTransactionId(),
        payment.getQrData(),
        payment.getQrImageUrl(),
        payment.getCheckoutUrl(),
        booking == null ? null : buildPaymentDetails(booking));
  }

  private static List<PaymentMethodView> filterSupportedOnly(List<PaymentMethodView> groups) {
    return groups.stream()
        .map(
            group ->
                new PaymentMethodView(
                    group.type(),
                    group.label(),
                    group.methods().stream()
                        .filter(PaymentMethodView.PaymentMethodItemView::supported)
                        .toList()))
        .filter(group -> !group.methods().isEmpty())
        .toList();
  }

  @Transactional
  public CreatePaymentResult createPayment(CreatePaymentCommand command) {
    return createPayment(command, true);
  }

  @Transactional
  public CreatePaymentResult createPaymentPublic(CreatePaymentCommand command) {
    return createPayment(command, false);
  }

  private CreatePaymentResult createPayment(CreatePaymentCommand command, boolean requireOwner) {
    String normalizedMethod = normalizeMethod(command.method());
    if (!METHOD_PAYOS.equals(normalizedMethod)
        && !METHOD_STRIPE_CARD.equals(normalizedMethod)
        && !METHOD_PAY_LATER.equals(normalizedMethod)) {
      throw new AppException("UNSUPPORTED_PAYMENT_METHOD", "Payment method is not supported", 400);
    }

    assertProviderConfigured(normalizedMethod);

    BookingEntity booking =
        requireOwner
            ? requirePayableBookingForUpdate(command.bookingId(), command.userId())
            : requirePayableBookingForUpdatePublic(command.bookingId());
    if (!supportsCurrency(booking.getCurrency())) {
      throw new AppException(
          "UNSUPPORTED_PAYMENT_CURRENCY", "Only VND payments are supported in phase 1", 400);
    }

    OffsetDateTime now = OffsetDateTime.now();
    List<PaymentEntity> previousPending =
        paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(
            booking.getId(), PAYMENT_STATUS_PENDING);
    PaymentEntity latestReusablePending =
        findLatestReusablePending(previousPending, normalizedMethod);
    OffsetDateTime expireAt =
        resolveEffectiveExpireAt(booking, normalizedMethod, now, latestReusablePending != null);
    assertSufficientPaymentWindow(booking, normalizedMethod, expireAt, now);

    PaymentEntity reusablePending =
        findReusablePending(previousPending, normalizedMethod, booking.getPnrExpiresAt(), expireAt);
    boolean shouldReusePending =
        reusablePending != null && Objects.equals(booking.getPnrExpiresAt(), expireAt);

    if (shouldReusePending) {
      log.info(
          "Reusing pending payment: bookingId={}, paymentId={}, requestedMethod={}, paymentMethod={}",
          booking.getId(),
          reusablePending.getId(),
          normalizedMethod,
          reusablePending.getPaymentMethod());
      ensureBookingPendingPayment(booking, now);
      return buildCreatePaymentResult(reusablePending, booking, expireAt);
    }

    applyEffectiveExpireAt(booking, expireAt, now);

    assertPaymentCreationAllowed(booking, normalizedMethod, now);

    for (PaymentEntity existing : previousPending) {
      if (isSamePaymentMethod(existing, normalizedMethod)) {
        if (isReusablePending(existing)) {
          cancelWithProvider(existing, "Payment refreshed");
          existing.setPaymentStatus(PAYMENT_STATUS_CANCELLED);
        } else {
          existing.setPaymentStatus(PAYMENT_STATUS_FAILED);
        }
      } else if (isReusablePending(existing)) {
        log.info(
            "Cancelling previous pending payment because payment method changed: bookingId={}, oldPaymentId={}, oldMethod={}, newMethod={}",
            booking.getId(),
            existing.getId(),
            existing.getPaymentMethod(),
            normalizedMethod);
        cancelWithProvider(existing, "Payment method changed");
        existing.setPaymentStatus(PAYMENT_STATUS_CANCELLED);
      } else {
        existing.setPaymentStatus(PAYMENT_STATUS_FAILED);
      }
      existing.setUpdatedAt(now);
    }

    PaymentEntity payment = new PaymentEntity();
    payment.setBooking(booking);
    payment.setAmount(normalizeAmount(booking.getTotalAmount()));
    payment.setCurrency(booking.getCurrency());
    payment.setPaymentMethod(normalizedMethod);
    payment.setPaymentStatus(PAYMENT_STATUS_PENDING);
    payment.setType(actionTypeForMethod(normalizedMethod));
    payment.setCreatedAt(now);
    payment.setUpdatedAt(now);
    paymentRepository.saveAndFlush(payment);
    log.info(
        "Created pending payment record: bookingId={}, paymentId={}, method={}, amount={}, currency={}",
        booking.getId(),
        payment.getId(),
        normalizedMethod,
        payment.getAmount(),
        payment.getCurrency());

    PaymentGatewayResult gatewayResult;
    try {
      gatewayResult =
          gatewayFor(normalizedMethod)
              .createPaymentLink(
                  new PaymentGatewayRequest(
                      payment.getId(),
                      payment.getId(),
                      payment.getAmount(),
                      payment.getCurrency(),
                      buildDescription(payment.getId()),
                      appendPaymentId(paymentProperties.returnUrl(), payment.getId()),
                      appendPaymentId(paymentProperties.cancelUrl(), payment.getId()),
                      booking.getPnrCode(),
                      booking.getContactEmail(),
                      booking.getContactPhone(),
                      expireAt,
                      booking.getId(),
                      providerForMethod(normalizedMethod),
                      buildProductName(booking),
                      buildProductDescription(booking),
                      buildStripeLineItems(booking),
                      buildStripeMetadata(booking)));
    } catch (RuntimeException exception) {
      payment.setPaymentStatus(PAYMENT_STATUS_FAILED);
      payment.setUpdatedAt(OffsetDateTime.now());
      paymentRepository.save(payment);
      throw exception;
    }

    payment.setTransactionId(gatewayResult.paymentLinkId());
    payment.setQrData(gatewayResult.qrCode());
    payment.setType(gatewayResult.actionType());
    payment.setCheckoutUrl(gatewayResult.checkoutUrl());
    payment.setUpdatedAt(OffsetDateTime.now());
    paymentRepository.save(payment);

    ensureBookingPendingPayment(booking, OffsetDateTime.now());
    return buildCreatePaymentResult(payment, booking, expireAt);
  }

  private void assertPaymentCreationAllowed(
      BookingEntity booking, String normalizedMethod, OffsetDateTime now) {
    OffsetDateTime windowStart = now.minusMinutes(PAYMENT_CREATION_WINDOW_MINUTES);
    List<PaymentEntity> recentPayments =
        paymentRepository.findByBookingIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            booking.getId(), windowStart);
    if (recentPayments == null || recentPayments.isEmpty()) {
      return;
    }

    PaymentEntity newestPaymentForMethod =
        recentPayments.stream()
            .filter(payment -> isSamePaymentMethod(payment, normalizedMethod))
            .filter(payment -> payment.getCreatedAt() != null)
            .findFirst()
            .orElse(null);
    if (newestPaymentForMethod != null
        && newestPaymentForMethod
            .getCreatedAt()
            .isAfter(now.minusSeconds(PAYMENT_CREATION_COOLDOWN_SECONDS))) {
      log.info(
          "Blocked payment creation by cooldown: bookingId={}, latestPaymentId={}, latestCreatedAt={}, requestedMethod={}",
          booking.getId(),
          newestPaymentForMethod.getId(),
          newestPaymentForMethod.getCreatedAt(),
          normalizedMethod);
      throw new AppException(
          "PAYMENT_CREATION_COOLDOWN",
          "Please wait 15 seconds before creating another payment for this booking",
          429);
    }

    if (recentPayments.size() >= PAYMENT_CREATION_LIMIT_PER_WINDOW) {
      log.info(
          "Blocked payment creation by rate limit: bookingId={}, requestedMethod={}, attemptsInWindow={}, windowMinutes={}",
          booking.getId(),
          normalizedMethod,
          recentPayments.size(),
          PAYMENT_CREATION_WINDOW_MINUTES);
      throw new AppException(
          "PAYMENT_CREATION_LIMIT_EXCEEDED",
          "You have reached the payment creation limit for this booking. Please wait a few minutes before trying again",
          429);
    }
  }

  private OffsetDateTime resolveEffectiveExpireAt(
      BookingEntity booking,
      String method,
      OffsetDateTime now,
      boolean preserveLongerCurrentWindow) {
    if (booking == null) {
      return null;
    }
    OffsetDateTime targetExpireAt = resolveTargetExpireAt(method, now);
    OffsetDateTime cappedExpireAt = capExpireAt(booking, method, targetExpireAt);
    if (cappedExpireAt == null) {
      return booking.getPnrExpiresAt();
    }
    if (preserveLongerCurrentWindow
        && booking.getPnrExpiresAt() != null
        && booking.getPnrExpiresAt().isAfter(cappedExpireAt)) {
      return booking.getPnrExpiresAt();
    }
    return cappedExpireAt;
  }

  private OffsetDateTime resolveTargetExpireAt(String method, OffsetDateTime now) {
    if (METHOD_STRIPE_CARD.equals(method)) {
      return now.plusMinutes(STRIPE_PAYMENT_WINDOW_MINUTES);
    }
    if (METHOD_PAY_LATER.equals(method)) {
      return now.plusMinutes(PAY_LATER_HOLD_MINUTES);
    }
    return now.plusMinutes(PAYOS_PAYMENT_WINDOW_MINUTES);
  }

  private OffsetDateTime capExpireAt(
      BookingEntity booking, String method, OffsetDateTime targetExpireAt) {
    OffsetDateTime capExpireAt = resolveMethodCapExpireAt(booking, method);
    if (capExpireAt == null || targetExpireAt == null || !targetExpireAt.isAfter(capExpireAt)) {
      return targetExpireAt;
    }
    return capExpireAt;
  }

  private OffsetDateTime resolveMethodCapExpireAt(BookingEntity booking, String method) {
    if (booking == null || booking.getCreatedAt() == null) {
      return booking == null ? null : booking.getPnrExpiresAt();
    }
    if (METHOD_STRIPE_CARD.equals(method)) {
      return booking.getCreatedAt().plusMinutes(STRIPE_PAYMENT_WINDOW_MINUTES);
    }
    if (METHOD_PAY_LATER.equals(method)) {
      return booking.getCreatedAt().plusMinutes(PAY_LATER_HOLD_MINUTES);
    }
    return null;
  }

  private void applyEffectiveExpireAt(
      BookingEntity booking, OffsetDateTime effectiveExpireAt, OffsetDateTime now) {
    if (booking == null || effectiveExpireAt == null) {
      return;
    }
    if (Objects.equals(booking.getPnrExpiresAt(), effectiveExpireAt)) {
      return;
    }
    if (booking.getPnrExpiresAt() == null || effectiveExpireAt.isAfter(booking.getPnrExpiresAt())) {
      booking.setPaymentWindowExtendedAt(now);
    }
    booking.setPnrExpiresAt(effectiveExpireAt);
    booking.setUpdatedAt(now);
    bookingFareHoldRepository.extendHeldExpiryByBookingId(booking.getId(), effectiveExpireAt, now);
    flightSeatRepository.extendReservedUntilForBooking(booking.getId(), effectiveExpireAt);
  }

  private void assertSufficientPaymentWindow(
      BookingEntity booking, String method, OffsetDateTime expireAt, OffsetDateTime now) {
    if (hasValidEffectivePaymentWindow(booking, method, now, expireAt)) {
      return;
    }
    if (METHOD_STRIPE_CARD.equals(method)) {
      throw new AppException(
          "STRIPE_PAYMENT_WINDOW_TOO_SHORT",
          "Booking must have at least 30 minutes remaining to create a Stripe payment",
          409);
    }
    if (METHOD_PAY_LATER.equals(method)) {
      throw new AppException(
          "PAY_LATER_WINDOW_TOO_SHORT", "Booking cannot be extended for pay later anymore", 409);
    }
    throw new AppException(
        "PAYMENT_WINDOW_TOO_SHORT",
        "Booking must have at least 5 minutes remaining to create a new payment",
        409);
  }

  private boolean hasValidEffectivePaymentWindow(
      BookingEntity booking, String method, OffsetDateTime now) {
    return hasValidEffectivePaymentWindow(
        booking, method, now, resolveEffectiveExpireAt(booking, method, now, false));
  }

  private boolean hasValidEffectivePaymentWindow(
      BookingEntity booking, String method, OffsetDateTime now, OffsetDateTime expireAt) {
    if (booking == null || expireAt == null) {
      return true;
    }
    if (METHOD_PAYOS.equals(method)) {
      return !expireAt.plusSeconds(1).isBefore(now.plusMinutes(PAYOS_PAYMENT_WINDOW_MINUTES));
    }
    return expireAt.isAfter(now);
  }

  private void ensureBookingPendingPayment(BookingEntity booking, OffsetDateTime now) {
    if (booking.getStatus() == null
        || !BOOKING_STATUS_PENDING_PAYMENT.equalsIgnoreCase(booking.getStatus().trim())) {
      booking.setStatus(BOOKING_STATUS_PENDING_PAYMENT);
      booking.setUpdatedAt(now);
    }
  }

  private PaymentEntity findReusablePending(
      List<PaymentEntity> previousPending,
      String method,
      OffsetDateTime currentExpireAt,
      OffsetDateTime effectiveExpireAt) {
    if (previousPending == null || previousPending.isEmpty()) {
      return null;
    }
    return previousPending.stream()
        .filter(payment -> isSamePaymentMethod(payment, method))
        .filter(this::isReusablePending)
        .filter(payment -> Objects.equals(currentExpireAt, effectiveExpireAt))
        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
        .findFirst()
        .orElse(null);
  }

  private PaymentEntity findLatestReusablePending(
      List<PaymentEntity> previousPending, String method) {
    if (previousPending == null || previousPending.isEmpty()) {
      return null;
    }
    return previousPending.stream()
        .filter(payment -> isSamePaymentMethod(payment, method))
        .filter(this::isReusablePending)
        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
        .findFirst()
        .orElse(null);
  }

  private boolean isSamePaymentMethod(PaymentEntity payment, String method) {
    if (payment == null || method == null) {
      return false;
    }
    String paymentMethod = payment.getPaymentMethod();
    return paymentMethod != null && method.equalsIgnoreCase(paymentMethod.trim());
  }

  private boolean isReusablePending(PaymentEntity payment) {
    if (payment == null) {
      return false;
    }
    if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
      return false;
    }
    return payment.getCheckoutUrl() != null && !payment.getCheckoutUrl().isBlank();
  }

  private CreatePaymentResult buildCreatePaymentResult(
      PaymentEntity payment, BookingEntity booking, OffsetDateTime expireAt) {
    return new CreatePaymentResult(
        new CreatePaymentResult.PaymentView(
            payment.getId(),
            booking.getId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getPaymentMethod(),
            payment.getPaymentStatus(),
            expireAt,
            payment.getTransactionId(),
            payment.getQrData(),
            buildPaymentDetails(booking)),
        new CreatePaymentResult.PaymentActionView(
            payment.getType(),
            payment.getQrData(),
            payment.getQrImageUrl(),
            payment.getCheckoutUrl()));
  }

  private BookingEntity requirePayableBooking(Long bookingId, Long userId) {
    BookingEntity booking =
        bookingRepository
            .findByIdAndUserId(bookingId, userId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));
    assertPayable(booking);
    return booking;
  }

  private BookingEntity requirePayableBookingForUpdatePublic(Long bookingId) {
    BookingEntity booking =
        bookingRepository
            .findByIdForUpdate(bookingId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));
    assertPayable(booking);
    return booking;
  }

  private BookingEntity requirePayableBookingPublic(Long bookingId) {
    BookingEntity booking =
        bookingRepository
            .findById(bookingId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));
    assertPayable(booking);
    return booking;
  }

  private BookingEntity requirePayableBookingForUpdate(Long bookingId, Long userId) {
    BookingEntity booking =
        bookingRepository
            .findByIdAndUserIdForUpdate(bookingId, userId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));
    assertPayable(booking);
    return booking;
  }

  private void assertPayable(BookingEntity booking) {
    OffsetDateTime expireAt = booking.getPnrExpiresAt();
    if (expireAt != null && OffsetDateTime.now().isAfter(expireAt)) {
      throw new AppException("BOOKING_EXPIRED", "Booking payment window has expired", 410);
    }

    String normalizedStatus =
        booking.getStatus() == null ? "" : booking.getStatus().trim().toUpperCase(Locale.ROOT);
    if (!"PENDING".equals(normalizedStatus) && !"PENDING_PAYMENT".equals(normalizedStatus)) {
      throw new AppException(
          "BOOKING_NOT_PAYABLE", "Booking is not eligible for payment in its current status", 409);
    }
  }

  private boolean supportsCurrency(String currency) {
    return currency != null && "VND".equalsIgnoreCase(currency);
  }

  private BigDecimal normalizeAmount(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      throw new AppException(
          "INVALID_PAYMENT_AMOUNT", "Booking amount must be greater than zero", 400);
    }
    return amount;
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

  private String appendPaymentId(String url, Long paymentId) {
    return UriComponentsBuilder.fromUriString(url)
        .queryParam("paymentId", paymentId)
        .build()
        .toUriString();
  }

  private String buildDescription(Long paymentId) {
    long compactId = paymentId % 10_000_000L;
    return "AB" + String.format("%07d", compactId);
  }

  private void assertProviderConfigured(String method) {
    if ((METHOD_PAYOS.equals(method) || METHOD_PAY_LATER.equals(method))
        && !paymentProperties.isPayosConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "PayOS provider is not fully configured", 500);
    }
    if (METHOD_STRIPE_CARD.equals(method) && !paymentProperties.isStripeConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "Stripe provider is not fully configured", 500);
    }
  }

  private PaymentGateway gatewayFor(String method) {
    if (METHOD_STRIPE_CARD.equals(method)) {
      return stripePaymentGateway::createCheckoutSession;
    }
    return payosPaymentGateway;
  }

  private String actionTypeForMethod(String method) {
    return METHOD_STRIPE_CARD.equals(method) ? PAYMENT_ACTION_CHECKOUT : PAYMENT_ACTION_QR;
  }

  private String providerForMethod(String method) {
    return METHOD_STRIPE_CARD.equals(method) ? METHOD_STRIPE : METHOD_PAYOS;
  }

  private void cancelWithProvider(PaymentEntity payment, String cancellationReason) {
    if (METHOD_STRIPE_CARD.equalsIgnoreCase(payment.getPaymentMethod())) {
      stripePaymentGateway.expireCheckoutSession(payment.getTransactionId());
      return;
    }
    payosPaymentGateway.cancelPaymentLink(
        new PaymentGateway.CancelPaymentLinkRequest(
            payment.getId(), payment.getId(), cancellationReason));
  }

  private PaymentDetailsView buildPaymentDetails(BookingEntity booking) {
    Optional<BookingFareHoldEntity> holdResult =
        bookingFareHoldRepository.findFirstByBookingIdAndStatusIn(
            booking.getId(), List.of("HELD", "CONFIRMED"));
    BookingFareHoldEntity hold = holdResult == null ? null : holdResult.orElse(null);
    FlightFareEntity fare = hold == null ? null : hold.getFlightFare();
    FlightEntity flight = fare == null ? null : fare.getFlight();
    BigDecimal baseFare = multiply(nonNullMoney(fare == null ? null : fare.getBasePrice()), hold);
    BigDecimal tax = multiply(nonNullMoney(fare == null ? null : fare.getTotalTaxAmount()), hold);
    BigDecimal discount = nonNullMoney(booking.getDiscountAmount());
    BigDecimal serviceFee =
        nonNullMoney(bookingServiceRepository.sumPriceByBookingId(booking.getId()));
    BigDecimal seatFee =
        nonNullMoney(
            passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(booking.getId()));
    BigDecimal total = nonNullMoney(booking.getTotalAmount());

    return new PaymentDetailsView(
        buildFlightView(flight),
        new PaymentDetailsView.PriceBreakdownView(
            baseFare, discount, tax, serviceFee, seatFee, total, booking.getCurrency()));
  }

  private PaymentDetailsView.FlightView buildFlightView(FlightEntity flight) {
    if (flight == null) {
      return null;
    }
    AirlineEntity airline = flight.getAirline();
    AirportEntity departure = flight.getDepartureAirport();
    AirportEntity arrival = flight.getArrivalAirport();
    String flightName =
        String.join(
                " ",
                List.of(
                    nullToBlank(airline == null ? null : airline.getCode()),
                    nullToBlank(flight.getFlightNumber())))
            .trim();
    return new PaymentDetailsView.FlightView(
        flight.getFlightNumber(),
        flightName.isBlank() ? flight.getFlightNumber() : flightName,
        airline == null ? null : airline.getName(),
        departure == null ? null : departure.getCode(),
        departure == null ? null : departure.getCity(),
        departure == null ? null : departure.getName(),
        arrival == null ? null : arrival.getCode(),
        arrival == null ? null : arrival.getCity(),
        arrival == null ? null : arrival.getName(),
        flight.getFlightDate(),
        flight.getDepartureTime(),
        flight.getArrivalTime());
  }

  private String buildProductName(BookingEntity booking) {
    PaymentDetailsView.FlightView flight = buildPaymentDetails(booking).flight();
    if (flight == null || flight.flightName() == null || flight.flightName().isBlank()) {
      return "Airline booking";
    }
    return "Flight " + flight.flightName();
  }

  private String buildProductDescription(BookingEntity booking) {
    PaymentDetailsView details = buildPaymentDetails(booking);
    PaymentDetailsView.FlightView flight = details.flight();
    if (flight == null) {
      return "PNR " + booking.getPnrCode();
    }
    String route = buildRouteLabel(flight);
    if (route.isBlank()) {
      return "PNR " + booking.getPnrCode();
    }
    return route + " | PNR " + booking.getPnrCode();
  }

  private String buildRouteLabel(PaymentDetailsView.FlightView flight) {
    String departure = nullToBlank(flight.departureCode());
    String arrival = nullToBlank(flight.arrivalCode());
    if (departure.isBlank() || arrival.isBlank()) {
      return "";
    }
    return departure + " to " + arrival;
  }

  private List<PaymentLineItem> buildStripeLineItems(BookingEntity booking) {
    PaymentDetailsView details = buildPaymentDetails(booking);
    PaymentDetailsView.FlightView flight = details.flight();
    PaymentDetailsView.PriceBreakdownView price = details.price();
    String route = flight == null ? "" : buildRouteLabel(flight);
    String pnr = "PNR " + booking.getPnrCode();
    String flightDescription = route.isBlank() ? pnr : route + " | " + pnr;
    BigDecimal fareAfterDiscount = price.baseFare().subtract(price.discount());
    if (fareAfterDiscount.compareTo(BigDecimal.ZERO) < 0) {
      fareAfterDiscount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    BigDecimal itemizedTotal =
        fareAfterDiscount.add(price.tax()).add(price.serviceFee()).add(price.seatFee());
    if (itemizedTotal.compareTo(BigDecimal.ZERO) == 0
        && price.total().compareTo(BigDecimal.ZERO) > 0) {
      fareAfterDiscount = price.total();
    }
    return List.of(
        new PaymentLineItem(
            buildProductName(booking),
            buildFareDescription(flightDescription, price),
            fareAfterDiscount),
        new PaymentLineItem("Taxes", "Government taxes and fees", price.tax()),
        new PaymentLineItem("Services", "Selected add-on services", price.serviceFee()),
        new PaymentLineItem("Seats", "Seat selection fees", price.seatFee()));
  }

  private String buildFareDescription(
      String flightDescription, PaymentDetailsView.PriceBreakdownView price) {
    if (price.discount().compareTo(BigDecimal.ZERO) <= 0) {
      return flightDescription;
    }
    return flightDescription
        + " | Base "
        + price.baseFare().toPlainString()
        + " "
        + price.currency()
        + ", discount "
        + price.discount().toPlainString()
        + " "
        + price.currency();
  }

  private Map<String, String> buildStripeMetadata(BookingEntity booking) {
    PaymentDetailsView details = buildPaymentDetails(booking);
    PaymentDetailsView.FlightView flight = details.flight();
    PaymentDetailsView.PriceBreakdownView price = details.price();
    return Map.ofEntries(
        Map.entry("pnrCode", nullToBlank(booking.getPnrCode())),
        Map.entry("flightNumber", nullToBlank(flight == null ? null : flight.flightNumber())),
        Map.entry("flightName", nullToBlank(flight == null ? null : flight.flightName())),
        Map.entry("airline", nullToBlank(flight == null ? null : flight.airline())),
        Map.entry("departureCode", nullToBlank(flight == null ? null : flight.departureCode())),
        Map.entry("departureCity", nullToBlank(flight == null ? null : flight.departureCity())),
        Map.entry("arrivalCode", nullToBlank(flight == null ? null : flight.arrivalCode())),
        Map.entry("arrivalCity", nullToBlank(flight == null ? null : flight.arrivalCity())),
        Map.entry("baseFare", price.baseFare().toPlainString()),
        Map.entry("discount", price.discount().toPlainString()),
        Map.entry("tax", price.tax().toPlainString()),
        Map.entry("serviceFee", price.serviceFee().toPlainString()),
        Map.entry("seatFee", price.seatFee().toPlainString()),
        Map.entry("total", price.total().toPlainString()),
        Map.entry("currency", nullToBlank(price.currency())));
  }

  private BigDecimal multiply(BigDecimal value, BookingFareHoldEntity hold) {
    int quantity = hold == null ? 1 : Math.max(hold.getQuantity(), 1);
    return value.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal nonNullMoney(BigDecimal value) {
    return value == null
        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        : value.setScale(2, RoundingMode.HALF_UP);
  }

  private String nullToBlank(String value) {
    return value == null ? "" : value;
  }
}
