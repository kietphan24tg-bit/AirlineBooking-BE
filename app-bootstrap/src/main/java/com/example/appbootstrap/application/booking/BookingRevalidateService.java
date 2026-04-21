package com.example.appbootstrap.application.booking;

import com.example.appbootstrap.controller.booking.dto.CreateBookingRequest;
import com.example.appbootstrap.controller.booking.dto.CreateBookingResponse;
import com.example.appbootstrap.controller.booking.dto.PassengerSummaryRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareResponse;
import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PromotionEntity;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PromotionRepository;
import com.example.moduleflight.application.CountryCurrencyResolver;
import com.example.moduleflight.application.FlightInventoryAvailabilityService;
import com.example.moduleflight.application.FlightPricingService;
import com.example.moduleflight.application.PromotionPreviewService;
import com.example.moduleflight.application.PromotionPreviewService.PromoPreview;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.repository.FlightFareRepository;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BookingRevalidateService {

  private static final Logger LOG = LoggerFactory.getLogger(BookingRevalidateService.class);
  private static final Duration REVALIDATE_TTL = Duration.ofMinutes(15);
  private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(6);
  private static final int CHILD_MIN_AGE = 2;
  private static final int ADULT_MIN_AGE = 12;
  private static final BigDecimal CHILD_RATE = new BigDecimal("0.75");
  private static final BigDecimal INFANT_RATE = new BigDecimal("0.10");
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

  private final FlightFareRepository flightFareRepository;
  private final FlightPricingService flightPricingService;
  private final PromotionPreviewService promotionPreviewService;
  private final CountryCurrencyResolver countryCurrencyResolver;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final UserRepository userRepository;
  private final BookingRepository bookingRepository;
  private final BookingContactRepository bookingContactRepository;
  private final PassengerRepository passengerRepository;
  private final PromotionRepository promotionRepository;
  private final FlightSeatRepository flightSeatRepository;
  private final FlightInventoryAvailabilityService flightInventoryAvailabilityService;

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
      PassengerRepository passengerRepository,
      PromotionRepository promotionRepository,
      FlightSeatRepository flightSeatRepository,
      FlightInventoryAvailabilityService flightInventoryAvailabilityService) {
    this.flightFareRepository = flightFareRepository;
    this.flightPricingService = flightPricingService;
    this.promotionPreviewService = promotionPreviewService;
    this.countryCurrencyResolver = countryCurrencyResolver;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.userRepository = userRepository;
    this.bookingRepository = bookingRepository;
    this.bookingContactRepository = bookingContactRepository;
    this.passengerRepository = passengerRepository;
    this.promotionRepository = promotionRepository;
    this.flightSeatRepository = flightSeatRepository;
    this.flightInventoryAvailabilityService = flightInventoryAvailabilityService;
  }

  @Transactional(readOnly = true)
  public RevalidateFareResponse revalidate(RevalidateFareRequest request) {
    validatePassengerSummary(request.passengerSummary());
    FlightFareEntity fare = requireFareForFlight(request.fareId(), request.flightId());
    ensureSaleable(fare);

    int requiredSeats = request.passengerSummary().seatedPassengers();
    if (requiredSeats <= 0) {
      throw new AppException(
          "INVALID_PASSENGER_BREAKDOWN", "At least one seated passenger required", 400);
    }
    long availableSeats =
        flightInventoryAvailabilityService.countAvailableSeats(
            fare.getFlight().getId(), fare.getFareClass().getId());
    if (availableSeats < requiredSeats) {
      throw new AppException(
          "FARE_SOLD_OUT", "Selected fare does not have enough available seats", 409);
    }

    PricingBreakdown pricing = buildPricing(fare, request.passengerSummary());
    PromoPreview preview =
        applyPromotion(
            request.promoCode(),
            pricing.originalTotalPrice(),
            countryCurrencyResolver.resolveCurrency(
                countryCurrencyResolver.resolveCountryCode(fare)),
            countryCurrencyResolver.resolveCountryCode(fare));
    String normalizedPromoCode = normalizePromoCode(request.promoCode());
    Long promotionId = resolvePromotionId(normalizedPromoCode, preview);

    BigDecimal discountAmount =
        "APPLIED".equals(preview.status()) ? preview.discountAmount() : ZERO;
    Integer discountPercent = "APPLIED".equals(preview.status()) ? preview.discountPercent() : null;
    BigDecimal discountedTotal =
        pricing.originalTotalPrice().subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

    String revalidateId =
        "RV_"
            + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    RevalidateSnapshot snapshot =
        new RevalidateSnapshot(
            revalidateId,
            fare.getFlight().getId(),
            fare.getId(),
            fare.getFareClass().getId(),
            fareVersionOf(fare),
            normalizedPromoCode,
            promotionId,
            new PassengerSummary(
                request.passengerSummary().adult(),
                request.passengerSummary().child(),
                request.passengerSummary().infant()),
            new PricingSnapshot(
                pricing.currency(),
                pricing.baseFare(),
                pricing.taxes(),
                pricing.fees(),
                pricing.originalTotalPrice(),
                discountAmount,
                discountPercent,
                discountedTotal),
            OffsetDateTime.now().plus(REVALIDATE_TTL),
            "ACTIVE");
    saveRevalidateSnapshot(snapshot);
    LOG.info(
        "revalidate created id={} flightId={} fareId={} fareClassId={} promoCode={} promotionId={} expiresAt={}",
        snapshot.revalidateId(),
        snapshot.flightId(),
        snapshot.fareId(),
        snapshot.fareClassId(),
        snapshot.promoCode(),
        snapshot.promotionId(),
        snapshot.expiresAt());

    return new RevalidateFareResponse(
        snapshot.revalidateId(),
        REVALIDATE_TTL.toSeconds(),
        snapshot.flightId(),
        snapshot.fareId(),
        snapshot.promoCode(),
        new RevalidateFareResponse.PassengerSummaryResponse(
            snapshot.passengerSummary().adult(),
            snapshot.passengerSummary().child(),
            snapshot.passengerSummary().infant()),
        new RevalidateFareResponse.PricingResponse(
            snapshot.pricing().currency(),
            snapshot.pricing().baseFare(),
            snapshot.pricing().taxes(),
            snapshot.pricing().fees(),
            snapshot.pricing().originalTotalPrice(),
            snapshot.pricing().discountAmount(),
            snapshot.pricing().discountPercent(),
            snapshot.pricing().discountedTotalPrice()));
  }

  @Transactional
  public CreateBookingResponse createBooking(Long userId, CreateBookingRequest request) {
    validatePassengerSummary(request.passengerSummary());
    List<NormalizedContact> contacts = normalizeContacts(request.contacts());
    List<NormalizedPassenger> passengers = normalizePassengers(request.passengers());
    validatePassengersMatchSummary(passengers, request.passengerSummary());
    String revalidateId = request.revalidateId().trim();
    RevalidateSnapshot snapshot = loadRevalidateSnapshot(revalidateId);
    if (!"ACTIVE".equals(snapshot.status())) {
      throw new AppException("REVALIDATE_EXPIRED", "Revalidate session is no longer active", 410);
    }
    PassengerSummary canonical =
        new PassengerSummary(
            request.passengerSummary().adult(),
            request.passengerSummary().child(),
            request.passengerSummary().infant());
    if (!snapshot.passengerSummary().equals(canonical)) {
      throw new AppException(
          "REVALIDATE_MISMATCH", "Passenger summary does not match revalidate snapshot", 409);
    }

    String requestHash =
        hash(
            toJson(
                new CreateBookingFingerprint(
                    snapshot.revalidateId(),
                    snapshot.flightId(),
                    snapshot.fareId(),
                    canonical,
                    contacts,
                    passengers)));
    IdempotencyResult idempotencyResult = acquireIdempotency(request.idempotencyKey(), requestHash);
    if (idempotencyResult.replay() != null) {
      return idempotencyResult.replay();
    }

    try {
      FlightFareEntity fare = requireFareForFlight(snapshot.fareId(), snapshot.flightId());
      if (!Objects.equals(fareVersionOf(fare), snapshot.fareVersion())) {
        throw new AppException(
            "PRICE_CHANGED", "Fare changed after revalidation. Please revalidate again.", 409);
      }
      validatePassengerAges(passengers, fare.getFlight().getFlightDate());

      int requiredSeats = snapshot.passengerSummary().seatedPassengers();
      UserEntity user =
          userRepository
              .findById(userId)
              .orElseThrow(
                  () ->
                      new AppException("USER_NOT_FOUND", "Authenticated user does not exist", 404));
      BookingEntity booking = new BookingEntity();
      booking.setUser(user);
      booking.setPnrCode(generatePnrCode());
      PrimaryContactValues primaryContactValues = primaryContactValues(contacts);
      booking.setContactEmail(primaryContactValues.email());
      booking.setContactPhone(primaryContactValues.phone());
      booking.setTotalAmount(snapshot.pricing().discountedTotalPrice());
      booking.setCurrency(snapshot.pricing().currency());
      booking.setStatus("PENDING_PAYMENT");
      booking.setDiscountAmount(snapshot.pricing().discountAmount());
      booking.setPnrExpiresAt(snapshot.expiresAt());
      booking.setCreatedAt(OffsetDateTime.now());
      booking.setUpdatedAt(OffsetDateTime.now());
      if (snapshot.promotionId() != null) {
        PromotionEntity promotion =
            promotionRepository.findById(snapshot.promotionId()).orElse(null);
        booking.setPromotion(promotion);
      }
      bookingRepository.saveAndFlush(booking);

      List<BookingContactEntity> contactEntities = saveContacts(booking, contacts);
      List<PassengerEntity> passengerEntities = savePassengers(booking, passengers);

      int updated =
          flightSeatRepository.reserveForBooking(
              booking.getId(),
              snapshot.flightId(),
              snapshot.fareClassId(),
              requiredSeats,
              booking.getPnrExpiresAt());
      if (updated < requiredSeats) {
        throw new AppException("FARE_SOLD_OUT", "Selected fare is sold out during booking", 409);
      }

      deleteRevalidateSnapshot(snapshot.revalidateId());

      CreateBookingResponse response =
          toCreateBookingResponse(
              booking, fare, snapshot, contactEntities, passengerEntities, updated);
      markIdempotencySuccess(request.idempotencyKey(), requestHash, response);
      LOG.info(
          "booking created bookingId={} userId={} revalidateId={} fareId={} flightId={} seats={}",
          response.booking().bookingId(),
          userId,
          snapshot.revalidateId(),
          snapshot.fareId(),
          snapshot.flightId(),
          requiredSeats);
      return response;
    } catch (RuntimeException ex) {
      markIdempotencyFailed(request.idempotencyKey(), requestHash, ex.getMessage());
      LOG.warn(
          "booking create failed userId={} revalidateId={} reason={}",
          userId,
          request.revalidateId(),
          ex.getMessage());
      throw ex;
    }
  }

  private void validatePassengerSummary(PassengerSummaryRequest summary) {
    if (summary.adult() + summary.child() < 1) {
      throw new AppException(
          "INVALID_PASSENGER_BREAKDOWN", "At least one ADULT or CHILD is required", 400);
    }
    if (summary.infant() > summary.adult()) {
      throw new AppException("INVALID_PASSENGER_BREAKDOWN", "INFANT must not exceed ADULT", 400);
    }
    if (summary.totalPassengers() > 9) {
      throw new AppException(
          "INVALID_PASSENGER_BREAKDOWN", "Total passengers must not exceed 9", 400);
    }
  }

  private List<NormalizedContact> normalizeContacts(
      List<CreateBookingRequest.BookingContactRequest> contacts) {
    if (contacts == null || contacts.isEmpty()) {
      throw new AppException("CONTACT_REQUIRED", "At least one contact is required", 400);
    }
    List<NormalizedContact> normalized = new ArrayList<>(contacts.size());
    int primaryCount = 0;
    for (CreateBookingRequest.BookingContactRequest contact : contacts) {
      String email = normalizeEmail(contact.email());
      String phone = normalizeText(contact.phone());
      if (!StringUtils.hasText(email) && !StringUtils.hasText(phone)) {
        throw new AppException("CONTACT_REQUIRED", "Each contact must include email or phone", 400);
      }
      boolean primary = Boolean.TRUE.equals(contact.primary());
      if (primary) {
        primaryCount++;
      }
      normalized.add(new NormalizedContact(email, phone, primary));
    }
    if (primaryCount > 1) {
      throw new AppException("INVALID_CONTACTS", "Only one primary contact is allowed", 400);
    }
    if (primaryCount == 0) {
      NormalizedContact first = normalized.getFirst();
      normalized.set(0, new NormalizedContact(first.email(), first.phone(), true));
    }
    return List.copyOf(normalized);
  }

  private List<NormalizedPassenger> normalizePassengers(
      List<CreateBookingRequest.BookingPassengerRequest> passengers) {
    if (passengers == null || passengers.isEmpty()) {
      throw new AppException("PASSENGER_REQUIRED", "At least one passenger is required", 400);
    }
    List<NormalizedPassenger> normalized = new ArrayList<>(passengers.size());
    for (CreateBookingRequest.BookingPassengerRequest passenger : passengers) {
      String passengerType = normalizeCode(passenger.passengerType());
      String gender = normalizeCode(passenger.gender());
      String identifyType = normalizeCode(passenger.identifyType());
      validatePassengerCodes(passengerType, gender, identifyType);
      normalized.add(
          new NormalizedPassenger(
              normalizeRequiredText(passenger.firstName(), "firstName"),
              normalizeRequiredText(passenger.lastName(), "lastName"),
              passenger.dateOfBirth(),
              gender,
              normalizeRequiredText(passenger.nationality(), "nationality"),
              passengerType,
              identifyType,
              normalizeRequiredText(passenger.identifyNumber(), "identifyNumber"),
              passenger.expiryDate()));
    }
    return List.copyOf(normalized);
  }

  private void validatePassengerCodes(String passengerType, String gender, String identifyType) {
    if (!"ADULT".equals(passengerType)
        && !"CHILD".equals(passengerType)
        && !"INFANT".equals(passengerType)) {
      throw new AppException(
          "INVALID_PASSENGER_TYPE", "passengerType must be ADULT, CHILD, or INFANT", 400);
    }
    if (!"MALE".equals(gender) && !"FEMALE".equals(gender) && !"OTHER".equals(gender)) {
      throw new AppException("INVALID_GENDER", "gender must be MALE, FEMALE, or OTHER", 400);
    }
    if (!"ID_CARD".equals(identifyType) && !"PASSPORT".equals(identifyType)) {
      throw new AppException(
          "INVALID_IDENTIFY_TYPE", "identifyType must be ID_CARD or PASSPORT", 400);
    }
  }

  private void validatePassengersMatchSummary(
      List<NormalizedPassenger> passengers, PassengerSummaryRequest summary) {
    if (passengers.size() != summary.totalPassengers()) {
      throw new AppException(
          "PASSENGER_COUNT_MISMATCH", "passengers size must match passengerSummary total", 400);
    }
    long adult = passengers.stream().filter(p -> "ADULT".equals(p.passengerType())).count();
    long child = passengers.stream().filter(p -> "CHILD".equals(p.passengerType())).count();
    long infant = passengers.stream().filter(p -> "INFANT".equals(p.passengerType())).count();
    if (adult != summary.adult() || child != summary.child() || infant != summary.infant()) {
      throw new AppException(
          "PASSENGER_COUNT_MISMATCH", "passengerType counts must match passengerSummary", 400);
    }
  }

  private void validatePassengerAges(List<NormalizedPassenger> passengers, LocalDate flightDate) {
    for (NormalizedPassenger passenger : passengers) {
      int age = ageOnFlightDate(passenger.dateOfBirth(), flightDate);
      boolean valid =
          switch (passenger.passengerType()) {
            case "ADULT" -> age >= ADULT_MIN_AGE;
            case "CHILD" -> age >= CHILD_MIN_AGE && age < ADULT_MIN_AGE;
            case "INFANT" -> age < CHILD_MIN_AGE;
            default -> false;
          };
      if (!valid) {
        throw new AppException(
            "INVALID_PASSENGER_AGE",
            "passengerType does not match age on flightDate for " + passenger.firstName(),
            400);
      }
    }
  }

  private int ageOnFlightDate(LocalDate dateOfBirth, LocalDate flightDate) {
    if (dateOfBirth == null || flightDate == null || dateOfBirth.isAfter(flightDate)) {
      throw new AppException(
          "INVALID_PASSENGER_AGE", "dateOfBirth must be on or before flightDate", 400);
    }
    return Period.between(dateOfBirth, flightDate).getYears();
  }

  private String normalizeEmail(String value) {
    String normalized = normalizeText(value);
    return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
  }

  private String normalizeCode(String value) {
    return normalizeRequiredText(value, "code").toUpperCase(Locale.ROOT);
  }

  private String normalizeRequiredText(String value, String fieldName) {
    String normalized = normalizeText(value);
    if (!StringUtils.hasText(normalized)) {
      throw new AppException("VALIDATION_FAILED", fieldName + " is required", 400);
    }
    return normalized;
  }

  private String normalizeText(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private PrimaryContactValues primaryContactValues(List<NormalizedContact> contacts) {
    NormalizedContact primary =
        contacts.stream()
            .filter(NormalizedContact::primary)
            .findFirst()
            .orElse(contacts.getFirst());
    String email =
        StringUtils.hasText(primary.email())
            ? primary.email()
            : contacts.stream()
                .map(NormalizedContact::email)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    String phone =
        StringUtils.hasText(primary.phone())
            ? primary.phone()
            : contacts.stream()
                .map(NormalizedContact::phone)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    if (!StringUtils.hasText(email) || !StringUtils.hasText(phone)) {
      throw new AppException(
          "CONTACT_REQUIRED",
          "contacts must include at least one email and one phone for booking contact",
          400);
    }
    return new PrimaryContactValues(email, phone);
  }

  private List<BookingContactEntity> saveContacts(
      BookingEntity booking, List<NormalizedContact> contacts) {
    OffsetDateTime now = OffsetDateTime.now();
    List<BookingContactEntity> entities = new ArrayList<>(contacts.size());
    for (NormalizedContact contact : contacts) {
      BookingContactEntity entity = new BookingContactEntity();
      entity.setBooking(booking);
      entity.setEmail(contact.email());
      entity.setPhone(contact.phone());
      entity.setPrimary(contact.primary());
      entity.setCreatedAt(now);
      entity.setUpdatedAt(now);
      entities.add(entity);
    }
    return bookingContactRepository.saveAll(entities);
  }

  private List<PassengerEntity> savePassengers(
      BookingEntity booking, List<NormalizedPassenger> passengers) {
    OffsetDateTime now = OffsetDateTime.now();
    List<PassengerEntity> entities = new ArrayList<>(passengers.size());
    for (NormalizedPassenger passenger : passengers) {
      PassengerEntity entity = new PassengerEntity();
      entity.setBooking(booking);
      entity.setFirstName(passenger.firstName());
      entity.setLastName(passenger.lastName());
      entity.setDateOfBirth(passenger.dateOfBirth());
      entity.setGender(passenger.gender());
      entity.setNationality(passenger.nationality());
      entity.setPassengerType(passenger.passengerType());
      entity.setIdentifyType(passenger.identifyType());
      entity.setIdentifyNumber(passenger.identifyNumber());
      entity.setExpiryDate(passenger.expiryDate());
      entity.setCreatedAt(now);
      entity.setUpdatedAt(now);
      entities.add(entity);
    }
    return passengerRepository.saveAll(entities);
  }

  private CreateBookingResponse toCreateBookingResponse(
      BookingEntity booking,
      FlightFareEntity fare,
      RevalidateSnapshot snapshot,
      List<BookingContactEntity> contacts,
      List<PassengerEntity> passengers,
      int reservedSeatCount) {
    FlightEntity flight = fare.getFlight();
    FareClassEntity fareClass = fare.getFareClass();
    AirlineEntity airline = flight.getAirline();
    AirportEntity departureAirport = flight.getDepartureAirport();
    AirportEntity arrivalAirport = flight.getArrivalAirport();
    PricingSnapshot pricing = snapshot.pricing();
    return new CreateBookingResponse(
        new CreateBookingResponse.BookingResponse(
            booking.getId(),
            booking.getPnrCode(),
            booking.getStatus(),
            booking.getPnrExpiresAt(),
            booking.getCreatedAt()),
        new CreateBookingResponse.FlightResponse(
            flight.getId(),
            flight.getFlightNumber(),
            flight.getFlightDate(),
            flight.getStatus(),
            new CreateBookingResponse.AirlineResponse(
                airline.getId(), airline.getCode(), airline.getName(), airline.getLogoUrl()),
            new CreateBookingResponse.FlightEndpointResponse(
                departureAirport.getId(),
                departureAirport.getCode(),
                departureAirport.getName(),
                departureAirport.getCity(),
                departureAirport.getCountry(),
                departureAirport.getTimeZone(),
                flight.getDepartureTerminal(),
                flight.getDepartureGate(),
                flight.getDepartureTime()),
            new CreateBookingResponse.FlightEndpointResponse(
                arrivalAirport.getId(),
                arrivalAirport.getCode(),
                arrivalAirport.getName(),
                arrivalAirport.getCity(),
                arrivalAirport.getCountry(),
                arrivalAirport.getTimeZone(),
                flight.getArrivalTerminal(),
                flight.getArrivalGate(),
                flight.getArrivalTime())),
        new CreateBookingResponse.FareResponse(
            fare.getId(),
            fareClass.getId(),
            fare.getStatus(),
            fareClass.getCode(),
            fareClass.getName(),
            fareClass.getCabinClass(),
            fare.getBasePrice(),
            fare.getTotalTaxAmount(),
            fare.getTotalPrice(),
            pricing.currency(),
            fareClass.getBaggageCheckinKg(),
            fareClass.getBaggageHandKg(),
            fareClass.isRefundable(),
            fareClass.isChangeable(),
            fareClass.getChangeFee(),
            fareClass.getRefundFee()),
        new CreateBookingResponse.PricingResponse(
            pricing.currency(),
            pricing.baseFare(),
            pricing.taxes(),
            pricing.fees(),
            pricing.originalTotalPrice(),
            pricing.discountAmount(),
            pricing.discountPercent(),
            pricing.discountedTotalPrice()),
        new CreateBookingResponse.PromotionResponse(
            snapshot.promoCode(),
            snapshot.promotionId(),
            pricing.discountAmount(),
            pricing.discountPercent()),
        contacts.stream()
            .map(
                contact ->
                    new CreateBookingResponse.ContactResponse(
                        contact.getId(),
                        contact.getEmail(),
                        contact.getPhone(),
                        contact.isPrimary()))
            .toList(),
        new CreateBookingResponse.PassengerSummaryResponse(
            snapshot.passengerSummary().adult(),
            snapshot.passengerSummary().child(),
            snapshot.passengerSummary().infant()),
        passengers.stream()
            .map(
                passenger ->
                    new CreateBookingResponse.PassengerResponse(
                        passenger.getId(),
                        passenger.getFirstName(),
                        passenger.getLastName(),
                        passenger.getDateOfBirth(),
                        passenger.getGender(),
                        passenger.getNationality(),
                        passenger.getPassengerType(),
                        passenger.getIdentifyType(),
                        passenger.getIdentifyNumber(),
                        passenger.getExpiryDate()))
            .toList(),
        new CreateBookingResponse.SeatReservationResponse(
            reservedSeatCount, "RESERVED", booking.getPnrExpiresAt()),
        new CreateBookingResponse.NextActionResponse("CREATE_PAYMENT", "/api/payments"));
  }

  private FlightFareEntity requireFareForFlight(Long fareId, Long flightId) {
    FlightFareEntity fare =
        flightFareRepository
            .findByIdWithDetails(fareId)
            .orElseThrow(() -> new AppException("FARE_NOT_FOUND", "Fare not found", 404));
    if (!Objects.equals(fare.getFlight().getId(), flightId)) {
      throw new AppException("FARE_NOT_FOUND", "Fare does not belong to the requested flight", 404);
    }
    return fare;
  }

  private void ensureSaleable(FlightFareEntity fare) {
    String fareStatus = normalizeStatus(fare.getStatus());
    String flightStatus = normalizeStatus(fare.getFlight().getStatus());
    if (!"AVAILABLE".equals(fareStatus)) {
      throw new AppException("FARE_SOLD_OUT", "Selected fare is not available", 409);
    }
    if (!"SCHEDULED".equals(flightStatus) && !"ON_TIME".equals(flightStatus)) {
      throw new AppException("FLIGHT_NOT_SALEABLE", "Flight is not saleable", 409);
    }
  }

  private PromoPreview applyPromotion(
      String promoCode, BigDecimal originalPrice, String currency, String countryCode) {
    PromoPreview preview =
        promotionPreviewService.preview(promoCode, originalPrice, currency, countryCode, true);
    if (!StringUtils.hasText(promoCode)) {
      return preview;
    }
    if ("APPLIED".equals(preview.status())) {
      return preview;
    }
    String message = preview.message() == null ? "Promo code is not applicable" : preview.message();
    String normalizedMessage = message.toLowerCase(Locale.ROOT);
    if (normalizedMessage.contains("not found")) {
      throw new AppException("PROMO_INVALID", message, 400);
    }
    if (normalizedMessage.contains("expired")) {
      throw new AppException("PROMO_EXPIRED", message, 400);
    }
    throw new AppException("PROMO_NOT_APPLICABLE", message, 400);
  }

  private PricingBreakdown buildPricing(FlightFareEntity fare, PassengerSummaryRequest summary) {
    BigDecimal oneAdultTotal = nonNullMoney(fare.getTotalPrice());
    BigDecimal oneAdultBase = nonNullMoney(fare.getBasePrice());
    BigDecimal original =
        flightPricingService.calculateTotalPrice(
            oneAdultTotal, summary.adult(), summary.child(), summary.infant());
    BigDecimal baseFare =
        calculateTieredPrice(oneAdultBase, summary.adult(), summary.child(), summary.infant());
    BigDecimal taxes = original.subtract(baseFare).max(ZERO).setScale(2, RoundingMode.HALF_UP);
    return new PricingBreakdown(
        countryCurrencyResolver.resolveCurrency(countryCurrencyResolver.resolveCountryCode(fare)),
        baseFare,
        taxes,
        ZERO,
        original);
  }

  private BigDecimal calculateTieredPrice(
      BigDecimal oneAdultPrice, int adult, int child, int infant) {
    BigDecimal adultTotal = oneAdultPrice.multiply(BigDecimal.valueOf(adult));
    BigDecimal childTotal = oneAdultPrice.multiply(CHILD_RATE).multiply(BigDecimal.valueOf(child));
    BigDecimal infantTotal =
        oneAdultPrice.multiply(INFANT_RATE).multiply(BigDecimal.valueOf(infant));
    return adultTotal.add(childTotal).add(infantTotal).setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal nonNullMoney(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  private String normalizeStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return "";
    }
    return status.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizePromoCode(String promoCode) {
    if (!StringUtils.hasText(promoCode)) {
      return null;
    }
    return promoCode.trim().toUpperCase(Locale.ROOT);
  }

  private Long resolvePromotionId(String normalizedPromoCode, PromoPreview preview) {
    if (!StringUtils.hasText(normalizedPromoCode) || !"APPLIED".equals(preview.status())) {
      return null;
    }
    return promotionRepository
        .findByCodeIgnoreCase(normalizedPromoCode)
        .map(PromotionEntity::getId)
        .orElse(null);
  }

  private String fareVersionOf(FlightFareEntity fare) {
    return fare.getUpdatedAt() == null
        ? "0"
        : String.valueOf(fare.getUpdatedAt().toInstant().toEpochMilli());
  }

  private String revalidateKey(String revalidateId) {
    return "revalidate:" + revalidateId;
  }

  private void saveRevalidateSnapshot(RevalidateSnapshot snapshot) {
    writeValue(revalidateKey(snapshot.revalidateId()), snapshot, REVALIDATE_TTL);
  }

  private RevalidateSnapshot loadRevalidateSnapshot(String revalidateId) {
    String value = redisTemplate.opsForValue().get(revalidateKey(revalidateId));
    if (!StringUtils.hasText(value)) {
      throw new AppException("REVALIDATE_EXPIRED", "Revalidate session expired", 410);
    }
    try {
      return objectMapper.readValue(value, RevalidateSnapshot.class);
    } catch (JsonProcessingException e) {
      throw new AppException("REVALIDATE_FAILED", "Failed to read revalidate snapshot", 500);
    }
  }

  private void deleteRevalidateSnapshot(String revalidateId) {
    redisTemplate.delete(revalidateKey(revalidateId));
  }

  private IdempotencyResult acquireIdempotency(String idempotencyKey, String requestHash) {
    String key = "idem:booking:" + idempotencyKey.trim();
    IdempotencyPayload processingPayload =
        new IdempotencyPayload("PROCESSING", requestHash, null, null, null);
    String processingJson = toJson(processingPayload);
    Boolean created = redisTemplate.opsForValue().setIfAbsent(key, processingJson, IDEMPOTENCY_TTL);
    if (Boolean.TRUE.equals(created)) {
      return new IdempotencyResult(null);
    }
    String existingJson = redisTemplate.opsForValue().get(key);
    if (!StringUtils.hasText(existingJson)) {
      throw new AppException("REQUEST_IN_PROGRESS", "Booking request is processing", 409);
    }
    IdempotencyPayload existing = fromIdempotencyJson(existingJson);
    if (!Objects.equals(existing.requestHash(), requestHash)) {
      throw new AppException(
          "IDEMPOTENCY_KEY_REUSED", "idempotencyKey was used with a different payload", 409);
    }
    if ("SUCCESS".equals(existing.status()) && existing.response() != null) {
      return new IdempotencyResult(existing.response());
    }
    throw new AppException("REQUEST_IN_PROGRESS", "Booking request is processing", 409);
  }

  private void markIdempotencySuccess(
      String idempotencyKey, String requestHash, CreateBookingResponse response) {
    IdempotencyPayload payload =
        new IdempotencyPayload(
            "SUCCESS", requestHash, response.booking().bookingId(), null, response);
    writeValue("idem:booking:" + idempotencyKey.trim(), payload, IDEMPOTENCY_TTL);
  }

  private void markIdempotencyFailed(String idempotencyKey, String requestHash, String reason) {
    IdempotencyPayload payload = new IdempotencyPayload("FAILED", requestHash, null, reason, null);
    writeValue("idem:booking:" + idempotencyKey.trim(), payload, IDEMPOTENCY_TTL);
  }

  private IdempotencyPayload fromIdempotencyJson(String json) {
    try {
      return objectMapper.readValue(json, IdempotencyPayload.class);
    } catch (JsonProcessingException e) {
      throw new AppException("REQUEST_IN_PROGRESS", "Booking request is processing", 409);
    }
  }

  private String generatePnrCode() {
    return "PNR"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
  }

  private String hash(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new AppException("INTERNAL_SERVER_ERROR", "Unable to hash request", 500);
    }
  }

  private String toJson(Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new AppException("INTERNAL_SERVER_ERROR", "Unable to serialize payload", 500);
    }
  }

  private void writeValue(String key, Object payload, Duration ttl) {
    redisTemplate.opsForValue().set(key, toJson(payload), ttl);
  }

  private record PricingBreakdown(
      String currency,
      BigDecimal baseFare,
      BigDecimal taxes,
      BigDecimal fees,
      BigDecimal originalTotalPrice) {}

  private record PassengerSummary(int adult, int child, int infant) {
    /** CHILD counts toward seats, INFANT is a lap infant and does not. */
    private int seatedPassengers() {
      return adult + child;
    }
  }

  private record NormalizedContact(String email, String phone, boolean primary) {}

  private record PrimaryContactValues(String email, String phone) {}

  private record NormalizedPassenger(
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      String gender,
      String nationality,
      String passengerType,
      String identifyType,
      String identifyNumber,
      LocalDate expiryDate) {}

  private record CreateBookingFingerprint(
      String revalidateId,
      Long flightId,
      Long fareId,
      PassengerSummary passengerSummary,
      List<NormalizedContact> contacts,
      List<NormalizedPassenger> passengers) {}

  private record PricingSnapshot(
      String currency,
      BigDecimal baseFare,
      BigDecimal taxes,
      BigDecimal fees,
      BigDecimal originalTotalPrice,
      BigDecimal discountAmount,
      Integer discountPercent,
      BigDecimal discountedTotalPrice) {}

  private record RevalidateSnapshot(
      String revalidateId,
      Long flightId,
      Long fareId,
      Long fareClassId,
      String fareVersion,
      String promoCode,
      Long promotionId,
      PassengerSummary passengerSummary,
      PricingSnapshot pricing,
      OffsetDateTime expiresAt,
      String status) {}

  private record IdempotencyPayload(
      String status,
      String requestHash,
      Long bookingId,
      String failureReason,
      CreateBookingResponse response) {}

  private record IdempotencyResult(CreateBookingResponse replay) {}
}
