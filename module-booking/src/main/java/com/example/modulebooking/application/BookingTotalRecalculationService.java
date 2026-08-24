package com.example.modulebooking.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class BookingTotalRecalculationService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

  private final BookingRepository bookingRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;

  public BookingTotalRecalculationService(
      BookingRepository bookingRepository,
      BookingServiceRepository bookingServiceRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository) {
    this.bookingRepository = bookingRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
  }

  public BigDecimal captureFareTotal(BookingEntity booking) {
    BigDecimal fareTotal =
        normalizeMoney(booking.getTotalAmount())
            .subtract(currentServiceTotal(booking.getId()))
            .subtract(currentSeatSurchargeTotal(booking.getId()));
    if (fareTotal.signum() < 0) {
      throw new AppException(
          "BOOKING_TOTAL_INVALID", "Booking total is lower than selected add-ons", 409);
    }
    return fareTotal.setScale(2, RoundingMode.HALF_UP);
  }

  public BookingTotalBreakdown refreshTotal(BookingEntity booking, BigDecimal fareTotal) {
    BigDecimal serviceTotal = currentServiceTotal(booking.getId());
    BigDecimal seatSurchargeTotal = currentSeatSurchargeTotal(booking.getId());
    BigDecimal totalAmount =
        normalizeMoney(fareTotal)
            .add(serviceTotal)
            .add(seatSurchargeTotal)
            .setScale(2, RoundingMode.HALF_UP);
    booking.setTotalAmount(totalAmount);
    booking.setUpdatedAt(OffsetDateTime.now());
    bookingRepository.save(booking);
    return new BookingTotalBreakdown(
        normalizeMoney(fareTotal), serviceTotal, seatSurchargeTotal, totalAmount);
  }

  private BigDecimal currentServiceTotal(Long bookingId) {
    return normalizeMoney(bookingServiceRepository.sumPriceByBookingId(bookingId));
  }

  private BigDecimal currentSeatSurchargeTotal(Long bookingId) {
    return normalizeMoney(
        passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(bookingId));
  }

  private BigDecimal normalizeMoney(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  public record BookingTotalBreakdown(
      BigDecimal fareTotal,
      BigDecimal ancillaryTotal,
      BigDecimal seatSurchargeTotal,
      BigDecimal totalAmount) {}
}
