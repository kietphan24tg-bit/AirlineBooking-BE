package com.example.appbootstrap.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.controller.booking.dto.BookingLookupRequest;
import com.example.appbootstrap.controller.booking.dto.BookingLookupResponse;
import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
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
class BookingLookupServiceTest {

  @Mock private BookingRepository bookingRepository;
  @Mock private PassengerRepository passengerRepository;
  @Mock private BookingContactRepository bookingContactRepository;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private BookingServiceRepository bookingServiceRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;

  private BookingLookupService service;

  @BeforeEach
  void setUp() {
    service =
        new BookingLookupService(
            bookingRepository,
            passengerRepository,
            bookingContactRepository,
            bookingFareHoldRepository,
            bookingServiceRepository,
            passengerSeatAssignmentRepository);
  }

  @Test
  void lookup_returnsSummaryWhenPnrAndLastNameMatch() {
    BookingEntity booking = booking(10L, "ABC123", "PENDING_PAYMENT");
    PassengerEntity passenger = passenger(1L, booking, "Nguyen");
    BookingContactEntity primary = contact("guest@example.com", "0901111222", true);
    BookingFareHoldEntity hold = fareHold(booking);

    when(bookingRepository.findByPnrCode("ABC123")).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(10L))
        .thenReturn(List.of(primary));
    when(bookingFareHoldRepository.findByBookingIdOrderByIdAsc(10L)).thenReturn(List.of(hold));
    when(bookingServiceRepository.sumPriceByBookingId(10L)).thenReturn(new BigDecimal("50000"));
    when(passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(10L))
        .thenReturn(new BigDecimal("20000"));
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(eq(10L), any()))
        .thenReturn(List.of());
    when(bookingServiceRepository.findDetailedByBookingId(10L)).thenReturn(List.of());

    BookingLookupResponse response = service.lookup(new BookingLookupRequest("abc123", "NGUYEN"));

    assertEquals(10L, response.booking().bookingId());
    assertEquals("ABC123", response.booking().pnrCode());
    assertEquals("Pending payment", response.booking().statusLabel());
    assertEquals("guest@example.com", response.primaryContact().email());
    assertEquals(1, response.itineraries().size());
    assertEquals("VN123", response.itineraries().getFirst().flightNumber());
    assertEquals(23, response.itineraries().getFirst().fareBenefits().baggageCheckinKg());
    assertEquals("ECO", response.itineraries().getFirst().fareBenefits().fareClassCode());
    assertTrue(response.passengers().getFirst().seatAssignments().isEmpty());
    assertTrue(response.passengers().getFirst().serviceSelections().isEmpty());
    assertEquals(new BigDecimal("1000000.00"), response.paymentDetails().price().baseFare());
    assertEquals(new BigDecimal("150000.00"), response.paymentDetails().price().tax());
    assertEquals(new BigDecimal("50000.00"), response.paymentDetails().price().serviceFee());
    assertEquals(new BigDecimal("20000.00"), response.paymentDetails().price().seatFee());
    assertEquals(true, response.actions().canSelectSeat());
  }

  @Test
  void lookup_throwsNotFoundWhenLastNameDoesNotMatch() {
    BookingEntity booking = booking(10L, "ABC123", "CONFIRMED");
    PassengerEntity passenger = passenger(1L, booking, "Tran");

    when(bookingRepository.findByPnrCode("ABC123")).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));

    AppException exception =
        assertThrows(
            AppException.class, () -> service.lookup(new BookingLookupRequest("ABC123", "Nguyen")));

    assertEquals("BOOKING_NOT_FOUND", exception.getCode());
    assertEquals(404, exception.getStatusCode());
  }

  @Test
  void lookup_fallsBackToBookingContactWhenPrimaryMissing() {
    BookingEntity booking = booking(10L, "ABC123", "CONFIRMED");
    PassengerEntity passenger = passenger(1L, booking, "Nguyen");

    when(bookingRepository.findByPnrCode("ABC123")).thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(10L)).thenReturn(List.of(passenger));
    when(bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(10L))
        .thenReturn(List.of());
    when(bookingFareHoldRepository.findByBookingIdOrderByIdAsc(10L)).thenReturn(List.of());
    when(bookingServiceRepository.sumPriceByBookingId(10L)).thenReturn(BigDecimal.ZERO);
    when(passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(10L))
        .thenReturn(BigDecimal.ZERO);
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(eq(10L), any()))
        .thenReturn(List.of());
    when(bookingServiceRepository.findDetailedByBookingId(10L)).thenReturn(List.of());

    BookingLookupResponse response = service.lookup(new BookingLookupRequest("ABC123", "Nguyen"));

    assertEquals("booking@example.com", response.primaryContact().email());
    assertEquals("0900000000", response.primaryContact().phone());
    assertEquals(true, response.actions().canCheckIn());
  }

  private BookingEntity booking(Long id, String pnrCode, String status) {
    BookingEntity booking = new BookingEntity();
    booking.setId(id);
    booking.setPnrCode(pnrCode);
    booking.setStatus(status);
    booking.setContactEmail("booking@example.com");
    booking.setContactPhone("0900000000");
    booking.setCurrency("VND");
    booking.setTotalAmount(new BigDecimal("1200000.00"));
    booking.setDiscountAmount(new BigDecimal("33000.00"));
    booking.setCreatedAt(OffsetDateTime.parse("2026-05-01T10:00:00Z"));
    return booking;
  }

  private PassengerEntity passenger(Long id, BookingEntity booking, String lastName) {
    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(id);
    passenger.setBooking(booking);
    passenger.setFirstName("Van A");
    passenger.setLastName(lastName);
    passenger.setPassengerType("ADULT");
    passenger.setGender("MALE");
    passenger.setDateOfBirth(LocalDate.parse("1990-01-01"));
    return passenger;
  }

  private BookingContactEntity contact(String email, String phone, boolean primary) {
    BookingContactEntity contact = new BookingContactEntity();
    contact.setEmail(email);
    contact.setPhone(phone);
    contact.setPrimary(primary);
    return contact;
  }

  private BookingFareHoldEntity fareHold(BookingEntity booking) {
    AirportEntity sgn = new AirportEntity();
    sgn.setCode("SGN");
    sgn.setCity("Ho Chi Minh");
    AirportEntity han = new AirportEntity();
    han.setCode("HAN");
    han.setCity("Ha Noi");

    FlightEntity flight = new FlightEntity();
    flight.setId(100L);
    flight.setFlightNumber("VN123");
    flight.setDepartureAirport(sgn);
    flight.setArrivalAirport(han);
    flight.setDepartureTerminal("T1");
    flight.setArrivalTerminal("T1");
    flight.setDepartureTime(OffsetDateTime.parse("2026-06-01T08:30:00+07:00"));
    flight.setArrivalTime(OffsetDateTime.parse("2026-06-01T10:35:00+07:00"));
    flight.setStatus("SCHEDULED");

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setCode("ECO");
    fareClass.setName("Eco");
    fareClass.setCabinClass("ECONOMY");
    fareClass.setBaggageCheckinKg(23);
    fareClass.setBaggageHandKg(7);
    fareClass.setChangeFee(new BigDecimal("500000"));
    fareClass.setRefundFee(new BigDecimal("300000"));
    fareClass.setRefundable(true);
    fareClass.setChangeable(true);
    fareClass.setPriorityCheckin(false);
    fareClass.setPriorityBoarding(false);
    fareClass.setMileageRate(new BigDecimal("1.00"));
    fareClass.setLoungeAccess(false);
    fareClass.setSeatSelection(true);
    fareClass.setSeatSelectionPolicy("STANDARD");
    fareClass.setMealType("STANDARD");
    fareClass.setWifiIncluded(false);
    fareClass.setFastTrackSecurity(false);

    FlightFareEntity flightFare = new FlightFareEntity();
    flightFare.setFlight(flight);
    flightFare.setFareClass(fareClass);
    flightFare.setBasePrice(new BigDecimal("1000000.00"));
    flightFare.setTotalTaxAmount(new BigDecimal("150000.00"));

    BookingFareHoldEntity hold = new BookingFareHoldEntity();
    hold.setBooking(booking);
    hold.setFlightFare(flightFare);
    hold.setQuantity(1);
    return hold;
  }
}
