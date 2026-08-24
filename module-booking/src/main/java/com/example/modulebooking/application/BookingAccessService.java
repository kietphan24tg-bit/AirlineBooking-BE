package com.example.modulebooking.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.exception.BookingNotFoundException;
import com.example.modulebooking.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingAccessService {

  private final BookingRepository bookingRepository;

  public BookingAccessService(BookingRepository bookingRepository) {
    this.bookingRepository = bookingRepository;
  }

  @Transactional(readOnly = true)
  public Long requireOwnedBooking(Long userId, Long bookingId) {
    if (userId == null || bookingId == null) {
      throw new AppException("BOOKING_NOT_FOUND", "Booking not found", 404);
    }
    return bookingRepository
        .findByIdAndUserId(bookingId, userId)
        .map(booking -> booking.getId())
        .orElseThrow(() -> new BookingNotFoundException(bookingId));
  }
}
