package com.example.modulescheduler;

import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingExpirationScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(BookingExpirationScheduler.class);

  private final BookingRepository bookingRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final FlightSeatRepository flightSeatRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;

  public BookingExpirationScheduler(
      BookingRepository bookingRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      FlightSeatRepository flightSeatRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository) {
    this.bookingRepository = bookingRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.flightSeatRepository = flightSeatRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
  }

  @Scheduled(cron = "${app.booking-expiration.cron:0 * * * * *}")
  @Transactional
  public void expirePendingBookings() {
    OffsetDateTime now = OffsetDateTime.now();
    int releasedAssignments = passengerSeatAssignmentRepository.releaseExpiredReserved(now);
    int releasedSeats = flightSeatRepository.releaseExpiredReservations(now);
    int releasedHolds = bookingFareHoldRepository.releaseExpiredHolds(now);
    int expiredBookings = bookingRepository.expirePendingPaymentBookings(now);
    if (releasedSeats > 0 || releasedHolds > 0 || releasedAssignments > 0 || expiredBookings > 0) {
      LOG.info(
          "booking expiration completed expiredBookings={} releasedHolds={} releasedAssignments={} releasedSeats={}",
          expiredBookings,
          releasedHolds,
          releasedAssignments,
          releasedSeats);
    }
  }
}
