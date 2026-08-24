package com.example.moduleweb.application.booking;

import com.example.modulebooking.application.CreateBookingCommand;
import com.example.modulebooking.application.PassengerSummaryCommand;
import com.example.modulebooking.application.RevalidateFareCommand;
import com.example.modulebooking.application.RevalidateItineraryCommand;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PromotionRepository;
import com.example.moduleflight.application.CountryCurrencyResolver;
import com.example.moduleflight.application.FlightInventoryAvailabilityService;
import com.example.moduleflight.application.FlightPricingService;
import com.example.moduleflight.application.PromotionPreviewService;
import com.example.moduleflight.repository.FlightFareRepository;
import com.example.moduleidentity.repository.UserRepository;
import com.example.moduleweb.controller.booking.dto.CreateBookingRequest;
import com.example.moduleweb.controller.booking.dto.CreateBookingResponse;
import com.example.moduleweb.controller.booking.dto.PassengerSummaryRequest;
import com.example.moduleweb.controller.booking.dto.RevalidateFareRequest;
import com.example.moduleweb.controller.booking.dto.RevalidateFareResponse;
import com.example.moduleweb.controller.booking.dto.RevalidateItineraryRequest;
import com.example.moduleweb.controller.booking.dto.RevalidateItineraryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service("webBookingRevalidateService")
public class BookingRevalidateService {

  private final com.example.modulebooking.application.BookingRevalidateService delegate;

  @Autowired
  public BookingRevalidateService(
      com.example.modulebooking.application.BookingRevalidateService delegate) {
    this.delegate = delegate;
  }

  public BookingRevalidateService(
      FlightFareRepository flightFareRepository,
      FlightPricingService flightPricingService,
      PromotionPreviewService promotionPreviewService,
      CountryCurrencyResolver countryCurrencyResolver,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      UserRepository userRepository,
      BookingRepository bookingRepository,
      BookingContactRepository bookingContactRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerRepository passengerRepository,
      PromotionRepository promotionRepository,
      FlightSeatRepository flightSeatRepository,
      FlightInventoryAvailabilityService flightInventoryAvailabilityService,
      PasswordEncoder passwordEncoder) {
    this(
        new com.example.modulebooking.application.BookingRevalidateService(
            flightFareRepository,
            flightPricingService,
            promotionPreviewService,
            countryCurrencyResolver,
            redisTemplate,
            objectMapper,
            userRepository,
            bookingRepository,
            bookingContactRepository,
            bookingFareHoldRepository,
            passengerRepository,
            promotionRepository,
            flightSeatRepository,
            flightInventoryAvailabilityService,
            passwordEncoder));
  }

  public RevalidateFareResponse revalidate(RevalidateFareRequest request) {
    return RevalidateFareResponse.from(delegate.revalidate(toCommand(request)));
  }

  public RevalidateItineraryResponse revalidateItinerary(RevalidateItineraryRequest request) {
    return RevalidateItineraryResponse.from(delegate.revalidateItinerary(toCommand(request)));
  }

  public CreateBookingResponse createBooking(Long userId, CreateBookingRequest request) {
    return CreateBookingResponse.from(delegate.createBooking(userId, toCommand(request)));
  }

  private RevalidateFareCommand toCommand(RevalidateFareRequest request) {
    return new RevalidateFareCommand(
        request.flightId(),
        request.fareId(),
        request.promoCode(),
        toCommand(request.passengerSummary()));
  }

  private RevalidateItineraryCommand toCommand(RevalidateItineraryRequest request) {
    return new RevalidateItineraryCommand(
        request.tripType(),
        request.promoCode(),
        toCommand(request.passengerSummary()),
        request.segments().stream()
            .map(
                segment ->
                    new RevalidateItineraryCommand.SegmentCommand(
                        segment.direction(), segment.flightId(), segment.fareId()))
            .toList());
  }

  private CreateBookingCommand toCommand(CreateBookingRequest request) {
    return new CreateBookingCommand(
        request.revalidateId(),
        request.idempotencyKey(),
        toCommand(request.passengerSummary()),
        request.contacts().stream()
            .map(
                contact ->
                    new CreateBookingCommand.ContactCommand(
                        contact.email(), contact.phone(), contact.primary()))
            .toList(),
        request.passengers().stream()
            .map(
                passenger ->
                    new CreateBookingCommand.PassengerCommand(
                        passenger.firstName(),
                        passenger.lastName(),
                        passenger.dateOfBirth(),
                        passenger.gender(),
                        passenger.nationality(),
                        passenger.passengerType(),
                        passenger.identifyType(),
                        passenger.identifyNumber(),
                        passenger.expiryDate()))
            .toList());
  }

  private PassengerSummaryCommand toCommand(PassengerSummaryRequest request) {
    return new PassengerSummaryCommand(request.adult(), request.child(), request.infant());
  }
}
