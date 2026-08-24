package com.example.modulepayment.application;

import com.example.modulebooking.application.BookingPaymentDetailsPort;
import com.example.modulebooking.application.BookingPaymentStatusPort;
import com.example.modulepayment.repository.PaymentRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BookingPaymentStatusAdapter
    implements BookingPaymentStatusPort, BookingPaymentDetailsPort {

  private final PaymentRepository paymentRepository;

  public BookingPaymentStatusAdapter(PaymentRepository paymentRepository) {
    this.paymentRepository = paymentRepository;
  }

  @Override
  public boolean hasPendingBookingPayment(Long bookingId) {
    return paymentRepository.existsByBookingIdAndPaymentStatus(bookingId, "PENDING");
  }

  @Override
  public BookingPaymentSummary findLatestBookingLevelPayment(Long bookingId) {
    return paymentRepository.findBookingLevelPaymentsByBookingIdOrderByLatest(bookingId).stream()
        .findFirst()
        .map(
            payment ->
                new BookingPaymentSummary(
                    payment.getPaymentStatus(),
                    payment.getPaymentMethod(),
                    payment.getTransactionId(),
                    payment.getAmount(),
                    payment.getCurrency()))
        .orElse(null);
  }

  @Override
  public Map<Long, BookingPaymentSummary> findLatestBookingLevelPayments(
      Collection<Long> bookingIds) {
    if (bookingIds == null || bookingIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, BookingPaymentSummary> latest = new LinkedHashMap<>();
    paymentRepository
        .findBookingLevelPaymentsByBookingIdInOrderByLatest(bookingIds)
        .forEach(
            payment ->
                latest.putIfAbsent(
                    payment.getBooking().getId(),
                    new BookingPaymentSummary(
                        payment.getPaymentStatus(),
                        payment.getPaymentMethod(),
                        payment.getTransactionId(),
                        payment.getAmount(),
                        payment.getCurrency())));
    return latest;
  }
}
