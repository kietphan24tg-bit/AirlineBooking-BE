package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.repository.FlightFareRepository;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.FlightChangeSettlementEntity;
import com.example.modulepayment.entity.FlightChangeSettlementItemEntity;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.entity.TicketFinancialSnapshotEntity;
import com.example.modulepayment.gateway.PaymentRefundGateway.PaymentRefundResult;
import com.example.modulepayment.repository.BookingRepository;
import com.example.modulepayment.repository.FlightChangeSettlementRepository;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.modulepayment.repository.TicketFinancialSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightChangeSettlementService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  private static final BigDecimal CHILD_RATE = new BigDecimal("0.75");
  private static final BigDecimal INFANT_RATE = new BigDecimal("0.10");
  private static final String STATUS_COMPLETED = "COMPLETED";
  private static final String STATUS_PAYMENT_REQUIRED = "PAYMENT_REQUIRED";
  private static final String STATUS_REFUND_FAILED = "REFUND_FAILED";
  private static final String STATUS_REFUND_PENDING = "REFUND_PENDING";
  private static final String SETTLEMENT_NO_CHANGE = "NO_CHANGE";
  private static final String SETTLEMENT_REFUND = "REFUND";
  private static final String SETTLEMENT_COLLECT_PAYMENT = "COLLECT_PAYMENT";
  private static final String SETTLEMENT_STATUS_CANCELLED = "CANCELLED";
  private static final String SETTLEMENT_STATUS_COMPLETED = "COMPLETED";
  private static final String SETTLEMENT_STATUS_EXPIRED = "EXPIRED";
  private static final String SETTLEMENT_STATUS_PAYMENT_FAILED = "PAYMENT_FAILED";
  private static final String SETTLEMENT_STATUS_PAYMENT_PENDING = "PAYMENT_PENDING";
  private static final String SETTLEMENT_STATUS_PAYMENT_REQUIRED = "PAYMENT_REQUIRED";
  private static final String SETTLEMENT_STATUS_REFUND_PENDING = "REFUND_PENDING";
  private static final String PAYMENT_STATUS_PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
  private static final String PAYMENT_STATUS_REFUNDED = "REFUNDED";
  private static final String VND = "VND";
  private static final String STRIPE_CARD = "STRIPE_CARD";
  private static final List<String> ACTIVE_ASSIGNMENT_STATUSES = List.of("RESERVED", "BOOKED");
  private static final List<String> ACTIVE_HOLD_STATUSES = List.of("HELD", "CONFIRMED");
  private static final List<String> REFUNDABLE_PAYMENT_STATUSES =
      List.of("SUCCESS", "PARTIALLY_REFUNDED");

  private final BookingRepository bookingRepository;
  private final TicketRepository ticketRepository;
  private final PassengerRepository passengerRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final FlightFareRepository flightFareRepository;
  private final PaymentRepository paymentRepository;
  private final TicketFinancialSnapshotRepository ticketFinancialSnapshotRepository;
  private final PaymentRefundService paymentRefundService;
  private final FlightChangeSettlementRepository flightChangeSettlementRepository;
  private final PaymentProperties paymentProperties;

  public FlightChangeSettlementService(
      BookingRepository bookingRepository,
      TicketRepository ticketRepository,
      PassengerRepository passengerRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      BookingServiceRepository bookingServiceRepository,
      FlightFareRepository flightFareRepository,
      PaymentRepository paymentRepository,
      TicketFinancialSnapshotRepository ticketFinancialSnapshotRepository,
      PaymentRefundService paymentRefundService,
      FlightChangeSettlementRepository flightChangeSettlementRepository,
      PaymentProperties paymentProperties) {
    this.bookingRepository = bookingRepository;
    this.ticketRepository = ticketRepository;
    this.passengerRepository = passengerRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.flightFareRepository = flightFareRepository;
    this.paymentRepository = paymentRepository;
    this.ticketFinancialSnapshotRepository = ticketFinancialSnapshotRepository;
    this.paymentRefundService = paymentRefundService;
    this.flightChangeSettlementRepository = flightChangeSettlementRepository;
    this.paymentProperties = paymentProperties;
  }

  @Transactional
  public FlightChangeConfirmView confirm(ConfirmCommand command) {
    if (!StringUtils.hasText(command.idempotencyKey())) {
      throw new AppException("VALIDATION_FAILED", "idempotencyKey is required", 400);
    }
    String normalizedIdempotencyKey = command.idempotencyKey().trim();
    var existing =
        flightChangeSettlementRepository.findFirstByBookingIdAndIdempotencyKeyWithItems(
            command.bookingId(), normalizedIdempotencyKey);
    if (existing.isPresent()) {
      return toConfirmView(existing.get());
    }

    BookingEntity booking = requireBooking(command.userId(), command.bookingId(), true);
    validateExpectedAmounts(command);

    FlightChangeSettlementEntity settlement =
        saveSettlement(
            booking,
            command,
            normalizedIdempotencyKey,
            initialStatus(command),
            null,
            null,
            null,
            null);

    if (SETTLEMENT_NO_CHANGE.equals(command.settlementType())) {
      applyFlightChange(settlement);
      settlement.setStatus(SETTLEMENT_STATUS_COMPLETED);
      settlement.setUpdatedAt(OffsetDateTime.now());
      flightChangeSettlementRepository.save(settlement);
      return toConfirmView(settlement);
    }

    if (SETTLEMENT_COLLECT_PAYMENT.equals(command.settlementType())) {
      return toConfirmView(settlement);
    }

    PaymentEntity payment = requireRefundablePayment(command.bookingId(), true);
    try {
      paymentRefundService.preparePaymentForRefund(payment);
      paymentRepository.save(payment);
      PaymentRefundResult refundResult =
          paymentRefundService.refund(
              command.bookingId(),
              normalizedIdempotencyKey,
              payment,
              command.refundAmount(),
              "Flight change refund");
      payment.setPaymentStatus(resolveRefundedPaymentStatus(payment, command.refundAmount()));
      paymentRepository.save(payment);
      applyFlightChange(settlement);
      settlement.setStatus(SETTLEMENT_STATUS_COMPLETED);
      settlement.setProvider(paymentRefundService.providerOf(payment));
      settlement.setProviderRefundId(refundResult.providerRefundId());
      settlement.setUpdatedAt(OffsetDateTime.now());
      flightChangeSettlementRepository.save(settlement);
      return toConfirmView(settlement);
    } catch (RuntimeException exception) {
      settlement.setStatus(SETTLEMENT_STATUS_PAYMENT_FAILED);
      settlement.setErrorCode(
          exception instanceof AppException appException
              ? appException.getCode()
              : "REFUND_FAILED");
      settlement.setErrorMessage(exception.getMessage());
      settlement.setUpdatedAt(OffsetDateTime.now());
      flightChangeSettlementRepository.save(settlement);
      return toConfirmView(settlement);
    }
  }

  @Transactional
  public FlightChangeSettlementEntity requireSettlementForPayment(
      Long bookingId, Long settlementId, Long userId) {
    FlightChangeSettlementEntity settlement =
        flightChangeSettlementRepository
            .findByIdWithItemsForUpdate(settlementId)
            .orElseThrow(
                () ->
                    new AppException(
                        "FLIGHT_CHANGE_SETTLEMENT_NOT_FOUND",
                        "Flight change settlement not found",
                        404));
    if (!Objects.equals(settlement.getBooking().getId(), bookingId)) {
      throw new AppException(
          "FLIGHT_CHANGE_SETTLEMENT_NOT_FOUND", "Flight change settlement not found", 404);
    }
    if (userId != null
        && settlement.getBooking().getUser() != null
        && !Objects.equals(settlement.getBooking().getUser().getId(), userId)) {
      throw new AppException(
          "FLIGHT_CHANGE_SETTLEMENT_NOT_FOUND", "Flight change settlement not found", 404);
    }
    if (!SETTLEMENT_COLLECT_PAYMENT.equals(settlement.getSettlementType())) {
      throw new AppException(
          "FLIGHT_CHANGE_SETTLEMENT_NOT_PAYABLE",
          "Flight change settlement does not require payment",
          409);
    }
    if (SETTLEMENT_STATUS_COMPLETED.equals(settlement.getStatus())) {
      throw new AppException(
          "FLIGHT_CHANGE_SETTLEMENT_COMPLETED",
          "Flight change settlement has already been completed",
          409);
    }
    return settlement;
  }

  @Transactional
  public void markPaymentCreated(FlightChangeSettlementEntity settlement) {
    settlement.setStatus(SETTLEMENT_STATUS_PAYMENT_PENDING);
    settlement.setUpdatedAt(OffsetDateTime.now());
    flightChangeSettlementRepository.save(settlement);
  }

  @Transactional
  public void handlePaymentSuccess(PaymentEntity payment, String providerPaymentReference) {
    FlightChangeSettlementEntity settlement = payment.getFlightChangeSettlement();
    if (settlement == null) {
      return;
    }
    settlement =
        flightChangeSettlementRepository
            .findByIdWithItemsForUpdate(settlement.getId())
            .orElseThrow();
    if (SETTLEMENT_STATUS_COMPLETED.equals(settlement.getStatus())) {
      return;
    }
    if (providerPaymentReference != null && !providerPaymentReference.isBlank()) {
      settlement.setProviderPaymentReference(providerPaymentReference);
    }
    applyFlightChange(settlement);
    settlement.setStatus(SETTLEMENT_STATUS_COMPLETED);
    settlement.setUpdatedAt(OffsetDateTime.now());
    flightChangeSettlementRepository.save(settlement);
  }

  @Transactional
  public void handlePaymentFailure(PaymentEntity payment, String status) {
    FlightChangeSettlementEntity settlement = payment.getFlightChangeSettlement();
    if (settlement == null) {
      return;
    }
    settlement =
        flightChangeSettlementRepository
            .findByIdWithItemsForUpdate(settlement.getId())
            .orElseThrow();
    settlement.setStatus(
        "EXPIRED".equalsIgnoreCase(status)
            ? SETTLEMENT_STATUS_EXPIRED
            : SETTLEMENT_STATUS_PAYMENT_FAILED);
    settlement.setUpdatedAt(OffsetDateTime.now());
    flightChangeSettlementRepository.save(settlement);
  }

  @Transactional
  public void handlePaymentCancelled(PaymentEntity payment) {
    FlightChangeSettlementEntity settlement = payment.getFlightChangeSettlement();
    if (settlement == null) {
      return;
    }
    settlement =
        flightChangeSettlementRepository
            .findByIdWithItemsForUpdate(settlement.getId())
            .orElseThrow();
    settlement.setStatus(SETTLEMENT_STATUS_CANCELLED);
    settlement.setUpdatedAt(OffsetDateTime.now());
    flightChangeSettlementRepository.save(settlement);
  }

  private void applyFlightChange(FlightChangeSettlementEntity settlement) {
    Long bookingId = settlement.getBooking().getId();
    List<Long> ticketIds =
        settlement.getItems().stream().map(FlightChangeSettlementItemEntity::getTicketId).toList();
    List<TicketEntity> tickets =
        ticketRepository.findByBookingIdAndTicketIdInWithDetailsForUpdate(bookingId, ticketIds);
    if (tickets.size() != ticketIds.size()) {
      throw new AppException(
          "FLIGHT_CHANGE_SETTLEMENT_STALE",
          "Flight change settlement is out of date. Please create a new one.",
          409);
    }
    FlightFareEntity newFare =
        flightFareRepository
            .findByIdWithDetails(settlement.getNewFareId())
            .orElseThrow(
                () ->
                    new AppException(
                        "FLIGHT_CHANGE_SETTLEMENT_STALE",
                        "Flight change settlement is out of date. Please create a new one.",
                        409));
    BookingFareHoldEntity hold =
        bookingFareHoldRepository
            .findByBookingIdAndFlightIdAndStatusIn(
                bookingId, settlement.getCurrentFlightId(), ACTIVE_HOLD_STATUSES)
            .orElseThrow(
                () ->
                    new AppException(
                        "FLIGHT_CHANGE_SETTLEMENT_STALE",
                        "Flight change settlement is out of date. Please create a new one.",
                        409));

    OffsetDateTime now = OffsetDateTime.now();
    Set<Long> passengerIds =
        settlement.getItems().stream()
            .map(FlightChangeSettlementItemEntity::getPassengerId)
            .collect(Collectors.toSet());

    for (TicketEntity ticket : tickets) {
      ticket.setFlightFare(newFare);
      ticket.setFareClass(newFare.getFareClass());
      ticket.setFlight(newFare.getFlight());
      ticket.setFlightSeat(null);
      ticket.setUpdatedAt(now);
    }

    hold.setFlightFare(newFare);
    hold.setUpdatedAt(now);

    List<PassengerSeatAssignmentEntity> seatAssignments =
        passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            bookingId, passengerIds, ACTIVE_ASSIGNMENT_STATUSES);
    for (PassengerSeatAssignmentEntity assignment : seatAssignments) {
      if (!Objects.equals(
          assignment.getFlightSeat().getFlight().getId(), settlement.getCurrentFlightId())) {
        continue;
      }
      assignment.setStatus("RELEASED");
      assignment.setUpdatedAt(now);
    }

    Map<Long, TicketFinancialSnapshotEntity> snapshotsByTicketId =
        ticketFinancialSnapshotRepository.findByBookingIdWithDetails(bookingId).stream()
            .collect(
                LinkedHashMap::new,
                (map, snapshot) -> map.put(snapshot.getTicket().getId(), snapshot),
                Map::putAll);
    for (TicketEntity ticket : tickets) {
      TicketFinancialSnapshotEntity snapshot = snapshotsByTicketId.get(ticket.getId());
      if (snapshot == null) {
        continue;
      }
      BigDecimal newFareAmount = fareAmount(ticket);
      snapshot.setFareGrossAmount(newFareAmount);
      snapshot.setPromoAllocatedAmount(ZERO);
      snapshot.setFareNetAmount(newFareAmount);
      snapshot.setPassengerType(ticket.getPassenger().getPassengerType());
      snapshot.setUpdatedAt(now);
    }

    BookingEntity booking = settlement.getBooking();
    booking.setTotalAmount(
        normalizeMoney(money(booking.getTotalAmount()).add(money(settlement.getNetAmount()))));
    booking.setUpdatedAt(now);

    ticketRepository.saveAll(tickets);
    bookingFareHoldRepository.save(hold);
    if (!seatAssignments.isEmpty()) {
      passengerSeatAssignmentRepository.saveAll(seatAssignments);
    }
    if (!snapshotsByTicketId.isEmpty()) {
      ticketFinancialSnapshotRepository.saveAll(new ArrayList<>(snapshotsByTicketId.values()));
    }
    bookingRepository.save(booking);
  }

  private BookingEntity requireBooking(Long userId, Long bookingId, boolean forUpdate) {
    if (userId == null) {
      return forUpdate
          ? bookingRepository
              .findByIdForUpdate(bookingId)
              .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404))
          : bookingRepository
              .findById(bookingId)
              .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404));
    }
    return forUpdate
        ? bookingRepository
            .findByIdAndUserIdForUpdate(bookingId, userId)
            .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404))
        : bookingRepository
            .findByIdAndUserId(bookingId, userId)
            .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404));
  }

  private void validateExpectedAmounts(ConfirmCommand command) {
    if (money(command.expectedAdditionalAmount()).compareTo(money(command.additionalAmount())) != 0
        || money(command.expectedRefundAmount()).compareTo(money(command.refundAmount())) != 0
        || money(command.expectedNetAmount()).compareTo(money(command.netAmount())) != 0) {
      throw new AppException(
          "FLIGHT_CHANGE_QUOTE_STALE",
          "Flight change totals are out of date. Please review the latest quote.",
          409);
    }
  }

  private String initialStatus(ConfirmCommand command) {
    return switch (command.settlementType()) {
      case SETTLEMENT_NO_CHANGE -> SETTLEMENT_STATUS_COMPLETED;
      case SETTLEMENT_COLLECT_PAYMENT -> SETTLEMENT_STATUS_PAYMENT_REQUIRED;
      case SETTLEMENT_REFUND -> SETTLEMENT_STATUS_REFUND_PENDING;
      default -> throw new AppException("VALIDATION_FAILED", "Unsupported settlement type", 400);
    };
  }

  private FlightChangeSettlementEntity saveSettlement(
      BookingEntity booking,
      ConfirmCommand command,
      String idempotencyKey,
      String status,
      String provider,
      String providerRefundId,
      String errorCode,
      String errorMessage) {
    FlightChangeSettlementEntity settlement = new FlightChangeSettlementEntity();
    settlement.setBooking(booking);
    settlement.setQuoteToken(command.quoteToken());
    settlement.setIdempotencyKey(idempotencyKey);
    settlement.setCurrentFlightId(command.currentFlightId());
    settlement.setNewFlightId(command.newFlightId());
    settlement.setNewFareId(command.newFareId());
    settlement.setSettlementType(command.settlementType());
    settlement.setStatus(status);
    settlement.setAdditionalAmount(command.additionalAmount());
    settlement.setRefundAmount(command.refundAmount());
    settlement.setNetAmount(command.netAmount());
    settlement.setCurrency(command.currency());
    settlement.setProvider(provider);
    settlement.setProviderRefundId(providerRefundId);
    settlement.setErrorCode(errorCode);
    settlement.setErrorMessage(errorMessage);
    OffsetDateTime now = OffsetDateTime.now();
    settlement.setCreatedAt(now);
    settlement.setUpdatedAt(now);
    for (ItemCommand itemCommand : command.items()) {
      FlightChangeSettlementItemEntity item = new FlightChangeSettlementItemEntity();
      item.setSettlement(settlement);
      item.setTicketId(itemCommand.ticketId());
      item.setPassengerId(itemCommand.passengerId());
      item.setPassengerName(itemCommand.passengerName());
      item.setPassengerType(itemCommand.passengerType());
      item.setOriginalFareAmount(itemCommand.originalFareAmount());
      item.setNewFareAmount(itemCommand.newFareAmount());
      item.setFareDifference(itemCommand.fareDifference());
      item.setChangeFee(itemCommand.changeFee());
      item.setTaxDifference(itemCommand.taxDifference());
      item.setNetAmount(itemCommand.netAmount());
      settlement.getItems().add(item);
    }
    return flightChangeSettlementRepository.save(settlement);
  }

  private PaymentEntity requireRefundablePayment(Long bookingId, boolean forUpdate) {
    List<PaymentEntity> preferredPayments =
        forUpdate
            ? paymentRepository
                .findBookingLevelPaymentsByBookingIdAndPaymentStatusInForUpdateOrderByLatest(
                    bookingId, REFUNDABLE_PAYMENT_STATUSES)
            : paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
                bookingId, REFUNDABLE_PAYMENT_STATUSES);
    if (!preferredPayments.isEmpty()) {
      return preferredPayments.getFirst();
    }
    List<PaymentEntity> fallbackPayments =
        forUpdate
            ? paymentRepository.findByBookingIdAndPaymentStatusInForUpdateOrderByLatest(
                bookingId, REFUNDABLE_PAYMENT_STATUSES)
            : paymentRepository.findByBookingIdAndPaymentStatusInOrderByLatest(
                bookingId, REFUNDABLE_PAYMENT_STATUSES);
    return fallbackPayments.stream()
        .findFirst()
        .orElseThrow(
            () ->
                new AppException(
                    "PAYMENT_NOT_REFUNDABLE", "Booking does not have a refundable payment", 409));
  }

  private FlightChangeConfirmView toConfirmView(FlightChangeSettlementEntity settlement) {
    String normalizedStatus = normalize(settlement.getStatus());
    boolean hasRefund = money(settlement.getRefundAmount()).signum() > 0;
    String status =
        switch (normalizedStatus) {
          case SETTLEMENT_STATUS_PAYMENT_REQUIRED, SETTLEMENT_STATUS_PAYMENT_PENDING ->
              STATUS_PAYMENT_REQUIRED;
          case SETTLEMENT_STATUS_REFUND_PENDING, SETTLEMENT_STATUS_COMPLETED ->
              hasRefund ? STATUS_REFUND_PENDING : STATUS_COMPLETED;
          case SETTLEMENT_STATUS_PAYMENT_FAILED,
                  SETTLEMENT_STATUS_EXPIRED,
                  SETTLEMENT_STATUS_CANCELLED ->
              hasRefund ? STATUS_REFUND_FAILED : STATUS_PAYMENT_REQUIRED;
          default -> STATUS_COMPLETED;
        };
    String refundStatus;
    if (hasRefund && SETTLEMENT_STATUS_PAYMENT_FAILED.equals(normalizedStatus)) {
      refundStatus = "FAILED";
    } else if (hasRefund) {
      refundStatus = "SUCCEEDED";
    } else {
      refundStatus = null;
    }
    return new FlightChangeConfirmView(
        settlement.getId(),
        settlement.getBooking().getId(),
        settlement.getCurrency(),
        settlement.getSettlementType(),
        status,
        refundStatus,
        settlement.getProvider(),
        settlement.getProviderRefundId(),
        settlement.getErrorCode(),
        settlement.getErrorMessage(),
        hasRefund ? buildRefundMethod(null, settlement.getProvider()) : null,
        SETTLEMENT_COLLECT_PAYMENT.equals(settlement.getSettlementType())
            ? supportedPaymentMethods(settlement.getCurrency())
            : List.of(),
        new FlightChangeConfirmView.SummaryView(
            settlement.getAdditionalAmount(),
            settlement.getRefundAmount(),
            settlement.getNetAmount()));
  }

  private List<FlightChangeConfirmView.PaymentMethodItemView> supportedPaymentMethods(
      String currency) {
    String normalizedCurrency = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
    List<FlightChangeConfirmView.PaymentMethodItemView> methods = new ArrayList<>();
    if (paymentProperties.isPayosConfigured() && VND.equalsIgnoreCase(normalizedCurrency)) {
      methods.add(
          new FlightChangeConfirmView.PaymentMethodItemView(
              "PAYOS_QR", "Mobile Banking (VietQR)", "PAYOS", null, true));
    }
    if (paymentProperties.isStripeConfigured() && supportsStripeCurrency(normalizedCurrency)) {
      methods.add(
          new FlightChangeConfirmView.PaymentMethodItemView(
              STRIPE_CARD, "International credit or debit card", "STRIPE", null, true));
    }
    return List.copyOf(methods);
  }

  private boolean supportsStripeCurrency(String currency) {
    return !currency.isBlank() && currency.matches("^[A-Z]{3}$");
  }

  private static FlightChangeConfirmView.RefundMethodView buildRefundMethod(
      PaymentEntity payment, String provider) {
    String rawMethod = payment == null ? null : payment.getPaymentMethod();
    String type = rawMethod == null ? "" : rawMethod.trim().toUpperCase(Locale.ROOT);
    String label =
        switch (type) {
          case "CREDIT_CARD", "CARD", "STRIPE_CARD" -> "Credit card";
          case "DEBIT_CARD" -> "Debit card";
          case "BANK_TRANSFER", "BANK", "PAYOS_QR" -> "Bank transfer";
          case "EWALLET", "E_WALLET", "WALLET" -> "E-wallet";
          case "QR", "QR_CODE" -> "QR Code";
          default -> type.isEmpty() ? "Refund" : rawMethod;
        };
    return new FlightChangeConfirmView.RefundMethodView(
        type.isEmpty() ? null : type,
        label,
        buildMaskedAccount(payment),
        resolveEstimatedDays(provider));
  }

  private static String buildMaskedAccount(PaymentEntity payment) {
    if (payment == null) {
      return null;
    }
    String accountNumber = payment.getRefundCounterAccountNumber();
    String bankName = payment.getRefundCounterAccountBankName();
    if (accountNumber != null && !accountNumber.isBlank()) {
      String trimmed = accountNumber.trim();
      String tail = trimmed.length() <= 4 ? trimmed : trimmed.substring(trimmed.length() - 4);
      String masked = "**** " + tail;
      return bankName != null && !bankName.isBlank() ? bankName + " " + masked : masked;
    }
    if (bankName != null && !bankName.isBlank()) {
      return bankName;
    }
    return null;
  }

  private static String resolveEstimatedDays(String provider) {
    String normalized = provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "STRIPE" -> "5-10 business days";
      case "PAYOS" -> "Processed 24/7";
      default -> "3-7 business days";
    };
  }

  private String resolveRefundedPaymentStatus(PaymentEntity payment, BigDecimal refundAmount) {
    if (payment.getAmount() != null
        && money(refundAmount).compareTo(money(payment.getAmount())) >= 0) {
      return PAYMENT_STATUS_REFUNDED;
    }
    return PAYMENT_STATUS_PARTIALLY_REFUNDED;
  }

  private BigDecimal fareAmount(TicketEntity ticket) {
    BigDecimal adultFare =
        ticket.getFlightFare() == null ? ZERO : money(ticket.getFlightFare().getTotalPrice());
    String passengerType =
        ticket.getPassenger() == null ? null : normalize(ticket.getPassenger().getPassengerType());
    if ("CHILD".equals(passengerType)) {
      return normalizeMoney(adultFare.multiply(CHILD_RATE));
    }
    if ("INFANT".equals(passengerType)) {
      return normalizeMoney(adultFare.multiply(INFANT_RATE));
    }
    return adultFare;
  }

  private BigDecimal money(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal normalizeMoney(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  public record ConfirmCommand(
      Long userId,
      Long bookingId,
      String quoteToken,
      String idempotencyKey,
      Long currentFlightId,
      Long newFlightId,
      Long newFareId,
      String settlementType,
      String currency,
      BigDecimal additionalAmount,
      BigDecimal refundAmount,
      BigDecimal netAmount,
      BigDecimal expectedAdditionalAmount,
      BigDecimal expectedRefundAmount,
      BigDecimal expectedNetAmount,
      List<ItemCommand> items) {}

  public record ItemCommand(
      Long ticketId,
      Long passengerId,
      String passengerName,
      String passengerType,
      BigDecimal originalFareAmount,
      BigDecimal newFareAmount,
      BigDecimal fareDifference,
      BigDecimal changeFee,
      BigDecimal taxDifference,
      BigDecimal netAmount) {}
}
