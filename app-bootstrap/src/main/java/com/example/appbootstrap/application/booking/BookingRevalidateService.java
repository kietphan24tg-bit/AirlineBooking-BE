package com.example.appbootstrap.application.booking;

import com.example.appbootstrap.controller.booking.dto.CreateBookingRequest;
import com.example.appbootstrap.controller.booking.dto.CreateBookingResponse;
import com.example.appbootstrap.controller.booking.dto.PassengerSummaryRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareResponse;
import com.example.appbootstrap.controller.booking.dto.RevalidateItineraryRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateItineraryResponse;
import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PromotionEntity;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PromotionRepository;
import com.example.moduleflight.application.CountryCurrencyResolver;
import com.example.moduleflight.application.CurrencyAmountNormalizer;
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
import com.example.moduleidentity.entity.Role;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
  private final FlightFareRepository flightFareRepository;
  private final FlightPricingService flightPricingService;
  private final PromotionPreviewService promotionPreviewService;
  private final CountryCurrencyResolver countryCurrencyResolver;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final UserRepository userRepository;
  private final BookingRepository bookingRepository;
  private final BookingContactRepository bookingContactRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerRepository passengerRepository;
  private final PromotionRepository promotionRepository;
  private final FlightSeatRepository flightSeatRepository;
  private final FlightInventoryAvailabilityService flightInventoryAvailabilityService;
  private final PasswordEncoder passwordEncoder;

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
    this.flightFareRepository = flightFareRepository;
    this.flightPricingService = flightPricingService;
    this.promotionPreviewService = promotionPreviewService;
    this.countryCurrencyResolver = countryCurrencyResolver;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.userRepository = userRepository;
    this.bookingRepository = bookingRepository;
    this.bookingContactRepository = bookingContactRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerRepository = passengerRepository;
    this.promotionRepository = promotionRepository;
    this.flightSeatRepository = flightSeatRepository;
    this.flightInventoryAvailabilityService = flightInventoryAvailabilityService;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public RevalidateFareResponse revalidate(RevalidateFareRequest request) {
    RevalidateItineraryResponse itinerary =
        revalidateItinerary(
            new RevalidateItineraryRequest(
                "ONE_WAY",
                request.promoCode(),
                request.passengerSummary(),
                List.of(
                    new RevalidateItineraryRequest.SegmentRequest(
                        "OUTBOUND", request.flightId(), request.fareId()))));
    RevalidateItineraryResponse.SegmentResponse segment = itinerary.segments().getFirst();
    return new RevalidateFareResponse(
        itinerary.revalidateId(),
        itinerary.expiresInSeconds(),
        segment.flightId(),
        segment.fareId(),
        itinerary.promoCode(),
        new RevalidateFareResponse.PassengerSummaryResponse(
            itinerary.passengerSummary().adult(),
            itinerary.passengerSummary().child(),
            itinerary.passengerSummary().infant()),
        new RevalidateFareResponse.PricingResponse(
            itinerary.pricing().currency(),
            itinerary.pricing().baseFare(),
            itinerary.pricing().taxes(),
            itinerary.pricing().fees(),
            itinerary.pricing().originalTotalPrice(),
            itinerary.pricing().discountAmount(),
            itinerary.pricing().discountPercent(),
            itinerary.pricing().discountedTotalPrice()));
  }

  @Transactional(readOnly = true)
  public RevalidateItineraryResponse revalidateItinerary(RevalidateItineraryRequest request) {
    validatePassengerSummary(request.passengerSummary());
    PassengerSummary canonicalSummary =
        new PassengerSummary(
            request.passengerSummary().adult(),
            request.passengerSummary().child(),
            request.passengerSummary().infant());
    int requiredSeats = canonicalSummary.seatedPassengers();
    if (requiredSeats <= 0) {
      throw new AppException(
          "INVALID_PASSENGER_BREAKDOWN", "At least one seated passenger required", 400);
    }

    String normalizedTripType = normalizeTripType(request.tripType());
    List<RevalidateItineraryRequest.SegmentRequest> segmentRequests =
        normalizeAndValidateSegmentRequests(normalizedTripType, request.segments());

    List<SegmentRuntime> segmentRuntimes = new ArrayList<>(segmentRequests.size());
    for (RevalidateItineraryRequest.SegmentRequest segmentRequest : segmentRequests) {
      FlightFareEntity fare =
          requireFareForFlight(segmentRequest.fareId(), segmentRequest.flightId());
      ensureSaleable(fare);
      long availableSeats =
          flightInventoryAvailabilityService.countAvailableSeats(
              fare.getFlight().getId(), fare.getFareClass().getId());
      if (availableSeats < requiredSeats) {
        throw new AppException(
            "FARE_SOLD_OUT", "Selected fare does not have enough available seats", 409);
      }
      String countryCode = countryCurrencyResolver.resolveCountryCode(fare);
      String currency = countryCurrencyResolver.resolveCurrency(countryCode);
      PricingBreakdown pricing = buildPricing(fare, request.passengerSummary(), currency);
      segmentRuntimes.add(
          new SegmentRuntime(
              normalizeSegmentDirection(segmentRequest.direction()), fare, countryCode, pricing));
    }

    String pricingCurrency = segmentRuntimes.getFirst().pricing().currency();
    BigDecimal originalTotalPrice = zeroMoney(pricingCurrency);
    BigDecimal baseFareTotal = zeroMoney(pricingCurrency);
    BigDecimal taxesTotal = zeroMoney(pricingCurrency);
    BigDecimal feesTotal = zeroMoney(pricingCurrency);
    for (SegmentRuntime segment : segmentRuntimes) {
      if (!pricingCurrency.equals(segment.pricing().currency())) {
        throw new AppException(
            "MULTI_CURRENCY_NOT_SUPPORTED",
            "Round-trip pricing across multiple currencies is not supported",
            409);
      }
      originalTotalPrice =
          CurrencyAmountNormalizer.normalize(
              originalTotalPrice.add(segment.pricing().originalTotalPrice()), pricingCurrency);
      baseFareTotal =
          CurrencyAmountNormalizer.normalize(
              baseFareTotal.add(segment.pricing().baseFare()), pricingCurrency);
      taxesTotal =
          CurrencyAmountNormalizer.normalize(
              taxesTotal.add(segment.pricing().taxes()), pricingCurrency);
      feesTotal =
          CurrencyAmountNormalizer.normalize(
              feesTotal.add(segment.pricing().fees()), pricingCurrency);
    }

    PromoPreview preview =
        applyPromotion(
            request.promoCode(),
            originalTotalPrice,
            pricingCurrency,
            segmentRuntimes.getFirst().countryCode());
    String normalizedPromoCode = normalizePromoCode(request.promoCode());
    Long promotionId = resolvePromotionId(normalizedPromoCode, preview);
    BigDecimal discountAmount =
        "APPLIED".equals(preview.status())
            ? CurrencyAmountNormalizer.normalizeOrZero(preview.discountAmount(), pricingCurrency)
            : zeroMoney(pricingCurrency);
    Integer discountPercent = "APPLIED".equals(preview.status()) ? preview.discountPercent() : null;
    BigDecimal discountedTotal =
        CurrencyAmountNormalizer.normalize(
            originalTotalPrice.subtract(discountAmount), pricingCurrency);

    String revalidateId = generateRevalidateId();
    PricingSnapshot aggregatePricing =
        new PricingSnapshot(
            pricingCurrency,
            baseFareTotal,
            taxesTotal,
            feesTotal,
            originalTotalPrice,
            discountAmount,
            discountPercent,
            discountedTotal);

    List<SegmentSnapshot> segmentSnapshots = new ArrayList<>(segmentRuntimes.size());
    for (SegmentRuntime segment : segmentRuntimes) {
      FlightFareEntity fare = segment.fare();
      FareClassEntity fareClass = fare.getFareClass();
      segmentSnapshots.add(
          new SegmentSnapshot(
              segment.direction(),
              fare.getFlight().getId(),
              fare.getId(),
              fareClass.getId(),
              fareVersionOf(fare),
              fareClass.getCode(),
              fareClass.getName(),
              fareClass.getCabinClass(),
              segment.pricing().currency(),
              new PricingSnapshot(
                  segment.pricing().currency(),
                  segment.pricing().baseFare(),
                  segment.pricing().taxes(),
                  segment.pricing().fees(),
                  segment.pricing().originalTotalPrice(),
                  zeroMoney(segment.pricing().currency()),
                  null,
                  segment.pricing().originalTotalPrice())));
    }

    SegmentSnapshot primarySegment = segmentSnapshots.getFirst();
    RevalidateSnapshot snapshot =
        new RevalidateSnapshot(
            revalidateId,
            normalizedTripType,
            primarySegment.flightId(),
            primarySegment.fareId(),
            primarySegment.fareClassId(),
            primarySegment.fareVersion(),
            normalizedPromoCode,
            promotionId,
            canonicalSummary,
            aggregatePricing,
            segmentSnapshots,
            OffsetDateTime.now().plus(REVALIDATE_TTL),
            "ACTIVE");
    saveRevalidateSnapshot(snapshot);
    LOG.info(
        "revalidate itinerary created id={} tripType={} segments={} promoCode={} promotionId={} expiresAt={}",
        snapshot.revalidateId(),
        snapshot.tripType(),
        snapshot.segments().size(),
        snapshot.promoCode(),
        snapshot.promotionId(),
        snapshot.expiresAt());

    return new RevalidateItineraryResponse(
        snapshot.revalidateId(),
        REVALIDATE_TTL.toSeconds(),
        snapshot.tripType(),
        snapshot.promoCode(),
        new RevalidateItineraryResponse.PassengerSummaryResponse(
            snapshot.passengerSummary().adult(),
            snapshot.passengerSummary().child(),
            snapshot.passengerSummary().infant()),
        new RevalidateItineraryResponse.PricingResponse(
            snapshot.pricing().currency(),
            snapshot.pricing().baseFare(),
            snapshot.pricing().taxes(),
            snapshot.pricing().fees(),
            snapshot.pricing().originalTotalPrice(),
            snapshot.pricing().discountAmount(),
            snapshot.pricing().discountPercent(),
            snapshot.pricing().discountedTotalPrice()),
        snapshot.segments().stream()
            .map(
                segment ->
                    new RevalidateItineraryResponse.SegmentResponse(
                        segment.direction(),
                        segment.flightId(),
                        segment.fareId(),
                        segment.fareClassId(),
                        segment.fareCode(),
                        segment.fareName(),
                        segment.cabinClass(),
                        segment.currency(),
                        new RevalidateItineraryResponse.PricingResponse(
                            segment.pricing().currency(),
                            segment.pricing().baseFare(),
                            segment.pricing().taxes(),
                            segment.pricing().fees(),
                            segment.pricing().originalTotalPrice(),
                            segment.pricing().discountAmount(),
                            segment.pricing().discountPercent(),
                            segment.pricing().discountedTotalPrice())))
            .toList());
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
                    snapshot.segments().stream()
                        .map(
                            segment -> new SegmentFingerprint(segment.flightId(), segment.fareId()))
                        .toList(),
                    canonical,
                    contacts,
                    passengers)));
    IdempotencyResult idempotencyResult = acquireIdempotency(request.idempotencyKey(), requestHash);
    if (idempotencyResult.replay() != null) {
      return idempotencyResult.replay();
    }

    try {
      List<SegmentSnapshot> segments = snapshot.segments();
      if (segments == null || segments.isEmpty()) {
        throw new AppException("REVALIDATE_FAILED", "Revalidate snapshot has no segment data", 500);
      }

      int requiredSeats = snapshot.passengerSummary().seatedPassengers();
      List<FlightFareEntity> faresForBooking = new ArrayList<>(segments.size());
      for (SegmentSnapshot segment : segments) {
        FlightFareEntity fare = requireFareForFlightForUpdate(segment.fareId(), segment.flightId());
        if (!Objects.equals(fareVersionOf(fare), segment.fareVersion())) {
          throw new AppException(
              "PRICE_CHANGED", "Fare changed after revalidation. Please revalidate again.", 409);
        }
        validatePassengerAges(passengers, fare.getFlight().getFlightDate());
        long availableSeats =
            flightInventoryAvailabilityService.countAvailableSeats(
                segment.flightId(), segment.fareClassId());
        if (availableSeats < requiredSeats) {
          throw new AppException("FARE_SOLD_OUT", "Selected fare is sold out during booking", 409);
        }
        faresForBooking.add(fare);
      }

      UserEntity user = resolveBookingUser(userId);
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
      int totalReservedSeats = 0;
      for (FlightFareEntity fare : faresForBooking) {
        BookingFareHoldEntity fareHold = saveFareHold(booking, fare, requiredSeats);
        totalReservedSeats += fareHold.getQuantity();
      }

      deleteRevalidateSnapshot(snapshot.revalidateId());

      CreateBookingResponse response =
          toCreateBookingResponse(
              booking,
              faresForBooking.getFirst(),
              snapshot,
              contactEntities,
              passengerEntities,
              totalReservedSeats);
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

  private UserEntity resolveBookingUser(Long userId) {
    if (userId != null) {
      return userRepository
          .findById(userId)
          .orElseThrow(
              () -> new AppException("USER_NOT_FOUND", "Authenticated user does not exist", 404));
    }

    OffsetDateTime now = OffsetDateTime.now();
    UserEntity guestUser = new UserEntity();
    guestUser.setEmail("guest+" + UUID.randomUUID() + "@booking.local");
    guestUser.setFirstName("Guest");
    guestUser.setLastName("Booking");
    guestUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
    guestUser.setRole(Role.USER);
    guestUser.setAcceptTerms(true);
    guestUser.setCreatedAt(now);
    guestUser.setUpdatedAt(now);
    return userRepository.save(guestUser);
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
      String identifyType = normalizeOptionalCode(passenger.identifyType());
      String nationality = normalizeText(passenger.nationality());
      String identifyNumber = normalizeText(passenger.identifyNumber());
      validatePassengerFields(passengerType, gender, nationality, identifyType, identifyNumber);
      normalized.add(
          new NormalizedPassenger(
              normalizeRequiredText(passenger.firstName(), "firstName"),
              normalizeRequiredText(passenger.lastName(), "lastName"),
              passenger.dateOfBirth(),
              gender,
              nationality,
              passengerType,
              identifyType,
              identifyNumber,
              passenger.expiryDate()));
    }
    return List.copyOf(normalized);
  }

  private void validatePassengerFields(
      String passengerType,
      String gender,
      String nationality,
      String identifyType,
      String identifyNumber) {
    if (!"ADULT".equals(passengerType)
        && !"CHILD".equals(passengerType)
        && !"INFANT".equals(passengerType)) {
      throw new AppException(
          "INVALID_PASSENGER_TYPE", "passengerType must be ADULT, CHILD, or INFANT", 400);
    }
    if (!"MALE".equals(gender) && !"FEMALE".equals(gender) && !"OTHER".equals(gender)) {
      throw new AppException("INVALID_GENDER", "gender must be MALE, FEMALE, or OTHER", 400);
    }
    if ("ADULT".equals(passengerType)) {
      if (!StringUtils.hasText(nationality)) {
        throw new AppException("VALIDATION_FAILED", "nationality is required for ADULT", 400);
      }
      if (!StringUtils.hasText(identifyType)) {
        throw new AppException("VALIDATION_FAILED", "identifyType is required for ADULT", 400);
      }
      if (!StringUtils.hasText(identifyNumber)) {
        throw new AppException("VALIDATION_FAILED", "identifyNumber is required for ADULT", 400);
      }
    }
    if (StringUtils.hasText(identifyType)
        && !"ID_CARD".equals(identifyType)
        && !"PASSPORT".equals(identifyType)) {
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

  private String normalizeOptionalCode(String value) {
    String normalized = normalizeText(value);
    return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
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

  private BookingFareHoldEntity saveFareHold(
      BookingEntity booking, FlightFareEntity fare, int quantity) {
    OffsetDateTime now = OffsetDateTime.now();
    BookingFareHoldEntity hold = new BookingFareHoldEntity();
    hold.setBooking(booking);
    hold.setFlightFare(fare);
    hold.setQuantity(quantity);
    hold.setStatus("HELD");
    hold.setExpiresAt(booking.getPnrExpiresAt());
    hold.setCreatedAt(now);
    hold.setUpdatedAt(now);
    return bookingFareHoldRepository.save(hold);
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
            reservedSeatCount, "HELD", booking.getPnrExpiresAt()),
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

  private FlightFareEntity requireFareForFlightForUpdate(Long fareId, Long flightId) {
    FlightFareEntity fare =
        flightFareRepository
            .findByIdWithDetailsForUpdate(fareId)
            .orElseThrow(() -> new AppException("FARE_NOT_FOUND", "Fare not found", 404));
    if (!Objects.equals(fare.getFlight().getId(), flightId)) {
      throw new AppException("FARE_NOT_FOUND", "Fare does not belong to the requested flight", 404);
    }
    return fare;
  }

  private String normalizeTripType(String tripType) {
    if (!StringUtils.hasText(tripType)) {
      throw new AppException("INVALID_TRIP_TYPE", "tripType is required", 400);
    }
    String normalized = tripType.trim().toUpperCase(Locale.ROOT);
    if (!"ONE_WAY".equals(normalized) && !"ROUND_TRIP".equals(normalized)) {
      throw new AppException("INVALID_TRIP_TYPE", "tripType must be ONE_WAY or ROUND_TRIP", 400);
    }
    return normalized;
  }

  private List<RevalidateItineraryRequest.SegmentRequest> normalizeAndValidateSegmentRequests(
      String tripType, List<RevalidateItineraryRequest.SegmentRequest> segments) {
    if (segments == null || segments.isEmpty()) {
      throw new AppException("INVALID_ITINERARY_SEGMENTS", "segments is required", 400);
    }
    List<RevalidateItineraryRequest.SegmentRequest> normalized = new ArrayList<>(segments.size());
    Set<String> directions = new HashSet<>();
    for (RevalidateItineraryRequest.SegmentRequest segment : segments) {
      if (segment == null) {
        continue;
      }
      String direction = normalizeSegmentDirection(segment.direction());
      if (!directions.add(direction)) {
        throw new AppException(
            "INVALID_ITINERARY_SEGMENTS",
            "Each segment direction must be unique for this itinerary",
            400);
      }
      normalized.add(
          new RevalidateItineraryRequest.SegmentRequest(
              direction, segment.flightId(), segment.fareId()));
    }
    if (normalized.isEmpty()) {
      throw new AppException("INVALID_ITINERARY_SEGMENTS", "segments is required", 400);
    }
    if ("ONE_WAY".equals(tripType)) {
      if (normalized.size() != 1 || !"OUTBOUND".equals(normalized.getFirst().direction())) {
        throw new AppException(
            "INVALID_ITINERARY_SEGMENTS",
            "ONE_WAY itinerary requires exactly one OUTBOUND segment",
            400);
      }
      return normalized;
    }
    if (normalized.size() != 2
        || !directions.contains("OUTBOUND")
        || !directions.contains("INBOUND")) {
      throw new AppException(
          "INVALID_ITINERARY_SEGMENTS",
          "ROUND_TRIP itinerary requires OUTBOUND and INBOUND segments",
          400);
    }
    return normalized;
  }

  private String normalizeSegmentDirection(String rawDirection) {
    if (!StringUtils.hasText(rawDirection)) {
      throw new AppException("INVALID_SEGMENT_DIRECTION", "segment direction is required", 400);
    }
    String normalized = rawDirection.trim().toUpperCase(Locale.ROOT);
    if (!"OUTBOUND".equals(normalized) && !"INBOUND".equals(normalized)) {
      throw new AppException(
          "INVALID_SEGMENT_DIRECTION", "segment direction must be OUTBOUND or INBOUND", 400);
    }
    return normalized;
  }

  private String generateRevalidateId() {
    return "RV_"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
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
    String currency =
        countryCurrencyResolver.resolveCurrency(countryCurrencyResolver.resolveCountryCode(fare));
    return buildPricing(fare, summary, currency);
  }

  private PricingBreakdown buildPricing(
      FlightFareEntity fare, PassengerSummaryRequest summary, String currency) {
    BigDecimal oneAdultTotal = nonNullMoney(fare.getTotalPrice());
    BigDecimal oneAdultBase = nonNullMoney(fare.getBasePrice());
    BigDecimal original =
        flightPricingService.calculateTotalPrice(
            oneAdultTotal, summary.adult(), summary.child(), summary.infant());
    BigDecimal baseFare =
        calculateTieredPrice(
            oneAdultBase, summary.adult(), summary.child(), summary.infant(), currency);
    original = CurrencyAmountNormalizer.normalize(original, currency);
    BigDecimal taxes =
        CurrencyAmountNormalizer.normalize(
            original.subtract(baseFare).max(BigDecimal.ZERO), currency);
    return new PricingBreakdown(currency, baseFare, taxes, zeroMoney(currency), original);
  }

  private BigDecimal calculateTieredPrice(
      BigDecimal oneAdultPrice, int adult, int child, int infant, String currency) {
    BigDecimal adultTotal = oneAdultPrice.multiply(BigDecimal.valueOf(adult));
    BigDecimal childTotal = oneAdultPrice.multiply(CHILD_RATE).multiply(BigDecimal.valueOf(child));
    BigDecimal infantTotal =
        oneAdultPrice.multiply(INFANT_RATE).multiply(BigDecimal.valueOf(infant));
    return CurrencyAmountNormalizer.normalize(
        adultTotal.add(childTotal).add(infantTotal), currency);
  }

  private BigDecimal nonNullMoney(BigDecimal value) {
    return value == null
        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        : value.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal zeroMoney(String currency) {
    return CurrencyAmountNormalizer.normalizeOrZero(null, currency);
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
    if ("FAILED".equals(existing.status())) {
      writeValue(key, processingPayload, IDEMPOTENCY_TTL);
      return new IdempotencyResult(null);
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

  private record SegmentFingerprint(Long flightId, Long fareId) {}

  private record CreateBookingFingerprint(
      String revalidateId,
      List<SegmentFingerprint> segments,
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

  private record SegmentSnapshot(
      String direction,
      Long flightId,
      Long fareId,
      Long fareClassId,
      String fareVersion,
      String fareCode,
      String fareName,
      String cabinClass,
      String currency,
      PricingSnapshot pricing) {}

  private record SegmentRuntime(
      String direction, FlightFareEntity fare, String countryCode, PricingBreakdown pricing) {}

  private record RevalidateSnapshot(
      String revalidateId,
      String tripType,
      Long flightId,
      Long fareId,
      Long fareClassId,
      String fareVersion,
      String promoCode,
      Long promotionId,
      PassengerSummary passengerSummary,
      PricingSnapshot pricing,
      List<SegmentSnapshot> segments,
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
