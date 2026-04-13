package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.exception.BookingNotFoundException;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.PaymentEntity;
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
  private static final String PAYMENT_ACTION_QR = "QR";

  private final BookingRepository bookingRepository;
  private final PaymentRepository paymentRepository;
  private final PaymentGateway paymentGateway;
  private final PaymentProperties paymentProperties;

  public PaymentService(
      BookingRepository bookingRepository,
      PaymentRepository paymentRepository,
      PaymentGateway paymentGateway,
      PaymentProperties paymentProperties) {
    this.bookingRepository = bookingRepository;
    this.paymentRepository = paymentRepository;
    this.paymentGateway = paymentGateway;
    this.paymentProperties = paymentProperties;
  }

  @Transactional(readOnly = true)
  public List<PaymentMethodView> getPaymentMethods(
      Long bookingId, Long userId, boolean onlySupported) {
    BookingEntity booking = requirePayableBooking(bookingId, userId);
    boolean payosSupported =
        paymentProperties.isPayosConfigured() && supportsCurrency(booking.getCurrency());
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
                        "visa_mastercard.png",
                        false,
                        null),
                    new PaymentMethodView.PaymentMethodItemView(
                        METHOD_PAYOS_QR,
                        "Mobile Banking (VietQR)",
                        "PAYOS",
                        "vietqr.png",
                        payosSupported,
                        null),
                    new PaymentMethodView.PaymentMethodItemView(
                        METHOD_VNPAY, "Thanh toán VNPay", "VNPAY", "vnpay.png", false, null))),
            new PaymentMethodView(
                "EWALLET",
                "Ví điện tử",
                List.of(
                    new PaymentMethodView.PaymentMethodItemView(
                        METHOD_MOMO, "Ví MoMo", "MOMO", null, false, null))),
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
    String normalizedMethod = normalizeMethod(command.method());
    if (!METHOD_PAYOS.equals(normalizedMethod)) {
      throw new AppException(
          "UNSUPPORTED_PAYMENT_METHOD", "Only PAYOS is supported in phase 1", 400);
    }

    if (!paymentProperties.isPayosConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "PayOS provider is not fully configured", 500);
    }

    BookingEntity booking = requirePayableBookingForUpdate(command.bookingId(), command.userId());
    if (!supportsCurrency(booking.getCurrency())) {
      throw new AppException(
          "UNSUPPORTED_PAYMENT_CURRENCY", "Only VND payments are supported in phase 1", 400);
    }

    OffsetDateTime expireAt = booking.getPnrExpiresAt();
    OffsetDateTime now = OffsetDateTime.now();

    List<PaymentEntity> previousPending =
        paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(
            booking.getId(), PAYMENT_STATUS_PENDING);
    for (PaymentEntity existing : previousPending) {
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

    PaymentGatewayResult gatewayResult =
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

    payment.setTransactionId(gatewayResult.paymentLinkId());
    payment.setQrData(gatewayResult.qrCode());
    payment.setType(gatewayResult.actionType());
    payment.setUpdatedAt(OffsetDateTime.now());
    paymentRepository.save(payment);

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
            gatewayResult.actionType(),
            gatewayResult.qrCode(),
            payment.getQrImageUrl(),
            gatewayResult.checkoutUrl()));
  }

  private BookingEntity requirePayableBooking(Long bookingId, Long userId) {
    BookingEntity booking =
        bookingRepository
            .findByIdAndUserId(bookingId, userId)
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
