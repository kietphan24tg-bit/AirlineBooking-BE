package com.example.appbootstrap.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTripDetailServiceTest {

  private static final OffsetDateTime SEGMENT_DEPARTURE =
      OffsetDateTime.now()
          .plusDays(7)
          .withHour(8)
          .withMinute(30)
          .withSecond(0)
          .withNano(0)
          .truncatedTo(ChronoUnit.SECONDS);

  private static final OffsetDateTime SEGMENT_ARRIVAL =
      SEGMENT_DEPARTURE.plusHours(1).plusMinutes(35);

  @Mock private BookingRepository bookingRepository;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private PassengerRepository passengerRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private PaymentRepository paymentRepository;

  private UserTripDetailService service;

  @BeforeEach
  void setUp() {
    service =
        new UserTripDetailService(
            bookingRepository,
            bookingFareHoldRepository,
            passengerRepository,
            passengerSeatAssignmentRepository,
            ticketRepository,
            paymentRepository);
  }

  @Test
  void getTripDetailShouldReturnSegmentPassengersAndPayment() {
    BookingEntity booking = booking();
    BookingFareHoldEntity hold = hold(booking);
    PassengerEntity passenger = passenger(booking);
    PassengerSeatAssignmentEntity seatAssignment =
        seatAssignment(booking, passenger, hold.getFlightFare().getFlight());
    TicketEntity ticket =
        ticket(booking, passenger, hold.getFlightFare().getFlight(), hold.getFlightFare());
    PaymentEntity payment = payment(booking);

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(bookingFareHoldRepository.findByBookingIdAndFlightIdAndStatusIn(
            10L, 100L, List.of("HELD", "CONFIRMED", "RELEASED")))
        .thenReturn(Optional.of(hold));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(passengerSeatAssignmentRepository.findByBookingIdAndFlightIdAndStatusIn(
            10L, 100L, List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of(seatAssignment));
    when(ticketRepository.findByBookingIdAndFlightIdWithDetails(10L, 100L))
        .thenReturn(List.of(ticket));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdOrderByLatest(10L))
        .thenReturn(List.of(payment));

    UserTripDetailService.TripDetailView result = service.getTripDetail(7L, 10L, 100L);

    assertEquals("ABC123", result.booking().pnrCode());
    assertEquals("UPCOMING", result.booking().tripStatus());
    assertEquals("VN123", result.segment().flightNumber());
    assertEquals(20, result.segment().checkedBaggageKg());
    assertEquals(1, result.passengers().size());
    assertEquals("Nguyen Van A", result.passengers().getFirst().fullName());
    assertEquals("7381234567890", result.passengers().getFirst().ticket().ticketNumber());
    assertEquals("12A", result.passengers().getFirst().seat().seatNumber());
    assertEquals("Paid", result.payment().paymentStatusLabel());
  }

  private static BookingEntity booking() {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setPnrCode("ABC123");
    booking.setStatus("CONFIRMED");
    booking.setCurrency("VND");
    booking.setTotalAmount(new BigDecimal("2300000.00"));
    booking.setCreatedAt(OffsetDateTime.parse("2026-05-30T10:00:00+07:00"));
    return booking;
  }

  private static BookingFareHoldEntity hold(BookingEntity booking) {
    AirportEntity departure = new AirportEntity();
    departure.setCode("SGN");
    departure.setName("Tan Son Nhat");
    departure.setCity("Ho Chi Minh City");
    AirportEntity arrival = new AirportEntity();
    arrival.setCode("HAN");
    arrival.setName("Noi Bai");
    arrival.setCity("Ha Noi");

    FlightEntity flight = new FlightEntity();
    flight.setId(100L);
    flight.setFlightNumber("VN123");
    flight.setFlightDate(SEGMENT_DEPARTURE.toLocalDate());
    flight.setDepartureAirport(departure);
    flight.setArrivalAirport(arrival);
    flight.setDepartureTime(SEGMENT_DEPARTURE);
    flight.setArrivalTime(SEGMENT_ARRIVAL);
    flight.setDepartureTerminal("T1");
    flight.setArrivalTerminal("T1");

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setCode("ECO");
    fareClass.setName("Economy");
    fareClass.setCabinClass("ECONOMY");
    fareClass.setBaggageCheckinKg(20);
    fareClass.setBaggageHandKg(7);

    FlightFareEntity fare = new FlightFareEntity();
    fare.setId(1000L);
    fare.setFlight(flight);
    fare.setFareClass(fareClass);

    BookingFareHoldEntity hold = new BookingFareHoldEntity();
    hold.setBooking(booking);
    hold.setFlightFare(fare);
    hold.setStatus("CONFIRMED");
    return hold;
  }

  private static PassengerEntity passenger(BookingEntity booking) {
    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(1L);
    passenger.setBooking(booking);
    passenger.setFirstName("Van A");
    passenger.setLastName("Nguyen");
    passenger.setPassengerType("ADULT");
    return passenger;
  }

  private static PassengerSeatAssignmentEntity seatAssignment(
      BookingEntity booking, PassengerEntity passenger, FlightEntity flight) {
    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setId(500L);
    seat.setSeatNumber("12A");
    seat.setFlight(flight);

    PassengerSeatAssignmentEntity assignment = new PassengerSeatAssignmentEntity();
    assignment.setBooking(booking);
    assignment.setPassenger(passenger);
    assignment.setFlightSeat(seat);
    assignment.setStatus("BOOKED");
    assignment.setSurchargeAmount(new BigDecimal("0.00"));
    assignment.setCurrency("VND");
    return assignment;
  }

  private static TicketEntity ticket(
      BookingEntity booking,
      PassengerEntity passenger,
      FlightEntity flight,
      FlightFareEntity fare) {
    TicketEntity ticket = new TicketEntity();
    ticket.setId(1000L);
    ticket.setBooking(booking);
    ticket.setPassenger(passenger);
    ticket.setFlight(flight);
    ticket.setFlightFare(fare);
    ticket.setFareClass(fare.getFareClass());
    ticket.setTicketNumber("7381234567890");
    ticket.setStatus("ISSUED");
    ticket.setIssuedAt(OffsetDateTime.parse("2026-05-30T11:00:00+07:00"));
    return ticket;
  }

  private static PaymentEntity payment(BookingEntity booking) {
    PaymentEntity payment = new PaymentEntity();
    payment.setBooking(booking);
    payment.setPaymentStatus("SUCCESS");
    payment.setPaymentMethod("STRIPE_CARD");
    payment.setTransactionId("txn_123");
    payment.setAmount(new BigDecimal("2300000.00"));
    payment.setCurrency("VND");
    payment.setUpdatedAt(OffsetDateTime.parse("2026-05-30T11:30:00+07:00"));
    return payment;
  }
}
