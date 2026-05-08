package com.example.appbootstrap.application.booking;

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
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingManageOptionsService {

  private static final List<String> ACTIVE_ASSIGNMENT_STATUSES = List.of("RESERVED", "BOOKED");

  private final BookingRepository bookingRepository;
  private final PassengerRepository passengerRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final BookingServiceRepository bookingServiceRepository;

  public BookingManageOptionsService(
      BookingRepository bookingRepository,
      PassengerRepository passengerRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      BookingServiceRepository bookingServiceRepository) {
    this.bookingRepository = bookingRepository;
    this.passengerRepository = passengerRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.bookingServiceRepository = bookingServiceRepository;
  }

  @Transactional(readOnly = true)
  public BookingManageOptionsView getManageOptions(Long userId, Long bookingId) {
    BookingEntity booking = requireBooking(userId, bookingId);
    List<PassengerEntity> passengers = passengerRepository.findByBookingIdOrderById(bookingId);
    List<BookingFareHoldEntity> holds =
        bookingFareHoldRepository.findByBookingIdOrderByIdAsc(bookingId);
    List<PassengerSeatAssignmentEntity> seatAssignments =
        passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(
            bookingId, ACTIVE_ASSIGNMENT_STATUSES);
    List<BookingServiceEntity> serviceSelections =
        bookingServiceRepository.findDetailedByBookingId(bookingId);

    return new BookingManageOptionsView(
        booking.getId(),
        booking.getStatus(),
        booking.getCurrency(),
        passengers.stream().map(this::toPassengerView).toList(),
        holds.stream().map(this::toItineraryView).toList(),
        seatAssignments.stream().map(this::toSeatAssignmentView).toList(),
        serviceSelections.stream().map(this::toServiceSelectionView).toList());
  }

  private BookingEntity requireBooking(Long userId, Long bookingId) {
    return userId == null
        ? bookingRepository
            .findById(bookingId)
            .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404))
        : bookingRepository
            .findByIdAndUserId(bookingId, userId)
            .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404));
  }

  private PassengerView toPassengerView(PassengerEntity passenger) {
    return new PassengerView(
        passenger.getId(),
        passenger.getFirstName(),
        passenger.getLastName(),
        passenger.getPassengerType());
  }

  private ItineraryView toItineraryView(BookingFareHoldEntity hold) {
    FlightFareEntity fare = hold.getFlightFare();
    FlightEntity flight = fare.getFlight();
    return new ItineraryView(
        flight.getId(),
        fare.getId(),
        flight.getFlightNumber(),
        flight.getDepartureTime(),
        flight.getArrivalTime(),
        flight.getDepartureAirport().getCode(),
        flight.getArrivalAirport().getCode());
  }

  private SeatAssignmentView toSeatAssignmentView(PassengerSeatAssignmentEntity assignment) {
    FlightSeatEntity seat = assignment.getFlightSeat();
    return new SeatAssignmentView(
        assignment.getPassenger().getId(),
        seat.getFlight().getId(),
        seat.getId(),
        seat.getSeatNumber(),
        assignment.getStatus(),
        assignment.getSurchargeAmount(),
        assignment.getCurrency());
  }

  private ServiceSelectionView toServiceSelectionView(BookingServiceEntity serviceSelection) {
    ServiceEntity service = serviceSelection.getService();
    ServiceOptionEntity option = serviceSelection.getServiceOption();
    return new ServiceSelectionView(
        serviceSelection.getPassenger().getId(),
        service == null ? null : service.getId(),
        service == null ? null : service.getType(),
        service == null ? null : service.getName(),
        option == null ? null : option.getId(),
        option == null ? null : option.getName(),
        serviceSelection.getQuantity(),
        serviceSelection.getPrice());
  }

  public record BookingManageOptionsView(
      Long bookingId,
      String bookingStatus,
      String currency,
      List<PassengerView> passengers,
      List<ItineraryView> itineraries,
      List<SeatAssignmentView> seatAssignments,
      List<ServiceSelectionView> serviceSelections) {}

  public record PassengerView(
      Long passengerId, String firstName, String lastName, String passengerType) {}

  public record ItineraryView(
      Long flightId,
      Long fareId,
      String flightNumber,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      String departureAirportCode,
      String arrivalAirportCode) {}

  public record SeatAssignmentView(
      Long passengerId,
      Long flightId,
      Long seatId,
      String seatNumber,
      String status,
      BigDecimal surchargeAmount,
      String currency) {}

  public record ServiceSelectionView(
      Long passengerId,
      Long serviceId,
      String serviceType,
      String serviceName,
      Long optionId,
      String optionName,
      Integer quantity,
      BigDecimal price) {}
}
