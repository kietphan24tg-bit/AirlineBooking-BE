package com.example.modulecheckin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BoardingPassEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingServiceEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.ServiceEntity;
import com.example.modulebooking.entity.ServiceOptionEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BoardingPassRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.modulecheckin.config.CheckInProperties;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.entity.SeatCategoryEntity;
import com.example.moduleflight.entity.SeatClassEntity;
import com.example.moduleflight.entity.SeatTypeEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckInContinueServiceTest {

  @Mock private BookingRepository bookingRepository;
  @Mock private PassengerRepository passengerRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  @Mock private BookingServiceRepository bookingServiceRepository;
  @Mock private BoardingPassRepository boardingPassRepository;

  private CheckInContinueService service;

  @BeforeEach
  void setUp() {
    service =
        new CheckInContinueService(
            bookingRepository,
            passengerRepository,
            ticketRepository,
            passengerSeatAssignmentRepository,
            bookingServiceRepository,
            boardingPassRepository,
            new CheckInProperties(15, 24, 40, 60));
  }

  @Test
  void continueCheckIn_returnsReadySnapshotForOpenDomesticFlight() {
    BookingEntity booking = booking("CONFIRMED");
    PassengerEntity passenger = passenger(1L, booking, "Nguyen", "Van A");
    TicketEntity ticket = domesticTicket(booking, passenger, true, OffsetDateTime.now().plusHours(3));

    when(bookingRepository.findByPnrCode("ABC123")).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            10L, Set.of(1L), List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of());
    when(bookingServiceRepository.findAllByBookingId(10L)).thenReturn(List.of(serviceEntry(booking, passenger)));
    when(boardingPassRepository.findByTicketIdIn(List.of(9001L))).thenReturn(List.of());

    CheckInContinueView response =
        service.continueCheckIn(
            new CheckInContinueCommand(
                "ABC123", "NGUYEN", List.of(new CheckInContinueCommand.SegmentSelection(1001L, List.of(1L)))));

    assertEquals("CONFIRM_CHECKIN", response.actions().nextStep());
    assertTrue(response.actions().allowConfirmCheckIn());
    assertFalse(response.actions().allowSeatSelection());
    assertEquals("READY", response.segments().getFirst().passengers().getFirst().checkInStatus());
    assertEquals("12A", response.segments().getFirst().passengers().getFirst().seat().seatNumber());
    assertEquals("MEAL", response.segments().getFirst().passengers().getFirst().services().getFirst().code());
  }

  @Test
  void continueCheckIn_requiresSeatSelectionWhenSeatMissing() {
    BookingEntity booking = booking("CONFIRMED");
    PassengerEntity passenger = passenger(1L, booking, "Nguyen", "Van A");
    TicketEntity ticket = domesticTicket(booking, passenger, false, OffsetDateTime.now().plusHours(3));

    when(bookingRepository.findByPnrCode("ABC123")).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            10L, Set.of(1L), List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of());
    when(bookingServiceRepository.findAllByBookingId(10L)).thenReturn(List.of());
    when(boardingPassRepository.findByTicketIdIn(List.of(9001L))).thenReturn(List.of());

    CheckInContinueView response =
        service.continueCheckIn(
            new CheckInContinueCommand(
                "ABC123", "NGUYEN", List.of(new CheckInContinueCommand.SegmentSelection(1001L, List.of(1L)))));

    assertEquals("SEAT_SELECTION", response.actions().nextStep());
    assertFalse(response.actions().allowConfirmCheckIn());
    assertTrue(response.actions().allowSeatSelection());
    assertEquals(
        "SEAT_SELECTION_REQUIRED",
        response.segments().getFirst().passengers().getFirst().checkInStatus());
  }

  @Test
  void continueCheckIn_blocksWhenWindowClosed() {
    BookingEntity booking = booking("CONFIRMED");
    PassengerEntity passenger = passenger(1L, booking, "Nguyen", "Van A");
    TicketEntity ticket = domesticTicket(booking, passenger, true, OffsetDateTime.now().plusMinutes(30));

    when(bookingRepository.findByPnrCode("ABC123")).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            10L, Set.of(1L), List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of());
    when(bookingServiceRepository.findAllByBookingId(10L)).thenReturn(List.of());
    when(boardingPassRepository.findByTicketIdIn(List.of(9001L))).thenReturn(List.of());

    CheckInContinueView response =
        service.continueCheckIn(
            new CheckInContinueCommand(
                "ABC123", "NGUYEN", List.of(new CheckInContinueCommand.SegmentSelection(1001L, List.of(1L)))));

    assertEquals("BLOCKED", response.actions().nextStep());
    assertEquals("CLOSED", response.segments().getFirst().checkInWindow().status());
    assertEquals("CHECKIN_CLOSED", response.segments().getFirst().passengers().getFirst().checkInStatus());
  }

  @Test
  void continueCheckIn_rejectsDuplicatePassengerIdsWithinFlight() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.continueCheckIn(
                    new CheckInContinueCommand(
                        "ABC123",
                        "NGUYEN",
                        List.of(
                            new CheckInContinueCommand.SegmentSelection(1001L, List.of(1L, 1L))))));

    assertEquals("VALIDATION_FAILED", exception.getCode());
  }

  @Test
  void continueCheckIn_marksAlreadyCheckedInPassengerAsBlocked() {
    BookingEntity booking = booking("CONFIRMED");
    PassengerEntity passenger = passenger(1L, booking, "Nguyen", "Van A");
    TicketEntity ticket = domesticTicket(booking, passenger, true, OffsetDateTime.now().plusHours(3));
    BoardingPassEntity boardingPass = new BoardingPassEntity();
    boardingPass.setTicket(ticket);

    when(bookingRepository.findByPnrCode("ABC123")).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            10L, Set.of(1L), List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of());
    when(bookingServiceRepository.findAllByBookingId(10L)).thenReturn(List.of());
    when(boardingPassRepository.findByTicketIdIn(List.of(9001L))).thenReturn(List.of(boardingPass));

    CheckInContinueView response =
        service.continueCheckIn(
            new CheckInContinueCommand(
                "ABC123", "NGUYEN", List.of(new CheckInContinueCommand.SegmentSelection(1001L, List.of(1L)))));

    assertEquals("BLOCKED", response.actions().nextStep());
    assertEquals("ALREADY_CHECKED_IN", response.segments().getFirst().passengers().getFirst().checkInStatus());
  }

  private static BookingEntity booking(String status) {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setPnrCode("ABC123");
    booking.setStatus(status);
    booking.setContactEmail("nguyen@example.com");
    booking.setContactPhone("+84901234567");
    booking.setCurrency("VND");
    return booking;
  }

  private static PassengerEntity passenger(
      Long id, BookingEntity booking, String lastName, String firstName) {
    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(id);
    passenger.setBooking(booking);
    passenger.setLastName(lastName);
    passenger.setFirstName(firstName);
    passenger.setDateOfBirth(LocalDate.of(1990, 1, 1));
    passenger.setPassengerType("ADULT");
    return passenger;
  }

  private static TicketEntity domesticTicket(
      BookingEntity booking, PassengerEntity passenger, boolean withSeat, OffsetDateTime departureTime) {
    AirlineEntity airline = new AirlineEntity();
    airline.setCode("VJ");
    airline.setName("VietJet Air");
    airline.setLogoUrl("https://example.com/logo.svg");

    AirportEntity departure = new AirportEntity();
    departure.setCode("SGN");
    departure.setName("Tan Son Nhat");
    departure.setCity("Ho Chi Minh");
    departure.setCountryCode("VN");

    AirportEntity arrival = new AirportEntity();
    arrival.setCode("HAN");
    arrival.setName("Noi Bai");
    arrival.setCity("Ha Noi");
    arrival.setCountryCode("VN");

    FlightEntity flight = new FlightEntity();
    flight.setId(1001L);
    flight.setFlightNumber("VJ123");
    flight.setAirline(airline);
    flight.setDepartureAirport(departure);
    flight.setArrivalAirport(arrival);
    flight.setDepartureTerminal("T1");
    flight.setDepartureGate("12");
    flight.setDepartureTime(departureTime);
    flight.setArrivalTime(departureTime.plusHours(2));

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setName("Eco");
    fareClass.setCabinClass("Economy");
    fareClass.setBaggageHandKg(7);
    fareClass.setBaggageCheckinKg(20);

    FlightFareEntity fare = new FlightFareEntity();
    fare.setFlight(flight);
    fare.setFareClass(fareClass);

    TicketEntity ticket = new TicketEntity();
    ticket.setId(9001L);
    ticket.setBooking(booking);
    ticket.setPassenger(passenger);
    ticket.setFlight(flight);
    ticket.setFlightFare(fare);
    ticket.setFareClass(fareClass);
    ticket.setStatus("ISSUED");
    ticket.setTicketNumber("ABC123-1");
    if (withSeat) {
      FlightSeatEntity seat = new FlightSeatEntity();
      seat.setId(501L);
      seat.setSeatNumber("12A");
      seat.setFlight(flight);
      seat.setSeatClass(new SeatClassEntity());
      seat.setSeatCategory(new SeatCategoryEntity());
      seat.setSeatType(new SeatTypeEntity());
      ticket.setFlightSeat(seat);
    }
    return ticket;
  }

  private static BookingServiceEntity serviceEntry(BookingEntity booking, PassengerEntity passenger) {
    ServiceEntity service = new ServiceEntity();
    service.setType("MEAL");
    service.setName("Meal");

    ServiceOptionEntity option = new ServiceOptionEntity();
    option.setName("Hot Meal");

    BookingServiceEntity entity = new BookingServiceEntity();
    entity.setBooking(booking);
    entity.setPassenger(passenger);
    entity.setService(service);
    entity.setServiceOption(option);
    entity.setQuantity(1);
    entity.setPrice(BigDecimal.TEN);
    return entity;
  }
}
