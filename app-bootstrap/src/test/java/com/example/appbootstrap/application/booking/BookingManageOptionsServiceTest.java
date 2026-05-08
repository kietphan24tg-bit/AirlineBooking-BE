package com.example.appbootstrap.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.BookingServiceEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.ServiceEntity;
import com.example.modulebooking.entity.ServiceOptionEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingManageOptionsServiceTest {

  @Mock private BookingRepository bookingRepository;
  @Mock private PassengerRepository passengerRepository;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  @Mock private BookingServiceRepository bookingServiceRepository;

  private BookingManageOptionsService service;

  @BeforeEach
  void setUp() {
    service =
        new BookingManageOptionsService(
            bookingRepository,
            passengerRepository,
            bookingFareHoldRepository,
            passengerSeatAssignmentRepository,
            bookingServiceRepository);
  }

  @Test
  void getManageOptions_returnsBookingScopedContext() {
    BookingEntity booking = booking(10L, "PENDING_PAYMENT");
    PassengerEntity passenger = passenger(1L, booking, "Van A", "Nguyen");
    BookingFareHoldEntity hold = hold(booking, 100L, 1000L, "VN123", "SGN", "HAN");
    PassengerSeatAssignmentEntity assignment =
        seatAssignment(booking, passenger, 500L, 100L, "12A");
    BookingServiceEntity serviceSelection =
        serviceSelection(booking, passenger, 20L, "MEAL", "Meals", 200L, "Pho", 2);

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(bookingFareHoldRepository.findByBookingIdOrderByIdAsc(10L)).thenReturn(List.of(hold));
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(
            10L, List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of(assignment));
    when(bookingServiceRepository.findDetailedByBookingId(10L))
        .thenReturn(List.of(serviceSelection));

    BookingManageOptionsService.BookingManageOptionsView view = service.getManageOptions(null, 10L);

    assertEquals(10L, view.bookingId());
    assertEquals("PENDING_PAYMENT", view.bookingStatus());
    assertEquals("VND", view.currency());
    assertEquals(1, view.passengers().size());
    assertEquals(1L, view.passengers().getFirst().passengerId());
    assertEquals(1, view.itineraries().size());
    assertEquals(100L, view.itineraries().getFirst().flightId());
    assertEquals(1000L, view.itineraries().getFirst().fareId());
    assertEquals(1, view.seatAssignments().size());
    assertEquals("12A", view.seatAssignments().getFirst().seatNumber());
    assertEquals(1, view.serviceSelections().size());
    assertEquals("MEAL", view.serviceSelections().getFirst().serviceType());
    assertEquals("Pho", view.serviceSelections().getFirst().optionName());
  }

  @Test
  void getManageOptions_returnsEmptySelectionsWhenNoneExist() {
    BookingEntity booking = booking(10L, "CONFIRMED");
    PassengerEntity passenger = passenger(1L, booking, "Van A", "Nguyen");

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(bookingFareHoldRepository.findByBookingIdOrderByIdAsc(10L)).thenReturn(List.of());
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(
            10L, List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of());
    when(bookingServiceRepository.findDetailedByBookingId(10L)).thenReturn(List.of());

    BookingManageOptionsService.BookingManageOptionsView view = service.getManageOptions(null, 10L);

    assertEquals(1, view.passengers().size());
    assertEquals(0, view.seatAssignments().size());
    assertEquals(0, view.serviceSelections().size());
    assertEquals(0, view.itineraries().size());
  }

  @Test
  void getManageOptions_returnsMultipleItineraries() {
    BookingEntity booking = booking(10L, "PENDING_PAYMENT");

    when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of());
    when(bookingFareHoldRepository.findByBookingIdOrderByIdAsc(10L))
        .thenReturn(
            List.of(
                hold(booking, 100L, 1000L, "VN123", "SGN", "HAN"),
                hold(booking, 101L, 1001L, "VN456", "HAN", "DAD")));
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(
            10L, List.of("RESERVED", "BOOKED")))
        .thenReturn(List.of());
    when(bookingServiceRepository.findDetailedByBookingId(10L)).thenReturn(List.of());

    BookingManageOptionsService.BookingManageOptionsView view = service.getManageOptions(null, 10L);

    assertEquals(2, view.itineraries().size());
    assertEquals(100L, view.itineraries().getFirst().flightId());
    assertEquals(101L, view.itineraries().get(1).flightId());
  }

  @Test
  void getManageOptions_throwsWhenBookingNotFoundForUser() {
    when(bookingRepository.findByIdAndUserId(10L, 99L)).thenReturn(Optional.empty());

    AppException exception =
        assertThrows(AppException.class, () -> service.getManageOptions(99L, 10L));

    assertEquals("BOOKING_NOT_FOUND", exception.getCode());
    assertEquals(404, exception.getStatusCode());
  }

  private BookingEntity booking(Long id, String status) {
    BookingEntity booking = new BookingEntity();
    booking.setId(id);
    booking.setStatus(status);
    booking.setCurrency("VND");
    return booking;
  }

  private PassengerEntity passenger(
      Long id, BookingEntity booking, String firstName, String lastName) {
    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(id);
    passenger.setBooking(booking);
    passenger.setFirstName(firstName);
    passenger.setLastName(lastName);
    passenger.setPassengerType("ADULT");
    return passenger;
  }

  private BookingFareHoldEntity hold(
      BookingEntity booking,
      Long flightId,
      Long fareId,
      String flightNumber,
      String departureCode,
      String arrivalCode) {
    AirportEntity departureAirport = new AirportEntity();
    departureAirport.setCode(departureCode);
    AirportEntity arrivalAirport = new AirportEntity();
    arrivalAirport.setCode(arrivalCode);

    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);
    flight.setFlightNumber(flightNumber);
    flight.setDepartureAirport(departureAirport);
    flight.setArrivalAirport(arrivalAirport);
    flight.setDepartureTime(OffsetDateTime.parse("2026-06-01T08:30:00+07:00"));
    flight.setArrivalTime(OffsetDateTime.parse("2026-06-01T10:35:00+07:00"));

    FlightFareEntity fare = new FlightFareEntity();
    fare.setId(fareId);
    fare.setFlight(flight);

    BookingFareHoldEntity hold = new BookingFareHoldEntity();
    hold.setBooking(booking);
    hold.setFlightFare(fare);
    return hold;
  }

  private PassengerSeatAssignmentEntity seatAssignment(
      BookingEntity booking,
      PassengerEntity passenger,
      Long seatId,
      Long flightId,
      String seatNumber) {
    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);

    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setId(seatId);
    seat.setFlight(flight);
    seat.setSeatNumber(seatNumber);

    PassengerSeatAssignmentEntity assignment = new PassengerSeatAssignmentEntity();
    assignment.setBooking(booking);
    assignment.setPassenger(passenger);
    assignment.setFlightSeat(seat);
    assignment.setStatus("BOOKED");
    assignment.setSurchargeAmount(new BigDecimal("20000.00"));
    assignment.setCurrency("VND");
    return assignment;
  }

  private BookingServiceEntity serviceSelection(
      BookingEntity booking,
      PassengerEntity passenger,
      Long serviceId,
      String serviceType,
      String serviceName,
      Long optionId,
      String optionName,
      int quantity) {
    ServiceEntity service = new ServiceEntity();
    service.setId(serviceId);
    service.setType(serviceType);
    service.setName(serviceName);

    ServiceOptionEntity option = new ServiceOptionEntity();
    option.setId(optionId);
    option.setName(optionName);

    BookingServiceEntity entity = new BookingServiceEntity();
    entity.setBooking(booking);
    entity.setPassenger(passenger);
    entity.setService(service);
    entity.setServiceOption(option);
    entity.setQuantity(quantity);
    entity.setPrice(new BigDecimal("240000.00"));
    return entity;
  }
}
