package com.example.modulepayment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulepayment.repository.FlightChangeSettlementRepository;
import com.example.modulepayment.repository.SeatChangeSettlementRepository;
import com.example.modulepayment.repository.ServiceChangeSettlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingChangePaymentGuardTest {

  @Mock private SeatChangeSettlementRepository seatChangeSettlementRepository;
  @Mock private ServiceChangeSettlementRepository serviceChangeSettlementRepository;
  @Mock private FlightChangeSettlementRepository flightChangeSettlementRepository;

  @Test
  void shouldAllowChangeWhenNoActiveSettlementPaymentExists() {
    BookingChangePaymentGuard guard =
        new BookingChangePaymentGuard(
            seatChangeSettlementRepository,
            serviceChangeSettlementRepository,
            flightChangeSettlementRepository);

    guard.assertNoActiveChangePayment(42L);

    verify(seatChangeSettlementRepository).existsByBookingIdAndStatusIn(eq(42L), anyList());
    verify(serviceChangeSettlementRepository).existsByBookingIdAndStatusIn(eq(42L), anyList());
    verify(flightChangeSettlementRepository).existsByBookingIdAndStatusIn(eq(42L), anyList());
  }

  @Test
  void shouldRejectChangeWhenActiveSettlementPaymentExists() {
    when(serviceChangeSettlementRepository.existsByBookingIdAndStatusIn(eq(42L), anyList()))
        .thenReturn(true);
    BookingChangePaymentGuard guard =
        new BookingChangePaymentGuard(
            seatChangeSettlementRepository,
            serviceChangeSettlementRepository,
            flightChangeSettlementRepository);

    AppException exception =
        assertThrows(AppException.class, () -> guard.assertNoActiveChangePayment(42L));

    assertEquals("BOOKING_CHANGE_PAYMENT_ALREADY_STARTED", exception.getCode());
  }
}
