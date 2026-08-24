package com.example.moduleweb.application.booking;

import com.example.modulebooking.application.BookingPaymentDetailsPort;
import com.example.modulebooking.repository.TicketRepository;
import com.example.modulebooking.repository.UserTripHistoryRepository;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("webUserTripHistoryService")
public class UserTripHistoryService {

  private final com.example.modulebooking.application.UserTripHistoryService delegate;

  @Autowired
  public UserTripHistoryService(
      com.example.modulebooking.application.UserTripHistoryService delegate) {
    this.delegate = delegate;
  }

  public UserTripHistoryService(
      UserTripHistoryRepository userTripHistoryRepository,
      TicketRepository ticketRepository,
      PaymentRepository paymentRepository,
      UserTripHistoryPaginationService ignoredPaginationService) {
    this(
        new com.example.modulebooking.application.UserTripHistoryService(
            userTripHistoryRepository,
            ticketRepository,
            new PaymentDetailsAdapter(paymentRepository),
            new com.example.modulebooking.application.UserTripHistoryPaginationService()));
  }

  public UserTripHistoryResult getTrips(Long userId, UserTripHistoryQuery query) {
    com.example.modulebooking.application.UserTripHistoryService.UserTripHistoryResult result =
        delegate.getTrips(
            userId,
            new com.example.modulebooking.application.UserTripHistoryService.UserTripHistoryQuery(
                com.example.modulebooking.application.UserTripHistoryService.TripFilterStatus
                    .valueOf(query.status().name()),
                query.query(),
                query.fromDate(),
                query.toDate(),
                query.cursor(),
                query.limit(),
                query.referenceTime()));
    return new UserTripHistoryResult(
        result.items().stream().map(this::toItemView).toList(),
        new PageInfo(
            result.pageInfo().nextCursor(),
            result.pageInfo().hasMore(),
            result.pageInfo().limit()));
  }

  public TripStatusCounts getTripStatusCounts(Long userId, OffsetDateTime referenceTime) {
    com.example.modulebooking.application.UserTripHistoryService.TripStatusCounts counts =
        delegate.getTripStatusCounts(userId, referenceTime);
    return new TripStatusCounts(counts.upcoming(), counts.completed(), counts.cancelled());
  }

  private TripHistoryItemView toItemView(
      com.example.modulebooking.application.UserTripHistoryService.TripHistoryItemView item) {
    return new TripHistoryItemView(
        item.bookingId(),
        item.flightId(),
        item.pnrCode(),
        TripFilterStatus.valueOf(item.tripStatus().name()),
        item.tripStatusLabel(),
        item.paymentStatus(),
        item.paymentStatusLabel(),
        item.flightNumber(),
        item.cabinClass(),
        item.departureAirportCode(),
        item.departureCity(),
        item.arrivalAirportCode(),
        item.arrivalCity(),
        item.departureTime(),
        item.arrivalTime(),
        item.flightDate(),
        item.passengerCount(),
        item.totalAmount(),
        item.currency(),
        new TripActionsView(
            item.actions().canManageBooking(),
            item.actions().canCheckIn(),
            item.actions().canViewDetail()),
        new UserTripHistoryPaginationService.UserTripHistoryRankKey(
            TripFilterStatus.valueOf(item.rankKey().status().name()),
            UserTripHistoryPaginationService.SortDirection.valueOf(
                item.rankKey().direction().name()),
            item.rankKey().departureTime(),
            item.rankKey().bookingId(),
            item.rankKey().flightId()));
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

  public record TripStatusCounts(int upcoming, int completed, int cancelled) {}

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
      UserTripHistoryPaginationService.UserTripHistoryRankKey rankKey)
      implements UserTripHistoryPaginationService.HasRankKey {}

  private static class PaymentDetailsAdapter implements BookingPaymentDetailsPort {

    private final PaymentRepository paymentRepository;

    PaymentDetailsAdapter(PaymentRepository paymentRepository) {
      this.paymentRepository = paymentRepository;
    }

    @Override
    public BookingPaymentSummary findLatestBookingLevelPayment(Long bookingId) {
      return paymentRepository.findBookingLevelPaymentsByBookingIdOrderByLatest(bookingId).stream()
          .findFirst()
          .map(this::toSummary)
          .orElse(null);
    }

    @Override
    public Map<Long, BookingPaymentSummary> findLatestBookingLevelPayments(
        Collection<Long> bookingIds) {
      Map<Long, BookingPaymentSummary> summaries = new LinkedHashMap<>();
      for (PaymentEntity payment :
          paymentRepository.findBookingLevelPaymentsByBookingIdInOrderByLatest(bookingIds)) {
        Long bookingId = payment.getBooking().getId();
        summaries.putIfAbsent(bookingId, toSummary(payment));
      }
      return summaries;
    }

    private BookingPaymentSummary toSummary(PaymentEntity payment) {
      return new BookingPaymentSummary(
          payment.getPaymentStatus(),
          payment.getPaymentMethod(),
          payment.getTransactionId(),
          payment.getAmount(),
          payment.getCurrency());
    }
  }
}
