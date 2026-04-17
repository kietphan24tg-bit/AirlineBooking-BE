package com.example.modulescheduler;

import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
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
  private final FlightSeatRepository flightSeatRepository;

  public BookingExpirationScheduler(
      BookingRepository bookingRepository, FlightSeatRepository flightSeatRepository) {
    this.bookingRepository = bookingRepository;
    this.flightSeatRepository = flightSeatRepository;
  }

  @Scheduled(cron = "${app.booking-expiration.cron:0 * * * * *}")
  @Transactional
  public void expirePendingBookings() {
    OffsetDateTime now = OffsetDateTime.now();
    int releasedSeats = flightSeatRepository.releaseExpiredReservations(now);
    int expiredBookings = bookingRepository.expirePendingPaymentBookings(now);
    if (releasedSeats > 0 || expiredBookings > 0) {
      LOG.info(
          "booking expiration completed expiredBookings={} releasedSeats={}",
          expiredBookings,
          releasedSeats);
    }
  }
}
