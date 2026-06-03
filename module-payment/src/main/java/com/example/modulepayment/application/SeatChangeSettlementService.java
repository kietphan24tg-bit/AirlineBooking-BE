package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.moduleflight.application.SeatSelectionPolicy;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.SeatCategoryEntity;
import com.example.moduleflight.entity.SeatTypeEntity;
import com.example.modulepayment.application.SeatChangeConfirmView.SummaryView;
import com.example.modulepayment.application.SeatChangePreviewView.ItemView;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.entity.SeatChangeSettlementEntity;
import com.example.modulepayment.entity.SeatChangeSettlementItemEntity;
import com.example.modulepayment.gateway.PaymentRefundGateway.PaymentRefundResult;
import com.example.modulepayment.repository.BookingRepository;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.modulepayment.repository.SeatChangeSettlementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
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
public class SeatChangeSettlementService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  private static final String STATUS_COMPLETED = "COMPLETED";
  private static final String STATUS_PAYMENT_REQUIRED = "PAYMENT_REQUIRED";
  private static final String STATUS_REFUND_FAILED = "REFUND_FAILED";
  private static final String STATUS_REFUND_PENDING = "REFUND_PENDING";
  private static final String SETTLEMENT_NONE = "NONE";
  private static final String SETTLEMENT_REFUND = "REFUND";
  private static final String SETTLEMENT_COLLECT_PAYMENT = "COLLECT_PAYMENT";
  private static final String PAYMENT_STATUS_PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
  private static final String PAYMENT_STATUS_REFUNDED = "REFUNDED";
  private static final String SETTLEMENT_STATUS_CANCELLED = "CANCELLED";
  private static final String SETTLEMENT_STATUS_COMPLETED = "COMPLETED";
  private static final String SETTLEMENT_STATUS_EXPIRED = "EXPIRED";
  private static final String SETTLEMENT_STATUS_MANUAL_REVIEW_REQUIRED = "MANUAL_REVIEW_REQUIRED";
  private static final String SETTLEMENT_STATUS_PAYMENT_FAILED = "PAYMENT_FAILED";
  private static final String SETTLEMENT_STATUS_PAYMENT_PENDING = "PAYMENT_PENDING";
  private static final String SETTLEMENT_STATUS_PAYMENT_REQUIRED = "PAYMENT_REQUIRED";
  private static final String SETTLEMENT_STATUS_REFUND_PENDING = "REFUND_PENDING";
  private static final String VND = "VND";
  private static final List<String> ACTIVE_ASSIGNMENT_STATUSES = List.of("RESERVED", "BOOKED");
  private static final List<String> ACTIVE_HOLD_STATUSES = List.of("HELD", "CONFIRMED");
  private static final List<String> REFUNDABLE_PAYMENT_STATUSES =
      List.of("SUCCESS", "PARTIALLY_REFUNDED");

  private final BookingRepository bookingRepository;
  private final PassengerRepository passengerRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final FlightSeatRepository flightSeatRepository;
  private final PaymentRepository paymentRepository;
  private final SeatChangeSettlementRepository seatChangeSettlementRepository;
  private final PaymentRefundService paymentRefundService;
  private final PaymentProperties paymentProperties;

  public SeatChangeSettlementService(
      BookingRepository bookingRepository,
      PassengerRepository passengerRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      FlightSeatRepository flightSeatRepository,
      PaymentRepository paymentRepository,
      SeatChangeSettlementRepository seatChangeSettlementRepository,
      PaymentRefundService paymentRefundService,
      PaymentProperties paymentProperties) {
    this.bookingRepository = bookingRepository;
    this.passengerRepository = passengerRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.flightSeatRepository = flightSeatRepository;
    this.paymentRepository = paymentRepository;
    this.seatChangeSettlementRepository = seatChangeSettlementRepository;
    this.paymentRefundService = paymentRefundService;
    this.paymentProperties = paymentProperties;
  }

  @Transactional(readOnly = true)
  public SeatChangePreviewView preview(Long userId, Long bookingId, List<ChangeCommand> changes) {
    PreparedSettlement prepared = prepareSettlement(userId, bookingId, changes, false);
    return prepared.preview();
  }

  @Transactional
  public SeatChangeConfirmView confirm(
      Long userId,
      Long bookingId,
      List<ChangeCommand> changes,
      String idempotencyKey,
      BigDecimal expectedAdditionalAmount,
      BigDecimal expectedRefundAmount,
      BigDecimal expectedNetAmount) {
    if (!StringUtils.hasText(idempotencyKey)) {
      throw new AppException("VALIDATION_FAILED", "idempotencyKey is required", 400);
    }
    PreparedSettlement prepared = prepareSettlement(userId, bookingId, changes, true);
    validateExpectedSummary(
        prepared.preview().summary(),
        expectedAdditionalAmount,
        expectedRefundAmount,
        expectedNetAmount);

    SeatChangePreviewView preview = prepared.preview();
    if (SETTLEMENT_NONE.equals(preview.settlementType())) {
      return new SeatChangeConfirmView(
          null,
          preview.bookingId(),
          preview.currency(),
          preview.settlementType(),
          STATUS_COMPLETED,
          null,
          null,
          null,
          null,
          null,
          null,
          List.of(),
          toSummary(preview.summary()));
    }

    if (SETTLEMENT_COLLECT_PAYMENT.equals(preview.settlementType())) {
      SeatChangeSettlementEntity settlement =
          saveSettlement(
              prepared.booking(),
              preview,
              idempotencyKey,
              SETTLEMENT_STATUS_PAYMENT_REQUIRED,
              null,
              null,
              null,
              null);
      return new SeatChangeConfirmView(
          settlement.getId(),
          preview.bookingId(),
          preview.currency(),
          preview.settlementType(),
          STATUS_PAYMENT_REQUIRED,
          null,
          null,
          null,
          null,
          null,
          null,
          supportedPaymentMethods(preview.currency()),
          toSummary(preview.summary()));
    }

    var payment = requireRefundablePayment(bookingId, true);
    SeatChangeSettlementEntity settlement =
        saveSettlement(
            prepared.booking(),
            preview,
            idempotencyKey,
            SETTLEMENT_STATUS_REFUND_PENDING,
            null,
            null,
            null,
            null);
    try {
      paymentRefundService.preparePaymentForRefund(payment);
      paymentRepository.save(payment);
      PaymentRefundResult refundResult =
          paymentRefundService.refund(
              bookingId,
              idempotencyKey.trim(),
              payment,
              preview.summary().refundAmount(),
              "Seat change refund");
      payment.setPaymentStatus(
          resolveRefundedPaymentStatus(payment, preview.summary().refundAmount()));
      paymentRepository.save(payment);
      settlement.setStatus(SETTLEMENT_STATUS_COMPLETED);
      settlement.setProvider(paymentRefundService.providerOf(payment));
      settlement.setProviderRefundId(refundResult.providerRefundId());
      settlement.setUpdatedAt(java.time.OffsetDateTime.now());
      seatChangeSettlementRepository.save(settlement);
      return new SeatChangeConfirmView(
          settlement.getId(),
          preview.bookingId(),
          preview.currency(),
          preview.settlementType(),
          STATUS_REFUND_PENDING,
          "SUCCEEDED",
          paymentRefundService.providerOf(payment),
          refundResult.providerRefundId(),
          null,
          null,
          buildRefundMethod(payment, paymentRefundService.providerOf(payment)),
          List.of(),
          toSummary(preview.summary()));
    } catch (RuntimeException exception) {
      settlement.setStatus(SETTLEMENT_STATUS_PAYMENT_FAILED);
      settlement.setErrorCode(
          exception instanceof AppException appException
              ? appException.getCode()
              : "REFUND_FAILED");
      settlement.setErrorMessage(exception.getMessage());
      settlement.setUpdatedAt(java.time.OffsetDateTime.now());
      seatChangeSettlementRepository.save(settlement);
      return new SeatChangeConfirmView(
          settlement.getId(),
          preview.bookingId(),
          preview.currency(),
          preview.settlementType(),
          STATUS_REFUND_FAILED,
          "FAILED",
          null,
          null,
          exception instanceof AppException appException ? appException.getCode() : "REFUND_FAILED",
          exception.getMessage(),
          null,
          List.of(),
          toSummary(preview.summary()));
    }
  }

  @Transactional
  public SeatChangeSettlementEntity requireSettlementForPayment(
      Long bookingId, Long settlementId, Long userId) {
    SeatChangeSettlementEntity settlement =
        seatChangeSettlementRepository
            .findByIdWithItemsForUpdate(settlementId)
            .orElseThrow(
                () ->
                    new AppException(
                        "SEAT_CHANGE_SETTLEMENT_NOT_FOUND",
                        "Seat change settlement not found",
                        404));
    if (!Objects.equals(settlement.getBooking().getId(), bookingId)) {
      throw new AppException(
          "SEAT_CHANGE_SETTLEMENT_NOT_FOUND", "Seat change settlement not found", 404);
    }
    if (userId != null
        && settlement.getBooking().getUser() != null
        && !Objects.equals(settlement.getBooking().getUser().getId(), userId)) {
      throw new AppException(
          "SEAT_CHANGE_SETTLEMENT_NOT_FOUND", "Seat change settlement not found", 404);
    }
    if (!SETTLEMENT_COLLECT_PAYMENT.equals(settlement.getSettlementType())) {
      throw new AppException(
          "SEAT_CHANGE_SETTLEMENT_NOT_PAYABLE",
          "Seat change settlement does not require payment",
          409);
    }
    if (SETTLEMENT_STATUS_COMPLETED.equals(settlement.getStatus())) {
      throw new AppException(
          "SEAT_CHANGE_SETTLEMENT_COMPLETED",
          "Seat change settlement has already been completed",
          409);
    }
    return settlement;
  }

  @Transactional
  public void markPaymentCreated(SeatChangeSettlementEntity settlement) {
    settlement.setStatus(SETTLEMENT_STATUS_PAYMENT_PENDING);
    settlement.setUpdatedAt(java.time.OffsetDateTime.now());
    seatChangeSettlementRepository.save(settlement);
  }

  @Transactional
  public void handlePaymentSuccess(PaymentEntity payment, String providerPaymentReference) {
    SeatChangeSettlementEntity settlement = payment.getSeatChangeSettlement();
    if (settlement == null) {
      return;
    }
    settlement =
        seatChangeSettlementRepository.findByIdWithItemsForUpdate(settlement.getId()).orElseThrow();
    settlement.setStatus(SETTLEMENT_STATUS_COMPLETED);
    if (providerPaymentReference != null && !providerPaymentReference.isBlank()) {
      payment.setProviderPaymentReference(providerPaymentReference);
    }
    settlement.setUpdatedAt(java.time.OffsetDateTime.now());
    seatChangeSettlementRepository.save(settlement);
  }

  @Transactional
  public void handlePaymentFailure(PaymentEntity payment, String status) {
    SeatChangeSettlementEntity settlement = payment.getSeatChangeSettlement();
    if (settlement == null) {
      return;
    }
    settlement =
        seatChangeSettlementRepository.findByIdWithItemsForUpdate(settlement.getId()).orElseThrow();
    rollbackSettlement(settlement);
    settlement.setStatus(
        "EXPIRED".equalsIgnoreCase(status)
            ? SETTLEMENT_STATUS_EXPIRED
            : SETTLEMENT_STATUS_PAYMENT_FAILED);
    settlement.setUpdatedAt(java.time.OffsetDateTime.now());
    seatChangeSettlementRepository.save(settlement);
  }

  @Transactional
  public void handlePaymentCancelled(PaymentEntity payment) {
    SeatChangeSettlementEntity settlement = payment.getSeatChangeSettlement();
    if (settlement == null) {
      return;
    }
    settlement =
        seatChangeSettlementRepository.findByIdWithItemsForUpdate(settlement.getId()).orElseThrow();
    rollbackSettlement(settlement);
    settlement.setStatus(SETTLEMENT_STATUS_CANCELLED);
    settlement.setUpdatedAt(java.time.OffsetDateTime.now());
    seatChangeSettlementRepository.save(settlement);
  }

  private PreparedSettlement prepareSettlement(
      Long userId, Long bookingId, List<ChangeCommand> rawChanges, boolean forUpdate) {
    List<ChangeCommand> changes = normalizeChanges(rawChanges);
    BookingEntity booking = requireBooking(userId, bookingId, forUpdate);
    validateBookingAssignable(booking);

    Map<Long, PassengerEntity> passengersById =
        passengerRepository.findByBookingIdOrderById(bookingId).stream()
            .collect(Collectors.toMap(PassengerEntity::getId, passenger -> passenger));
    Map<Long, BookingFareHoldEntity> holdsByFlightId =
        bookingFareHoldRepository.findByBookingIdOrderByIdAsc(bookingId).stream()
            .filter(hold -> ACTIVE_HOLD_STATUSES.contains(normalize(hold.getStatus())))
            .collect(
                LinkedHashMap::new,
                (map, hold) -> map.put(hold.getFlightFare().getFlight().getId(), hold),
                Map::putAll);
    Map<String, PassengerSeatAssignmentEntity> activeAssignments =
        passengerSeatAssignmentRepository
            .findByBookingIdAndStatusIn(bookingId, ACTIVE_ASSIGNMENT_STATUSES)
            .stream()
            .collect(
                Collectors.toMap(
                    assignment ->
                        assignmentKey(
                            assignment.getPassenger().getId(),
                            assignment.getFlightSeat().getFlight().getId()),
                    assignment -> assignment,
                    (left, right) -> left,
                    LinkedHashMap::new));

    Map<Long, FlightSeatEntity> seatsById = loadReferencedSeats(changes);

    List<ItemView> items = new ArrayList<>();
    BigDecimal additionalAmount = ZERO;
    BigDecimal refundAmount = ZERO;
    for (ChangeCommand change : changes) {
      PassengerEntity passenger = passengersById.get(change.passengerId());
      if (passenger == null) {
        throw new AppException("PASSENGER_NOT_FOUND", "Passenger not found", 404);
      }
      BookingFareHoldEntity hold = holdsByFlightId.get(change.flightId());
      if (hold == null) {
        throw new AppException("FLIGHT_NOT_FOUND", "Flight is not part of this booking", 404);
      }
      PassengerSeatAssignmentEntity currentAssignment =
          activeAssignments.get(assignmentKey(change.passengerId(), change.flightId()));
      Long currentSeatId =
          currentAssignment == null ? null : currentAssignment.getFlightSeat().getId();
      if (!Objects.equals(currentSeatId, change.toSeatId())) {
        throw new AppException(
            "SEAT_CHANGE_STALE",
            "One or more seat changes are no longer valid. Please review the updated seat map.",
            409);
      }

      FlightSeatEntity fromSeat =
          requireSeatForFlight(seatsById, change.fromSeatId(), change.flightId());
      FlightSeatEntity toSeat =
          requireSeatForFlight(seatsById, change.toSeatId(), change.flightId());
      FareClassEntity fareClass = hold.getFlightFare().getFareClass();
      SeatSelectionPolicy selectionPolicy =
          SeatSelectionPolicy.fromValue(
              fareClass == null ? null : fareClass.getSeatSelectionPolicy());
      BigDecimal oldAmount =
          fromSeat == null
              ? ZERO
              : calculateSeatSurcharge(
                  fromSeat, selectionPolicy, fareClass.getIncludedSeatCategory());
      BigDecimal newAmount =
          toSeat == null
              ? ZERO
              : calculateSeatSurcharge(
                  toSeat, selectionPolicy, fareClass.getIncludedSeatCategory());
      BigDecimal delta = normalizeMoney(newAmount.subtract(oldAmount));
      if (delta.signum() > 0) {
        if (!VND.equalsIgnoreCase(booking.getCurrency())) {
          throw new AppException(
              "SEAT_SURCHARGE_CURRENCY_UNSUPPORTED",
              "Seat surcharge is only supported for VND bookings",
              409);
        }
        if ("CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
          throw new AppException(
              "SEAT_PAYMENT_REQUIRED",
              "Paid seat selection is not supported after booking is confirmed",
              409);
        }
        additionalAmount = normalizeMoney(additionalAmount.add(delta));
      } else if (delta.signum() < 0) {
        refundAmount = normalizeMoney(refundAmount.add(delta.abs()));
      }
      items.add(
          new ItemView(
              change.passengerId(),
              buildPassengerName(passenger),
              change.flightId(),
              hold.getFlightFare().getFlight().getFlightNumber(),
              hold.getFlightFare().getFlight().getDepartureAirport().getCode(),
              hold.getFlightFare().getFlight().getArrivalAirport().getCode(),
              hold.getFlightFare().getFlight().getDepartureTime(),
              change.fromSeatId(),
              fromSeat == null ? null : fromSeat.getSeatNumber(),
              change.toSeatId(),
              toSeat == null ? null : toSeat.getSeatNumber(),
              oldAmount,
              newAmount,
              delta,
              "VALID",
              null));
    }

    BigDecimal netAmount = normalizeMoney(additionalAmount.subtract(refundAmount));
    String settlementType =
        netAmount.signum() > 0
            ? SETTLEMENT_COLLECT_PAYMENT
            : netAmount.signum() < 0 ? SETTLEMENT_REFUND : SETTLEMENT_NONE;
    SeatChangePreviewView preview =
        new SeatChangePreviewView(
            booking.getId(),
            booking.getCurrency(),
            buildPreviewToken(booking.getId(), changes),
            settlementType,
            items,
            new SeatChangePreviewView.SummaryView(additionalAmount, refundAmount, netAmount));
    return new PreparedSettlement(booking, preview);
  }

  private String buildPassengerName(PassengerEntity passenger) {
    String lastName = passenger.getLastName() == null ? "" : passenger.getLastName().trim();
    String firstName = passenger.getFirstName() == null ? "" : passenger.getFirstName().trim();
    String fullName = (lastName + " " + firstName).trim();
    return fullName.isEmpty() ? "Passenger " + passenger.getId() : fullName;
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

  private void validateBookingAssignable(BookingEntity booking) {
    String status = normalize(booking.getStatus());
    if (!"PENDING_PAYMENT".equals(status) && !"CONFIRMED".equals(status)) {
      throw new AppException(
          "BOOKING_NOT_ASSIGNABLE", "Booking is not eligible for seat assignment", 409);
    }
  }

  private List<ChangeCommand> normalizeChanges(List<ChangeCommand> rawChanges) {
    if (rawChanges == null || rawChanges.isEmpty()) {
      throw new AppException("VALIDATION_FAILED", "changes is required", 400);
    }
    Map<String, ChangeCommand> deduped = new LinkedHashMap<>();
    for (ChangeCommand change : rawChanges) {
      if (change == null
          || change.passengerId() == null
          || change.flightId() == null
          || (change.fromSeatId() == null && change.toSeatId() == null)) {
        throw new AppException(
            "VALIDATION_FAILED",
            "Each change must include passenger, flight, and seat information",
            400);
      }
      deduped.put(assignmentKey(change.passengerId(), change.flightId()), change);
    }
    return List.copyOf(deduped.values());
  }

  private Map<Long, FlightSeatEntity> loadReferencedSeats(List<ChangeCommand> changes) {
    Set<Long> seatIds =
        changes.stream()
            .flatMap(change -> java.util.stream.Stream.of(change.fromSeatId(), change.toSeatId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (seatIds.isEmpty()) {
      return Map.of();
    }
    List<FlightSeatEntity> seats = flightSeatRepository.findAllById(seatIds);
    if (seats.size() != seatIds.size()) {
      throw new AppException("SEAT_NOT_FOUND", "One or more seats were not found", 404);
    }
    return seats.stream().collect(Collectors.toMap(FlightSeatEntity::getId, seat -> seat));
  }

  private FlightSeatEntity requireSeatForFlight(
      Map<Long, FlightSeatEntity> seatsById, Long seatId, Long flightId) {
    if (seatId == null) {
      return null;
    }
    FlightSeatEntity seat = seatsById.get(seatId);
    if (seat == null) {
      throw new AppException("SEAT_NOT_FOUND", "Seat not found", 404);
    }
    if (!Objects.equals(seat.getFlight().getId(), flightId)) {
      throw new AppException("SEAT_NOT_ALLOWED", "Seat does not belong to booking flight", 409);
    }
    return seat;
  }

  private PaymentEntity requireRefundablePayment(Long bookingId, boolean forUpdate) {
    List<com.example.modulepayment.entity.PaymentEntity> preferredPayments =
        forUpdate
            ? paymentRepository
                .findBookingLevelPaymentsByBookingIdAndPaymentStatusInForUpdateOrderByLatest(
                    bookingId, REFUNDABLE_PAYMENT_STATUSES)
            : paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
                bookingId, REFUNDABLE_PAYMENT_STATUSES);
    if (!preferredPayments.isEmpty()) {
      return preferredPayments.getFirst();
    }

    List<com.example.modulepayment.entity.PaymentEntity> fallbackPayments =
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

  private void validateExpectedSummary(
      SeatChangePreviewView.SummaryView summary,
      BigDecimal expectedAdditionalAmount,
      BigDecimal expectedRefundAmount,
      BigDecimal expectedNetAmount) {
    if (normalizeMoney(expectedAdditionalAmount).compareTo(summary.additionalAmount()) != 0
        || normalizeMoney(expectedRefundAmount).compareTo(summary.refundAmount()) != 0
        || normalizeMoney(expectedNetAmount).compareTo(summary.netAmount()) != 0) {
      throw new AppException(
          "SEAT_CHANGE_PREVIEW_STALE",
          "Seat change totals are out of date. Please review the latest preview.",
          409);
    }
  }

  private SummaryView toSummary(SeatChangePreviewView.SummaryView summary) {
    return new SummaryView(summary.additionalAmount(), summary.refundAmount(), summary.netAmount());
  }

  private static SeatChangeConfirmView.RefundMethodView buildRefundMethod(
      PaymentEntity payment, String provider) {
    if (payment == null) {
      return null;
    }
    String rawMethod = payment.getPaymentMethod();
    String type = rawMethod == null ? "" : rawMethod.trim().toUpperCase(Locale.ROOT);
    String label =
        switch (type) {
          case "CREDIT_CARD", "CARD" -> "Credit card";
          case "DEBIT_CARD" -> "Debit card";
          case "BANK_TRANSFER", "BANK" -> "Bank transfer";
          case "EWALLET", "E_WALLET", "WALLET" -> "E-wallet";
          case "QR", "QR_CODE" -> "QR Code";
          default -> type.isEmpty() ? "Refund" : rawMethod;
        };
    String maskedAccount = buildMaskedAccount(payment);
    String estimatedDays = resolveEstimatedDays(provider);
    return new SeatChangeConfirmView.RefundMethodView(
        type.isEmpty() ? null : type, label, maskedAccount, estimatedDays);
  }

  private static String buildMaskedAccount(PaymentEntity payment) {
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

  private SeatChangeSettlementEntity saveSettlement(
      BookingEntity booking,
      SeatChangePreviewView preview,
      String idempotencyKey,
      String status,
      String provider,
      String providerRefundId,
      String errorCode,
      String errorMessage) {
    SeatChangeSettlementEntity settlement = new SeatChangeSettlementEntity();
    settlement.setBooking(booking);
    settlement.setPreviewToken(preview.previewToken());
    settlement.setIdempotencyKey(idempotencyKey == null ? null : idempotencyKey.trim());
    settlement.setSettlementType(preview.settlementType());
    settlement.setStatus(status);
    settlement.setAdditionalAmount(preview.summary().additionalAmount());
    settlement.setRefundAmount(preview.summary().refundAmount());
    settlement.setNetAmount(preview.summary().netAmount());
    settlement.setCurrency(preview.currency());
    settlement.setProvider(provider);
    settlement.setProviderRefundId(providerRefundId);
    settlement.setErrorCode(errorCode);
    settlement.setErrorMessage(errorMessage);
    var now = java.time.OffsetDateTime.now();
    settlement.setCreatedAt(now);
    settlement.setUpdatedAt(now);
    for (ItemView itemView : preview.items()) {
      SeatChangeSettlementItemEntity item = new SeatChangeSettlementItemEntity();
      item.setSettlement(settlement);
      item.setPassengerId(itemView.passengerId());
      item.setFlightId(itemView.flightId());
      item.setFromSeatId(itemView.fromSeatId());
      item.setFromSeatNumber(itemView.fromSeatNumber());
      item.setToSeatId(itemView.toSeatId());
      item.setToSeatNumber(itemView.toSeatNumber());
      item.setOldAmount(itemView.oldAmount());
      item.setNewAmount(itemView.newAmount());
      item.setDeltaAmount(itemView.delta());
      settlement.getItems().add(item);
    }
    return seatChangeSettlementRepository.save(settlement);
  }

  private List<SeatChangeConfirmView.PaymentMethodItemView> supportedPaymentMethods(
      String currency) {
    if (!VND.equalsIgnoreCase(currency)) {
      return List.of();
    }
    List<SeatChangeConfirmView.PaymentMethodItemView> methods = new ArrayList<>();
    if (paymentProperties.isPayosConfigured()) {
      methods.add(
          new SeatChangeConfirmView.PaymentMethodItemView(
              "PAYOS_QR", "Mobile Banking (VietQR)", "PAYOS", null, true));
    }
    if (paymentProperties.isStripeConfigured()) {
      methods.add(
          new SeatChangeConfirmView.PaymentMethodItemView(
              "STRIPE_CARD", "International credit or debit card", "STRIPE", null, true));
    }
    return List.copyOf(methods);
  }

  private void rollbackSettlement(SeatChangeSettlementEntity settlement) {
    var booking = settlement.getBooking();
    if (booking == null) {
      settlement.setStatus(SETTLEMENT_STATUS_MANUAL_REVIEW_REQUIRED);
      return;
    }
    var now = java.time.OffsetDateTime.now();
    List<PassengerSeatAssignmentEntity> activeAssignments =
        passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(
            booking.getId(), ACTIVE_ASSIGNMENT_STATUSES);
    Map<String, PassengerSeatAssignmentEntity> activeByPassengerFlight =
        activeAssignments.stream()
            .collect(
                Collectors.toMap(
                    assignment ->
                        assignmentKey(
                            assignment.getPassenger().getId(),
                            assignment.getFlightSeat().getFlight().getId()),
                    assignment -> assignment,
                    (left, right) -> left,
                    LinkedHashMap::new));
    for (SeatChangeSettlementItemEntity item : settlement.getItems()) {
      String key = assignmentKey(item.getPassengerId(), item.getFlightId());
      PassengerSeatAssignmentEntity current = activeByPassengerFlight.get(key);
      if (current != null && item.getToSeatId() != null) {
        current.setStatus("RELEASED");
        current.setUpdatedAt(now);
        passengerSeatAssignmentRepository.save(current);
        int released =
            flightSeatRepository.releaseSelectedForBooking(
                booking.getId(), List.of(item.getToSeatId()));
        if (released != 1) {
          settlement.setStatus(SETTLEMENT_STATUS_MANUAL_REVIEW_REQUIRED);
          return;
        }
      }
      if (item.getFromSeatId() == null) {
        continue;
      }
      FlightSeatEntity originalSeat =
          flightSeatRepository
              .findById(item.getFromSeatId())
              .orElseThrow(
                  () ->
                      new AppException(
                          "SEAT_NOT_FOUND", "Original seat not found during rollback", 404));
      int restored =
          "CONFIRMED".equalsIgnoreCase(booking.getStatus())
              ? flightSeatRepository.bookSelectedForBooking(
                  booking.getId(), List.of(item.getFromSeatId()))
              : flightSeatRepository.reserveSelectedForBooking(
                  booking.getId(), List.of(item.getFromSeatId()), booking.getPnrExpiresAt());
      if (restored != 1) {
        settlement.setStatus(SETTLEMENT_STATUS_MANUAL_REVIEW_REQUIRED);
        return;
      }
      PassengerEntity passenger =
          passengerRepository
              .findByIdAndBookingId(item.getPassengerId(), booking.getId())
              .orElseThrow(
                  () ->
                      new AppException(
                          "PASSENGER_NOT_FOUND", "Passenger not found during rollback", 404));
      PassengerSeatAssignmentEntity restoredAssignment = new PassengerSeatAssignmentEntity();
      restoredAssignment.setBooking(booking);
      restoredAssignment.setPassenger(passenger);
      restoredAssignment.setFlightSeat(originalSeat);
      restoredAssignment.setStatus(
          "CONFIRMED".equalsIgnoreCase(booking.getStatus()) ? "BOOKED" : "RESERVED");
      restoredAssignment.setSurchargeAmount(item.getOldAmount());
      restoredAssignment.setCurrency(settlement.getCurrency());
      restoredAssignment.setCreatedAt(now);
      restoredAssignment.setUpdatedAt(now);
      passengerSeatAssignmentRepository.save(restoredAssignment);
    }
  }

  private BigDecimal calculateSeatSurcharge(
      FlightSeatEntity seat,
      SeatSelectionPolicy selectionPolicy,
      SeatCategoryEntity includedSeatCategory) {
    BigDecimal surcharge =
        isSeatIncluded(selectionPolicy, seat, includedSeatCategory)
            ? ZERO
            : surchargeAmount(seat.getSeatCategory()).add(surchargeAmount(seat.getSeatType()));
    return normalizeMoney(surcharge);
  }

  private boolean isSeatIncluded(
      SeatSelectionPolicy selectionPolicy,
      FlightSeatEntity seat,
      SeatCategoryEntity includedSeatCategory) {
    if (!selectionPolicy.isIncluded()
        || includedSeatCategory == null
        || seat.getSeatCategory() == null) {
      return false;
    }
    Integer includedRank = includedSeatCategory.getPricingRank();
    Integer seatRank = seat.getSeatCategory().getPricingRank();
    if (includedRank == null || seatRank == null) {
      return false;
    }
    return seatRank <= includedRank;
  }

  private BigDecimal surchargeAmount(SeatCategoryEntity seatCategory) {
    if (seatCategory == null || seatCategory.getSurchargeAmount() == null) {
      return ZERO;
    }
    return seatCategory.getSurchargeAmount();
  }

  private BigDecimal surchargeAmount(SeatTypeEntity seatType) {
    if (seatType == null || seatType.getSurchargeAmount() == null) {
      return ZERO;
    }
    return seatType.getSurchargeAmount();
  }

  private BigDecimal normalizeMoney(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  private String resolveRefundedPaymentStatus(
      com.example.modulepayment.entity.PaymentEntity payment, BigDecimal refundAmount) {
    if (payment.getAmount() != null
        && normalizeMoney(refundAmount).compareTo(normalizeMoney(payment.getAmount())) >= 0) {
      return PAYMENT_STATUS_REFUNDED;
    }
    return PAYMENT_STATUS_PARTIALLY_REFUNDED;
  }

  private String buildPreviewToken(Long bookingId, List<ChangeCommand> changes) {
    String payload =
        bookingId
            + "|"
            + changes.stream()
                .map(
                    change ->
                        change.passengerId()
                            + ":"
                            + change.flightId()
                            + ":"
                            + change.fromSeatId()
                            + ":"
                            + change.toSeatId())
                .collect(Collectors.joining("|"));
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return "seatchg_prev_"
          + HexFormat.of()
              .formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)))
              .substring(0, 16);
    } catch (NoSuchAlgorithmException exception) {
      return "seatchg_prev_" + Math.abs(payload.hashCode());
    }
  }

  private String assignmentKey(Long passengerId, Long flightId) {
    return passengerId + ":" + flightId;
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  public record ChangeCommand(Long passengerId, Long flightId, Long fromSeatId, Long toSeatId) {}

  private record PreparedSettlement(BookingEntity booking, SeatChangePreviewView preview) {}
}
