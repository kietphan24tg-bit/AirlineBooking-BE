package com.example.moduleweb.application.booking;

import com.example.modulebooking.application.PassengerServiceSelectionCommand;
import com.example.modulebooking.application.PassengerServiceSelectionView;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.ServiceOptionRepository;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.moduleweb.controller.booking.dto.PassengerServiceSelectionRequest;
import com.example.moduleweb.controller.booking.dto.PassengerServiceSelectionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("webBookingAncillarySelectionService")
public class BookingAncillarySelectionService {

  private final com.example.modulebooking.application.BookingAncillarySelectionService delegate;

  @Autowired
  public BookingAncillarySelectionService(
      com.example.modulebooking.application.BookingAncillarySelectionService delegate) {
    this.delegate = delegate;
  }

  public BookingAncillarySelectionService(
      BookingRepository bookingRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerRepository passengerRepository,
      ServiceOptionRepository serviceOptionRepository,
      BookingServiceRepository bookingServiceRepository,
      PaymentRepository paymentRepository,
      BookingTotalRecalculationService bookingTotalRecalculationService) {
    this(
        new com.example.modulebooking.application.BookingAncillarySelectionService(
            bookingRepository,
            bookingFareHoldRepository,
            passengerRepository,
            serviceOptionRepository,
            bookingServiceRepository,
            bookingId -> paymentRepository.existsByBookingIdAndPaymentStatus(bookingId, "PENDING"),
            bookingTotalRecalculationService.toModuleBookingService()));
  }

  public PassengerServiceSelectionResponse updateServices(
      Long userId, Long bookingId, PassengerServiceSelectionRequest request) {
    return toResponse(
        delegate.updateServices(
            userId,
            bookingId,
            new PassengerServiceSelectionCommand(
                request.passengers().stream()
                    .map(
                        passenger ->
                            new PassengerServiceSelectionCommand.PassengerServicesCommand(
                                passenger.passengerId(),
                                passenger.selections().stream()
                                    .map(
                                        selection ->
                                            new PassengerServiceSelectionCommand
                                                .ServiceSelectionCommand(
                                                selection.optionId(), selection.quantity()))
                                    .toList()))
                    .toList())));
  }

  private PassengerServiceSelectionResponse toResponse(PassengerServiceSelectionView view) {
    return new PassengerServiceSelectionResponse(
        view.bookingId(),
        view.ancillaryTotal(),
        view.totalAmount(),
        view.currency(),
        view.passengers().stream()
            .map(
                passenger ->
                    new PassengerServiceSelectionResponse.PassengerServicesResponse(
                        passenger.passengerId(),
                        passenger.selections().stream()
                            .map(
                                selection ->
                                    new PassengerServiceSelectionResponse.ServiceSelectionResponse(
                                        selection.serviceId(),
                                        selection.serviceType(),
                                        selection.serviceName(),
                                        selection.optionId(),
                                        selection.optionName(),
                                        selection.quantity(),
                                        selection.price()))
                            .toList()))
            .toList());
  }
}
