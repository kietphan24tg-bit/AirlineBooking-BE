package com.example.modulescheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulepayment.application.StripeCheckoutExpirationService;
import com.example.modulepayment.repository.PaymentRepository;
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
  @Mock private StripeCheckoutExpirationService stripeCheckoutExpirationService;
  @Mock private PaymentRepository paymentRepository;

  @Test
  void expirePendingBookings_releasesSeatsAndExpiresBookings() {
    when(flightSeatRepository.releaseExpiredReservations(any())).thenReturn(2);
    when(bookingFareHoldRepository.releaseExpiredHolds(any())).thenReturn(1);
    when(passengerSeatAssignmentRepository.releaseExpiredReserved(any())).thenReturn(1);
    when(stripeCheckoutExpirationService.expireCheckoutSessionsPastBookingCap(any())).thenReturn(1);
    when(bookingRepository.expirePendingPaymentBookings(any())).thenReturn(1);
    when(paymentRepository.expirePendingPaymentsForExpiredBookings(any())).thenReturn(1);

    new BookingExpirationScheduler(
            bookingRepository,
            bookingFareHoldRepository,
            flightSeatRepository,
            passengerSeatAssignmentRepository,
            stripeCheckoutExpirationService,
            paymentRepository)
        .expirePendingBookings();

    verify(passengerSeatAssignmentRepository).releaseExpiredReserved(any());
    verify(flightSeatRepository).releaseExpiredReservations(any());
    verify(bookingFareHoldRepository).releaseExpiredHolds(any());
    verify(stripeCheckoutExpirationService).expireCheckoutSessionsPastBookingCap(any());
    verify(bookingRepository).expirePendingPaymentBookings(any());
    verify(paymentRepository).expirePendingPaymentsForExpiredBookings(any());
  }
}
