package com.example.moduleweb.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.TicketRepository;
import com.example.modulebooking.repository.UserTripHistoryRepository;
import com.example.modulebooking.repository.UserTripHistoryRow;
import com.example.moduleflight.entity.FlightEntity;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTripHistoryServiceTest {

  @Mock private UserTripHistoryRepository userTripHistoryRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private PaymentRepository paymentRepository;

  private UserTripHistoryService service;

  @BeforeEach
  void setUp() {
    service =
        new UserTripHistoryService(
            userTripHistoryRepository,
            ticketRepository,
            paymentRepository,
            new UserTripHistoryPaginationService());
  }

  @Test
  void getTripsShouldReturnUpcomingItemsSortedAscending() {
    UserTripHistoryRow later =
        row(
            10L,
            200L,
            "ABC123",
            "CONFIRMED",
            "CONFIRMED",
            "VN200",
            OffsetDateTime.parse("2026-06-10T10:00:00+07:00"));
    UserTripHistoryRow earlier =
        row(
            10L,
            100L,
            "ABC123",
            "CONFIRMED",
            "CONFIRMED",
            "VN100",
            OffsetDateTime.parse("2026-06-02T08:00:00+07:00"));
    when(userTripHistoryRepository.findTripHistoryRows(7L, null, null, null))
        .thenReturn(List.of(later, earlier));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdInOrderByLatest(List.of(10L)))
        .thenReturn(List.of(payment(10L, "SUCCESS")));
    when(ticketRepository.findByBookingIdInWithBookingAndFlight(List.of(10L)))
        .thenReturn(List.of(ticket(10L, 100L, "ISSUED"), ticket(10L, 200L, "ISSUED")));

    UserTripHistoryService.UserTripHistoryResult result =
        service.getTrips(
            7L,
            new UserTripHistoryService.UserTripHistoryQuery(
                UserTripHistoryService.TripFilterStatus.UPCOMING,
                null,
                null,
                null,
                null,
                20,
                OffsetDateTime.parse("2026-06-01T00:00:00+07:00")));

    assertEquals(2, result.items().size());
    assertEquals(100L, result.items().getFirst().flightId());
    assertEquals(200L, result.items().getLast().flightId());
  }

  @Test
  void getTripsShouldReturnCompletedItemsSortedDescending() {
    UserTripHistoryRow earlier =
        row(
            10L,
            100L,
            "ABC123",
            "CONFIRMED",
            "CONFIRMED",
            "VN100",
            OffsetDateTime.parse("2026-05-02T08:00:00+07:00"));
    UserTripHistoryRow later =
        row(
            10L,
            200L,
            "ABC123",
            "CONFIRMED",
            "CONFIRMED",
            "VN200",
            OffsetDateTime.parse("2026-05-10T10:00:00+07:00"));
    when(userTripHistoryRepository.findTripHistoryRows(7L, null, null, null))
        .thenReturn(List.of(earlier, later));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdInOrderByLatest(List.of(10L)))
        .thenReturn(List.of(payment(10L, "SUCCESS")));
    when(ticketRepository.findByBookingIdInWithBookingAndFlight(List.of(10L)))
        .thenReturn(List.of(ticket(10L, 100L, "ISSUED"), ticket(10L, 200L, "ISSUED")));

    UserTripHistoryService.UserTripHistoryResult result =
        service.getTrips(
            7L,
            new UserTripHistoryService.UserTripHistoryQuery(
                UserTripHistoryService.TripFilterStatus.COMPLETED,
                null,
                null,
                null,
                null,
                20,
                OffsetDateTime.parse("2026-06-01T00:00:00+07:00")));

    assertEquals(2, result.items().size());
    assertEquals(200L, result.items().getFirst().flightId());
    assertEquals(100L, result.items().getLast().flightId());
  }

  @Test
  void getTripsShouldClassifyCancelledFromReleasedHoldAndCancelledTickets() {
    UserTripHistoryRow released =
        row(
            11L,
            300L,
            "DEF456",
            "CONFIRMED",
            "RELEASED",
            "VN300",
            OffsetDateTime.parse("2026-06-15T09:00:00+07:00"));
    when(userTripHistoryRepository.findTripHistoryRows(7L, null, null, null))
        .thenReturn(List.of(released));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdInOrderByLatest(List.of(11L)))
        .thenReturn(List.of(payment(11L, "PARTIALLY_REFUNDED")));
    when(ticketRepository.findByBookingIdInWithBookingAndFlight(List.of(11L)))
        .thenReturn(List.of(ticket(11L, 300L, "CANCELLED")));

    UserTripHistoryService.UserTripHistoryResult result =
        service.getTrips(
            7L,
            new UserTripHistoryService.UserTripHistoryQuery(
                UserTripHistoryService.TripFilterStatus.CANCELLED,
                null,
                null,
                null,
                null,
                20,
                OffsetDateTime.parse("2026-06-01T00:00:00+07:00")));

    assertEquals(1, result.items().size());
    assertEquals("Cancelled", result.items().getFirst().tripStatusLabel());
    assertEquals("Partially refunded", result.items().getFirst().paymentStatusLabel());
  }

  @Test
  void getTripsShouldPaginateWithCursor() {
    UserTripHistoryRow first =
        row(
            10L,
            100L,
            "ABC123",
            "CONFIRMED",
            "CONFIRMED",
            "VN100",
            OffsetDateTime.parse("2026-06-02T08:00:00+07:00"));
    UserTripHistoryRow second =
        row(
            10L,
            200L,
            "ABC123",
            "CONFIRMED",
            "CONFIRMED",
            "VN200",
            OffsetDateTime.parse("2026-06-03T08:00:00+07:00"));
    UserTripHistoryRow third =
        row(
            10L,
            300L,
            "ABC123",
            "CONFIRMED",
            "CONFIRMED",
            "VN300",
            OffsetDateTime.parse("2026-06-04T08:00:00+07:00"));
    when(userTripHistoryRepository.findTripHistoryRows(7L, null, null, null))
        .thenReturn(List.of(third, first, second));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdInOrderByLatest(List.of(10L)))
        .thenReturn(List.of(payment(10L, "SUCCESS")));
    when(ticketRepository.findByBookingIdInWithBookingAndFlight(List.of(10L)))
        .thenReturn(
            List.of(
                ticket(10L, 100L, "ISSUED"),
                ticket(10L, 200L, "ISSUED"),
                ticket(10L, 300L, "ISSUED")));

    UserTripHistoryService.UserTripHistoryResult firstPage =
        service.getTrips(
            7L,
            new UserTripHistoryService.UserTripHistoryQuery(
                UserTripHistoryService.TripFilterStatus.UPCOMING,
                null,
                null,
                null,
                null,
                2,
                OffsetDateTime.parse("2026-06-01T00:00:00+07:00")));

    UserTripHistoryService.UserTripHistoryResult secondPage =
        service.getTrips(
            7L,
            new UserTripHistoryService.UserTripHistoryQuery(
                UserTripHistoryService.TripFilterStatus.UPCOMING,
                null,
                null,
                null,
                firstPage.pageInfo().nextCursor(),
                2,
                OffsetDateTime.parse("2026-06-01T00:00:00+07:00")));

    assertEquals(2, firstPage.items().size());
    assertEquals(true, firstPage.pageInfo().hasMore());
    assertEquals(1, secondPage.items().size());
    assertEquals(300L, secondPage.items().getFirst().flightId());
  }

  private static UserTripHistoryRow row(
      Long bookingId,
      Long flightId,
      String pnrCode,
      String bookingStatus,
      String holdStatus,
      String flightNumber,
      OffsetDateTime departureTime) {
    return new UserTripHistoryRow(
        bookingId,
        flightId,
        pnrCode,
        bookingStatus,
        holdStatus,
        flightNumber,
        "ECONOMY",
        "SGN",
        "Ho Chi Minh City",
        "HAN",
        "Ha Noi",
        departureTime,
        departureTime.plusHours(2),
        LocalDate.from(departureTime),
        2L,
        new BigDecimal("2300000.00"),
        "VND");
  }

  private static PaymentEntity payment(Long bookingId, String status) {
    BookingEntity booking = new BookingEntity();
    booking.setId(bookingId);

    PaymentEntity payment = new PaymentEntity();
    payment.setBooking(booking);
    payment.setPaymentStatus(status);
    payment.setUpdatedAt(OffsetDateTime.parse("2026-06-01T00:00:00+07:00"));
    return payment;
  }

  private static TicketEntity ticket(Long bookingId, Long flightId, String status) {
    BookingEntity booking = new BookingEntity();
    booking.setId(bookingId);
    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);

    TicketEntity ticket = new TicketEntity();
    ticket.setBooking(booking);
    ticket.setFlight(flight);
    ticket.setStatus(status);
    return ticket;
  }
}
