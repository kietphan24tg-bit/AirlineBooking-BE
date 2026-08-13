package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulepayment.repository.FlightChangeSettlementRepository;
import com.example.modulepayment.repository.SeatChangeSettlementRepository;
import com.example.modulepayment.repository.ServiceChangeSettlementRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BookingChangePaymentGuard {

  private static final List<String> ACTIVE_SETTLEMENT_STATUSES =
      List.of("PAYMENT_REQUIRED", "PAYMENT_PENDING", "REFUND_PENDING");

  private final SeatChangeSettlementRepository seatChangeSettlementRepository;
  private final ServiceChangeSettlementRepository serviceChangeSettlementRepository;
  private final FlightChangeSettlementRepository flightChangeSettlementRepository;

  public BookingChangePaymentGuard(
      SeatChangeSettlementRepository seatChangeSettlementRepository,
      ServiceChangeSettlementRepository serviceChangeSettlementRepository,
      FlightChangeSettlementRepository flightChangeSettlementRepository) {
    this.seatChangeSettlementRepository = seatChangeSettlementRepository;
    this.serviceChangeSettlementRepository = serviceChangeSettlementRepository;
    this.flightChangeSettlementRepository = flightChangeSettlementRepository;
  }

  public void assertNoActiveChangePayment(Long bookingId) {
    if (seatChangeSettlementRepository.existsByBookingIdAndStatusIn(
            bookingId, ACTIVE_SETTLEMENT_STATUSES)
        || serviceChangeSettlementRepository.existsByBookingIdAndStatusIn(
            bookingId, ACTIVE_SETTLEMENT_STATUSES)
        || flightChangeSettlementRepository.existsByBookingIdAndStatusIn(
            bookingId, ACTIVE_SETTLEMENT_STATUSES)) {
      throw new AppException(
          "BOOKING_CHANGE_PAYMENT_ALREADY_STARTED",
          "Booking cannot be changed while another change payment is pending",
          409);
    }
  }
}
