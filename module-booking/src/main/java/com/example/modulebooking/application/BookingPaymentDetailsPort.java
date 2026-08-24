package com.example.modulebooking.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

public interface BookingPaymentDetailsPort {

  BookingPaymentSummary findLatestBookingLevelPayment(Long bookingId);

  Map<Long, BookingPaymentSummary> findLatestBookingLevelPayments(Collection<Long> bookingIds);

  record BookingPaymentSummary(
      String paymentStatus,
      String paymentMethod,
      String transactionId,
      BigDecimal amount,
      String currency) {}
}
