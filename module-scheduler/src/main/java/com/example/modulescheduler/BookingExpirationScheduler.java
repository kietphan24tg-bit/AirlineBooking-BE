package com.example.modulescheduler;

import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
  private final NamedParameterJdbcTemplate jdbcTemplate;

  public BookingExpirationScheduler(
      BookingRepository bookingRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      FlightSeatRepository flightSeatRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      NamedParameterJdbcTemplate jdbcTemplate) {
    this.bookingRepository = bookingRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.flightSeatRepository = flightSeatRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Scheduled(cron = "${app.booking-expiration.cron:0 * * * * *}")
  @Transactional
  public void expirePendingBookings() {
    OffsetDateTime now = OffsetDateTime.now();
    int releasedAssignments = passengerSeatAssignmentRepository.releaseExpiredReserved(now);
    int releasedSeats = flightSeatRepository.releaseExpiredReservations(now);
    int releasedHolds = bookingFareHoldRepository.releaseExpiredHolds(now);
    int expiredBookings = bookingRepository.expirePendingPaymentBookings(now);
    int expiredPayments =
        jdbcTemplate.update(
            """
            update payments p
            set payment_status = 'EXPIRED',
                updated_at = :now
            from bookings b
            where p.booking_id = b.id
              and p.payment_status = 'PENDING'
              and (b.status = 'EXPIRED'
                or (b.pnr_expires_at is not null and b.pnr_expires_at < :now))
            """,
            Map.of("now", now));
    if (releasedSeats > 0
        || releasedHolds > 0
        || releasedAssignments > 0
        || expiredBookings > 0
        || expiredPayments > 0) {
      LOG.info(
          "booking expiration completed expiredBookings={} expiredPayments={} releasedHolds={} releasedAssignments={} releasedSeats={}",
          expiredBookings,
          expiredPayments,
          releasedHolds,
          releasedAssignments,
          releasedSeats);
    }
  }
}
