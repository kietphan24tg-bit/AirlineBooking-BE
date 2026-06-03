package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.application.TicketIssuanceService;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.modulepayment.entity.BookingCancellationEntity;
import com.example.modulepayment.entity.BookingCancellationItemEntity;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.entity.TicketFinancialSnapshotEntity;
import com.example.modulepayment.gateway.PaymentRefundGateway.PaymentRefundResult;
import com.example.modulepayment.repository.BookingCancellationRepository;
import com.example.modulepayment.repository.BookingRepository;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.modulepayment.repository.TicketFinancialSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class BookingCancellationService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  private static final String BOOKING_STATUS_CANCELLED = "CANCELLED";
  private static final String BOOKING_STATUS_CONFIRMED = "CONFIRMED";
  private static final String BOOKING_STATUS_CHECKED_IN_LOCKED = "CHECKED_IN_LOCKED";
  private static final String CANCELLATION_STATUS_FAILED = "FAILED";
  private static final String CANCELLATION_STATUS_PENDING = "PENDING";
  private static final String CANCELLATION_STATUS_SUCCEEDED = "SUCCEEDED";
  private static final List<String> CANCELLABLE_PAYMENT_STATUSES =
      List.of("SUCCESS", "PARTIALLY_REFUNDED");
  private static final Set<String> CANCELLABLE_TICKET_STATUSES = Set.of("ISSUED");
  private static final List<String> RELEASEABLE_ASSIGNMENT_STATUSES = List.of("BOOKED");
  private static final String PAYMENT_STATUS_PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
  private static final String PAYMENT_STATUS_REFUNDED = "REFUNDED";

  private final BookingRepository bookingRepository;
  private final PaymentRepository paymentRepository;
  private final TicketRepository ticketRepository;
  private final TicketFinancialSnapshotRepository ticketFinancialSnapshotRepository;
  private final BookingCancellationRepository bookingCancellationRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final FlightSeatRepository flightSeatRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final TicketIssuanceService ticketIssuanceService;
  private final TicketFinancialSnapshotService ticketFinancialSnapshotService;
  private final PaymentRefundService paymentRefundService;
  private final BookingCancellationNotifier bookingCancellationNotifier;

  public BookingCancellationService(
      BookingRepository bookingRepository,
      PaymentRepository paymentRepository,
      TicketRepository ticketRepository,
      TicketFinancialSnapshotRepository ticketFinancialSnapshotRepository,
      BookingCancellationRepository bookingCancellationRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      FlightSeatRepository flightSeatRepository,
      BookingServiceRepository bookingServiceRepository,
      TicketIssuanceService ticketIssuanceService,
      TicketFinancialSnapshotService ticketFinancialSnapshotService,
      PaymentRefundService paymentRefundService,
      BookingCancellationNotifier bookingCancellationNotifier) {
    this.bookingRepository = bookingRepository;
    this.paymentRepository = paymentRepository;
    this.ticketRepository = ticketRepository;
    this.ticketFinancialSnapshotRepository = ticketFinancialSnapshotRepository;
    this.bookingCancellationRepository = bookingCancellationRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.flightSeatRepository = flightSeatRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.ticketIssuanceService = ticketIssuanceService;
    this.ticketFinancialSnapshotService = ticketFinancialSnapshotService;
    this.paymentRefundService = paymentRefundService;
    this.bookingCancellationNotifier = bookingCancellationNotifier;
  }

  @Transactional
  public BookingCancellationPreviewView preview(Long userId, Long bookingId, List<Long> ticketIds) {
    List<Long> normalizedTicketIds = normalizeTicketIds(ticketIds);
    ticketIssuanceService.issueTicketsForBooking(userId, bookingId);

    BookingEntity booking = requirePreviewBooking(bookingId, userId);
    validateBookingCancellable(booking);
    PaymentEntity payment = requireRefundablePayment(booking.getId(), false);
    ticketFinancialSnapshotService.ensureSnapshotsForBooking(booking);
    PreparedCancellation prepared =
        preparePreviewCancellation(booking, payment, normalizedTicketIds);
    return prepared.preview();
  }

  @Transactional
  public BookingCancellationResultView cancel(
      Long userId,
      Long bookingId,
      List<Long> ticketIds,
      String reason,
      String idempotencyKey,
      BigDecimal expectedNetRefund,
      BigDecimal expectedRefundFee) {
    requireUser(userId);
    if (!StringUtils.hasText(idempotencyKey)) {
      throw new AppException("VALIDATION_FAILED", "idempotencyKey is required", 400);
    }
    List<Long> normalizedTicketIds = normalizeTicketIds(ticketIds);
    String normalizedReason = normalizeReason(reason);
    String requestHash =
        requestHash(normalizedTicketIds, normalizedReason, expectedNetRefund, expectedRefundFee);

    BookingCancellationEntity existing =
        bookingCancellationRepository
            .findByBookingIdAndIdempotencyKeyWithItems(bookingId, idempotencyKey.trim())
            .orElse(null);
    if (existing != null) {
      if (!Objects.equals(existing.getRequestHash(), requestHash)) {
        throw new AppException(
            "IDEMPOTENCY_KEY_REUSED", "idempotencyKey was used with a different payload", 409);
      }
      return toResultView(existing, existing.getBooking(), existing.getPayment());
    }

    ticketIssuanceService.issueTicketsForBooking(userId, bookingId);

    BookingEntity booking = requireBookingForUser(bookingId, userId, true);
    validateBookingCancellable(booking);
    PaymentEntity payment = requireRefundablePayment(booking.getId(), true);
    ticketFinancialSnapshotService.ensureSnapshotsForBooking(booking);
    PreparedCancellation prepared =
        prepareCancellation(booking, payment, normalizedTicketIds, true);

    validateExpectedTotals(prepared.preview(), expectedNetRefund, expectedRefundFee);

    OffsetDateTime now = OffsetDateTime.now();
    BookingCancellationEntity cancellation =
        newCancellation(
            booking,
            payment,
            idempotencyKey.trim(),
            requestHash,
            normalizedReason,
            prepared.tickets(),
            prepared.preview(),
            now);
    bookingCancellationRepository.save(cancellation);

    try {
      paymentRefundService.preparePaymentForRefund(payment);
      paymentRepository.save(payment);
      PaymentRefundResult refundResult =
          paymentRefundService.refund(
              cancellation.getId(),
              cancellation.getIdempotencyKey(),
              payment,
              prepared.preview().summary().netRefund(),
              normalizedReason);

      applySuccessfulCancellation(prepared, cancellation, refundResult, now);
      registerSuccessNotification(cancellation.getId());
      return toResultView(cancellation, booking, payment);
    } catch (RuntimeException exception) {
      cancellation.setRefundStatus(CANCELLATION_STATUS_FAILED);
      cancellation.setErrorCode(
          exception instanceof AppException appException
              ? appException.getCode()
              : "REFUND_FAILED");
      cancellation.setErrorMessage(exception.getMessage());
      cancellation.setProcessedAt(now);
      cancellation.setUpdatedAt(now);
      bookingCancellationRepository.save(cancellation);
      return toResultView(cancellation, booking, payment);
    }
  }

  private PreparedCancellation prepareCancellation(
      BookingEntity booking, PaymentEntity payment, List<Long> ticketIds, boolean forUpdate) {
    List<TicketEntity> selectedTickets =
        forUpdate
            ? ticketRepository.findByBookingIdAndTicketIdInWithDetailsForUpdate(
                booking.getId(), ticketIds)
            : ticketRepository.findByBookingIdAndTicketIdInWithDetails(booking.getId(), ticketIds);
    if (selectedTickets.size() != ticketIds.size()) {
      throw new AppException(
          "TICKET_NOT_FOUND", "One or more selected tickets do not belong to this booking", 404);
    }

    Map<Long, TicketFinancialSnapshotEntity> snapshotByTicketId =
        ticketFinancialSnapshotRepository.findByBookingIdWithDetails(booking.getId()).stream()
            .collect(
                LinkedHashMap::new,
                (map, snapshot) -> map.put(snapshot.getTicket().getId(), snapshot),
                Map::putAll);

    List<BookingCancellationPreviewView.ItemView> items = new ArrayList<>();
    BigDecimal fareGross = ZERO;
    BigDecimal promoAllocated = ZERO;
    BigDecimal fareNet = ZERO;
    BigDecimal seatRefund = ZERO;
    BigDecimal serviceRefund = ZERO;
    BigDecimal refundFee = ZERO;
    BigDecimal netRefund = ZERO;

    for (TicketEntity ticket : selectedTickets) {
      validateTicketCancellable(ticket);
      TicketFinancialSnapshotEntity snapshot = snapshotByTicketId.get(ticket.getId());
      if (snapshot == null) {
        throw new AppException(
            "CANCELLATION_SNAPSHOT_MISSING", "Ticket financial snapshot is missing", 409);
      }
      if (!snapshot.isRefundable()) {
        throw new AppException("TICKET_NOT_REFUNDABLE", "Selected ticket is not refundable", 409);
      }
      BigDecimal itemNetRefund =
          normalizeMoney(
              snapshot
                  .getFareNetAmount()
                  .add(snapshot.getSeatRefundAmount())
                  .add(snapshot.getServiceRefundAmount())
                  .subtract(snapshot.getRefundFeeAmount()));
      String passengerName =
          ticket.getPassenger().getFirstName() + " " + ticket.getPassenger().getLastName();
      items.add(
          new BookingCancellationPreviewView.ItemView(
              ticket.getId(),
              ticket.getPassenger().getId(),
              ticket.getPassenger().getPassengerType(),
              passengerName.trim(),
              true,
              null,
              snapshot.getFareGrossAmount(),
              snapshot.getPromoAllocatedAmount(),
              snapshot.getFareNetAmount(),
              snapshot.getSeatRefundAmount(),
              snapshot.getServiceRefundAmount(),
              snapshot.getRefundFeeAmount(),
              itemNetRefund));
      fareGross = normalizeMoney(fareGross.add(snapshot.getFareGrossAmount()));
      promoAllocated = normalizeMoney(promoAllocated.add(snapshot.getPromoAllocatedAmount()));
      fareNet = normalizeMoney(fareNet.add(snapshot.getFareNetAmount()));
      seatRefund = normalizeMoney(seatRefund.add(snapshot.getSeatRefundAmount()));
      serviceRefund = normalizeMoney(serviceRefund.add(snapshot.getServiceRefundAmount()));
      refundFee = normalizeMoney(refundFee.add(snapshot.getRefundFeeAmount()));
      netRefund = normalizeMoney(netRefund.add(itemNetRefund));
    }

    BookingCancellationPreviewView preview =
        new BookingCancellationPreviewView(
            booking.getId(),
            booking.getStatus(),
            payment.getPaymentStatus(),
            payment.getCurrency(),
            items,
            new BookingCancellationPreviewView.SummaryView(
                fareGross,
                promoAllocated,
                fareNet,
                seatRefund,
                serviceRefund,
                refundFee,
                netRefund));
    return new PreparedCancellation(booking, payment, selectedTickets, preview);
  }

  private PreparedCancellation preparePreviewCancellation(
      BookingEntity booking, PaymentEntity payment, List<Long> ticketIds) {
    List<TicketEntity> selectedTickets =
        ticketRepository.findByBookingIdAndTicketIdInWithDetails(booking.getId(), ticketIds);
    if (selectedTickets.size() != ticketIds.size()) {
      throw new AppException(
          "TICKET_NOT_FOUND", "One or more selected tickets do not belong to this booking", 404);
    }
    return prepareCancellationPreviewFromTickets(booking, payment, selectedTickets);
  }

  private PreparedCancellation prepareCancellationPreviewFromTickets(
      BookingEntity booking, PaymentEntity payment, List<TicketEntity> selectedTickets) {
    List<TicketEntity> orderedTickets =
        selectedTickets.stream().sorted(Comparator.comparing(TicketEntity::getId)).toList();

    Map<Long, TicketFinancialSnapshotEntity> snapshotByTicketId =
        ticketFinancialSnapshotRepository.findByBookingIdWithDetails(booking.getId()).stream()
            .collect(
                LinkedHashMap::new,
                (map, snapshot) -> map.put(snapshot.getTicket().getId(), snapshot),
                Map::putAll);

    List<BookingCancellationPreviewView.ItemView> items = new ArrayList<>();
    BigDecimal fareGross = ZERO;
    BigDecimal promoAllocated = ZERO;
    BigDecimal fareNet = ZERO;
    BigDecimal seatRefund = ZERO;
    BigDecimal serviceRefund = ZERO;
    BigDecimal refundFee = ZERO;
    BigDecimal netRefund = ZERO;

    for (TicketEntity ticket : orderedTickets) {
      validateTicketCancellable(ticket);
      TicketFinancialSnapshotEntity snapshot = snapshotByTicketId.get(ticket.getId());
      if (snapshot == null) {
        throw new AppException(
            "CANCELLATION_SNAPSHOT_MISSING", "Ticket financial snapshot is missing", 409);
      }
      if (!snapshot.isRefundable()) {
        throw new AppException("TICKET_NOT_REFUNDABLE", "Selected ticket is not refundable", 409);
      }
      BigDecimal itemNetRefund =
          normalizeMoney(
              snapshot
                  .getFareNetAmount()
                  .add(snapshot.getSeatRefundAmount())
                  .add(snapshot.getServiceRefundAmount())
                  .subtract(snapshot.getRefundFeeAmount()));
      String passengerName =
          ticket.getPassenger().getFirstName() + " " + ticket.getPassenger().getLastName();
      items.add(
          new BookingCancellationPreviewView.ItemView(
              ticket.getId(),
              ticket.getPassenger().getId(),
              ticket.getPassenger().getPassengerType(),
              passengerName.trim(),
              true,
              null,
              snapshot.getFareGrossAmount(),
              snapshot.getPromoAllocatedAmount(),
              snapshot.getFareNetAmount(),
              snapshot.getSeatRefundAmount(),
              snapshot.getServiceRefundAmount(),
              snapshot.getRefundFeeAmount(),
              itemNetRefund));
      fareGross = normalizeMoney(fareGross.add(snapshot.getFareGrossAmount()));
      promoAllocated = normalizeMoney(promoAllocated.add(snapshot.getPromoAllocatedAmount()));
      fareNet = normalizeMoney(fareNet.add(snapshot.getFareNetAmount()));
      seatRefund = normalizeMoney(seatRefund.add(snapshot.getSeatRefundAmount()));
      serviceRefund = normalizeMoney(serviceRefund.add(snapshot.getServiceRefundAmount()));
      refundFee = normalizeMoney(refundFee.add(snapshot.getRefundFeeAmount()));
      netRefund = normalizeMoney(netRefund.add(itemNetRefund));
    }

    BookingCancellationPreviewView preview =
        new BookingCancellationPreviewView(
            booking.getId(),
            booking.getStatus(),
            payment.getPaymentStatus(),
            payment.getCurrency(),
            items,
            new BookingCancellationPreviewView.SummaryView(
                fareGross,
                promoAllocated,
                fareNet,
                seatRefund,
                serviceRefund,
                refundFee,
                netRefund));
    return new PreparedCancellation(booking, payment, orderedTickets, preview);
  }

  private void applySuccessfulCancellation(
      PreparedCancellation prepared,
      BookingCancellationEntity cancellation,
      PaymentRefundResult refundResult,
      OffsetDateTime now) {
    List<Long> passengerIds =
        prepared.tickets().stream().map(ticket -> ticket.getPassenger().getId()).toList();
    List<PassengerSeatAssignmentEntity> assignments =
        passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            prepared.booking().getId(), passengerIds, RELEASEABLE_ASSIGNMENT_STATUSES);
    for (PassengerSeatAssignmentEntity assignment : assignments) {
      assignment.setStatus("RELEASED");
      assignment.setUpdatedAt(now);
    }
    if (!assignments.isEmpty()) {
      passengerSeatAssignmentRepository.saveAll(assignments);
    }

    List<Long> seatIds =
        assignments.stream()
            .map(PassengerSeatAssignmentEntity::getFlightSeat)
            .filter(Objects::nonNull)
            .map(flightSeat -> flightSeat.getId())
            .distinct()
            .toList();
    if (!seatIds.isEmpty()) {
      int released =
          flightSeatRepository.releaseSelectedForBooking(prepared.booking().getId(), seatIds);
      if (released != seatIds.size()) {
        throw new AppException(
            "SEAT_RELEASE_FAILED", "Could not release one or more booked seats", 409);
      }
    }

    for (TicketEntity ticket : prepared.tickets()) {
      ticket.setStatus("CANCELLED");
      ticket.setUpdatedAt(now);
    }
    ticketRepository.saveAll(prepared.tickets());
    bookingServiceRepository.deleteByBookingIdAndPassengerIdIn(
        prepared.booking().getId(), passengerIds);

    long remainingActiveTickets =
        ticketRepository.countByBookingIdAndStatusNot(prepared.booking().getId(), "CANCELLED");
    if (remainingActiveTickets == 0) {
      prepared.booking().setStatus(BOOKING_STATUS_CANCELLED);
      prepared.payment().setPaymentStatus(PAYMENT_STATUS_REFUNDED);
    } else {
      prepared.booking().setStatus(BOOKING_STATUS_CONFIRMED);
      prepared.payment().setPaymentStatus(PAYMENT_STATUS_PARTIALLY_REFUNDED);
    }
    prepared.booking().setUpdatedAt(now);
    prepared.payment().setUpdatedAt(now);
    bookingRepository.save(prepared.booking());
    paymentRepository.save(prepared.payment());

    cancellation.setProviderRefundId(refundResult.providerRefundId());
    cancellation.setProviderStatus(refundResult.providerStatus());
    cancellation.setRefundStatus(CANCELLATION_STATUS_SUCCEEDED);
    cancellation.setProcessedAt(now);
    cancellation.setUpdatedAt(now);
    bookingCancellationRepository.save(cancellation);
  }

  private BookingCancellationEntity newCancellation(
      BookingEntity booking,
      PaymentEntity payment,
      String idempotencyKey,
      String requestHash,
      String reason,
      List<TicketEntity> tickets,
      BookingCancellationPreviewView preview,
      OffsetDateTime now) {
    BookingCancellationEntity cancellation = new BookingCancellationEntity();
    cancellation.setBooking(booking);
    cancellation.setPayment(payment);
    cancellation.setRequestedByUser(booking.getUser());
    cancellation.setIdempotencyKey(idempotencyKey);
    cancellation.setRequestHash(requestHash);
    cancellation.setReason(reason);
    cancellation.setProvider(paymentRefundService.providerOf(payment));
    cancellation.setRefundStatus(CANCELLATION_STATUS_PENDING);
    cancellation.setCurrency(preview.currency());
    cancellation.setFareGrossTotal(preview.summary().fareGross());
    cancellation.setPromoAllocatedTotal(preview.summary().promoAllocated());
    cancellation.setFareNetTotal(preview.summary().fareNet());
    cancellation.setSeatRefundTotal(preview.summary().seatRefund());
    cancellation.setServiceRefundTotal(preview.summary().serviceRefund());
    cancellation.setRefundFeeTotal(preview.summary().refundFee());
    cancellation.setNetRefundTotal(preview.summary().netRefund());
    cancellation.setCreatedAt(now);
    cancellation.setUpdatedAt(now);
    Map<Long, TicketEntity> ticketById =
        tickets.stream()
            .collect(
                LinkedHashMap::new, (map, ticket) -> map.put(ticket.getId(), ticket), Map::putAll);
    for (BookingCancellationPreviewView.ItemView itemView : preview.items()) {
      TicketEntity ticket = ticketById.get(itemView.ticketId());
      if (ticket == null) {
        throw new AppException("TICKET_NOT_FOUND", "Ticket not found", 404);
      }
      BookingCancellationItemEntity item = new BookingCancellationItemEntity();
      item.setCancellation(cancellation);
      item.setTicket(ticket);
      item.setPassenger(ticket.getPassenger());
      item.setFareGrossAmount(itemView.fareGross());
      item.setPromoAllocatedAmount(itemView.promoAllocated());
      item.setFareNetAmount(itemView.fareNet());
      item.setSeatRefundAmount(itemView.seatRefund());
      item.setServiceRefundAmount(itemView.serviceRefund());
      item.setRefundFeeAmount(itemView.refundFee());
      item.setNetRefundAmount(itemView.netRefund());
      item.setCreatedAt(now);
      cancellation.getItems().add(item);
    }
    return cancellation;
  }

  private void validateExpectedTotals(
      BookingCancellationPreviewView preview,
      BigDecimal expectedNetRefund,
      BigDecimal expectedRefundFee) {
    if (normalizeMoney(expectedNetRefund).compareTo(preview.summary().netRefund()) != 0
        || normalizeMoney(expectedRefundFee).compareTo(preview.summary().refundFee()) != 0) {
      throw new AppException(
          "CANCELLATION_PREVIEW_STALE", "Cancellation preview is stale, please preview again", 409);
    }
  }

  private void validateTicketCancellable(TicketEntity ticket) {
    String status = normalize(ticket.getStatus());
    if (!CANCELLABLE_TICKET_STATUSES.contains(status)) {
      throw new AppException("TICKET_NOT_CANCELLABLE", "Selected ticket cannot be cancelled", 409);
    }
  }

  private PaymentEntity requireRefundablePayment(Long bookingId, boolean forUpdate) {
    List<PaymentEntity> preferredPayments =
        forUpdate
            ? paymentRepository
                .findBookingLevelPaymentsByBookingIdAndPaymentStatusInForUpdateOrderByLatest(
                    bookingId, CANCELLABLE_PAYMENT_STATUSES)
            : paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
                bookingId, CANCELLABLE_PAYMENT_STATUSES);
    if (!preferredPayments.isEmpty()) {
      return preferredPayments.getFirst();
    }

    List<PaymentEntity> fallbackPayments =
        forUpdate
            ? paymentRepository.findByBookingIdAndPaymentStatusInForUpdateOrderByLatest(
                bookingId, CANCELLABLE_PAYMENT_STATUSES)
            : paymentRepository.findByBookingIdAndPaymentStatusInOrderByLatest(
                bookingId, CANCELLABLE_PAYMENT_STATUSES);
    if (fallbackPayments.isEmpty()) {
      throw new AppException(
          "PAYMENT_NOT_REFUNDABLE", "Booking does not have a refundable payment", 409);
    }
    return fallbackPayments.getFirst();
  }

  private BookingEntity requireBookingForUser(Long bookingId, Long userId, boolean forUpdate) {
    return (forUpdate
            ? bookingRepository.findByIdAndUserIdForUpdate(bookingId, userId)
            : bookingRepository.findByIdAndUserId(bookingId, userId))
        .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404));
  }

  private BookingEntity requirePreviewBooking(Long bookingId, Long userId) {
    if (userId != null) {
      return requireBookingForUser(bookingId, userId, false);
    }
    return bookingRepository
        .findById(bookingId)
        .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404));
  }

  private void validateBookingCancellable(BookingEntity booking) {
    String status = normalize(booking.getStatus());
    if (BOOKING_STATUS_CHECKED_IN_LOCKED.equals(status)) {
      throw new AppException(
          "BOOKING_NOT_CANCELLABLE", "Checked-in bookings cannot be cancelled", 409);
    }
    if (!BOOKING_STATUS_CONFIRMED.equals(status)) {
      throw new AppException(
          "BOOKING_NOT_CANCELLABLE", "Booking is not eligible for cancellation", 409);
    }
  }

  private List<Long> normalizeTicketIds(Collection<Long> ticketIds) {
    if (ticketIds == null || ticketIds.isEmpty()) {
      throw new AppException("VALIDATION_FAILED", "ticketIds is required", 400);
    }
    List<Long> normalized = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    for (Long ticketId : ticketIds) {
      if (ticketId == null || ticketId <= 0) {
        throw new AppException("VALIDATION_FAILED", "ticketIds must be positive", 400);
      }
      if (!seen.add(ticketId)) {
        throw new AppException("VALIDATION_FAILED", "ticketIds must be unique", 400);
      }
      normalized.add(ticketId);
    }
    normalized.sort(Long::compareTo);
    return normalized;
  }

  private void requireUser(Long userId) {
    if (userId == null) {
      throw new AppException("AUTH_REQUIRED", "Authentication is required", 401);
    }
  }

  private String normalizeReason(String reason) {
    if (!StringUtils.hasText(reason)) {
      return null;
    }
    String normalized = reason.trim();
    return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
  }

  private String requestHash(
      List<Long> ticketIds,
      String reason,
      BigDecimal expectedNetRefund,
      BigDecimal expectedRefundFee) {
    String payload =
        ticketIds.stream().map(String::valueOf).collect(Collectors.joining(","))
            + "|"
            + (reason == null ? "" : reason)
            + "|"
            + normalizeMoney(expectedNetRefund)
            + "|"
            + normalizeMoney(expectedRefundFee);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new AppException("INTERNAL_SERVER_ERROR", "Unable to hash request", 500);
    }
  }

  private BookingCancellationResultView toResultView(
      BookingCancellationEntity cancellation, BookingEntity booking, PaymentEntity payment) {
    BookingCancellationRefundEstimate refundEstimate =
        BookingCancellationRefundEstimate.forProvider(cancellation.getProvider());
    List<BookingCancellationResultView.ItemView> items =
        cancellation.getItems().stream()
            .sorted(Comparator.comparing(BookingCancellationItemEntity::getId))
            .map(
                item ->
                    new BookingCancellationResultView.ItemView(
                        item.getTicket().getId(),
                        item.getPassenger().getId(),
                        item.getPassenger().getPassengerType(),
                        (item.getPassenger().getFirstName()
                                + " "
                                + item.getPassenger().getLastName())
                            .trim(),
                        item.getTicket().getStatus(),
                        item.getFareGrossAmount(),
                        item.getPromoAllocatedAmount(),
                        item.getFareNetAmount(),
                        item.getSeatRefundAmount(),
                        item.getServiceRefundAmount(),
                        item.getRefundFeeAmount(),
                        item.getNetRefundAmount()))
            .toList();
    return new BookingCancellationResultView(
        booking.getId(),
        booking.getPnrCode(),
        booking.getContactEmail(),
        cancellation.getId(),
        buildCancellationCode(cancellation.getId()),
        resolveCancelledAt(cancellation),
        cancellation.getRefundStatus(),
        cancellation.getProvider(),
        cancellation.getProviderRefundId(),
        booking.getStatus(),
        payment.getPaymentStatus(),
        cancellation.getCurrency(),
        cancellation.getErrorCode(),
        cancellation.getErrorMessage(),
        refundEstimate.title(),
        refundEstimate.message(),
        refundEstimate.note(),
        buildRefundMethod(payment, cancellation.getProvider()),
        items,
        new BookingCancellationResultView.SummaryView(
            cancellation.getFareGrossTotal(),
            cancellation.getPromoAllocatedTotal(),
            cancellation.getFareNetTotal(),
            cancellation.getSeatRefundTotal(),
            cancellation.getServiceRefundTotal(),
            cancellation.getRefundFeeTotal(),
            cancellation.getNetRefundTotal()));
  }

  private static String buildCancellationCode(Long cancellationId) {
    if (cancellationId == null) {
      return null;
    }
    return String.format(Locale.ROOT, "CAN%09d", cancellationId);
  }

  private static OffsetDateTime resolveCancelledAt(BookingCancellationEntity cancellation) {
    OffsetDateTime processed = cancellation.getProcessedAt();
    return processed != null ? processed : cancellation.getUpdatedAt();
  }

  private static BookingCancellationResultView.RefundMethodView buildRefundMethod(
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
    return new BookingCancellationResultView.RefundMethodView(
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

  private void registerSuccessNotification(Long cancellationId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      sendSuccessNotificationSafely(cancellationId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            sendSuccessNotificationSafely(cancellationId);
          }
        });
  }

  private void sendSuccessNotificationSafely(Long cancellationId) {
    try {
      bookingCancellationNotifier.sendCancellationSucceeded(cancellationId);
    } catch (RuntimeException ignored) {
      // Notification failures must not affect a successful cancellation flow.
    }
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

  private record PreparedCancellation(
      BookingEntity booking,
      PaymentEntity payment,
      List<TicketEntity> tickets,
      BookingCancellationPreviewView preview) {}
}
