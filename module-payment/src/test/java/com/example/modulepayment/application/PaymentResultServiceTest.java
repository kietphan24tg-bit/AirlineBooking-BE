package com.example.modulepayment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentResultServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private PassengerRepository passengerRepository;
  @Mock private BookingContactRepository bookingContactRepository;
  @Mock private BookingServiceRepository bookingServiceRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  @Mock private PaymentWebhookService paymentWebhookService;

  private PaymentResultService service;

  @BeforeEach
  void setUp() {
    service =
        new PaymentResultService(
            paymentRepository,
            ticketRepository,
            bookingFareHoldRepository,
            passengerRepository,
            bookingContactRepository,
            bookingServiceRepository,
            passengerSeatAssignmentRepository,
            paymentWebhookService);
  }

  @Test
  void shouldReturnSummaryForPendingPayment() {
    BookingEntity booking = booking("PENDING_PAYMENT");
    PaymentEntity payment = payment(booking, "PENDING");
    PassengerEntity passenger = passenger(booking);
    BookingFareHoldEntity hold = hold(booking);
    PassengerSeatAssignmentEntity seatAssignment = seatAssignment(booking, passenger, 1001L);
    BookingContactEntity contact = contact(booking);

    when(paymentRepository.findByIdAndBookingUserId(5L, 7L)).thenReturn(Optional.of(payment));
    when(bookingFareHoldRepository.findByBookingIdOrderByIdAsc(10L)).thenReturn(List.of(hold));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(10L))
        .thenReturn(List.of(contact));
    when(bookingServiceRepository.findDetailedByBookingId(10L)).thenReturn(List.of());
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(
            10L, List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of(seatAssignment));

    PaymentResultSummaryView result = service.getResultSummaryForUser(5L, 7L);

    assertEquals("PENDING", result.paymentStatus());
    assertEquals("PENDING_PAYMENT", result.bookingStatus());
    assertEquals(1, result.segments().size());
    assertEquals(1, result.passengers().size());
    assertEquals(0, result.tickets().size());
    assertNotNull(result.fareBreakdown());
    assertEquals("ABC123", result.booking().pnrCode());
    assertEquals("11A", result.passengers().getFirst().segments().getFirst().seat().seatNumber());
    assertNull(result.passengers().getFirst().segments().getFirst().ticket());
  }

  @Test
  void shouldReturnErrorStatusWhenPendingPaymentWindowHasExpired() {
    BookingEntity booking = booking("PENDING_PAYMENT");
    booking.setPnrExpiresAt(OffsetDateTime.parse("2020-05-17T09:00:00Z"));
    PaymentEntity payment = payment(booking, "PENDING");

    when(paymentRepository.findByIdAndBookingUserIdForUpdate(5L, 7L))
        .thenReturn(Optional.of(payment));

    PaymentResultStatusView result = service.getResultStatusForUser(5L, 7L);

    assertEquals("ERROR", result.resultPage());
    assertEquals("Booking payment window has expired", result.failure().message());
    assertEquals(Boolean.FALSE, result.polling().shouldPoll());
  }

  @Test
  void shouldAttemptStripeReconciliationBeforeReturningPendingStatus() {
    BookingEntity booking = booking("PENDING_PAYMENT");
    PaymentEntity payment = payment(booking, "PENDING");
    payment.setPaymentMethod("STRIPE_CARD");
    payment.setTransactionId("cs_test_123");

    when(paymentRepository.findByIdAndBookingUserIdForUpdate(5L, 7L)).thenReturn(Optional.of(payment));

    service.getResultStatusForUser(5L, 7L);

    verify(paymentWebhookService).reconcilePendingStripePayment(5L);
  }

  private static BookingEntity booking(String status) {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setPnrCode("ABC123");
    booking.setStatus(status);
    booking.setCurrency("VND");
    booking.setTotalAmount(new BigDecimal("1200000.00"));
    booking.setDiscountAmount(BigDecimal.ZERO.setScale(2));
    booking.setContactEmail("user@example.com");
    booking.setContactPhone("0901234567");
    booking.setCreatedAt(OffsetDateTime.parse("2026-05-17T08:00:00Z"));
    booking.setPnrExpiresAt(OffsetDateTime.parse("2099-05-17T09:00:00Z"));
    return booking;
  }

  private static PaymentEntity payment(BookingEntity booking, String status) {
    PaymentEntity payment = new PaymentEntity();
    payment.setId(5L);
    payment.setBooking(booking);
    payment.setAmount(new BigDecimal("1200000.00"));
    payment.setCurrency("VND");
    payment.setPaymentMethod("PAY_LATER");
    payment.setPaymentStatus(status);
    payment.setTransactionId("txn_pending_5");
    payment.setCheckoutUrl("https://pay.example/5");
    payment.setSessionExpiresAt(booking.getPnrExpiresAt());
    payment.setCreatedAt(OffsetDateTime.parse("2026-05-17T08:00:00Z"));
    payment.setPaidAt(null);
    return payment;
  }

  private static PassengerEntity passenger(BookingEntity booking) {
    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(20L);
    passenger.setBooking(booking);
    passenger.setFirstName("Kiet");
    passenger.setLastName("Phan");
    passenger.setGender("MALE");
    passenger.setPassengerType("ADULT");
    passenger.setDateOfBirth(LocalDate.parse("1998-01-01"));
    return passenger;
  }

  private static BookingContactEntity contact(BookingEntity booking) {
    BookingContactEntity contact = new BookingContactEntity();
    contact.setId(30L);
    contact.setBooking(booking);
    contact.setEmail("user@example.com");
    contact.setPhone("0901234567");
    contact.setPrimary(true);
    return contact;
  }

  private static BookingFareHoldEntity hold(BookingEntity booking) {
    AirlineEntity airline = new AirlineEntity();
    airline.setName("The Sovereign Airways");

    AirportEntity departure = new AirportEntity();
    departure.setCode("SGN");
    departure.setName("Tan Son Nhat");
    departure.setCity("Ho Chi Minh City");

    AirportEntity arrival = new AirportEntity();
    arrival.setCode("HAN");
    arrival.setName("Noi Bai");
    arrival.setCity("Ha Noi");

    FlightEntity flight = new FlightEntity();
    flight.setId(1001L);
    flight.setAirline(airline);
    flight.setFlightNumber("VN123");
    flight.setFlightDate(LocalDate.parse("2026-06-16"));
    flight.setDepartureAirport(departure);
    flight.setArrivalAirport(arrival);
    flight.setDepartureTerminal("T1");
    flight.setArrivalTerminal("T2");
    flight.setDepartureTime(OffsetDateTime.parse("2026-06-16T05:00:00+07:00"));
    flight.setArrivalTime(OffsetDateTime.parse("2026-06-16T07:10:00+07:00"));

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setCode("ECO");
    fareClass.setName("Economy Smart");
    fareClass.setCabinClass("ECO");
    fareClass.setBaggageCheckinKg(20);
    fareClass.setBaggageHandKg(7);

    FlightFareEntity fare = new FlightFareEntity();
    fare.setFlight(flight);
    fare.setFareClass(fareClass);
    fare.setBasePrice(new BigDecimal("938000.00"));
    fare.setTotalTaxAmount(new BigDecimal("0.00"));

    BookingFareHoldEntity hold = new BookingFareHoldEntity();
    hold.setId(40L);
    hold.setBooking(booking);
    hold.setFlightFare(fare);
    hold.setQuantity(1);
    hold.setStatus("HELD");
    return hold;
  }

  private static PassengerSeatAssignmentEntity seatAssignment(
      BookingEntity booking, PassengerEntity passenger, Long flightId) {
    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);

    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setId(50L);
    seat.setSeatNumber("11A");
    seat.setFlight(flight);

    PassengerSeatAssignmentEntity assignment = new PassengerSeatAssignmentEntity();
    assignment.setId(60L);
    assignment.setBooking(booking);
    assignment.setPassenger(passenger);
    assignment.setFlightSeat(seat);
    assignment.setStatus("RESERVED");
    assignment.setSurchargeAmount(BigDecimal.ZERO.setScale(2));
    assignment.setCurrency("VND");
    return assignment;
  }
}
