package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.BookingServiceEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.exception.PaymentNotFoundException;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentResultService {

  private static final String BOOKING_STATUS_CONFIRMED = "CONFIRMED";
  private static final String PAYMENT_STATUS_SUCCESS = "SUCCESS";
  private static final String PAYMENT_STATUS_PENDING = "PENDING";
  private static final Set<String> ERROR_PAYMENT_STATUSES =
      Set.of("FAILED", "CANCELLED", "CANCELED", "EXPIRED");
  private static final int NEXT_POLL_AFTER_SECONDS = 3;

  private final PaymentRepository paymentRepository;
  private final TicketRepository ticketRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerRepository passengerRepository;
  private final BookingContactRepository bookingContactRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;

  public PaymentResultService(
      PaymentRepository paymentRepository,
      TicketRepository ticketRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerRepository passengerRepository,
      BookingContactRepository bookingContactRepository,
      BookingServiceRepository bookingServiceRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository) {
    this.paymentRepository = paymentRepository;
    this.ticketRepository = ticketRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerRepository = passengerRepository;
    this.bookingContactRepository = bookingContactRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
  }

  @Transactional(readOnly = true)
  public PaymentResultStatusView getResultStatusPublic(Long paymentId) {
    PaymentEntity payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    return toResultStatus(payment);
  }

  @Transactional(readOnly = true)
  public PaymentResultStatusView getResultStatusForUser(Long paymentId, Long userId) {
    PaymentEntity payment =
        paymentRepository
            .findByIdAndBookingUserId(paymentId, userId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    return toResultStatus(payment);
  }

  @Transactional
  public PaymentResultSummaryView getResultSummaryPublic(Long paymentId) {
    PaymentEntity payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    return buildSummary(payment);
  }

  @Transactional
  public PaymentResultSummaryView getResultSummaryForUser(Long paymentId, Long userId) {
    PaymentEntity payment =
        paymentRepository
            .findByIdAndBookingUserId(paymentId, userId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    return buildSummary(payment);
  }

  private PaymentResultStatusView toResultStatus(PaymentEntity payment) {
    BookingEntity booking = payment.getBooking();
    String paymentStatus = normalize(payment.getPaymentStatus());
    String bookingStatus = booking == null ? "" : normalize(booking.getStatus());
    String resultPage = resolveResultPage(paymentStatus, bookingStatus);
    PaymentResultStatusView.FailureView failure =
        "ERROR".equals(resultPage)
            ? new PaymentResultStatusView.FailureView(
                "PAYMENT_RESULT_ERROR", buildFailureMessage(paymentStatus, bookingStatus))
            : null;
    return new PaymentResultStatusView(
        payment.getId(),
        booking == null ? null : booking.getId(),
        paymentStatus,
        bookingStatus,
        resultPage,
        new PaymentResultStatusView.PollingView(
            "PENDING".equals(resultPage),
            "PENDING".equals(resultPage) ? NEXT_POLL_AFTER_SECONDS : null),
        failure);
  }

  private PaymentResultSummaryView buildSummary(PaymentEntity payment) {
    BookingEntity booking = payment.getBooking();
    String paymentStatus = normalize(payment.getPaymentStatus());
    String bookingStatus = booking == null ? "" : normalize(booking.getStatus());
    String resultPage = resolveResultPage(paymentStatus, bookingStatus);
    if ("PENDING".equals(resultPage)) {
      throw new AppException("PAYMENT_NOT_COMPLETED", "Payment has not completed yet", 409);
    }
    if (!"SUCCESS".equals(resultPage)) {
      throw new AppException("PAYMENT_NOT_SUCCESSFUL", "Payment is not successful", 409);
    }

    Long bookingId = booking.getId();
    List<BookingFareHoldEntity> confirmedHolds = confirmedFareHolds(bookingId);
    List<PassengerEntity> passengers = passengerRepository.findByBookingIdOrderById(bookingId);
    List<BookingContactEntity> contacts =
        bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(bookingId);
    List<BookingServiceEntity> services =
        bookingServiceRepository.findDetailedByBookingId(bookingId);
    List<PassengerSeatAssignmentEntity> seatAssignments =
        passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(bookingId, List.of("BOOKED"));
    List<TicketEntity> tickets = ticketRepository.findByBookingIdWithDetails(bookingId);

    List<PaymentResultSummaryView.SegmentView> segmentViews = buildSegments(confirmedHolds);
    Map<Long, String> segmentKeyByFlightId =
        segmentViews.stream()
            .collect(
                Collectors.toMap(
                    PaymentResultSummaryView.SegmentView::flightId,
                    PaymentResultService::segmentKey));
    Map<String, PassengerSeatAssignmentEntity> seatByPassengerFlight =
        seatAssignments.stream()
            .collect(
                Collectors.toMap(
                    seat ->
                        ticketKey(
                            seat.getPassenger().getId(),
                            seat.getFlightSeat().getFlight().getId()),
                    seat -> seat,
                    (left, right) -> left,
                    LinkedHashMap::new));
    Map<String, TicketEntity> ticketByPassengerFlight =
        tickets.stream()
            .collect(
                Collectors.toMap(
                    ticket -> ticketKey(ticket.getPassenger().getId(), ticket.getFlight().getId()),
                    ticket -> ticket,
                    (left, right) -> left,
                    LinkedHashMap::new));

    PaymentResultSummaryView.ContactView contactView = buildContact(booking, contacts, passengers);
    List<PaymentResultSummaryView.PassengerView> passengerViews =
        buildPassengers(
            passengers,
            segmentViews,
            services,
            seatByPassengerFlight,
            ticketByPassengerFlight);
    List<PaymentResultSummaryView.TicketView> ticketViews =
        buildTickets(tickets, segmentKeyByFlightId, seatByPassengerFlight);

    SummaryTotals totals = computeTotals(booking, confirmedHolds, services, seatAssignments);

    return new PaymentResultSummaryView(
        payment.getId(),
        bookingId,
        paymentStatus,
        bookingStatus,
        new PaymentResultSummaryView.BookingView(
            booking.getPnrCode(),
            booking.getCreatedAt(),
            payment.getPaidAt(),
            booking.getPnrExpiresAt(),
            booking.getCurrency(),
            totals.total()),
        contactView,
        new PaymentResultSummaryView.PaymentView(
            payment.getPaymentMethod(),
            payment.getTransactionId(),
            payment.getAmount(),
            payment.getCurrency()),
        new PaymentResultSummaryView.SummaryView(
            passengers.size(), segmentViews.size(), totals.total(), booking.getCurrency()),
        segmentViews,
        passengerViews,
        ticketViews,
        buildFareBreakdown(
            booking,
            passengers,
            confirmedHolds,
            services,
            seatAssignments,
            segmentKeyByFlightId));
  }

  private static String segmentKey(PaymentResultSummaryView.SegmentView segment) {
    return segment.segmentKey();
  }

  private List<BookingFareHoldEntity> confirmedFareHolds(Long bookingId) {
    List<BookingFareHoldEntity> holds =
        bookingFareHoldRepository.findByBookingIdOrderByIdAsc(bookingId).stream()
            .filter(item -> BOOKING_STATUS_CONFIRMED.equalsIgnoreCase(normalize(item.getStatus())))
            .sorted(
                Comparator.comparing(
                        (BookingFareHoldEntity item) ->
                            item.getFlightFare().getFlight().getDepartureTime(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(item -> item.getFlightFare().getFlight().getId()))
            .toList();
    if (!holds.isEmpty()) {
      return holds;
    }
    throw new AppException("BOOKING_FARE_NOT_CONFIRMED", "Booking fare is not confirmed", 409);
  }

  private List<PaymentResultSummaryView.SegmentView> buildSegments(
      List<BookingFareHoldEntity> holds) {
    List<PaymentResultSummaryView.SegmentView> segments = new ArrayList<>();
    for (int index = 0; index < holds.size(); index++) {
      BookingFareHoldEntity hold = holds.get(index);
      FlightFareEntity fare = hold.getFlightFare();
      FlightEntity flight = fare.getFlight();
      FareClassEntity fareClass = fare.getFareClass();
      segments.add(
          new PaymentResultSummaryView.SegmentView(
              segmentKey(flight.getId()),
              flight.getId(),
              journeyType(index, holds.size()),
              flight.getFlightNumber(),
              flight.getAirline() == null ? null : flight.getAirline().getName(),
              toLocation(
                  flight.getDepartureAirport(),
                  flight.getDepartureTerminal(),
                  flight.getDepartureTime()),
              toLocation(
                  flight.getArrivalAirport(),
                  flight.getArrivalTerminal(),
                  flight.getArrivalTime()),
              durationMinutes(flight.getDepartureTime(), flight.getArrivalTime()),
              0,
              new PaymentResultSummaryView.FareView(
                  fareClass.getCode(),
                  fareClass.getName(),
                  fareClass.getCabinClass(),
                  fareClass.getBaggageCheckinKg(),
                  fareClass.getBaggageHandKg())));
    }
    return segments;
  }

  private PaymentResultSummaryView.LocationView toLocation(
      AirportEntity airport, String terminal, OffsetDateTime time) {
    return new PaymentResultSummaryView.LocationView(
        airport == null ? null : airport.getCode(),
        airport == null ? null : airport.getName(),
        airport == null ? null : airport.getCity(),
        terminal,
        time);
  }

  private List<PaymentResultSummaryView.PassengerView> buildPassengers(
      List<PassengerEntity> passengers,
      List<PaymentResultSummaryView.SegmentView> segments,
      List<BookingServiceEntity> services,
      Map<String, PassengerSeatAssignmentEntity> seatByPassengerFlight,
      Map<String, TicketEntity> ticketByPassengerFlight) {
    String singleSegmentKey = segments.size() == 1 ? segments.getFirst().segmentKey() : null;
    return passengers.stream()
        .map(
            passenger ->
                new PaymentResultSummaryView.PassengerView(
                    passenger.getId(),
                    titleFor(passenger),
                    passenger.getFirstName(),
                    passenger.getLastName(),
                    fullNameWithTitle(passenger),
                    passenger.getPassengerType(),
                    segments.stream()
                        .map(
                            segment -> {
                              String key = ticketKey(passenger.getId(), segment.flightId());
                              PassengerSeatAssignmentEntity seatAssignment =
                                  seatByPassengerFlight.get(key);
                              TicketEntity ticket = ticketByPassengerFlight.get(key);
                              return new PaymentResultSummaryView.PassengerSegmentView(
                                  segment.segmentKey(),
                                  seatView(
                                      seatAssignment == null
                                          ? null
                                          : seatAssignment.getFlightSeat()),
                                  ticket == null
                                      ? null
                                      : new PaymentResultSummaryView.EmbeddedTicketView(
                                          ticket.getId(),
                                          ticket.getTicketNumber(),
                                          ticket.getStatus(),
                                          ticket.getIssuedAt()));
                            })
                        .toList(),
                    services.stream()
                        .filter(
                            service ->
                                Objects.equals(service.getPassenger().getId(), passenger.getId()))
                        .map(
                            service ->
                                new PaymentResultSummaryView.PassengerServiceView(
                                    service.getService().getId(),
                                    service.getService().getType(),
                                    service.getService().getName(),
                                    service.getServiceOption() == null
                                        ? null
                                        : service.getServiceOption().getId(),
                                    service.getServiceOption() == null
                                        ? null
                                        : service.getServiceOption().getName(),
                                    service.getQuantity(),
                                    nonNullMoney(service.getPrice()),
                                    bookingCurrency(passenger),
                                    singleSegmentKey))
                        .toList()))
        .toList();
  }

  private List<PaymentResultSummaryView.TicketView> buildTickets(
      List<TicketEntity> tickets,
      Map<Long, String> segmentKeyByFlightId,
      Map<String, PassengerSeatAssignmentEntity> seatByPassengerFlight) {
    return tickets.stream()
        .sorted(
            Comparator.comparing((TicketEntity ticket) -> ticket.getPassenger().getId())
                .thenComparing(
                    ticket -> ticket.getFlight().getDepartureTime(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ticket -> ticket.getFlight().getId()))
        .map(
            ticket -> {
              String key = ticketKey(ticket.getPassenger().getId(), ticket.getFlight().getId());
              PassengerSeatAssignmentEntity seatAssignment = seatByPassengerFlight.get(key);
              return new PaymentResultSummaryView.TicketView(
                  ticket.getId(),
                  ticket.getTicketNumber(),
                  ticket.getStatus(),
                  ticket.getIssuedAt(),
                  ticket.getPassenger().getId(),
                  segmentKeyByFlightId.get(ticket.getFlight().getId()),
                  ticket.getFlight().getId(),
                  seatView(
                      seatAssignment == null
                          ? ticket.getFlightSeat()
                          : seatAssignment.getFlightSeat()));
            })
        .toList();
  }

  private PaymentResultSummaryView.ContactView buildContact(
      BookingEntity booking,
      List<BookingContactEntity> contacts,
      List<PassengerEntity> passengers) {
    Optional<BookingContactEntity> primary =
        contacts.stream().filter(BookingContactEntity::isPrimary).findFirst();
    PassengerEntity leadPassenger = passengers.isEmpty() ? null : passengers.getFirst();
    return new PaymentResultSummaryView.ContactView(
        leadPassenger == null ? null : fullName(leadPassenger),
        primary
            .map(BookingContactEntity::getEmail)
            .filter(value -> value != null && !value.isBlank())
            .orElse(booking.getContactEmail()),
        primary
            .map(BookingContactEntity::getPhone)
            .filter(value -> value != null && !value.isBlank())
            .orElse(booking.getContactPhone()));
  }

  private PaymentResultSummaryView.FareBreakdownView buildFareBreakdown(
      BookingEntity booking,
      List<PassengerEntity> passengers,
      List<BookingFareHoldEntity> holds,
      List<BookingServiceEntity> services,
      List<PassengerSeatAssignmentEntity> seatAssignments,
      Map<Long, String> segmentKeyByFlightId) {
    SummaryTotals totals = computeTotals(booking, holds, services, seatAssignments);
    int pricedPassengers = (int) passengers.stream().filter(this::isFarePayingPassenger).count();
    int divisor = Math.max(pricedPassengers, 1);

    Map<Long, List<BookingServiceEntity>> servicesByPassenger =
        services.stream()
            .collect(
                Collectors.groupingBy(
                    service -> service.getPassenger().getId(),
                    LinkedHashMap::new,
                    Collectors.toList()));
    Map<String, BigDecimal> seatSurchargeByPassengerFlight =
        seatAssignments.stream()
            .collect(
                Collectors.toMap(
                    seat ->
                        ticketKey(
                            seat.getPassenger().getId(),
                            seat.getFlightSeat().getFlight().getId()),
                    seat -> nonNullMoney(seat.getSurchargeAmount()),
                    BigDecimal::add,
                    LinkedHashMap::new));

    List<PaymentResultSummaryView.PassengerFareBreakdownView> byPassenger =
        passengers.stream()
            .map(
                passenger -> {
                  List<PaymentResultSummaryView.PassengerSegmentFareBreakdownView> bySegment =
                      new ArrayList<>();
                  BigDecimal passengerFareTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                  BigDecimal passengerServiceTotal =
                      servicesByPassenger.getOrDefault(passenger.getId(), List.of()).stream()
                          .map(BookingServiceEntity::getPrice)
                          .map(PaymentResultService::nonNullMoney)
                          .reduce(
                              BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                              BigDecimal::add);
                  BigDecimal passengerServiceVat =
                      passengerServiceTotal
                          .multiply(new BigDecimal("0.08"))
                          .setScale(2, RoundingMode.HALF_UP);

                  for (BookingFareHoldEntity hold : holds) {
                    FlightFareEntity fare = hold.getFlightFare();
                    FlightEntity flight = fare.getFlight();
                    String segmentKey = segmentKeyByFlightId.get(flight.getId());
                    BigDecimal totalBaseForHold =
                        nonNullMoney(fare.getBasePrice())
                            .multiply(BigDecimal.valueOf(Math.max(hold.getQuantity(), 1)))
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalTaxForHold =
                        nonNullMoney(fare.getTotalTaxAmount())
                            .multiply(BigDecimal.valueOf(Math.max(hold.getQuantity(), 1)))
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal baseShare =
                        isFarePayingPassenger(passenger)
                            ? totalBaseForHold.divide(
                                BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal taxShare =
                        isFarePayingPassenger(passenger)
                            ? totalTaxForHold.divide(
                                BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal seatShare =
                        seatSurchargeByPassengerFlight.getOrDefault(
                            ticketKey(passenger.getId(), flight.getId()),
                            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    BigDecimal segmentTotal = baseShare.add(taxShare).add(seatShare);
                    passengerFareTotal = passengerFareTotal.add(segmentTotal);
                    bySegment.add(
                        new PaymentResultSummaryView.PassengerSegmentFareBreakdownView(
                            segmentKey,
                            routeLabel(flight),
                            new PaymentResultSummaryView.SegmentFareAmountsView(
                                fare.getFareClass().getCode(),
                                baseShare,
                                taxShare,
                                baseShare.add(taxShare)),
                            List.of(
                                new PaymentResultSummaryView.LabeledAmountView(
                                    "Taxes and fees", taxShare)),
                            servicesByPassenger.getOrDefault(passenger.getId(), List.of()).stream()
                                .map(
                                    service ->
                                        new PaymentResultSummaryView.LabeledAmountView(
                                            serviceLabel(service),
                                            nonNullMoney(service.getPrice())))
                                .toList(),
                            segmentTotal.add(passengerServiceTotal).add(passengerServiceVat)));
                  }

                  BigDecimal passengerTotal =
                      passengerFareTotal.add(passengerServiceTotal).add(passengerServiceVat);
                  return new PaymentResultSummaryView.PassengerFareBreakdownView(
                      passenger.getId(), passengerLabel(passenger), bySegment, passengerTotal);
                })
            .toList();

    return new PaymentResultSummaryView.FareBreakdownView(
        booking.getCurrency(),
        new PaymentResultSummaryView.FareTotalsView(
            totals.baseFare(),
            totals.discount(),
            totals.tax(),
            totals.serviceFee(),
            totals.seatFee(),
            totals.total()),
        byPassenger);
  }

  private SummaryTotals computeTotals(
      BookingEntity booking,
      List<BookingFareHoldEntity> holds,
      List<BookingServiceEntity> services,
      List<PassengerSeatAssignmentEntity> seatAssignments) {
    BigDecimal baseFare =
        holds.stream()
            .map(
                hold ->
                    nonNullMoney(hold.getFlightFare().getBasePrice())
                        .multiply(BigDecimal.valueOf(Math.max(hold.getQuantity(), 1))))
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    BigDecimal tax =
        holds.stream()
            .map(
                hold ->
                    nonNullMoney(hold.getFlightFare().getTotalTaxAmount())
                        .multiply(BigDecimal.valueOf(Math.max(hold.getQuantity(), 1))))
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    BigDecimal serviceFee =
        services.stream()
            .map(BookingServiceEntity::getPrice)
            .map(PaymentResultService::nonNullMoney)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    BigDecimal seatFee =
        seatAssignments.stream()
            .map(PassengerSeatAssignmentEntity::getSurchargeAmount)
            .map(PaymentResultService::nonNullMoney)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    return new SummaryTotals(
        baseFare,
        nonNullMoney(booking.getDiscountAmount()),
        tax,
        serviceFee,
        seatFee,
        nonNullMoney(booking.getTotalAmount()));
  }

  private static String segmentKey(Long flightId) {
    return "flight-" + flightId;
  }

  private String resolveResultPage(String paymentStatus, String bookingStatus) {
    if (PAYMENT_STATUS_SUCCESS.equals(paymentStatus)
        && BOOKING_STATUS_CONFIRMED.equals(bookingStatus)) {
      return "SUCCESS";
    }
    if (PAYMENT_STATUS_PENDING.equals(paymentStatus)) {
      return "PENDING";
    }
    if (ERROR_PAYMENT_STATUSES.contains(paymentStatus)) {
      return "ERROR";
    }
    if (!BOOKING_STATUS_CONFIRMED.equals(bookingStatus)) {
      return "ERROR";
    }
    return "ERROR";
  }

  private String buildFailureMessage(String paymentStatus, String bookingStatus) {
    if (ERROR_PAYMENT_STATUSES.contains(paymentStatus)) {
      return "Payment ended with status " + paymentStatus;
    }
    return "Booking is in status " + bookingStatus;
  }

  private static int durationMinutes(OffsetDateTime departure, OffsetDateTime arrival) {
    if (departure == null || arrival == null) {
      return 0;
    }
    return Math.max(0, (int) Duration.between(departure, arrival).toMinutes());
  }

  private String journeyType(int index, int totalSegments) {
    if (index == 0) {
      return "OUTBOUND";
    }
    if (totalSegments == 2 && index == 1) {
      return "RETURN";
    }
    return "SEGMENT";
  }

  private PaymentResultSummaryView.SeatView seatView(FlightSeatEntity seat) {
    if (seat == null) {
      return new PaymentResultSummaryView.SeatView(false, null, null, "NOT_ASSIGNED");
    }
    return new PaymentResultSummaryView.SeatView(
        true, seat.getId(), seat.getSeatNumber(), "PREBOOKED");
  }

  private boolean isFarePayingPassenger(PassengerEntity passenger) {
    return passenger != null && !"INFANT".equalsIgnoreCase(normalize(passenger.getPassengerType()));
  }

  private String passengerLabel(PassengerEntity passenger) {
    String prefix = "Người lớn";
    if ("CHILD".equalsIgnoreCase(normalize(passenger.getPassengerType()))) {
      prefix = "Trẻ em";
    } else if ("INFANT".equalsIgnoreCase(normalize(passenger.getPassengerType()))) {
      prefix = "Em bé";
    }
    return prefix + ": " + fullNameWithTitle(passenger);
  }

  private String fullNameWithTitle(PassengerEntity passenger) {
    String title = titleFor(passenger);
    String name = fullName(passenger);
    return title == null || title.isBlank() ? name : title + " " + name;
  }

  private String fullName(PassengerEntity passenger) {
    return (nullToBlank(passenger.getLastName()) + " " + nullToBlank(passenger.getFirstName()))
        .trim();
  }

  private String titleFor(PassengerEntity passenger) {
    String gender = normalize(passenger.getGender());
    if ("MALE".equals(gender) || "M".equals(gender)) {
      return "Mr";
    }
    if ("FEMALE".equals(gender) || "F".equals(gender)) {
      return "Ms";
    }
    return null;
  }

  private String routeLabel(FlightEntity flight) {
    return airportCode(flight.getDepartureAirport())
        + " - "
        + airportCode(flight.getArrivalAirport());
  }

  private String airportCode(AirportEntity airport) {
    return airport == null ? "" : nullToBlank(airport.getCode());
  }

  private String ticketKey(Long passengerId, Long flightId) {
    return passengerId + ":" + flightId;
  }

  private String serviceLabel(BookingServiceEntity service) {
    if (service.getServiceOption() != null && service.getServiceOption().getName() != null) {
      return service.getService().getName() + " - " + service.getServiceOption().getName();
    }
    return service.getService().getName();
  }

  private String bookingCurrency(PassengerEntity passenger) {
    BookingEntity booking = passenger.getBooking();
    return booking == null ? null : booking.getCurrency();
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static String nullToBlank(String value) {
    return value == null ? "" : value;
  }

  private static BigDecimal nonNullMoney(BigDecimal value) {
    return value == null
        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        : value.setScale(2, RoundingMode.HALF_UP);
  }

  private record SummaryTotals(
      BigDecimal baseFare,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal serviceFee,
      BigDecimal seatFee,
      BigDecimal total) {}
}
