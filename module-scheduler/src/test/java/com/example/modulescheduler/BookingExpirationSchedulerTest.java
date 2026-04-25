package com.example.modulescheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingExpirationSchedulerTest {

  @Mock private BookingRepository bookingRepository;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private FlightSeatRepository flightSeatRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;

  @Test
  void expirePendingBookings_releasesSeatsAndExpiresBookings() {
    when(flightSeatRepository.releaseExpiredReservations(any())).thenReturn(2);
    when(bookingFareHoldRepository.releaseExpiredHolds(any())).thenReturn(1);
    when(passengerSeatAssignmentRepository.releaseExpiredReserved(any())).thenReturn(1);
    when(bookingRepository.expirePendingPaymentBookings(any())).thenReturn(1);

    new BookingExpirationScheduler(
            bookingRepository,
            bookingFareHoldRepository,
            flightSeatRepository,
            passengerSeatAssignmentRepository)
        .expirePendingBookings();

    verify(passengerSeatAssignmentRepository).releaseExpiredReserved(any());
    verify(flightSeatRepository).releaseExpiredReservations(any());
    verify(bookingFareHoldRepository).releaseExpiredHolds(any());
    verify(bookingRepository).expirePendingPaymentBookings(any());
  }
}
