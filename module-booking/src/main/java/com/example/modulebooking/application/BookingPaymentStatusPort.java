package com.example.modulebooking.application;

public interface BookingPaymentStatusPort {

  boolean hasPendingBookingPayment(Long bookingId);
}
