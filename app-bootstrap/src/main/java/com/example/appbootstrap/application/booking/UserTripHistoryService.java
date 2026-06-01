package com.example.appbootstrap.application.booking;

import com.example.appbootstrap.application.booking.UserTripHistoryPaginationService.HasRankKey;
import com.example.appbootstrap.application.booking.UserTripHistoryPaginationService.PageResult;
import com.example.appbootstrap.application.booking.UserTripHistoryPaginationService.SortDirection;
import com.example.appbootstrap.application.booking.UserTripHistoryPaginationService.UserTripHistoryRankKey;
import com.example.modulebooking.repository.TicketRepository;
import com.example.modulebooking.repository.UserTripHistoryRepository;
import com.example.modulebooking.repository.UserTripHistoryRow;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserTripHistoryService {

  private static final Set<String> CANCELLED_BOOKING_STATUSES = Set.of("CANCELLED", "EXPIRED");
  private static final Set<String> MANAGEABLE_BOOKING_STATUSES =
      Set.of("PENDING_PAYMENT", "CONFIRMED");

  private final UserTripHistoryRepository userTripHistoryRepository;
  private final TicketRepository ticketRepository;
  private final PaymentRepository paymentRepository;
  private final UserTripHistoryPaginationService paginationService;

  public UserTripHistoryService(
      UserTripHistoryRepository userTripHistoryRepository,
      TicketRepository ticketRepository,
      PaymentRepository paymentRepository,
      UserTripHistoryPaginationService paginationService) {
    this.userTripHistoryRepository = userTripHistoryRepository;
    this.ticketRepository = ticketRepository;
    this.paymentRepository = paymentRepository;
    this.paginationService = paginationService;
  }

  @Transactional(readOnly = true)
  public UserTripHistoryResult getTrips(Long userId, UserTripHistoryQuery query) {
    List<UserTripHistoryRow> rows =
        userTripHistoryRepository.findTripHistoryRows(
            userId, normalizeQuery(query.query()), query.fromDate(), query.toDate());

    Map<Long, PaymentEntity> latestPaymentByBookingId = loadLatestPaymentByBookingId(rows);
    Map<String, TicketSummary> ticketSummaryBySegment = loadTicketSummaryBySegment(rows);

    List<TripHistoryItemView> matchingItems =
        rows.stream()
            .map(
                row ->
                    toItemView(
                        row,
                        query.status(),
                        latestPaymentByBookingId.get(row.bookingId()),
                        ticketSummaryBySegment.get(segmentKey(row.bookingId(), row.flightId())),
                        query.referenceTime()))
            .filter(item -> item.tripStatus() == query.status())
            .toList();

    PageResult<TripHistoryItemView> page =
        paginationService.paginate(matchingItems, query.limit(), query.cursor());
    return new UserTripHistoryResult(
        page.items(), new PageInfo(page.nextCursor(), page.hasMore(), page.limit()));
  }

  private TripHistoryItemView toItemView(
      UserTripHistoryRow row,
      TripFilterStatus filterStatus,
      PaymentEntity latestPayment,
      TicketSummary ticketSummary,
      OffsetDateTime referenceTime) {
    TripFilterStatus tripStatus = classifyTripStatus(row, ticketSummary, referenceTime);
    String paymentStatus = resolvePaymentStatus(row.bookingStatus(), latestPayment);
    return new TripHistoryItemView(
        row.bookingId(),
        row.flightId(),
        row.pnrCode(),
        tripStatus,
        tripStatusLabel(tripStatus),
        paymentStatus,
        paymentStatusLabel(paymentStatus),
        row.flightNumber(),
        row.cabinClass(),
        row.departureAirportCode(),
        row.departureCity(),
        row.arrivalAirportCode(),
        row.arrivalCity(),
        row.departureTime(),
        row.arrivalTime(),
        row.flightDate(),
        row.passengerCount() == null ? 0 : row.passengerCount().intValue(),
        row.totalAmount(),
        row.currency(),
        new TripActionsView(
            MANAGEABLE_BOOKING_STATUSES.contains(normalize(row.bookingStatus())),
            "CONFIRMED".equals(normalize(row.bookingStatus()))
                && tripStatus == TripFilterStatus.UPCOMING,
            true),
        rankKeyFor(filterStatus, row));
  }

  private UserTripHistoryRankKey rankKeyFor(TripFilterStatus status, UserTripHistoryRow row) {
    SortDirection direction =
        status == TripFilterStatus.UPCOMING ? SortDirection.ASC : SortDirection.DESC;
    return new UserTripHistoryRankKey(
        status, direction, row.departureTime(), row.bookingId(), row.flightId());
  }

  private TripFilterStatus classifyTripStatus(
      UserTripHistoryRow row, TicketSummary ticketSummary, OffsetDateTime referenceTime) {
    if (CANCELLED_BOOKING_STATUSES.contains(normalize(row.bookingStatus()))
        || "RELEASED".equals(normalize(row.holdStatus()))
        || (ticketSummary != null
            && ticketSummary.totalCount() > 0
            && ticketSummary.activeCount() == 0)) {
      return TripFilterStatus.CANCELLED;
    }
    if (row.departureTime() != null && !row.departureTime().isBefore(referenceTime)) {
      return TripFilterStatus.UPCOMING;
    }
    return TripFilterStatus.COMPLETED;
  }

  private Map<String, TicketSummary> loadTicketSummaryBySegment(List<UserTripHistoryRow> rows) {
    List<Long> bookingIds = rows.stream().map(UserTripHistoryRow::bookingId).distinct().toList();
    if (bookingIds.isEmpty()) {
      return Map.of();
    }
    Map<String, MutableTicketSummary> mutable = new LinkedHashMap<>();
    ticketRepository
        .findByBookingIdInWithBookingAndFlight(bookingIds)
        .forEach(
            ticket -> {
              String key = segmentKey(ticket.getBooking().getId(), ticket.getFlight().getId());
              MutableTicketSummary summary =
                  mutable.computeIfAbsent(key, ignored -> new MutableTicketSummary());
              summary.totalCount++;
              if (!"CANCELLED".equalsIgnoreCase(ticket.getStatus())) {
                summary.activeCount++;
              }
            });
    return mutable.entrySet().stream()
        .collect(
            LinkedHashMap::new,
            (map, entry) ->
                map.put(
                    entry.getKey(),
                    new TicketSummary(entry.getValue().activeCount, entry.getValue().totalCount)),
            LinkedHashMap::putAll);
  }

  private Map<Long, PaymentEntity> loadLatestPaymentByBookingId(List<UserTripHistoryRow> rows) {
    List<Long> bookingIds = rows.stream().map(UserTripHistoryRow::bookingId).distinct().toList();
    if (bookingIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, PaymentEntity> latest = new LinkedHashMap<>();
    for (PaymentEntity payment :
        paymentRepository.findBookingLevelPaymentsByBookingIdInOrderByLatest(bookingIds)) {
      latest.putIfAbsent(payment.getBooking().getId(), payment);
    }
    return latest;
  }

  private String resolvePaymentStatus(String bookingStatus, PaymentEntity latestPayment) {
    if (latestPayment != null && StringUtils.hasText(latestPayment.getPaymentStatus())) {
      return latestPayment.getPaymentStatus();
    }
    return switch (normalize(bookingStatus)) {
      case "CONFIRMED" -> "SUCCESS";
      case "PENDING_PAYMENT" -> "PENDING";
      case "EXPIRED" -> "EXPIRED";
      case "CANCELLED" -> "CANCELLED";
      default -> "UNKNOWN";
    };
  }

  private String tripStatusLabel(TripFilterStatus status) {
    return switch (status) {
      case UPCOMING -> "Upcoming";
      case COMPLETED -> "Completed";
      case CANCELLED -> "Cancelled";
    };
  }

  private String paymentStatusLabel(String status) {
    return switch (normalize(status)) {
      case "SUCCESS" -> "Paid";
      case "PENDING" -> "Pending payment";
      case "FAILED" -> "Payment failed";
      case "CANCELLED" -> "Payment cancelled";
      case "EXPIRED" -> "Payment expired";
      case "REFUNDED" -> "Refunded";
      case "PARTIALLY_REFUNDED" -> "Partially refunded";
      case "UNKNOWN" -> "Unknown";
      default -> status;
    };
  }

  private String normalizeQuery(String value) {
    return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String segmentKey(Long bookingId, Long flightId) {
    return bookingId + ":" + flightId;
  }

  public enum TripFilterStatus {
    UPCOMING,
    COMPLETED,
    CANCELLED
  }

  public record UserTripHistoryQuery(
      TripFilterStatus status,
      String query,
      LocalDate fromDate,
      LocalDate toDate,
      String cursor,
      int limit,
      OffsetDateTime referenceTime) {}

  public record UserTripHistoryResult(List<TripHistoryItemView> items, PageInfo pageInfo) {}

  public record PageInfo(String nextCursor, boolean hasMore, int limit) {}

  public record TripActionsView(
      boolean canManageBooking, boolean canCheckIn, boolean canViewDetail) {}

  public record TripHistoryItemView(
      Long bookingId,
      Long flightId,
      String pnrCode,
      TripFilterStatus tripStatus,
      String tripStatusLabel,
      String paymentStatus,
      String paymentStatusLabel,
      String flightNumber,
      String cabinClass,
      String departureAirportCode,
      String departureCity,
      String arrivalAirportCode,
      String arrivalCity,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      LocalDate flightDate,
      int passengerCount,
      BigDecimal totalAmount,
      String currency,
      TripActionsView actions,
      UserTripHistoryRankKey rankKey)
      implements HasRankKey {}

  private record TicketSummary(int activeCount, int totalCount) {}

  private static final class MutableTicketSummary {
    private int activeCount;
    private int totalCount;
  }
}
