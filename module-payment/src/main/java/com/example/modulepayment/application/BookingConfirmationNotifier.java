package com.example.modulepayment.application;

public interface BookingConfirmationNotifier {

  void sendIfNeeded(Long bookingId);
}
