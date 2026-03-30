package com.example.modulebooking.exception;

import com.example.common.exception.AppException;
import java.util.Map;

public class BookingNotFoundException extends AppException {

  public BookingNotFoundException(Long bookingId) {
    super(
        "BOOKING_NOT_FOUND",
        "Booking not found",
        404,
        Map.of("bookingId", bookingId));
  }

  public BookingNotFoundException(String pnrCode) {
    super(
        "BOOKING_NOT_FOUND",
        "Booking not found",
        404,
        Map.of("pnrCode", pnrCode));
  }
}
