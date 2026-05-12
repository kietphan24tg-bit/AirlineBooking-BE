package com.example.modulecheckin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BoardingPassEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BoardingPassRepository;
import com.example.modulebooking.repository.BookingRepository;
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
class CheckInConfirmServiceTest {

  @Mock private BookingRepository bookingRepository;
  @Mock private PassengerRepository passengerRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  @Mock private BoardingPassRepository boardingPassRepository;

  private CheckInConfirmService service;

  @BeforeEach
  void setUp() {
    service =
        new CheckInConfirmService(
            bookingRepository,
            passengerRepository,
            ticketRepository,
            passengerSeatAssignmentRepository,
            boardingPassRepository,
            new CheckInProperties(15, 24, 40, 60));
  }

  @Test
  void confirmCheckIn_createsBoardingPassesForEligiblePassengers() {
    BookingEntity booking = booking("CONFIRMED");
    PassengerEntity passenger = passenger(1L, booking, "Nguyen", "Van A");
    TicketEntity ticket =
        domesticTicket(booking, passenger, true, OffsetDateTime.now().plusHours(3));

    when(bookingRepository.findByPnrCode("ABC123")).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            10L, Set.of(1L), List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of());
    when(boardingPassRepository.findByTicketIdIn(List.of(9001L))).thenReturn(List.of());
    when(boardingPassRepository.saveAll(anyList()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              List<BoardingPassEntity> items = invocation.getArgument(0, List.class);
              long id = 7001L;
              for (BoardingPassEntity item : items) {
                item.setId(id++);
              }
              return items;
            });

    CheckInConfirmView response =
        service.confirmCheckIn(
            new CheckInConfirmCommand(
                "ABC123",
                "NGUYEN",
                List.of(new CheckInConfirmCommand.SegmentSelection(1001L, List.of(1L)))));

    assertEquals("CHECKED_IN_COMPLETED", response.booking().status());
    assertEquals(1, response.summary().confirmedPassengers());
    assertEquals(
        "CHECKED_IN", response.segments().getFirst().passengers().getFirst().checkInStatus());
    assertEquals(1, response.boardingPasses().size());
    assertEquals("12A", response.boardingPasses().getFirst().seatLabel());
  }

  @Test
  void confirmCheckIn_rejectsWhenSeatMissing() {
    BookingEntity booking = booking("CONFIRMED");
    PassengerEntity passenger = passenger(1L, booking, "Nguyen", "Van A");
    TicketEntity ticket =
        domesticTicket(booking, passenger, false, OffsetDateTime.now().plusHours(3));

    when(bookingRepository.findByPnrCode("ABC123")).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            10L, Set.of(1L), List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of());
    when(boardingPassRepository.findByTicketIdIn(List.of(9001L))).thenReturn(List.of());

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.confirmCheckIn(
                    new CheckInConfirmCommand(
                        "ABC123",
                        "NGUYEN",
                        List.of(new CheckInConfirmCommand.SegmentSelection(1001L, List.of(1L))))));

    assertEquals("SEAT_SELECTION_REQUIRED", exception.getCode());
  }

  @Test
  void confirmCheckIn_rejectsAlreadyCheckedInPassenger() {
    BookingEntity booking = booking("CONFIRMED");
    PassengerEntity passenger = passenger(1L, booking, "Nguyen", "Van A");
    TicketEntity ticket =
        domesticTicket(booking, passenger, true, OffsetDateTime.now().plusHours(3));
    BoardingPassEntity boardingPass = new BoardingPassEntity();
    boardingPass.setTicket(ticket);

    when(bookingRepository.findByPnrCode("ABC123")).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            10L, Set.of(1L), List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of());
    when(boardingPassRepository.findByTicketIdIn(List.of(9001L))).thenReturn(List.of(boardingPass));

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.confirmCheckIn(
                    new CheckInConfirmCommand(
                        "ABC123",
                        "NGUYEN",
                        List.of(new CheckInConfirmCommand.SegmentSelection(1001L, List.of(1L))))));

    assertEquals("ALREADY_CHECKED_IN", exception.getCode());
  }

  @Test
  void confirmCheckIn_rejectsDuplicatePassengerIdsWithinFlight() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.confirmCheckIn(
                    new CheckInConfirmCommand(
                        "ABC123",
                        "NGUYEN",
                        List.of(
                            new CheckInConfirmCommand.SegmentSelection(1001L, List.of(1L, 1L))))));

    assertEquals("VALIDATION_FAILED", exception.getCode());
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
      BookingEntity booking,
      PassengerEntity passenger,
      boolean withSeat,
      OffsetDateTime departureTime) {
    AirlineEntity airline = new AirlineEntity();
    airline.setCode("VJ");
    airline.setName("VietJet Air");

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
    flight.setDepartureGate("12");
    flight.setDepartureTime(departureTime);
    flight.setArrivalTime(departureTime.plusHours(2));

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setName("Eco");
    fareClass.setCabinClass("Economy");

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
}
