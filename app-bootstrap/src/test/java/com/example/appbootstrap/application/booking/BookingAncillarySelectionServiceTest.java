package com.example.moduleweb.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleweb.controller.booking.dto.PassengerServiceSelectionRequest;
import com.example.moduleweb.controller.booking.dto.PassengerServiceSelectionResponse;
import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.BookingServiceEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.ServiceEntity;
import com.example.modulebooking.entity.ServiceOptionEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.ServiceOptionRepository;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingAncillarySelectionServiceTest {

  @Mock private BookingRepository bookingRepository;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private PassengerRepository passengerRepository;
  @Mock private ServiceOptionRepository serviceOptionRepository;
  @Mock private BookingServiceRepository bookingServiceRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  @Mock private PaymentRepository paymentRepository;

  private BookingAncillarySelectionService service;

  @BeforeEach
  void setUp() {
    service =
        new BookingAncillarySelectionService(
            bookingRepository,
            bookingFareHoldRepository,
            passengerRepository,
            serviceOptionRepository,
            bookingServiceRepository,
            paymentRepository,
            new BookingTotalRecalculationService(
                bookingRepository, bookingServiceRepository, passengerSeatAssignmentRepository));
  }

  @Test
  void updateServices_replacesPassengerSelectionsAndRefreshesTotalAmount() {
    BookingEntity booking = booking("1000.00");
    PassengerEntity passenger = passenger(1L, booking);
    ServiceEntity baggage = serviceEntity(10L, "BAGGAGE", "Baggage Add-on");
    ServiceEntity meal = serviceEntity(20L, "MEAL", "Pre-order Meals");
    ServiceOptionEntity baggageOption = option(11L, baggage, "Extra 15kg", "200.00");
    ServiceOptionEntity mealOption = option(21L, meal, "Kids Meal", "50.00");
    ServiceOptionEntity secondMealOption = option(22L, meal, "Fruit Cup", "30.00");
    AtomicReference<List<BookingServiceEntity>> savedServices =
        new AtomicReference<>(new ArrayList<>());

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerRepository.findByIdAndBookingId(1L, 10L)).thenReturn(Optional.of(passenger));
    when(serviceOptionRepository.findActiveByIdsWithService(anyCollection()))
        .thenReturn(List.of(mealOption, secondMealOption, baggageOption));
    when(bookingServiceRepository.sumPriceByBookingId(10L))
        .thenReturn(new BigDecimal("100.00"), new BigDecimal("330.00"));
    when(bookingServiceRepository.saveAll(any()))
        .thenAnswer(
            invocation -> {
              Iterable<BookingServiceEntity> argument = invocation.getArgument(0);
              List<BookingServiceEntity> services =
                  StreamSupport.stream(argument.spliterator(), false).toList();
              savedServices.set(services);
              return services;
            });
    when(bookingServiceRepository.findAllByBookingId(10L))
        .thenAnswer(invocation -> savedServices.get());

    PassengerServiceSelectionResponse response =
        service.updateServices(
            7L,
            10L,
            new PassengerServiceSelectionRequest(
                List.of(
                    new PassengerServiceSelectionRequest.PassengerServicesRequest(
                        1L,
                        List.of(
                            new PassengerServiceSelectionRequest.ServiceSelectionRequest(11L, 1),
                            new PassengerServiceSelectionRequest.ServiceSelectionRequest(21L, 2),
                            new PassengerServiceSelectionRequest.ServiceSelectionRequest(
                                22L, 1))))));

    assertEquals(new BigDecimal("1230.00"), booking.getTotalAmount());
    assertEquals(new BigDecimal("330.00"), response.ancillaryTotal());
    assertEquals(new BigDecimal("1230.00"), response.totalAmount());
    assertEquals(1, response.passengers().size());
    assertEquals(3, response.passengers().getFirst().selections().size());
    verify(bookingServiceRepository).deleteByBookingIdAndPassengerIdIn(10L, List.of(1L));
    verify(bookingServiceRepository).flush();
    verify(bookingRepository).save(booking);
  }

  @Test
  void updateServices_preservesExistingSeatSurchargeWhenRefreshingTotalAmount() {
    BookingEntity booking = booking("1120.00");
    PassengerEntity passenger = passenger(1L, booking);
    ServiceEntity baggage = serviceEntity(10L, "BAGGAGE", "Baggage Add-on");
    ServiceOptionEntity baggageOption = option(11L, baggage, "Extra 15kg", "200.00");
    AtomicReference<List<BookingServiceEntity>> savedServices =
        new AtomicReference<>(new ArrayList<>());

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerRepository.findByIdAndBookingId(1L, 10L)).thenReturn(Optional.of(passenger));
    when(serviceOptionRepository.findActiveByIdsWithService(anyCollection()))
        .thenReturn(List.of(baggageOption));
    when(bookingServiceRepository.sumPriceByBookingId(10L))
        .thenReturn(new BigDecimal("100.00"), new BigDecimal("200.00"));
    when(passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(10L))
        .thenReturn(new BigDecimal("20.00"), new BigDecimal("20.00"));
    when(bookingServiceRepository.saveAll(any()))
        .thenAnswer(
            invocation -> {
              Iterable<BookingServiceEntity> argument = invocation.getArgument(0);
              List<BookingServiceEntity> services =
                  StreamSupport.stream(argument.spliterator(), false).toList();
              savedServices.set(services);
              return services;
            });
    when(bookingServiceRepository.findAllByBookingId(10L))
        .thenAnswer(invocation -> savedServices.get());

    PassengerServiceSelectionResponse response =
        service.updateServices(
            7L,
            10L,
            new PassengerServiceSelectionRequest(
                List.of(
                    new PassengerServiceSelectionRequest.PassengerServicesRequest(
                        1L,
                        List.of(
                            new PassengerServiceSelectionRequest.ServiceSelectionRequest(
                                11L, 1))))));

    assertEquals(new BigDecimal("1220.00"), booking.getTotalAmount());
    assertEquals(new BigDecimal("200.00"), response.ancillaryTotal());
    assertEquals(new BigDecimal("1220.00"), response.totalAmount());
  }

  @Test
  void updateServices_rejectsPassengerOutsideBooking() {
    BookingEntity booking = booking("1000.00");
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerRepository.findByIdAndBookingId(99L, 10L)).thenReturn(Optional.empty());

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.updateServices(
                    7L,
                    10L,
                    new PassengerServiceSelectionRequest(
                        List.of(
                            new PassengerServiceSelectionRequest.PassengerServicesRequest(
                                99L,
                                List.of(
                                    new PassengerServiceSelectionRequest.ServiceSelectionRequest(
                                        11L, 1)))))));

    assertEquals("PASSENGER_NOT_FOUND", exception.getCode());
    verify(bookingServiceRepository, never()).deleteByBookingIdAndPassengerIdIn(any(), any());
  }

  @Test
  void updateServices_rejectsInactiveOrUnknownOption() {
    BookingEntity booking = booking("1000.00");
    PassengerEntity passenger = passenger(1L, booking);
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerRepository.findByIdAndBookingId(1L, 10L)).thenReturn(Optional.of(passenger));
    when(serviceOptionRepository.findActiveByIdsWithService(anyCollection())).thenReturn(List.of());

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.updateServices(
                    7L,
                    10L,
                    new PassengerServiceSelectionRequest(
                        List.of(
                            new PassengerServiceSelectionRequest.PassengerServicesRequest(
                                1L,
                                List.of(
                                    new PassengerServiceSelectionRequest.ServiceSelectionRequest(
                                        11L, 1)))))));

    assertEquals("SERVICE_OPTION_NOT_FOUND", exception.getCode());
  }

  @Test
  void updateServices_rejectsMultipleOptionsForSingleSelectionService() {
    BookingEntity booking = booking("1000.00");
    PassengerEntity passenger = passenger(1L, booking);
    ServiceEntity baggage = serviceEntity(10L, "BAGGAGE", "Baggage Add-on");
    ServiceOptionEntity firstOption = option(11L, baggage, "Extra 15kg", "200.00");
    ServiceOptionEntity secondOption = option(12L, baggage, "Extra 20kg", "250.00");
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerRepository.findByIdAndBookingId(1L, 10L)).thenReturn(Optional.of(passenger));
    when(serviceOptionRepository.findActiveByIdsWithService(anyCollection()))
        .thenReturn(List.of(secondOption, firstOption));

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.updateServices(
                    7L,
                    10L,
                    new PassengerServiceSelectionRequest(
                        List.of(
                            new PassengerServiceSelectionRequest.PassengerServicesRequest(
                                1L,
                                List.of(
                                    new PassengerServiceSelectionRequest.ServiceSelectionRequest(
                                        11L, 1),
                                    new PassengerServiceSelectionRequest.ServiceSelectionRequest(
                                        12L, 1)))))));

    assertEquals("SERVICE_SELECTION_LIMIT_EXCEEDED", exception.getCode());
  }

  @Test
  void updateServices_rejectsServiceAlreadyIncludedInFare() {
    BookingEntity booking = booking("1000.00");
    PassengerEntity passenger = passenger(1L, booking);
    ServiceEntity lounge = serviceEntity(10L, "LOUNGE", "Airport Lounge");
    ServiceOptionEntity loungeOption = option(11L, lounge, "Domestic Lounge Pass", "350.00");
    BookingFareHoldEntity hold = hold(true, false);

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerRepository.findByIdAndBookingId(1L, 10L)).thenReturn(Optional.of(passenger));
    when(serviceOptionRepository.findActiveByIdsWithService(anyCollection()))
        .thenReturn(List.of(loungeOption));
    when(bookingFareHoldRepository.findFirstByBookingIdAndStatusIn(
            10L, List.of("HELD", "CONFIRMED")))
        .thenReturn(Optional.of(hold));

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.updateServices(
                    7L,
                    10L,
                    new PassengerServiceSelectionRequest(
                        List.of(
                            new PassengerServiceSelectionRequest.PassengerServicesRequest(
                                1L,
                                List.of(
                                    new PassengerServiceSelectionRequest.ServiceSelectionRequest(
                                        11L, 1)))))));

    assertEquals("SERVICE_INCLUDED_IN_FARE", exception.getCode());
    verify(bookingServiceRepository, never()).deleteByBookingIdAndPassengerIdIn(any(), any());
  }

  @Test
  void updateServices_rejectsPendingPayment() {
    BookingEntity booking = booking("1000.00");
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(true);

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.updateServices(
                    7L,
                    10L,
                    new PassengerServiceSelectionRequest(
                        List.of(
                            new PassengerServiceSelectionRequest.PassengerServicesRequest(
                                1L,
                                List.of(
                                    new PassengerServiceSelectionRequest.ServiceSelectionRequest(
                                        11L, 1)))))));

    assertEquals("BOOKING_PAYMENT_ALREADY_STARTED", exception.getCode());
    verify(bookingServiceRepository, never()).deleteByBookingIdAndPassengerIdIn(any(), any());
  }

  @Test
  void updateServices_rejectsInvalidQuantity() {
    BookingEntity booking = booking("1000.00");
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.updateServices(
                    7L,
                    10L,
                    new PassengerServiceSelectionRequest(
                        List.of(
                            new PassengerServiceSelectionRequest.PassengerServicesRequest(
                                1L,
                                List.of(
                                    new PassengerServiceSelectionRequest.ServiceSelectionRequest(
                                        11L, 0)))))));

    assertEquals("INVALID_SERVICE_QUANTITY", exception.getCode());
  }

  private static BookingEntity booking(String totalAmount) {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setStatus("PENDING_PAYMENT");
    booking.setPnrExpiresAt(OffsetDateTime.now().plusMinutes(10));
    booking.setTotalAmount(new BigDecimal(totalAmount));
    booking.setCurrency("VND");
    return booking;
  }

  private static PassengerEntity passenger(Long id, BookingEntity booking) {
    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(id);
    passenger.setBooking(booking);
    return passenger;
  }

  private static ServiceEntity serviceEntity(Long id, String type, String name) {
    ServiceEntity service = new ServiceEntity();
    service.setId(id);
    service.setType(type);
    service.setName(name);
    service.setActive(true);
    return service;
  }

  private static ServiceOptionEntity option(
      Long id, ServiceEntity service, String name, String price) {
    ServiceOptionEntity option = new ServiceOptionEntity();
    option.setId(id);
    option.setService(service);
    option.setName(name);
    option.setPrice(new BigDecimal(price));
    option.setActive(true);
    return option;
  }

  private static BookingFareHoldEntity hold(boolean loungeAccess, boolean priorityBoarding) {
    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setLoungeAccess(loungeAccess);
    fareClass.setPriorityBoarding(priorityBoarding);
    FlightFareEntity fare = new FlightFareEntity();
    fare.setFareClass(fareClass);
    BookingFareHoldEntity hold = new BookingFareHoldEntity();
    hold.setFlightFare(fare);
    return hold;
  }
}
