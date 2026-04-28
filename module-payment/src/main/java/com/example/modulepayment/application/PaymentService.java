package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.exception.BookingNotFoundException;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.exception.PaymentNotFoundException;
import com.example.modulepayment.gateway.PaymentGateway;
import com.example.modulepayment.gateway.PaymentGateway.PaymentGatewayRequest;
import com.example.modulepayment.gateway.PaymentGateway.PaymentGatewayResult;
import com.example.modulepayment.repository.BookingRepository;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PaymentService {

  private static final String METHOD_PAYOS = "PAYOS";
  private static final String METHOD_PAYOS_QR = "PAYOS_QR";
  private static final String METHOD_STRIPE_CARD = "STRIPE_CARD";
  private static final String METHOD_VNPAY = "VNPAY";
  private static final String METHOD_MOMO = "MOMO";
  private static final String METHOD_PAY_LATER = "PAY_LATER";
  private static final String PAYMENT_STATUS_FAILED = "FAILED";
  private static final String PAYMENT_STATUS_PENDING = "PENDING";
  private static final String PAYMENT_STATUS_CANCELLED = "CANCELLED";
  private static final String PAYMENT_ACTION_QR = "QR";
  private static final String BOOKING_STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
  private static final long PAYMENT_WINDOW_EXTENSION_MINUTES = 5;
  private static final String DEFAULT_CANCELLATION_REASON = "User requested cancellation";

  private final BookingRepository bookingRepository;
  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;
  private final PaymentProperties paymentProperties;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final FlightSeatRepository flightSeatRepository;

  public PaymentService(
      BookingRepository bookingRepository,
      PaymentRepository paymentRepository,
      PaymentGateway paymentGateway,
      PaymentProperties paymentProperties,
      BookingFareHoldRepository bookingFareHoldRepository,
      FlightSeatRepository flightSeatRepository) {
    this.bookingRepository = bookingRepository;
    this.paymentRepository = paymentRepository;
    this.paymentGateway = paymentGateway;
    this.paymentProperties = paymentProperties;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.flightSeatRepository = flightSeatRepository;
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
    boolean payosSupported =
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
                        false,
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
                        "INTERNAL",
                        null,
                        false,
                        new PaymentMethodView.PaymentMethodMetaView(30, 0)))));
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
        payment.getCheckoutUrl());
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
        payment.getCheckoutUrl());
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

    // We cancel by orderCode (= paymentId in this integration) to match createPaymentLink usage.
    paymentGateway.cancelPaymentLink(
        new PaymentGateway.CancelPaymentLinkRequest(
            payment.getId(), payment.getId(), cancellationReason));

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

    paymentGateway.cancelPaymentLink(
        new PaymentGateway.CancelPaymentLinkRequest(
            payment.getId(), payment.getId(), cancellationReason));

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
        payment.getCheckoutUrl());
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
    if (!METHOD_PAYOS.equals(normalizedMethod)) {
      throw new AppException(
          "UNSUPPORTED_PAYMENT_METHOD", "Only PAYOS is supported in phase 1", 400);
    }

    if (!paymentProperties.isPayosConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "PayOS provider is not fully configured", 500);
    }

    BookingEntity booking =
        requireOwner
            ? requirePayableBookingForUpdate(command.bookingId(), command.userId())
            : requirePayableBookingForUpdatePublic(command.bookingId());
    if (!supportsCurrency(booking.getCurrency())) {
      throw new AppException(
          "UNSUPPORTED_PAYMENT_CURRENCY", "Only VND payments are supported in phase 1", 400);
    }

    OffsetDateTime now = OffsetDateTime.now();
    maybeExtendPaymentWindowOnce(booking, now);
    OffsetDateTime expireAt = booking.getPnrExpiresAt();

    List<PaymentEntity> previousPending =
        paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(
            booking.getId(), PAYMENT_STATUS_PENDING);
    PaymentEntity reusablePending = findReusablePending(previousPending);
    if (reusablePending != null) {
      ensureBookingPendingPayment(booking, now);
      return buildCreatePaymentResult(reusablePending, booking, expireAt);
    }

    for (PaymentEntity existing : previousPending) {
      // Non-reusable PENDING payments (e.g. provider link missing) are treated as failed so that we
      // don't block creating a new payment link for the same booking.
      existing.setPaymentStatus(PAYMENT_STATUS_FAILED);
      existing.setUpdatedAt(now);
    }

    PaymentEntity payment = new PaymentEntity();
    payment.setBooking(booking);
    payment.setAmount(normalizeAmount(booking.getTotalAmount()));
    payment.setCurrency(booking.getCurrency());
    payment.setPaymentMethod(normalizedMethod);
    payment.setPaymentStatus(PAYMENT_STATUS_PENDING);
    payment.setType(PAYMENT_ACTION_QR);
    payment.setCreatedAt(now);
    payment.setUpdatedAt(now);
    paymentRepository.saveAndFlush(payment);

    PaymentGatewayResult gatewayResult;
    try {
      gatewayResult =
          paymentGateway.createPaymentLink(
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
                  expireAt));
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

  private void maybeExtendPaymentWindowOnce(BookingEntity booking, OffsetDateTime now) {
    if (booking == null) {
      return;
    }
    OffsetDateTime currentExpireAt = booking.getPnrExpiresAt();
    if (currentExpireAt == null) {
      return;
    }
    if (booking.getPaymentWindowExtendedAt() != null) {
      return; // one-time extension per booking
    }

    OffsetDateTime targetExpireAt = now.plusMinutes(PAYMENT_WINDOW_EXTENSION_MINUTES);
    if (!currentExpireAt.isBefore(targetExpireAt)) {
      return; // already has >= 5 minutes remaining
    }

    booking.setPnrExpiresAt(targetExpireAt);
    booking.setPaymentWindowExtendedAt(now);
    booking.setUpdatedAt(now);

    // Keep all holds/reservations aligned with the new booking expiry.
    bookingFareHoldRepository.extendHeldExpiryByBookingId(booking.getId(), targetExpireAt, now);
    flightSeatRepository.extendReservedUntilForBooking(booking.getId(), targetExpireAt);
  }

  private void ensureBookingPendingPayment(BookingEntity booking, OffsetDateTime now) {
    if (booking.getStatus() == null
        || !BOOKING_STATUS_PENDING_PAYMENT.equalsIgnoreCase(booking.getStatus().trim())) {
      booking.setStatus(BOOKING_STATUS_PENDING_PAYMENT);
      booking.setUpdatedAt(now);
    }
  }

  private PaymentEntity findReusablePending(List<PaymentEntity> previousPending) {
    if (previousPending == null || previousPending.isEmpty()) {
      return null;
    }
    // Prefer the newest payment to avoid returning a stale link.
    PaymentEntity candidate =
        previousPending.stream()
            .filter(this::isReusablePending)
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .findFirst()
            .orElse(null);
    return candidate;
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
            payment.getQrData()),
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
}
