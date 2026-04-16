package com.example.appbootstrap.application.booking;

import com.example.appbootstrap.controller.booking.dto.CreateBookingRequest;
import com.example.appbootstrap.controller.booking.dto.CreateBookingResponse;
import com.example.appbootstrap.controller.booking.dto.PassengerSummaryRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareResponse;
import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.PromotionEntity;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PromotionRepository;
import com.example.moduleflight.application.CountryCurrencyResolver;
import com.example.moduleflight.application.FlightPricingService;
import com.example.moduleflight.application.PromotionPreviewService;
import com.example.moduleflight.application.PromotionPreviewService.PromoPreview;
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
import java.time.OffsetDateTime;
import java.util.HexFormat;
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
  private static final BigDecimal PRICE_THRESHOLD = new BigDecimal("0.01");
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
  private final PromotionRepository promotionRepository;
  private final FlightSeatRepository flightSeatRepository;

  public BookingRevalidateService(
      FlightFareRepository flightFareRepository,
      FlightPricingService flightPricingService,
      PromotionPreviewService promotionPreviewService,
      CountryCurrencyResolver countryCurrencyResolver,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      UserRepository userRepository,
      BookingRepository bookingRepository,
      PromotionRepository promotionRepository,
      FlightSeatRepository flightSeatRepository) {
    this.flightFareRepository = flightFareRepository;
    this.flightPricingService = flightPricingService;
    this.promotionPreviewService = promotionPreviewService;
    this.countryCurrencyResolver = countryCurrencyResolver;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.userRepository = userRepository;
    this.bookingRepository = bookingRepository;
    this.promotionRepository = promotionRepository;
    this.flightSeatRepository = flightSeatRepository;
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
        flightSeatRepository.countAvailableSeats(
            fare.getFlight().getId(), fare.getFareClass().getId());
    if (availableSeats < requiredSeats) {
      throw new AppException(
          "FARE_SOLD_OUT", "Selected fare does not have enough available seats", 409);
    }

    PricingBreakdown pricing = buildPricing(fare, request.passengerSummary());
    if (request.expectedPrice() != null) {
      BigDecimal delta = request.expectedPrice().subtract(pricing.originalTotalPrice()).abs();
      if (delta.compareTo(PRICE_THRESHOLD) > 0) {
        throw new AppException(
            "PRICE_CHANGED", "Displayed price is stale. Please refresh fares and try again.", 409);
      }
    }

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
            snapshot.revalidateId()
                + "|"
                + snapshot.flightId()
                + "|"
                + snapshot.fareId()
                + "|"
                + canonical.adult()
                + "|"
                + canonical.child()
                + "|"
                + canonical.infant()
                + "|"
                + request.contactEmail().trim().toLowerCase(Locale.ROOT)
                + "|"
                + request.contactPhone().trim());
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
      booking.setContactEmail(request.contactEmail().trim().toLowerCase(Locale.ROOT));
      booking.setContactPhone(request.contactPhone().trim());
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

      int updated =
          flightSeatRepository.reserveForBooking(
              booking.getId(), snapshot.flightId(), snapshot.fareClassId(), requiredSeats);
      if (updated < requiredSeats) {
        throw new AppException("FARE_SOLD_OUT", "Selected fare is sold out during booking", 409);
      }

      deleteRevalidateSnapshot(snapshot.revalidateId());

      CreateBookingResponse response =
          new CreateBookingResponse(
              booking.getId(),
              booking.getPnrCode(),
              booking.getStatus(),
              booking.getCurrency(),
              booking.getTotalAmount(),
              booking.getDiscountAmount());
      markIdempotencySuccess(request.idempotencyKey(), requestHash, response);
      LOG.info(
          "booking created bookingId={} userId={} revalidateId={} fareId={} flightId={} seats={}",
          response.bookingId(),
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
        new IdempotencyPayload("SUCCESS", requestHash, response.bookingId(), null, response);
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
    private int seatedPassengers() {
      return adult + child;
    }
  }

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
