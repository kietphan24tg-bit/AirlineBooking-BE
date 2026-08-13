package com.example.moduleweb.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleweb.config.BookingProperties;
import com.example.moduleweb.controller.booking.dto.FlightChangeQuoteRequest;
import com.example.moduleweb.controller.booking.dto.FlightChangeQuoteResponse;
import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.application.FlightInventoryAvailabilityService;
import com.example.moduleflight.application.FlightPricingService;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.repository.FlightFareRepository;
import com.example.moduleidentity.entity.UserEntity;
import com.example.modulepayment.application.TicketFinancialSnapshotService;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.entity.TicketFinancialSnapshotEntity;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.modulepayment.repository.TicketFinancialSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class FlightChangeQuoteServiceTest {

  private static final OffsetDateTime CURRENT_FLIGHT_DEPARTURE =
      OffsetDateTime.now()
          .plusDays(7)
          .withHour(8)
          .withMinute(30)
          .withSecond(0)
          .withNano(0)
          .truncatedTo(ChronoUnit.SECONDS);

  private static final OffsetDateTime CURRENT_FLIGHT_ARRIVAL =
      CURRENT_FLIGHT_DEPARTURE.plusHours(2).plusMinutes(5);

  private static final OffsetDateTime NEW_FLIGHT_DEPARTURE = CURRENT_FLIGHT_DEPARTURE.minusHours(2);

  private static final OffsetDateTime NEW_FLIGHT_ARRIVAL =
      NEW_FLIGHT_DEPARTURE.plusHours(2).plusMinutes(5);

  @Mock private BookingRepository bookingRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  @Mock private BookingServiceRepository bookingServiceRepository;
  @Mock private FlightFareRepository flightFareRepository;
  @Mock private FlightInventoryAvailabilityService flightInventoryAvailabilityService;
  @Mock private FlightPricingService flightPricingService;
  @Mock private PaymentRepository paymentRepository;
  @Mock private TicketFinancialSnapshotService ticketFinancialSnapshotService;
  @Mock private TicketFinancialSnapshotRepository ticketFinancialSnapshotRepository;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private FlightChangeQuoteService service;

  @BeforeEach
  void setUp() {
    Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    service =
        new FlightChangeQuoteService(
            bookingRepository,
            ticketRepository,
            bookingFareHoldRepository,
            passengerSeatAssignmentRepository,
            bookingServiceRepository,
            flightFareRepository,
            flightInventoryAvailabilityService,
            flightPricingService,
            paymentRepository,
            ticketFinancialSnapshotService,
            ticketFinancialSnapshotRepository,
            redisTemplate,
            new ObjectMapper().findAndRegisterModules(),
            new BookingProperties(15, 24, 40, 60, 15, 180, 90));
  }

  @Test
  void quoteShouldReturnRefundWhenNewFlightIsCheaper() {
    BookingEntity booking = booking();
    TicketEntity adultTicket = ticket(booking, 101L, 501L, "ADULT", true, "ISSUED");
    FlightFareEntity newFare = newFare(888L, 321L, "920000.00", "920000.00");
    TicketFinancialSnapshotEntity snapshot = snapshot(booking, adultTicket, "1500000.00");

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
            eq(10L), any()))
        .thenReturn(List.of(payment(booking)));
    when(ticketRepository.findByBookingIdAndTicketIdInWithDetails(10L, List.of(101L)))
        .thenReturn(List.of(adultTicket));
    when(flightFareRepository.findByIdWithDetails(321L)).thenReturn(Optional.of(newFare));
    when(flightInventoryAvailabilityService.hasEnoughSeats(888L, 20L, 1)).thenReturn(true);
    when(flightPricingService.calculateTotalPrice(new BigDecimal("920000.00"), 1, 0, 0))
        .thenReturn(new BigDecimal("920000.00"));
    when(ticketFinancialSnapshotRepository.findByBookingIdWithDetails(10L))
        .thenReturn(List.of(snapshot));
    when(bookingFareHoldRepository.findByBookingIdOrderByIdAsc(10L))
        .thenReturn(List.of(hold(adultTicket.getFlightFare())));
    when(bookingServiceRepository.findDetailedByBookingId(10L)).thenReturn(List.of());

    FlightChangeQuoteResponse response =
        service.quote(null, 10L, new FlightChangeQuoteRequest(List.of(101L), 501L, 888L, 321L));

    assertEquals(true, response.eligible());
    assertEquals("REFUND", response.settlementType());
    assertEquals(new BigDecimal("1500000.00"), response.summary().originalFareAmount());
    assertEquals(new BigDecimal("920000.00"), response.summary().newFareAmount());
    assertEquals(new BigDecimal("-280000.00"), response.summary().netAmount());
    verify(ticketFinancialSnapshotService).ensureSnapshotsForBooking(booking);
    verify(valueOperations).set(any(), any(), any());
  }

  @Test
  void quoteShouldRejectFareThatDoesNotAllowChanges() {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking, 101L, 501L, "ADULT", false, "ISSUED");

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
            eq(10L), any()))
        .thenReturn(List.of(payment(booking)));
    when(ticketRepository.findByBookingIdAndTicketIdInWithDetails(10L, List.of(101L)))
        .thenReturn(List.of(ticket));

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.quote(
                    null, 10L, new FlightChangeQuoteRequest(List.of(101L), 501L, 888L, 321L)));

    assertEquals("FARE_NOT_CHANGEABLE", exception.getCode());
    verify(flightFareRepository, never()).findByIdWithDetails(any());
  }

  @Test
  void quoteShouldRejectWhenChangeWindowClosed() {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking, 101L, 501L, "ADULT", true, "ISSUED");
    ticket.getFlight().setDepartureTime(OffsetDateTime.now().plusMinutes(179));

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
            eq(10L), any()))
        .thenReturn(List.of(payment(booking)));
    when(ticketRepository.findByBookingIdAndTicketIdInWithDetails(10L, List.of(101L)))
        .thenReturn(List.of(ticket));

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.quote(
                    null, 10L, new FlightChangeQuoteRequest(List.of(101L), 501L, 888L, 321L)));

    assertEquals("CHANGE_WINDOW_CLOSED", exception.getCode());
    verify(flightFareRepository, never()).findByIdWithDetails(any());
  }

  @Test
  void quoteShouldRejectSameFlightAsNewFlight() {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking, 101L, 501L, "ADULT", true, "ISSUED");
    FlightFareEntity newFare = newFare(501L, 321L, "1500000.00", "1500000.00");

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
            eq(10L), any()))
        .thenReturn(List.of(payment(booking)));
    when(ticketRepository.findByBookingIdAndTicketIdInWithDetails(10L, List.of(101L)))
        .thenReturn(List.of(ticket));
    when(flightFareRepository.findByIdWithDetails(321L)).thenReturn(Optional.of(newFare));

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.quote(
                    null, 10L, new FlightChangeQuoteRequest(List.of(101L), 501L, 501L, 321L)));

    assertEquals("INVALID_FLIGHT_CHANGE_ROUTE", exception.getCode());
  }

  @Test
  void quoteShouldRejectNewFlightWithDifferentRoute() {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking, 101L, 501L, "ADULT", true, "ISSUED");
    FlightFareEntity newFare = newFare(888L, 321L, "1500000.00", "1500000.00");
    AirportEntity differentArrival = new AirportEntity();
    differentArrival.setId(3L);
    differentArrival.setCode("DAD");
    newFare.getFlight().setArrivalAirport(differentArrival);

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
            eq(10L), any()))
        .thenReturn(List.of(payment(booking)));
    when(ticketRepository.findByBookingIdAndTicketIdInWithDetails(10L, List.of(101L)))
        .thenReturn(List.of(ticket));
    when(flightFareRepository.findByIdWithDetails(321L)).thenReturn(Optional.of(newFare));

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.quote(
                    null, 10L, new FlightChangeQuoteRequest(List.of(101L), 501L, 888L, 321L)));

    assertEquals("INVALID_FLIGHT_CHANGE_ROUTE", exception.getCode());
  }

  @Test
  void quoteShouldRejectUnavailableNewFare() {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking, 101L, 501L, "ADULT", true, "ISSUED");
    FlightFareEntity newFare = newFare(888L, 321L, "1500000.00", "1500000.00");
    newFare.setStatus("SOLD_OUT");

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
            eq(10L), any()))
        .thenReturn(List.of(payment(booking)));
    when(ticketRepository.findByBookingIdAndTicketIdInWithDetails(10L, List.of(101L)))
        .thenReturn(List.of(ticket));
    when(flightFareRepository.findByIdWithDetails(321L)).thenReturn(Optional.of(newFare));

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.quote(
                    null, 10L, new FlightChangeQuoteRequest(List.of(101L), 501L, 888L, 321L)));

    assertEquals("SEAT_UNAVAILABLE", exception.getCode());
  }

  @Test
  void quoteShouldRejectWhenNewFlightDoesNotHaveEnoughSeats() {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking, 101L, 501L, "ADULT", true, "ISSUED");
    FlightFareEntity newFare = newFare(888L, 321L, "1500000.00", "1500000.00");

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
            eq(10L), any()))
        .thenReturn(List.of(payment(booking)));
    when(ticketRepository.findByBookingIdAndTicketIdInWithDetails(10L, List.of(101L)))
        .thenReturn(List.of(ticket));
    when(flightFareRepository.findByIdWithDetails(321L)).thenReturn(Optional.of(newFare));
    when(flightInventoryAvailabilityService.hasEnoughSeats(888L, 20L, 1)).thenReturn(false);

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.quote(
                    null, 10L, new FlightChangeQuoteRequest(List.of(101L), 501L, 888L, 321L)));

    assertEquals("SEAT_UNAVAILABLE", exception.getCode());
  }

  @Test
  void quoteShouldRejectWhenConnectionLayoverIsTooShort() {
    BookingEntity booking = booking();
    TicketEntity ticket = ticket(booking, 101L, 501L, "ADULT", true, "ISSUED");
    FlightFareEntity newFare = newFare(888L, 321L, "1500000.00", "1500000.00");
    FlightFareEntity nextSegmentFare =
        connectionFare(
            777L,
            newFare.getFlight().getArrivalAirport(),
            airport(3L, "DAD"),
            newFare.getFlight().getArrivalTime().plusMinutes(60));

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
            eq(10L), any()))
        .thenReturn(List.of(payment(booking)));
    when(ticketRepository.findByBookingIdAndTicketIdInWithDetails(10L, List.of(101L)))
        .thenReturn(List.of(ticket));
    when(flightFareRepository.findByIdWithDetails(321L)).thenReturn(Optional.of(newFare));
    when(flightInventoryAvailabilityService.hasEnoughSeats(888L, 20L, 1)).thenReturn(true);
    when(bookingFareHoldRepository.findByBookingIdOrderByIdAsc(10L))
        .thenReturn(List.of(hold(ticket.getFlightFare()), hold(nextSegmentFare)));

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.quote(
                    null, 10L, new FlightChangeQuoteRequest(List.of(101L), 501L, 888L, 321L)));

    assertEquals("CONNECTION_INVALID", exception.getCode());
  }

  private BookingEntity booking() {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setStatus("CONFIRMED");
    booking.setCurrency("VND");
    booking.setUser(new UserEntity());
    return booking;
  }

  private PaymentEntity payment(BookingEntity booking) {
    PaymentEntity payment = new PaymentEntity();
    payment.setId(1L);
    payment.setBooking(booking);
    payment.setPaymentStatus("SUCCESS");
    return payment;
  }

  private TicketEntity ticket(
      BookingEntity booking,
      Long ticketId,
      Long flightId,
      String passengerType,
      boolean changeable,
      String status) {
    TicketEntity ticket = new TicketEntity();
    ticket.setId(ticketId);
    ticket.setBooking(booking);
    ticket.setStatus(status);

    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(700L + ticketId);
    passenger.setFirstName("An");
    passenger.setLastName("Nguyen");
    passenger.setPassengerType(passengerType);
    ticket.setPassenger(passenger);

    AirportEntity departureAirport = new AirportEntity();
    departureAirport.setId(1L);
    departureAirport.setCode("SGN");
    AirportEntity arrivalAirport = new AirportEntity();
    arrivalAirport.setId(2L);
    arrivalAirport.setCode("HAN");

    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);
    flight.setFlightNumber("VN123");
    flight.setStatus("SCHEDULED");
    flight.setFlightDate(CURRENT_FLIGHT_DEPARTURE.toLocalDate());
    flight.setDepartureAirport(departureAirport);
    flight.setArrivalAirport(arrivalAirport);
    flight.setDepartureTime(CURRENT_FLIGHT_DEPARTURE);
    flight.setArrivalTime(CURRENT_FLIGHT_ARRIVAL);
    ticket.setFlight(flight);

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setId(10L);
    fareClass.setCode("ECONOMY");
    fareClass.setChangeable(changeable);
    fareClass.setChangeFee(new BigDecimal("300000.00"));
    ticket.setFareClass(fareClass);

    FlightFareEntity flightFare = new FlightFareEntity();
    flightFare.setId(201L);
    flightFare.setFlight(flight);
    flightFare.setFareClass(fareClass);
    flightFare.setBasePrice(new BigDecimal("1200000.00"));
    flightFare.setTotalTaxAmount(new BigDecimal("300000.00"));
    flightFare.setTotalPrice(new BigDecimal("1500000.00"));
    ticket.setFlightFare(flightFare);
    return ticket;
  }

  private FlightFareEntity newFare(
      Long flightId, Long fareId, String totalPrice, String basePrice) {
    AirportEntity departureAirport = airport(1L, "SGN");
    AirportEntity arrivalAirport = airport(2L, "HAN");

    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);
    flight.setFlightNumber("VN220");
    flight.setStatus("SCHEDULED");
    flight.setFlightDate(NEW_FLIGHT_DEPARTURE.toLocalDate());
    flight.setDepartureAirport(departureAirport);
    flight.setArrivalAirport(arrivalAirport);
    flight.setDepartureTime(NEW_FLIGHT_DEPARTURE);
    flight.setArrivalTime(NEW_FLIGHT_ARRIVAL);

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setId(20L);
    fareClass.setCode("ECONOMY");

    FlightFareEntity fare = new FlightFareEntity();
    fare.setId(fareId);
    fare.setFlight(flight);
    fare.setFareClass(fareClass);
    fare.setStatus("AVAILABLE");
    fare.setBasePrice(new BigDecimal(basePrice));
    fare.setTotalTaxAmount(BigDecimal.ZERO);
    fare.setTotalPrice(new BigDecimal(totalPrice));
    return fare;
  }

  private FlightFareEntity connectionFare(
      Long flightId, AirportEntity departureAirport, AirportEntity arrivalAirport, OffsetDateTime departureTime) {
    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);
    flight.setFlightNumber("VN777");
    flight.setStatus("SCHEDULED");
    flight.setFlightDate(departureTime.toLocalDate());
    flight.setDepartureAirport(departureAirport);
    flight.setArrivalAirport(arrivalAirport);
    flight.setDepartureTime(departureTime);
    flight.setArrivalTime(departureTime.plusHours(1));

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setId(30L);
    fareClass.setCode("ECONOMY");

    FlightFareEntity fare = new FlightFareEntity();
    fare.setId(400L + flightId);
    fare.setFlight(flight);
    fare.setFareClass(fareClass);
    fare.setStatus("AVAILABLE");
    fare.setBasePrice(new BigDecimal("1000000.00"));
    fare.setTotalTaxAmount(BigDecimal.ZERO);
    fare.setTotalPrice(new BigDecimal("1000000.00"));
    return fare;
  }

  private AirportEntity airport(Long id, String code) {
    AirportEntity airport = new AirportEntity();
    airport.setId(id);
    airport.setCode(code);
    return airport;
  }

  private TicketFinancialSnapshotEntity snapshot(
      BookingEntity booking, TicketEntity ticket, String fareNetAmount) {
    TicketFinancialSnapshotEntity snapshot = new TicketFinancialSnapshotEntity();
    snapshot.setBooking(booking);
    snapshot.setTicket(ticket);
    snapshot.setPassenger(ticket.getPassenger());
    snapshot.setFareNetAmount(new BigDecimal(fareNetAmount));
    return snapshot;
  }

  private BookingFareHoldEntity hold(FlightFareEntity flightFare) {
    BookingFareHoldEntity hold = new BookingFareHoldEntity();
    hold.setFlightFare(flightFare);
    return hold;
  }
}
