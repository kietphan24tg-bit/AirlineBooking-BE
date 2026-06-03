package com.example.appbootstrap.application.booking;

import com.example.appbootstrap.config.BookingProperties;
import com.example.appbootstrap.controller.booking.dto.FlightChangeQuoteRequest;
import com.example.appbootstrap.controller.booking.dto.FlightChangeQuoteResponse;
import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.BookingServiceEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.application.FlightInventoryAvailabilityService;
import com.example.moduleflight.application.FlightPricingService;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.repository.FlightFareRepository;
import com.example.modulepayment.application.TicketFinancialSnapshotService;
import com.example.modulepayment.entity.TicketFinancialSnapshotEntity;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.modulepayment.repository.TicketFinancialSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightChangeQuoteService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  private static final BigDecimal CHILD_RATE = new BigDecimal("0.75");
  private static final BigDecimal INFANT_RATE = new BigDecimal("0.10");
  private static final String BOOKING_STATUS_CONFIRMED = "CONFIRMED";
  private static final String BOOKING_STATUS_CHECKED_IN_LOCKED = "CHECKED_IN_LOCKED";
  private static final String TICKET_STATUS_ISSUED = "ISSUED";
  private static final List<String> ACTIVE_ASSIGNMENT_STATUSES = List.of("RESERVED", "BOOKED");
  private static final List<String> CHANGEABLE_PAYMENT_STATUSES =
      List.of("SUCCESS", "PARTIALLY_REFUNDED");

  private final BookingRepository bookingRepository;
  private final TicketRepository ticketRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final FlightFareRepository flightFareRepository;
  private final FlightInventoryAvailabilityService flightInventoryAvailabilityService;
  private final FlightPricingService flightPricingService;
  private final PaymentRepository paymentRepository;
  private final TicketFinancialSnapshotService ticketFinancialSnapshotService;
  private final TicketFinancialSnapshotRepository ticketFinancialSnapshotRepository;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final BookingProperties bookingProperties;

  public FlightChangeQuoteService(
      BookingRepository bookingRepository,
      TicketRepository ticketRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      BookingServiceRepository bookingServiceRepository,
      FlightFareRepository flightFareRepository,
      FlightInventoryAvailabilityService flightInventoryAvailabilityService,
      FlightPricingService flightPricingService,
      PaymentRepository paymentRepository,
      TicketFinancialSnapshotService ticketFinancialSnapshotService,
      TicketFinancialSnapshotRepository ticketFinancialSnapshotRepository,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      BookingProperties bookingProperties) {
    this.bookingRepository = bookingRepository;
    this.ticketRepository = ticketRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.flightFareRepository = flightFareRepository;
    this.flightInventoryAvailabilityService = flightInventoryAvailabilityService;
    this.flightPricingService = flightPricingService;
    this.paymentRepository = paymentRepository;
    this.ticketFinancialSnapshotService = ticketFinancialSnapshotService;
    this.ticketFinancialSnapshotRepository = ticketFinancialSnapshotRepository;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.bookingProperties = bookingProperties;
  }

  @Transactional(readOnly = true)
  public FlightChangeQuoteResponse quote(
      Long userId, Long bookingId, FlightChangeQuoteRequest request) {
    validateTicketIds(request.ticketIds());
    BookingEntity booking = requireBooking(userId, bookingId);
    validateBookingChangeable(booking);

    List<TicketEntity> tickets =
        ticketRepository.findByBookingIdAndTicketIdInWithDetails(bookingId, request.ticketIds());
    if (tickets.size() != request.ticketIds().size()) {
      throw new AppException("TICKET_NOT_FOUND", "One or more tickets were not found", 404);
    }

    validateTicketsForCurrentFlight(tickets, request.currentFlightId());
    validateTicketsChangeable(tickets);
    validateCurrentFareRules(tickets);
    validateTimeRestriction(tickets);
    validatePassengerRules(tickets);

    FlightFareEntity newFare = requireNewFare(request.newFareId(), request.newFlightId());
    validateNewFlightAgainstCurrent(tickets.getFirst().getFlight(), newFare.getFlight());
    ensureSaleable(newFare);

    int seatedPassengers = countSeatedPassengers(tickets);
    if (!flightInventoryAvailabilityService.hasEnoughSeats(
        newFare.getFlight().getId(), newFare.getFareClass().getId(), seatedPassengers)) {
      throw new AppException(
          "SEAT_UNAVAILABLE",
          "Selected flight does not have enough available seats for this change",
          409);
    }

    validateConnectionRule(bookingId, request.currentFlightId(), newFare.getFlight());

    ticketFinancialSnapshotService.ensureSnapshotsForBooking(booking);
    Map<Long, TicketFinancialSnapshotEntity> snapshotByTicketId =
        ticketFinancialSnapshotRepository.findByBookingIdWithDetails(bookingId).stream()
            .collect(
                LinkedHashMap::new,
                (map, snapshot) -> map.put(snapshot.getTicket().getId(), snapshot),
                Map::putAll);

    PricingCounts counts = pricingCounts(tickets);
    BigDecimal repricedTotal =
        normalizeMoney(
            flightPricingService.calculateTotalPrice(
                money(newFare.getTotalPrice()), counts.adult(), counts.child(), counts.infant()));

    BigDecimal totalNewAmount = ZERO;
    BigDecimal totalOriginalAmount = ZERO;
    BigDecimal totalChangeFee = ZERO;
    List<FlightChangeQuoteResponse.ItemResponse> items = new ArrayList<>(tickets.size());
    BigDecimal perAdultNewTotal = money(newFare.getTotalPrice());

    for (TicketEntity ticket : tickets) {
      TicketFinancialSnapshotEntity snapshot = snapshotByTicketId.get(ticket.getId());
      if (snapshot == null) {
        throw new AppException(
            "PRICE_RECALCULATION_FAILED",
            "Missing financial snapshot for one or more tickets",
            409);
      }
      BigDecimal originalAmount = money(snapshot.getFareNetAmount());
      BigDecimal newAmount = normalizeMoney(perAdultNewTotal.multiply(passengerRate(ticket)));
      BigDecimal changeFee = money(ticket.getFareClass().getChangeFee());
      BigDecimal fareDifference = normalizeMoney(newAmount.subtract(originalAmount));
      BigDecimal netAmount = normalizeMoney(fareDifference.add(changeFee));

      totalOriginalAmount = normalizeMoney(totalOriginalAmount.add(originalAmount));
      totalNewAmount = normalizeMoney(totalNewAmount.add(newAmount));
      totalChangeFee = normalizeMoney(totalChangeFee.add(changeFee));

      items.add(
          new FlightChangeQuoteResponse.ItemResponse(
              ticket.getId(),
              ticket.getPassenger().getId(),
              fullName(ticket.getPassenger()),
              normalizePassengerType(ticket.getPassenger().getPassengerType()),
              true,
              null,
              originalAmount,
              newAmount,
              fareDifference,
              changeFee,
              ZERO,
              netAmount));
    }

    totalNewAmount = normalizeMoney(totalNewAmount);
    if (repricedTotal.compareTo(totalNewAmount) != 0) {
      totalNewAmount = repricedTotal;
    }
    BigDecimal totalFareDifference = normalizeMoney(totalNewAmount.subtract(totalOriginalAmount));
    BigDecimal netAmount = normalizeMoney(totalFareDifference.add(totalChangeFee));
    String settlementType = settlementType(netAmount);
    FlightChangeQuoteResponse.ImpactsResponse impacts = impacts(bookingId, tickets);
    OffsetDateTime expiresAt =
        OffsetDateTime.now().plusMinutes(bookingProperties.flightChangeQuoteExpirationMinutes());
    String quoteToken = generateQuoteToken();

    FlightChangeQuoteResponse response =
        new FlightChangeQuoteResponse(
            bookingId,
            booking.getCurrency(),
            quoteToken,
            expiresAt,
            true,
            settlementType,
            toFlightSummary(tickets.getFirst().getFlight()),
            toFlightSummary(newFare.getFlight()),
            items,
            new FlightChangeQuoteResponse.SummaryResponse(
                totalOriginalAmount,
                totalNewAmount,
                totalFareDifference,
                totalChangeFee,
                ZERO,
                ZERO,
                netAmount),
            impacts,
            List.of(
                "This quote is valid for "
                    + bookingProperties.flightChangeQuoteExpirationMinutes()
                    + " minutes."));
    saveQuoteSnapshot(
        new QuoteSnapshot(
            quoteToken,
            bookingId,
            request.ticketIds(),
            request.currentFlightId(),
            request.newFlightId(),
            request.newFareId(),
            expiresAt,
            response));
    return response;
  }

  public QuoteSnapshotView requireQuoteSnapshot(String quoteToken) {
    if (!StringUtils.hasText(quoteToken)) {
      throw new AppException("FLIGHT_CHANGE_QUOTE_EXPIRED", "Flight change quote has expired", 409);
    }
    QuoteSnapshot snapshot = readQuoteSnapshot(quoteToken.trim());
    if (snapshot == null || snapshot.response() == null) {
      throw new AppException("FLIGHT_CHANGE_QUOTE_EXPIRED", "Flight change quote has expired", 409);
    }
    return new QuoteSnapshotView(
        snapshot.quoteToken(),
        snapshot.bookingId(),
        List.copyOf(snapshot.ticketIds()),
        snapshot.currentFlightId(),
        snapshot.newFlightId(),
        snapshot.newFareId(),
        snapshot.expiresAt(),
        snapshot.response());
  }

  private void validateTicketIds(List<Long> ticketIds) {
    Set<Long> uniqueIds = new HashSet<>();
    for (Long ticketId : ticketIds) {
      if (!uniqueIds.add(ticketId)) {
        throw new AppException("VALIDATION_FAILED", "ticketIds must be unique", 400);
      }
    }
  }

  private BookingEntity requireBooking(Long userId, Long bookingId) {
    return userId == null
        ? bookingRepository
            .findById(bookingId)
            .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404))
        : bookingRepository
            .findByIdAndUserId(bookingId, userId)
            .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404));
  }

  private void validateBookingChangeable(BookingEntity booking) {
    String status = normalizeStatus(booking.getStatus());
    if (BOOKING_STATUS_CHECKED_IN_LOCKED.equals(status)) {
      throw new AppException(
          "TICKET_STATUS_NOT_CHANGEABLE",
          "Booking has already been checked in and cannot be changed online",
          409);
    }
    if (!BOOKING_STATUS_CONFIRMED.equals(status)) {
      throw new AppException(
          "BOOKING_NOT_ELIGIBLE_FOR_CHANGE", "Only confirmed bookings can be changed online", 409);
    }
    boolean hasSuccessfulPayment =
        !paymentRepository
            .findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
                booking.getId(), CHANGEABLE_PAYMENT_STATUSES)
            .isEmpty();
    if (!hasSuccessfulPayment) {
      throw new AppException(
          "BOOKING_NOT_ELIGIBLE_FOR_CHANGE",
          "Booking does not have a completed payment for flight changes",
          409);
    }
  }

  private void validateTicketsForCurrentFlight(List<TicketEntity> tickets, Long currentFlightId) {
    for (TicketEntity ticket : tickets) {
      if (!Objects.equals(ticket.getFlight().getId(), currentFlightId)) {
        throw new AppException(
            "INVALID_FLIGHT_CHANGE_ROUTE",
            "Selected tickets must belong to the current flight being changed",
            409);
      }
    }
  }

  private void validateTicketsChangeable(List<TicketEntity> tickets) {
    for (TicketEntity ticket : tickets) {
      if (!TICKET_STATUS_ISSUED.equals(normalizeStatus(ticket.getStatus()))) {
        throw new AppException(
            "TICKET_STATUS_NOT_CHANGEABLE",
            "One or more tickets are not eligible for online changes",
            409);
      }
    }
  }

  private void validateCurrentFareRules(List<TicketEntity> tickets) {
    for (TicketEntity ticket : tickets) {
      if (!ticket.getFareClass().isChangeable()) {
        throw new AppException(
            "FARE_NOT_CHANGEABLE", "The selected fare does not allow flight changes", 409);
      }
    }
  }

  private void validateTimeRestriction(List<TicketEntity> tickets) {
    OffsetDateTime now = OffsetDateTime.now();
    long closeMinutes = bookingProperties.flightChangeCloseMinutes();
    for (TicketEntity ticket : tickets) {
      if (ticket.getFlight().getDepartureTime() != null
          && !ticket.getFlight().getDepartureTime().isAfter(now.plusMinutes(closeMinutes))) {
        throw new AppException(
            "CHANGE_WINDOW_CLOSED",
            "Flight changes are no longer allowed within the configured time window",
            409);
      }
    }
  }

  private void validatePassengerRules(List<TicketEntity> tickets) {
    boolean hasInfant = tickets.stream().anyMatch(ticket -> isPassengerType(ticket, "INFANT"));
    boolean hasAdult = tickets.stream().anyMatch(ticket -> isPassengerType(ticket, "ADULT"));
    if (hasInfant && !hasAdult) {
      throw new AppException(
          "PASSENGER_RULE_VIOLATION",
          "INFANT passengers must be changed together with at least one ADULT passenger",
          409);
    }
  }

  private FlightFareEntity requireNewFare(Long newFareId, Long newFlightId) {
    FlightFareEntity fare =
        flightFareRepository
            .findByIdWithDetails(newFareId)
            .orElseThrow(
                () ->
                    new AppException(
                        "NEW_FLIGHT_NOT_FOUND",
                        "Selected fare for the new flight was not found",
                        404));
    if (!Objects.equals(fare.getFlight().getId(), newFlightId)) {
      throw new AppException(
          "NEW_FLIGHT_NOT_FOUND", "Selected fare does not belong to the requested flight", 404);
    }
    return fare;
  }

  private void validateNewFlightAgainstCurrent(FlightEntity currentFlight, FlightEntity newFlight) {
    if (Objects.equals(currentFlight.getId(), newFlight.getId())) {
      throw new AppException(
          "INVALID_FLIGHT_CHANGE_ROUTE",
          "The new flight must be different from the current flight",
          409);
    }
    if (!Objects.equals(
            currentFlight.getDepartureAirport().getId(), newFlight.getDepartureAirport().getId())
        || !Objects.equals(
            currentFlight.getArrivalAirport().getId(), newFlight.getArrivalAirport().getId())) {
      throw new AppException(
          "INVALID_FLIGHT_CHANGE_ROUTE",
          "The new flight must use the same departure and arrival airports",
          409);
    }
  }

  private void validateConnectionRule(
      Long bookingId, Long currentFlightId, FlightEntity newFlight) {
    List<BookingFareHoldEntity> holds =
        bookingFareHoldRepository.findByBookingIdOrderByIdAsc(bookingId);
    if (holds.size() <= 1) {
      return;
    }

    List<FlightEntity> flights =
        holds.stream()
            .map(hold -> hold.getFlightFare().getFlight())
            .sorted(java.util.Comparator.comparing(FlightEntity::getDepartureTime))
            .toList();
    List<FlightEntity> updatedFlights = new ArrayList<>(flights.size());
    boolean replaced = false;
    for (FlightEntity flight : flights) {
      if (Objects.equals(flight.getId(), currentFlightId)) {
        updatedFlights.add(newFlight);
        replaced = true;
      } else {
        updatedFlights.add(flight);
      }
    }
    if (!replaced) {
      return;
    }

    long minimumConnectionMinutes = bookingProperties.minimumConnectionMinutes();
    for (int index = 0; index < updatedFlights.size() - 1; index++) {
      FlightEntity previous = updatedFlights.get(index);
      FlightEntity next = updatedFlights.get(index + 1);
      if (!Objects.equals(
          previous.getArrivalAirport().getId(), next.getDepartureAirport().getId())) {
        throw new AppException(
            "CONNECTION_INVALID",
            "Changing this flight would break the itinerary airport connection",
            409);
      }
      if (previous.getArrivalTime() == null || next.getDepartureTime() == null) {
        continue;
      }
      long layoverMinutes =
          Duration.between(previous.getArrivalTime(), next.getDepartureTime()).toMinutes();
      if (layoverMinutes < minimumConnectionMinutes) {
        throw new AppException(
            "CONNECTION_INVALID",
            "Changing this flight would violate the minimum connection time",
            409);
      }
    }
  }

  private PricingCounts pricingCounts(List<TicketEntity> tickets) {
    int adult = 0;
    int child = 0;
    int infant = 0;
    for (TicketEntity ticket : tickets) {
      String passengerType = normalizePassengerType(ticket.getPassenger().getPassengerType());
      switch (passengerType) {
        case "CHILD" -> child++;
        case "INFANT" -> infant++;
        default -> adult++;
      }
    }
    return new PricingCounts(adult, child, infant);
  }

  private int countSeatedPassengers(List<TicketEntity> tickets) {
    int count = 0;
    for (TicketEntity ticket : tickets) {
      if (!isPassengerType(ticket, "INFANT")) {
        count++;
      }
    }
    return count;
  }

  private FlightChangeQuoteResponse.ImpactsResponse impacts(
      Long bookingId, List<TicketEntity> tickets) {
    Set<Long> passengerIds = new HashSet<>();
    Long currentFlightId = tickets.getFirst().getFlight().getId();
    for (TicketEntity ticket : tickets) {
      passengerIds.add(ticket.getPassenger().getId());
    }
    String seatAction =
        passengerSeatAssignmentRepository
                .findByBookingIdAndPassengerIdInAndStatusIn(
                    bookingId, passengerIds, ACTIVE_ASSIGNMENT_STATUSES)
                .stream()
                .anyMatch(
                    assignment ->
                        Objects.equals(
                            assignment.getFlightSeat().getFlight().getId(), currentFlightId))
            ? "RESELECT_REQUIRED"
            : "NO_CHANGE";
    String serviceAction =
        bookingServiceRepository.findDetailedByBookingId(bookingId).stream()
                .map(BookingServiceEntity::getPassenger)
                .filter(Objects::nonNull)
                .map(PassengerEntity::getId)
                .anyMatch(passengerIds::contains)
            ? "REVIEW_REQUIRED"
            : "NO_CHANGE";
    return new FlightChangeQuoteResponse.ImpactsResponse(seatAction, serviceAction);
  }

  private FlightChangeQuoteResponse.FlightSummaryResponse toFlightSummary(FlightEntity flight) {
    return new FlightChangeQuoteResponse.FlightSummaryResponse(
        flight.getId(),
        flight.getFlightNumber(),
        flight.getDepartureAirport().getCode(),
        flight.getArrivalAirport().getCode(),
        flight.getDepartureTime());
  }

  private void ensureSaleable(FlightFareEntity fare) {
    String fareStatus = normalizeStatus(fare.getStatus());
    String flightStatus = normalizeStatus(fare.getFlight().getStatus());
    if (!"AVAILABLE".equals(fareStatus)) {
      throw new AppException("SEAT_UNAVAILABLE", "Selected fare is no longer available", 409);
    }
    if (!"SCHEDULED".equals(flightStatus) && !"ON_TIME".equals(flightStatus)) {
      throw new AppException("NEW_FLIGHT_NOT_FOUND", "Selected flight is not saleable", 409);
    }
  }

  private BigDecimal passengerRate(TicketEntity ticket) {
    String passengerType = normalizePassengerType(ticket.getPassenger().getPassengerType());
    if ("CHILD".equals(passengerType)) {
      return CHILD_RATE;
    }
    if ("INFANT".equals(passengerType)) {
      return INFANT_RATE;
    }
    return BigDecimal.ONE;
  }

  private boolean isPassengerType(TicketEntity ticket, String passengerType) {
    return passengerType.equals(normalizePassengerType(ticket.getPassenger().getPassengerType()));
  }

  private String normalizePassengerType(String passengerType) {
    if (!StringUtils.hasText(passengerType)) {
      return "";
    }
    return passengerType.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return "";
    }
    return status.trim().toUpperCase(Locale.ROOT);
  }

  private String fullName(PassengerEntity passenger) {
    String firstName = passenger.getFirstName() == null ? "" : passenger.getFirstName().trim();
    String lastName = passenger.getLastName() == null ? "" : passenger.getLastName().trim();
    return (firstName + " " + lastName).trim();
  }

  private String settlementType(BigDecimal netAmount) {
    if (netAmount.signum() > 0) {
      return "COLLECT_PAYMENT";
    }
    if (netAmount.signum() < 0) {
      return "REFUND";
    }
    return "NO_CHANGE";
  }

  private BigDecimal money(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal normalizeMoney(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  private String generateQuoteToken() {
    return "FCQ_"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
  }

  private String quoteKey(String quoteToken) {
    return "flight-change-quote:" + quoteToken;
  }

  private QuoteSnapshot readQuoteSnapshot(String quoteToken) {
    String payload = redisTemplate.opsForValue().get(quoteKey(quoteToken));
    if (!StringUtils.hasText(payload)) {
      return null;
    }
    try {
      return objectMapper.readValue(payload, QuoteSnapshot.class);
    } catch (JsonProcessingException exception) {
      throw new AppException(
          "INTERNAL_SERVER_ERROR", "Unable to deserialize flight change quote", 500);
    }
  }

  private void saveQuoteSnapshot(QuoteSnapshot snapshot) {
    writeValue(
        quoteKey(snapshot.quoteToken()),
        snapshot,
        Duration.ofMinutes(bookingProperties.flightChangeQuoteExpirationMinutes()));
  }

  private void writeValue(String key, Object payload, Duration ttl) {
    try {
      redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), ttl);
    } catch (JsonProcessingException exception) {
      throw new AppException(
          "INTERNAL_SERVER_ERROR", "Unable to serialize flight change quote", 500);
    }
  }

  private record PricingCounts(int adult, int child, int infant) {}

  private record QuoteSnapshot(
      String quoteToken,
      Long bookingId,
      List<Long> ticketIds,
      Long currentFlightId,
      Long newFlightId,
      Long newFareId,
      OffsetDateTime expiresAt,
      FlightChangeQuoteResponse response) {}

  public record QuoteSnapshotView(
      String quoteToken,
      Long bookingId,
      List<Long> ticketIds,
      Long currentFlightId,
      Long newFlightId,
      Long newFareId,
      OffsetDateTime expiresAt,
      FlightChangeQuoteResponse response) {}
}
