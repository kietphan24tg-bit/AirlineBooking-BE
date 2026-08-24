package com.example.modulepayment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.gateway.PaymentGateway;
import com.example.modulepayment.gateway.StripePaymentGateway;
import com.example.modulepayment.repository.BookingRepository;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentServiceIdempotencyTest {

  @Test
  void shouldReturnExistingPaymentWithoutCallingGatewayAgain() {
    BookingRepository bookingRepository = mock(BookingRepository.class);
    PaymentRepository paymentRepository = mock(PaymentRepository.class);
    PaymentGateway payosGateway = mock(PaymentGateway.class);
    StripePaymentGateway stripeGateway = mock(StripePaymentGateway.class);
    BookingEntity booking = booking();
    PaymentEntity existing = payment(booking);
    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findByRequestKeyWithBookingForUpdate("ai-proposal:123"))
        .thenReturn(Optional.of(existing));

    PaymentService service =
        new PaymentService(
            bookingRepository,
            paymentRepository,
            payosGateway,
            stripeGateway,
            paymentProperties(),
            mock(BookingFareHoldRepository.class),
            mock(FlightSeatRepository.class),
            mock(BookingServiceRepository.class),
            mock(PassengerSeatAssignmentRepository.class),
            mock(SeatChangeSettlementService.class),
            mock(ServiceChangeSettlementService.class),
            mock(FlightChangeSettlementService.class));

    CreatePaymentResult result =
        service.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS", "ai-proposal:123"));

    assertEquals(20L, result.payment().id());
    verify(payosGateway, never()).createPaymentLink(any());
    verify(stripeGateway, never()).createCheckoutSession(any());
    verify(paymentRepository, never()).saveAndFlush(any(PaymentEntity.class));
  }

  private BookingEntity booking() {
    OffsetDateTime now = OffsetDateTime.now();
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setPnrCode("ABC123");
    booking.setStatus("PENDING");
    booking.setCurrency("VND");
    booking.setTotalAmount(new BigDecimal("1200000"));
    booking.setDiscountAmount(BigDecimal.ZERO);
    booking.setContactEmail("user@example.com");
    booking.setContactPhone("0901234567");
    booking.setPnrExpiresAt(now.plusMinutes(15));
    booking.setCreatedAt(now);
    booking.setUpdatedAt(now);
    return booking;
  }

  private PaymentEntity payment(BookingEntity booking) {
    PaymentEntity payment = new PaymentEntity();
    payment.setId(20L);
    payment.setBooking(booking);
    payment.setAmount(booking.getTotalAmount());
    payment.setCurrency(booking.getCurrency());
    payment.setPaymentMethod("PAYOS");
    payment.setPaymentStatus("PENDING");
    payment.setType("QR");
    payment.setRequestKey("ai-proposal:123");
    payment.setSessionExpiresAt(booking.getPnrExpiresAt());
    return payment;
  }

  private PaymentProperties paymentProperties() {
    return new PaymentProperties(
        "payos",
        "webhook-secret",
        "http://localhost:3000/payment/success",
        "http://localhost:3000/payment/cancel",
        "http://localhost:8080",
        new PaymentProperties.Payos(
            "client-id", "api-key", "checksum-key", "https://api-merchant.payos.vn"),
        null);
  }
}
