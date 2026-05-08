package com.example.appbootstrap.application.booking;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BookingLookupService {

  private static final String BOOKING_NOT_FOUND_CODE = "BOOKING_NOT_FOUND";
  private static final String BOOKING_NOT_FOUND_MESSAGE = "Booking not found";
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

  private final BookingRepository bookingRepository;
  private final PassengerRepository passengerRepository;
  private final BookingContactRepository bookingContactRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;

  public BookingLookupService(
      BookingRepository bookingRepository,
      PassengerRepository passengerRepository,
      BookingContactRepository bookingContactRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      BookingServiceRepository bookingServiceRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository) {
    this.bookingRepository = bookingRepository;
    this.passengerRepository = passengerRepository;
    this.bookingContactRepository = bookingContactRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
  }

  @Transactional(readOnly = true)
  public BookingLookupResponse lookup(BookingLookupRequest request) {
    String normalizedPnr = normalizePnr(request.pnrCode());
    String normalizedLastName = normalizeName(request.lastName());

    BookingEntity booking =
        bookingRepository.findByPnrCode(normalizedPnr).orElseThrow(this::bookingNotFoundException);

    List<PassengerEntity> passengers =
        passengerRepository.findByBookingIdOrderById(booking.getId());
    if (passengers.isEmpty() || !matchesPassengerLastName(passengers, normalizedLastName)) {
      throw bookingNotFoundException();
    }

    List<BookingContactEntity> contacts =
        bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(booking.getId());
    BookingLookupResponse.PrimaryContact primaryContact = toPrimaryContact(booking, contacts);

    List<BookingLookupResponse.ItinerarySummary> itineraries =
        bookingFareHoldRepository.findByBookingIdOrderByIdAsc(booking.getId()).stream()
            .map(this::toItinerarySummary)
            .sorted(Comparator.comparing(BookingLookupResponse.ItinerarySummary::departureTime))
            .toList();
    BookingLookupResponse.PaymentDetails paymentDetails = buildPaymentDetails(booking);

    return new BookingLookupResponse(
        new BookingLookupResponse.BookingSummary(
            booking.getId(),
            booking.getPnrCode(),
            booking.getStatus(),
            statusLabel(booking.getStatus()),
            booking.getTotalAmount(),
            booking.getCurrency(),
            booking.getCreatedAt(),
            booking.getPnrExpiresAt()),
        primaryContact,
        paymentDetails,
        itineraries,
        passengers.stream()
            .map(
                passenger ->
                    new BookingLookupResponse.PassengerSummary(
                        passenger.getId(),
                        passenger.getFirstName(),
                        passenger.getLastName(),
                        passenger.getPassengerType(),
                        passenger.getGender(),
                        passenger.getDateOfBirth()))
            .toList(),
        actionsForStatus(booking.getStatus()));
  }

  private BookingLookupResponse.PaymentDetails buildPaymentDetails(BookingEntity booking) {
    List<BookingFareHoldEntity> holds =
        bookingFareHoldRepository.findByBookingIdOrderByIdAsc(booking.getId());
    BigDecimal baseFare =
        holds.stream()
            .map(
                hold ->
                    multiply(nonNullMoney(hold.getFlightFare().getBasePrice()), hold.getQuantity()))
            .reduce(ZERO, BigDecimal::add);
    BigDecimal tax =
        holds.stream()
            .map(
                hold ->
                    multiply(
                        nonNullMoney(hold.getFlightFare().getTotalTaxAmount()), hold.getQuantity()))
            .reduce(ZERO, BigDecimal::add);
    BigDecimal discount = nonNullMoney(booking.getDiscountAmount());
    BigDecimal serviceFee =
        nonNullMoney(bookingServiceRepository.sumPriceByBookingId(booking.getId()));
    BigDecimal seatFee =
        nonNullMoney(
            passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(booking.getId()));
    BigDecimal total = nonNullMoney(booking.getTotalAmount());
    return new BookingLookupResponse.PaymentDetails(
        new BookingLookupResponse.PriceBreakdown(
            baseFare, discount, tax, serviceFee, seatFee, total, booking.getCurrency()));
  }

  private BookingLookupResponse.ItinerarySummary toItinerarySummary(BookingFareHoldEntity hold) {
    var fare = hold.getFlightFare();
    var flight = fare.getFlight();
    return new BookingLookupResponse.ItinerarySummary(
        flight.getId(),
        flight.getFlightNumber(),
        flight.getDepartureTime(),
        flight.getArrivalTime(),
        flight.getDepartureAirport().getCode(),
        flight.getDepartureAirport().getCity(),
        flight.getDepartureTerminal(),
        flight.getArrivalAirport().getCode(),
        flight.getArrivalAirport().getCity(),
        flight.getArrivalTerminal(),
        statusLabel(flight.getStatus()),
        fare.getFareClass().getName(),
        fare.getFareClass().getCabinClass());
  }

  private BookingLookupResponse.PrimaryContact toPrimaryContact(
      BookingEntity booking, List<BookingContactEntity> contacts) {
    BookingContactEntity primary =
        contacts.stream().filter(BookingContactEntity::isPrimary).findFirst().orElse(null);

    if (primary != null) {
      String primaryEmail =
          StringUtils.hasText(primary.getEmail()) ? primary.getEmail() : booking.getContactEmail();
      String primaryPhone =
          StringUtils.hasText(primary.getPhone()) ? primary.getPhone() : booking.getContactPhone();
      return new BookingLookupResponse.PrimaryContact(primaryEmail, primaryPhone);
    }

    return new BookingLookupResponse.PrimaryContact(
        booking.getContactEmail(), booking.getContactPhone());
  }

  private BookingLookupResponse.Actions actionsForStatus(String status) {
    boolean pendingPayment = "PENDING_PAYMENT".equalsIgnoreCase(status);
    boolean confirmed = "CONFIRMED".equalsIgnoreCase(status);
    return new BookingLookupResponse.Actions(
        pendingPayment || confirmed,
        pendingPayment || confirmed,
        confirmed,
        pendingPayment,
        pendingPayment);
  }

  private boolean matchesPassengerLastName(
      List<PassengerEntity> passengers, String normalizedLastName) {
    return passengers.stream()
        .map(PassengerEntity::getLastName)
        .filter(StringUtils::hasText)
        .map(this::normalizeName)
        .anyMatch(normalizedLastName::equals);
  }

  private String normalizePnr(String pnrCode) {
    return pnrCode.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeName(String name) {
    return name.trim().toUpperCase(Locale.ROOT);
  }

  private BigDecimal multiply(BigDecimal amount, int quantity) {
    return amount.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal nonNullMoney(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  private String statusLabel(String status) {
    if ("CONFIRMED".equalsIgnoreCase(status)) {
      return "Confirmed";
    }
    if ("PENDING_PAYMENT".equalsIgnoreCase(status)) {
      return "Pending payment";
    }
    if ("EXPIRED".equalsIgnoreCase(status)) {
      return "Expired";
    }
    if ("CANCELLED".equalsIgnoreCase(status)) {
      return "Cancelled";
    }
    return status;
  }

  private AppException bookingNotFoundException() {
    return new AppException(BOOKING_NOT_FOUND_CODE, BOOKING_NOT_FOUND_MESSAGE, 404);
  }
}
