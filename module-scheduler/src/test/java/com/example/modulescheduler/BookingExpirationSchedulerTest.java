package com.example.modulescheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingExpirationSchedulerTest {

  @Mock private BookingRepository bookingRepository;
  @Mock private FlightSeatRepository flightSeatRepository;

  @Test
  void expirePendingBookings_releasesSeatsAndExpiresBookings() {
    when(flightSeatRepository.releaseExpiredReservations(any())).thenReturn(2);
    when(bookingRepository.expirePendingPaymentBookings(any())).thenReturn(1);

    new BookingExpirationScheduler(bookingRepository, flightSeatRepository).expirePendingBookings();

    verify(flightSeatRepository).releaseExpiredReservations(any());
    verify(bookingRepository).expirePendingPaymentBookings(any());
  }
}
