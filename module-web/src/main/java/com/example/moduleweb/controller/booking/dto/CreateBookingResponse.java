package com.example.moduleweb.controller.booking.dto;

import com.example.modulebooking.application.CreateBookingView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateBookingResponse(
    BookingResponse booking,
    FlightResponse flight,
    FareResponse fare,
    PricingResponse pricing,
    PromotionResponse promotion,
    List<ContactResponse> contacts,
    PassengerSummaryResponse passengerSummary,
    List<PassengerResponse> passengers,
    SeatReservationResponse seatReservation,
    NextActionResponse nextAction) {

  public static CreateBookingResponse from(CreateBookingView view) {
    return new CreateBookingResponse(
        new BookingResponse(
            view.booking().bookingId(),
            view.booking().pnrCode(),
            view.booking().status(),
            view.booking().expiresAt(),
            view.booking().createdAt()),
        new FlightResponse(
            view.flight().flightId(),
            view.flight().flightNumber(),
            view.flight().flightDate(),
            view.flight().status(),
            new AirlineResponse(
                view.flight().airline().airlineId(),
                view.flight().airline().code(),
                view.flight().airline().name(),
                view.flight().airline().logoUrl()),
            new FlightEndpointResponse(
                view.flight().departure().airportId(),
                view.flight().departure().code(),
                view.flight().departure().name(),
                view.flight().departure().city(),
                view.flight().departure().country(),
                view.flight().departure().timeZone(),
                view.flight().departure().terminal(),
                view.flight().departure().gate(),
                view.flight().departure().time()),
            new FlightEndpointResponse(
                view.flight().arrival().airportId(),
                view.flight().arrival().code(),
                view.flight().arrival().name(),
                view.flight().arrival().city(),
                view.flight().arrival().country(),
                view.flight().arrival().timeZone(),
                view.flight().arrival().terminal(),
                view.flight().arrival().gate(),
                view.flight().arrival().time())),
        new FareResponse(
            view.fare().fareId(),
            view.fare().fareClassId(),
            view.fare().status(),
            view.fare().code(),
            view.fare().name(),
            view.fare().cabinClass(),
            view.fare().basePrice(),
            view.fare().taxes(),
            view.fare().totalPrice(),
            view.fare().currency(),
            view.fare().baggageCheckinKg(),
            view.fare().baggageHandKg(),
            view.fare().isRefundable(),
            view.fare().isChangeable(),
            view.fare().changeFee(),
            view.fare().refundFee()),
        new PricingResponse(
            view.pricing().currency(),
            view.pricing().baseFare(),
            view.pricing().taxes(),
            view.pricing().fees(),
            view.pricing().originalTotalPrice(),
            view.pricing().discountAmount(),
            view.pricing().discountPercent(),
            view.pricing().discountedTotalPrice()),
        new PromotionResponse(
            view.promotion().promoCode(),
            view.promotion().promotionId(),
            view.promotion().discountAmount(),
            view.promotion().discountPercent()),
        view.contacts().stream()
            .map(
                contact ->
                    new ContactResponse(
                        contact.contactId(), contact.email(), contact.phone(), contact.primary()))
            .toList(),
        new PassengerSummaryResponse(
            view.passengerSummary().adult(),
            view.passengerSummary().child(),
            view.passengerSummary().infant()),
        view.passengers().stream()
            .map(
                passenger ->
                    new PassengerResponse(
                        passenger.passengerId(),
                        passenger.firstName(),
                        passenger.lastName(),
                        passenger.dateOfBirth(),
                        passenger.gender(),
                        passenger.nationality(),
                        passenger.passengerType(),
                        passenger.identifyType(),
                        passenger.identifyNumber(),
                        passenger.expiryDate()))
            .toList(),
        new SeatReservationResponse(
            view.seatReservation().reservedSeatCount(),
            view.seatReservation().status(),
            view.seatReservation().reservedUntil()),
        new NextActionResponse(view.nextAction().type(), view.nextAction().endpoint()));
  }

  public record BookingResponse(
      Long bookingId,
      String pnrCode,
      String status,
      OffsetDateTime expiresAt,
      OffsetDateTime createdAt) {}

  public record FlightResponse(
      Long flightId,
      String flightNumber,
      LocalDate flightDate,
      String status,
      AirlineResponse airline,
      FlightEndpointResponse departure,
      FlightEndpointResponse arrival) {}

  public record AirlineResponse(Long airlineId, String code, String name, String logoUrl) {}

  public record FlightEndpointResponse(
      Long airportId,
      String code,
      String name,
      String city,
      String country,
      String timeZone,
      String terminal,
      String gate,
      OffsetDateTime time) {}

  public record FareResponse(
      Long fareId,
      Long fareClassId,
      String status,
      String code,
      String name,
      String cabinClass,
      BigDecimal basePrice,
      BigDecimal taxes,
      BigDecimal totalPrice,
      String currency,
      Integer baggageCheckinKg,
      Integer baggageHandKg,
      boolean isRefundable,
      boolean isChangeable,
      BigDecimal changeFee,
      BigDecimal refundFee) {}

  public record PricingResponse(
      String currency,
      BigDecimal baseFare,
      BigDecimal taxes,
      BigDecimal fees,
      BigDecimal originalTotalPrice,
      BigDecimal discountAmount,
      Integer discountPercent,
      BigDecimal discountedTotalPrice) {}

  public record PromotionResponse(
      String promoCode, Long promotionId, BigDecimal discountAmount, Integer discountPercent) {}

  public record ContactResponse(Long contactId, String email, String phone, boolean primary) {}

  public record PassengerSummaryResponse(int adult, int child, int infant) {}

  public record PassengerResponse(
      Long passengerId,
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      String gender,
      String nationality,
      String passengerType,
      String identifyType,
      String identifyNumber,
      LocalDate expiryDate) {}

  public record SeatReservationResponse(
      int reservedSeatCount, String status, OffsetDateTime reservedUntil) {}

  public record NextActionResponse(String type, String endpoint) {}
}
