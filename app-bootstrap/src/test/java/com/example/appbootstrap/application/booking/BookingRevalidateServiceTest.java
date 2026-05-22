package com.example.appbootstrap.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.controller.booking.dto.CreateBookingRequest;
import com.example.appbootstrap.controller.booking.dto.CreateBookingResponse;
import com.example.appbootstrap.controller.booking.dto.PassengerSummaryRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareRequest;
import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.PassengerEntity;
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
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.repository.FlightFareRepository;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class BookingRevalidateServiceTest {

  @Mock private FlightFareRepository flightFareRepository;
  @Mock private FlightPricingService flightPricingService;
  @Mock private PromotionPreviewService promotionPreviewService;
  @Mock private CountryCurrencyResolver countryCurrencyResolver;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private UserRepository userRepository;
  @Mock private BookingRepository bookingRepository;
  @Mock private BookingContactRepository bookingContactRepository;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private PassengerRepository passengerRepository;
  @Mock private PromotionRepository promotionRepository;
  @Mock private FlightSeatRepository flightSeatRepository;
  @Mock private FlightInventoryAvailabilityService flightInventoryAvailabilityService;
  @Mock private PasswordEncoder passwordEncoder;

  private BookingRevalidateService service;
  private FlightFareEntity fare;

  @BeforeEach
  void setUp() {
    service =
        new BookingRevalidateService(
            flightFareRepository,
            flightPricingService,
            promotionPreviewService,
            countryCurrencyResolver,
            redisTemplate,
            new ObjectMapper().findAndRegisterModules(),
            userRepository,
            bookingRepository,
            bookingContactRepository,
            bookingFareHoldRepository,
            passengerRepository,
            promotionRepository,
            flightSeatRepository,
            flightInventoryAvailabilityService,
            passwordEncoder);
    fare = fare();
  }

  @Test
  void createBooking_savesContactsPassengersAndReturnsFullSummary() {
    var revalidated = stubRevalidate(new PassengerSummaryRequest(1, 0, 0), 1L, "1500000.00");
    stubBookingPersistence(revalidated.revalidateId(), "idem-1", 123L, 1);

    CreateBookingResponse response =
        service.createBooking(
            7L,
            new CreateBookingRequest(
                revalidated.revalidateId(),
                "idem-1",
                new PassengerSummaryRequest(1, 0, 0),
                List.of(
                    new CreateBookingRequest.BookingContactRequest(
                        "USER@EXAMPLE.COM", "0900000000", true)),
                List.of(
                    new CreateBookingRequest.BookingPassengerRequest(
                        "AN",
                        "NGUYEN",
                        LocalDate.parse("1998-01-01"),
                        "male",
                        "VN",
                        "adult",
                        "passport",
                        "B1234567",
                        LocalDate.parse("2030-01-01")))));

    assertEquals(123L, response.booking().bookingId());
    assertEquals("PENDING_PAYMENT", response.booking().status());
    assertEquals(11L, response.flight().flightId());
    assertEquals("VN123", response.flight().flightNumber());
    assertEquals(101L, response.fare().fareId());
    assertEquals("AVAILABLE", response.fare().status());
    assertEquals(0, response.pricing().originalTotalPrice().compareTo(new BigDecimal("1500000")));
    assertEquals("user@example.com", response.contacts().getFirst().email());
    assertEquals(10L, response.passengers().getFirst().passengerId());
    assertEquals("ADULT", response.passengers().getFirst().passengerType());
    assertEquals("HELD", response.seatReservation().status());
    assertEquals(1, response.seatReservation().reservedSeatCount());
    assertEquals("CREATE_PAYMENT", response.nextAction().type());
    verify(flightSeatRepository, never())
        .reserveForBooking(anyLong(), anyLong(), anyLong(), anyInt(), any());
  }

  @Test
  void createBooking_childAndInfantApplyPricingRulesAndReserveOnlySeatedPassengers() {
    PassengerSummaryRequest summary = new PassengerSummaryRequest(1, 1, 1);
    var revalidated = stubRevalidate(summary, 2L, "2775000.00");
    stubBookingPersistence(revalidated.revalidateId(), "idem-2", 124L, 2);

    CreateBookingResponse response =
        service.createBooking(
            7L,
            new CreateBookingRequest(
                revalidated.revalidateId(),
                "idem-2",
                summary,
                List.of(
                    new CreateBookingRequest.BookingContactRequest(
                        "USER@EXAMPLE.COM", "0900000000", true)),
                List.of(
                    new CreateBookingRequest.BookingPassengerRequest(
                        "AN",
                        "NGUYEN",
                        LocalDate.parse("1990-01-01"),
                        "male",
                        "VN",
                        "adult",
                        "passport",
                        "B1234567",
                        LocalDate.parse("2030-01-01")),
                    new CreateBookingRequest.BookingPassengerRequest(
                        "BINH",
                        "NGUYEN",
                        LocalDate.parse("2018-05-01"),
                        "female",
                        null,
                        "child",
                        null,
                        null,
                        null),
                    new CreateBookingRequest.BookingPassengerRequest(
                        "CUONG",
                        "NGUYEN",
                        LocalDate.parse("2025-06-01"),
                        "male",
                        null,
                        "infant",
                        null,
                        null,
                        null))));

    assertEquals(0, response.pricing().originalTotalPrice().compareTo(new BigDecimal("2775000")));
    assertEquals(2, response.seatReservation().reservedSeatCount());
    assertEquals("ADULT", response.passengers().get(0).passengerType());
    assertEquals("VN", response.passengers().get(0).nationality());
    assertEquals("PASSPORT", response.passengers().get(0).identifyType());
    assertEquals("CHILD", response.passengers().get(1).passengerType());
    assertNull(response.passengers().get(1).nationality());
    assertNull(response.passengers().get(1).identifyType());
    assertNull(response.passengers().get(1).identifyNumber());
    assertEquals("INFANT", response.passengers().get(2).passengerType());
    assertNull(response.passengers().get(2).nationality());
    assertNull(response.passengers().get(2).identifyType());
    assertNull(response.passengers().get(2).identifyNumber());
    verify(flightPricingService).calculateTotalPrice(new BigDecimal("1500000.00"), 1, 1, 1);
    verify(flightInventoryAvailabilityService, times(2)).countAvailableSeats(11L, 5L);
    verify(flightSeatRepository, never())
        .reserveForBooking(anyLong(), anyLong(), anyLong(), anyInt(), any());
  }

  @Test
  void createBooking_withoutAuthentication_createsGuestUserAndBooking() {
    var revalidated = stubRevalidate(new PassengerSummaryRequest(1, 0, 0), 1L, "1500000.00");
    stubRevalidateSnapshotAccess(revalidated.revalidateId(), "idem-guest");
    when(passwordEncoder.encode(any())).thenReturn("guest-password-hash");
    when(userRepository.save(any(UserEntity.class)))
        .thenAnswer(
            invocation -> {
              UserEntity user = invocation.getArgument(0, UserEntity.class);
              user.setId(99L);
              return user;
            });
    when(bookingRepository.saveAndFlush(any(BookingEntity.class)))
        .thenAnswer(
            invocation -> {
              BookingEntity booking = invocation.getArgument(0, BookingEntity.class);
              booking.setId(125L);
              return booking;
            });
    when(bookingContactRepository.saveAll(any()))
        .thenAnswer(
            invocation -> {
              List<BookingContactEntity> contacts = invocation.getArgument(0);
              for (int i = 0; i < contacts.size(); i++) {
                contacts.get(i).setId((long) i + 1);
              }
              return contacts;
            });
    when(passengerRepository.saveAll(any()))
        .thenAnswer(
            invocation -> {
              List<PassengerEntity> passengers = invocation.getArgument(0);
              for (int i = 0; i < passengers.size(); i++) {
                passengers.get(i).setId((long) i + 10);
              }
              return passengers;
            });
    when(bookingFareHoldRepository.save(any(BookingFareHoldEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, BookingFareHoldEntity.class));

    CreateBookingResponse response =
        service.createBooking(
            null,
            new CreateBookingRequest(
                revalidated.revalidateId(),
                "idem-guest",
                new PassengerSummaryRequest(1, 0, 0),
                List.of(
                    new CreateBookingRequest.BookingContactRequest(
                        "guest@example.com", "0900000001", true)),
                List.of(
                    new CreateBookingRequest.BookingPassengerRequest(
                        "AN",
                        "NGUYEN",
                        LocalDate.parse("1998-01-01"),
                        "male",
                        "VN",
                        "adult",
                        "passport",
                        "B1234567",
                        LocalDate.parse("2030-01-01")))));

    assertEquals(125L, response.booking().bookingId());
    verify(userRepository).save(any(UserEntity.class));
  }

  @Test
  void createBooking_rejectsAdultMissingRequiredDocuments() {
    PassengerSummaryRequest summary = new PassengerSummaryRequest(1, 0, 0);
    var revalidated = stubRevalidate(summary, 1L, "1500000.00");

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.createBooking(
                    7L,
                    new CreateBookingRequest(
                        revalidated.revalidateId(),
                        "idem-adult-docs",
                        summary,
                        List.of(
                            new CreateBookingRequest.BookingContactRequest(
                                "USER@EXAMPLE.COM", "0900000000", true)),
                        List.of(
                            new CreateBookingRequest.BookingPassengerRequest(
                                "AN",
                                "NGUYEN",
                                LocalDate.parse("1998-01-01"),
                                "male",
                                null,
                                "adult",
                                null,
                                null,
                                null)))));

    assertEquals("VALIDATION_FAILED", exception.getCode());
    assertEquals("nationality is required for ADULT", exception.getMessage());
    verify(flightSeatRepository, never())
        .reserveForBooking(anyLong(), anyLong(), anyLong(), anyInt(), any());
  }

  @Test
  void createBooking_rejectsChildYoungerThanTwo() {
    PassengerSummaryRequest summary = new PassengerSummaryRequest(1, 1, 0);
    var revalidated = stubRevalidate(summary, 2L, "2625000.00");
    stubRevalidateSnapshotAccess(revalidated.revalidateId(), "idem-child-age");

    AppException exception =
        assertThrows(
            AppException.class,
            () -> {
              CreateBookingRequest request =
                  new CreateBookingRequest(
                      revalidated.revalidateId(),
                      "idem-child-age",
                      summary,
                      List.of(
                          new CreateBookingRequest.BookingContactRequest(
                              "USER@EXAMPLE.COM", "0900000000", true)),
                      List.of(
                          new CreateBookingRequest.BookingPassengerRequest(
                              "AN",
                              "NGUYEN",
                              LocalDate.parse("1990-01-01"),
                              "male",
                              "VN",
                              "adult",
                              "passport",
                              "B1234567",
                              LocalDate.parse("2030-01-01")),
                          new CreateBookingRequest.BookingPassengerRequest(
                              "BINH",
                              "NGUYEN",
                              LocalDate.parse("2025-05-02"),
                              "female",
                              "VN",
                              "child",
                              "passport",
                              "C1234567",
                              LocalDate.parse("2030-01-01"))));
              service.createBooking(7L, request);
            });

    assertEquals("INVALID_PASSENGER_AGE", exception.getCode());
    verify(flightSeatRepository, never())
        .reserveForBooking(anyLong(), anyLong(), anyLong(), anyInt(), any());
  }

  @Test
  void createBooking_rejectsInfantAgedTwoOrOlder() {
    PassengerSummaryRequest summary = new PassengerSummaryRequest(1, 0, 1);
    var revalidated = stubRevalidate(summary, 1L, "1650000.00");
    stubRevalidateSnapshotAccess(revalidated.revalidateId(), "idem-infant-age");

    AppException exception =
        assertThrows(
            AppException.class,
            () -> {
              CreateBookingRequest request =
                  new CreateBookingRequest(
                      revalidated.revalidateId(),
                      "idem-infant-age",
                      summary,
                      List.of(
                          new CreateBookingRequest.BookingContactRequest(
                              "USER@EXAMPLE.COM", "0900000000", true)),
                      List.of(
                          new CreateBookingRequest.BookingPassengerRequest(
                              "AN",
                              "NGUYEN",
                              LocalDate.parse("1990-01-01"),
                              "male",
                              "VN",
                              "adult",
                              "passport",
                              "B1234567",
                              LocalDate.parse("2030-01-01")),
                          new CreateBookingRequest.BookingPassengerRequest(
                              "CUONG",
                              "NGUYEN",
                              LocalDate.parse("2024-05-01"),
                              "male",
                              "VN",
                              "infant",
                              "passport",
                              "I1234567",
                              LocalDate.parse("2030-01-01"))));
              service.createBooking(7L, request);
            });

    assertEquals("INVALID_PASSENGER_AGE", exception.getCode());
    verify(flightSeatRepository, never())
        .reserveForBooking(anyLong(), anyLong(), anyLong(), anyInt(), any());
  }

  @Test
  void createBooking_rejectsAdultYoungerThanTwelve() {
    PassengerSummaryRequest summary = new PassengerSummaryRequest(1, 0, 0);
    var revalidated = stubRevalidate(summary, 1L, "1500000.00");
    stubRevalidateSnapshotAccess(revalidated.revalidateId(), "idem-adult-age");

    AppException exception =
        assertThrows(
            AppException.class,
            () -> {
              CreateBookingRequest request =
                  new CreateBookingRequest(
                      revalidated.revalidateId(),
                      "idem-adult-age",
                      summary,
                      List.of(
                          new CreateBookingRequest.BookingContactRequest(
                              "USER@EXAMPLE.COM", "0900000000", true)),
                      List.of(
                          new CreateBookingRequest.BookingPassengerRequest(
                              "AN",
                              "NGUYEN",
                              LocalDate.parse("2014-05-02"),
                              "male",
                              "VN",
                              "adult",
                              "passport",
                              "B1234567",
                              LocalDate.parse("2030-01-01"))));
              service.createBooking(7L, request);
            });

    assertEquals("INVALID_PASSENGER_AGE", exception.getCode());
    verify(flightSeatRepository, never())
        .reserveForBooking(anyLong(), anyLong(), anyLong(), anyInt(), any());
  }

  @Test
  void revalidate_normalizesAppliedPromoDiscountForVnd() {
    PassengerSummaryRequest summary = new PassengerSummaryRequest(1, 0, 0);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(flightFareRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(fare));
    when(flightInventoryAvailabilityService.countAvailableSeats(11L, 5L)).thenReturn(1L);
    when(flightPricingService.calculateTotalPrice(
            new BigDecimal("1500000.00"), summary.adult(), summary.child(), summary.infant()))
        .thenReturn(new BigDecimal("2808054.00"));
    when(countryCurrencyResolver.resolveCountryCode(fare)).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(promotionPreviewService.preview("SAVE10", new BigDecimal("2808054"), "VND", "VN", true))
        .thenReturn(
            new PromotionPreviewService.PromoPreview(
                "APPLIED",
                "Promo applied successfully",
                new BigDecimal("2808054"),
                new BigDecimal("2509248.60"),
                new BigDecimal("298805.40"),
                11));

    var response = service.revalidate(new RevalidateFareRequest(11L, 101L, "SAVE10", summary));

    assertEquals(new BigDecimal("298805"), response.pricing().discountAmount());
    assertEquals(new BigDecimal("2509249"), response.pricing().discountedTotalPrice());
  }

  private void stubBookingPersistence(
      String revalidateId, String idempotencyKey, long bookingId, int reservedSeatCount) {
    stubRevalidateSnapshotAccess(revalidateId, idempotencyKey);

    UserEntity user = new UserEntity();
    user.setId(7L);
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(bookingRepository.saveAndFlush(any(BookingEntity.class)))
        .thenAnswer(
            invocation -> {
              BookingEntity booking = invocation.getArgument(0, BookingEntity.class);
              booking.setId(bookingId);
              return booking;
            });
    when(bookingContactRepository.saveAll(any()))
        .thenAnswer(
            invocation -> {
              List<BookingContactEntity> contacts = invocation.getArgument(0);
              for (int i = 0; i < contacts.size(); i++) {
                contacts.get(i).setId((long) i + 1);
              }
              return contacts;
            });
    when(passengerRepository.saveAll(any()))
        .thenAnswer(
            invocation -> {
              List<PassengerEntity> passengers = invocation.getArgument(0);
              for (int i = 0; i < passengers.size(); i++) {
                passengers.get(i).setId((long) i + 10);
              }
              return passengers;
            });
    when(bookingFareHoldRepository.save(any(BookingFareHoldEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, BookingFareHoldEntity.class));
  }

  private void stubRevalidateSnapshotAccess(String revalidateId, String idempotencyKey) {
    ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations).set(eq("revalidate:" + revalidateId), snapshotCaptor.capture(), any());
    when(valueOperations.get("revalidate:" + revalidateId)).thenReturn(snapshotCaptor.getValue());
    when(valueOperations.setIfAbsent(eq("idem:booking:" + idempotencyKey), any(), any()))
        .thenReturn(true);
    when(flightFareRepository.findByIdWithDetailsForUpdate(101L)).thenReturn(Optional.of(fare));
  }

  private com.example.appbootstrap.controller.booking.dto.RevalidateFareResponse stubRevalidate(
      PassengerSummaryRequest summary, long availableSeats, String originalTotalPrice) {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(flightFareRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(fare));
    when(flightInventoryAvailabilityService.countAvailableSeats(11L, 5L))
        .thenReturn(availableSeats);
    when(flightPricingService.calculateTotalPrice(
            new BigDecimal("1500000.00"), summary.adult(), summary.child(), summary.infant()))
        .thenReturn(new BigDecimal(originalTotalPrice));
    when(countryCurrencyResolver.resolveCountryCode(fare)).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    BigDecimal expectedOriginalTotal = new BigDecimal(originalTotalPrice);
    when(promotionPreviewService.preview(
            eq(null),
            argThat(amount -> amount != null && amount.compareTo(expectedOriginalTotal) == 0),
            eq("VND"),
            eq("VN"),
            eq(true)))
        .thenReturn(
            PromotionPreviewService.PromoPreview.notApplicable(
                "No promo code provided", new BigDecimal(originalTotalPrice)));
    return service.revalidate(new RevalidateFareRequest(11L, 101L, null, summary));
  }

  private static FlightFareEntity fare() {
    AirlineEntity airline = new AirlineEntity();
    airline.setId(1L);
    airline.setCode("VN");
    airline.setName("Vietnam Airlines");
    airline.setLogoUrl("https://example.com/vn.png");

    AirportEntity departure = new AirportEntity();
    departure.setId(1L);
    departure.setCode("SGN");
    departure.setName("Tan Son Nhat International Airport");
    departure.setCity("Ho Chi Minh City");
    departure.setCountry("Vietnam");
    departure.setTimeZone("Asia/Ho_Chi_Minh");

    AirportEntity arrival = new AirportEntity();
    arrival.setId(2L);
    arrival.setCode("HAN");
    arrival.setName("Noi Bai International Airport");
    arrival.setCity("Hanoi");
    arrival.setCountry("Vietnam");
    arrival.setTimeZone("Asia/Ho_Chi_Minh");

    FlightEntity flight = new FlightEntity();
    flight.setId(11L);
    flight.setAirline(airline);
    flight.setFlightNumber("VN123");
    flight.setFlightDate(LocalDate.parse("2026-05-01"));
    flight.setDepartureAirport(departure);
    flight.setArrivalAirport(arrival);
    flight.setDepartureTerminal("T1");
    flight.setDepartureGate("A1");
    flight.setArrivalTerminal("T2");
    flight.setArrivalGate("B1");
    flight.setDepartureTime(OffsetDateTime.parse("2026-05-01T08:00:00+07:00"));
    flight.setArrivalTime(OffsetDateTime.parse("2026-05-01T10:10:00+07:00"));
    flight.setStatus("SCHEDULED");

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setId(5L);
    fareClass.setCode("ECONOMY");
    fareClass.setName("Economy");
    fareClass.setCabinClass("ECONOMY");
    fareClass.setBaggageCheckinKg(23);
    fareClass.setBaggageHandKg(7);
    fareClass.setRefundable(false);
    fareClass.setChangeable(true);
    fareClass.setChangeFee(new BigDecimal("300000.00"));
    fareClass.setRefundFee(new BigDecimal("500000.00"));

    FlightFareEntity fare = new FlightFareEntity();
    fare.setId(101L);
    fare.setFlight(flight);
    fare.setFareClass(fareClass);
    fare.setBasePrice(new BigDecimal("1200000.00"));
    fare.setTotalTaxAmount(new BigDecimal("300000.00"));
    fare.setTotalPrice(new BigDecimal("1500000.00"));
    fare.setStatus("AVAILABLE");
    fare.setUpdatedAt(OffsetDateTime.parse("2026-04-17T09:00:00+07:00"));
    return fare;
  }
}
