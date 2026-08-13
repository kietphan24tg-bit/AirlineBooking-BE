package com.example.moduleweb.application.booking;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BoardingPassEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.BookingServiceEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.ServiceEntity;
import com.example.modulebooking.entity.ServiceOptionEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BoardingPassRepository;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.modulecheckin.config.CheckInProperties;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingManageOptionsService {

  private static final List<String> ACTIVE_ASSIGNMENT_STATUSES = List.of("RESERVED", "BOOKED");
  private static final String BOOKING_STATUS_CONFIRMED = "CONFIRMED";
  private static final String TICKET_STATUS_ISSUED = "ISSUED";
  private static final String JOURNEY_TYPE_DOMESTIC = "DOMESTIC";
  private static final String JOURNEY_TYPE_INTERNATIONAL = "INTERNATIONAL";
  private static final int INTERNATIONAL_CHECKIN_OPEN_HOURS = 48;
  private static final String WINDOW_STATUS_OPEN = "OPEN";
  private static final String WINDOW_STATUS_NOT_OPEN = "NOT_OPEN";
  private static final String WINDOW_STATUS_CLOSED = "CLOSED";
  private static final String SEGMENT_STATUS_READY = "READY";
  private static final String SEGMENT_STATUS_SEAT_SELECTION_REQUIRED = "SEAT_SELECTION_REQUIRED";
  private static final String SEGMENT_STATUS_BLOCKED = "BLOCKED";

  private final BookingRepository bookingRepository;
  private final PassengerRepository passengerRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final TicketRepository ticketRepository;
  private final BoardingPassRepository boardingPassRepository;
  private final CheckInProperties checkInProperties;

  public BookingManageOptionsService(
      BookingRepository bookingRepository,
      PassengerRepository passengerRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      BookingServiceRepository bookingServiceRepository,
      TicketRepository ticketRepository,
      BoardingPassRepository boardingPassRepository,
      CheckInProperties checkInProperties) {
    this.bookingRepository = bookingRepository;
    this.passengerRepository = passengerRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.ticketRepository = ticketRepository;
    this.boardingPassRepository = boardingPassRepository;
    this.checkInProperties = checkInProperties;
  }

  @Transactional(readOnly = true)
  public BookingManageOptionsView getManageOptions(Long userId, Long bookingId) {
    BookingEntity booking = requireBooking(userId, bookingId);
    List<PassengerEntity> passengers = passengerRepository.findByBookingIdOrderById(bookingId);
    List<BookingFareHoldEntity> holds =
        bookingFareHoldRepository.findByBookingIdOrderByIdAsc(bookingId);
    List<TicketEntity> tickets = ticketRepository.findByBookingIdWithDetails(bookingId);
    List<PassengerSeatAssignmentEntity> seatAssignments =
        passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(
            bookingId, ACTIVE_ASSIGNMENT_STATUSES);
    List<BookingServiceEntity> serviceSelections =
        bookingServiceRepository.findDetailedByBookingId(bookingId);
    Map<PassengerFlightKey, FlightSeatEntity> activeSeatsByPassengerAndFlight =
        loadActiveSeatsByPassengerAndFlight(seatAssignments);
    Map<Long, BoardingPassEntity> boardingPassByTicketId = loadBoardingPassesByTicketId(tickets);
    Map<Long, Map<Long, TicketEntity>> ticketsByFlightAndPassenger =
        indexTicketsByFlightAndPassenger(tickets);
    OffsetDateTime now = OffsetDateTime.now();

    return new BookingManageOptionsView(
        booking.getId(),
        booking.getStatus(),
        booking.getCurrency(),
        passengers.stream()
            .map(
                passenger ->
                    toPassengerView(
                        passenger,
                        holds,
                        booking,
                        ticketsByFlightAndPassenger,
                        activeSeatsByPassengerAndFlight,
                        boardingPassByTicketId,
                        now))
            .toList(),
        holds.stream()
            .map(
                hold ->
                    toItineraryView(
                        hold,
                        passengers,
                        booking,
                        ticketsByFlightAndPassenger,
                        activeSeatsByPassengerAndFlight,
                        boardingPassByTicketId,
                        now))
            .toList(),
        seatAssignments.stream().map(this::toSeatAssignmentView).toList(),
        serviceSelections.stream().map(this::toServiceSelectionView).toList());
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

  private PassengerView toPassengerView(
      PassengerEntity passenger,
      List<BookingFareHoldEntity> holds,
      BookingEntity booking,
      Map<Long, Map<Long, TicketEntity>> ticketsByFlightAndPassenger,
      Map<PassengerFlightKey, FlightSeatEntity> activeSeatsByPassengerAndFlight,
      Map<Long, BoardingPassEntity> boardingPassByTicketId,
      OffsetDateTime now) {
    return new PassengerView(
        passenger.getId(),
        passenger.getFirstName(),
        passenger.getLastName(),
        passenger.getPassengerType(),
        holds.stream()
            .map(
                hold ->
                    toPassengerSegmentCheckInView(
                        passenger,
                        hold,
                        booking,
                        ticketsByFlightAndPassenger,
                        activeSeatsByPassengerAndFlight,
                        boardingPassByTicketId,
                        now))
            .toList());
  }

  private ItineraryView toItineraryView(
      BookingFareHoldEntity hold,
      List<PassengerEntity> passengers,
      BookingEntity booking,
      Map<Long, Map<Long, TicketEntity>> ticketsByFlightAndPassenger,
      Map<PassengerFlightKey, FlightSeatEntity> activeSeatsByPassengerAndFlight,
      Map<Long, BoardingPassEntity> boardingPassByTicketId,
      OffsetDateTime now) {
    FlightFareEntity fare = hold.getFlightFare();
    FlightEntity flight = fare.getFlight();
    FareClassEntity fareClass = fare.getFareClass();
    String journeyType = resolveJourneyType(flight);
    CheckInWindow checkInWindow = buildCheckInWindow(flight, journeyType, now);
    return new ItineraryView(
        flight.getId(),
        fare.getId(),
        flight.getFlightNumber(),
        flight.getDepartureTime(),
        flight.getArrivalTime(),
        flight.getDepartureAirport().getCode(),
        flight.getDepartureAirport().getCity(),
        flight.getDepartureTerminal(),
        flight.getArrivalAirport().getCode(),
        flight.getArrivalAirport().getCity(),
        flight.getArrivalTerminal(),
        fareClass == null ? null : fareClass.getName(),
        fareClass == null ? null : fareClass.getCabinClass(),
        nullableBaggageHandKg(fareClass),
        nullableBaggageCheckedKg(fareClass),
        journeyType,
        new CheckInWindowView(
            checkInWindow.status(),
            checkInWindow.opensAt(),
            checkInWindow.closesAt(),
            checkInWindow.message()),
        resolveSegmentStatus(
            booking,
            flight,
            passengers,
            ticketsByFlightAndPassenger,
            activeSeatsByPassengerAndFlight,
            boardingPassByTicketId,
            checkInWindow));
  }

  private SeatAssignmentView toSeatAssignmentView(PassengerSeatAssignmentEntity assignment) {
    FlightSeatEntity seat = assignment.getFlightSeat();
    return new SeatAssignmentView(
        assignment.getPassenger().getId(),
        seat.getFlight().getId(),
        seat.getId(),
        seat.getSeatNumber(),
        assignment.getStatus(),
        assignment.getSurchargeAmount(),
        assignment.getCurrency());
  }

  private ServiceSelectionView toServiceSelectionView(BookingServiceEntity serviceSelection) {
    ServiceEntity service = serviceSelection.getService();
    ServiceOptionEntity option = serviceSelection.getServiceOption();
    return new ServiceSelectionView(
        serviceSelection.getPassenger().getId(),
        service == null ? null : service.getId(),
        service == null ? null : service.getType(),
        service == null ? null : service.getName(),
        option == null ? null : option.getId(),
        option == null ? null : option.getName(),
        serviceSelection.getQuantity(),
        serviceSelection.getPrice());
  }

  private PassengerSegmentCheckInView toPassengerSegmentCheckInView(
      PassengerEntity passenger,
      BookingFareHoldEntity hold,
      BookingEntity booking,
      Map<Long, Map<Long, TicketEntity>> ticketsByFlightAndPassenger,
      Map<PassengerFlightKey, FlightSeatEntity> activeSeatsByPassengerAndFlight,
      Map<Long, BoardingPassEntity> boardingPassByTicketId,
      OffsetDateTime now) {
    FlightEntity flight = hold.getFlightFare().getFlight();
    CheckInWindow window = buildCheckInWindow(flight, resolveJourneyType(flight), now);
    PassengerCheckInResult result =
        evaluatePassenger(
            booking,
            flight,
            passenger,
            ticketsByFlightAndPassenger,
            activeSeatsByPassengerAndFlight,
            boardingPassByTicketId,
            window);
    return new PassengerSegmentCheckInView(
        flight.getId(),
        result.eligible(),
        result.checkInStatus(),
        result.seatAssigned(),
        result.seatNumber(),
        result.warnings());
  }

  private String resolveSegmentStatus(
      BookingEntity booking,
      FlightEntity flight,
      List<PassengerEntity> passengers,
      Map<Long, Map<Long, TicketEntity>> ticketsByFlightAndPassenger,
      Map<PassengerFlightKey, FlightSeatEntity> activeSeatsByPassengerAndFlight,
      Map<Long, BoardingPassEntity> boardingPassByTicketId,
      CheckInWindow window) {
    boolean hasSeatSelectionIssue = false;
    boolean hasBlockingIssue = false;
    for (PassengerEntity passenger : passengers) {
      PassengerCheckInResult result =
          evaluatePassenger(
              booking,
              flight,
              passenger,
              ticketsByFlightAndPassenger,
              activeSeatsByPassengerAndFlight,
              boardingPassByTicketId,
              window);
      if ("SEAT_SELECTION_REQUIRED".equals(result.checkInStatus())) {
        hasSeatSelectionIssue = true;
      }
      if (!result.eligible() && !"SEAT_SELECTION_REQUIRED".equals(result.checkInStatus())) {
        hasBlockingIssue = true;
      }
    }
    if (hasBlockingIssue) {
      return SEGMENT_STATUS_BLOCKED;
    }
    if (hasSeatSelectionIssue) {
      return SEGMENT_STATUS_SEAT_SELECTION_REQUIRED;
    }
    return SEGMENT_STATUS_READY;
  }

  private PassengerCheckInResult evaluatePassenger(
      BookingEntity booking,
      FlightEntity flight,
      PassengerEntity passenger,
      Map<Long, Map<Long, TicketEntity>> ticketsByFlightAndPassenger,
      Map<PassengerFlightKey, FlightSeatEntity> activeSeatsByPassengerAndFlight,
      Map<Long, BoardingPassEntity> boardingPassByTicketId,
      CheckInWindow window) {
    List<String> warnings = new ArrayList<>();
    TicketEntity ticket =
        ticketsByFlightAndPassenger.getOrDefault(flight.getId(), Map.of()).get(passenger.getId());
    if (ticket == null) {
      warnings.add("TICKET_NOT_FOUND");
      return new PassengerCheckInResult(
          false, "TICKET_NOT_FOUND", false, null, List.copyOf(warnings));
    }
    if (!BOOKING_STATUS_CONFIRMED.equalsIgnoreCase(booking.getStatus())) {
      warnings.add("BOOKING_NOT_CONFIRMED");
      return new PassengerCheckInResult(
          false,
          "BOOKING_NOT_CONFIRMED",
          hasSeat(ticket, activeSeatsByPassengerAndFlight),
          seatNumber(ticket, activeSeatsByPassengerAndFlight),
          List.copyOf(warnings));
    }
    if (!TICKET_STATUS_ISSUED.equalsIgnoreCase(ticket.getStatus())) {
      warnings.add("TICKET_NOT_ISSUED");
      return new PassengerCheckInResult(
          false,
          "TICKET_NOT_ISSUED",
          hasSeat(ticket, activeSeatsByPassengerAndFlight),
          seatNumber(ticket, activeSeatsByPassengerAndFlight),
          List.copyOf(warnings));
    }
    if (boardingPassByTicketId.containsKey(ticket.getId())) {
      warnings.add("ALREADY_CHECKED_IN");
      return new PassengerCheckInResult(
          false,
          "ALREADY_CHECKED_IN",
          hasSeat(ticket, activeSeatsByPassengerAndFlight),
          seatNumber(ticket, activeSeatsByPassengerAndFlight),
          List.copyOf(warnings));
    }
    if (!WINDOW_STATUS_OPEN.equals(window.status())) {
      String status =
          WINDOW_STATUS_NOT_OPEN.equals(window.status()) ? "CHECKIN_NOT_OPEN" : "CHECKIN_CLOSED";
      warnings.add(status);
      return new PassengerCheckInResult(
          false,
          status,
          hasSeat(ticket, activeSeatsByPassengerAndFlight),
          seatNumber(ticket, activeSeatsByPassengerAndFlight),
          List.copyOf(warnings));
    }
    FlightSeatEntity seat =
        resolveSeat(
            ticket,
            activeSeatsByPassengerAndFlight.get(
                new PassengerFlightKey(passenger.getId(), flight.getId())));
    if (seat == null) {
      warnings.add("SEAT_SELECTION_REQUIRED");
      return new PassengerCheckInResult(
          false, "SEAT_SELECTION_REQUIRED", false, null, List.copyOf(warnings));
    }
    return new PassengerCheckInResult(true, "READY", true, seat.getSeatNumber(), List.of());
  }

  private boolean hasSeat(
      TicketEntity ticket,
      Map<PassengerFlightKey, FlightSeatEntity> activeSeatsByPassengerAndFlight) {
    return resolveSeat(
            ticket,
            activeSeatsByPassengerAndFlight.get(
                new PassengerFlightKey(ticket.getPassenger().getId(), ticket.getFlight().getId())))
        != null;
  }

  private String seatNumber(
      TicketEntity ticket,
      Map<PassengerFlightKey, FlightSeatEntity> activeSeatsByPassengerAndFlight) {
    FlightSeatEntity seat =
        resolveSeat(
            ticket,
            activeSeatsByPassengerAndFlight.get(
                new PassengerFlightKey(ticket.getPassenger().getId(), ticket.getFlight().getId())));
    return seat == null ? null : seat.getSeatNumber();
  }

  private Map<PassengerFlightKey, FlightSeatEntity> loadActiveSeatsByPassengerAndFlight(
      List<PassengerSeatAssignmentEntity> seatAssignments) {
    Map<PassengerFlightKey, FlightSeatEntity> result = new HashMap<>();
    for (PassengerSeatAssignmentEntity assignment : seatAssignments) {
      FlightSeatEntity seat = assignment.getFlightSeat();
      if (seat == null || seat.getFlight() == null) {
        continue;
      }
      result.putIfAbsent(
          new PassengerFlightKey(assignment.getPassenger().getId(), seat.getFlight().getId()),
          seat);
    }
    return result;
  }

  private Map<Long, BoardingPassEntity> loadBoardingPassesByTicketId(List<TicketEntity> tickets) {
    if (tickets.isEmpty()) {
      return Map.of();
    }
    return boardingPassRepository
        .findByTicketIdIn(tickets.stream().map(TicketEntity::getId).toList())
        .stream()
        .collect(
            java.util.stream.Collectors.toMap(
                boardingPass -> boardingPass.getTicket().getId(),
                boardingPass -> boardingPass,
                (left, right) -> left,
                LinkedHashMap::new));
  }

  private Map<Long, Map<Long, TicketEntity>> indexTicketsByFlightAndPassenger(
      List<TicketEntity> tickets) {
    Map<Long, Map<Long, TicketEntity>> result = new LinkedHashMap<>();
    for (TicketEntity ticket : tickets) {
      result
          .computeIfAbsent(ticket.getFlight().getId(), ignored -> new LinkedHashMap<>())
          .put(ticket.getPassenger().getId(), ticket);
    }
    return result;
  }

  private FlightSeatEntity resolveSeat(TicketEntity ticket, FlightSeatEntity activeAssignmentSeat) {
    if (ticket.getFlightSeat() != null) {
      return ticket.getFlightSeat();
    }
    return activeAssignmentSeat;
  }

  private String resolveJourneyType(FlightEntity flight) {
    AirportEntity departure = flight.getDepartureAirport();
    AirportEntity arrival = flight.getArrivalAirport();
    String departureCountry = firstNonBlank(departure.getCountryCode(), departure.getCountry());
    String arrivalCountry = firstNonBlank(arrival.getCountryCode(), arrival.getCountry());
    return normalize(departureCountry).equals(normalize(arrivalCountry))
        ? JOURNEY_TYPE_DOMESTIC
        : JOURNEY_TYPE_INTERNATIONAL;
  }

  private CheckInWindow buildCheckInWindow(
      FlightEntity flight, String journeyType, OffsetDateTime now) {
    int openHours =
        JOURNEY_TYPE_DOMESTIC.equals(journeyType)
            ? checkInProperties.checkinOpenHours()
            : INTERNATIONAL_CHECKIN_OPEN_HOURS;
    int closeMinutes =
        JOURNEY_TYPE_DOMESTIC.equals(journeyType)
            ? checkInProperties.domesticCheckinCloseMinutes()
            : checkInProperties.internationalCheckinCloseMinutes();
    OffsetDateTime opensAt = flight.getDepartureTime().minusHours(openHours);
    OffsetDateTime closesAt = flight.getDepartureTime().minusMinutes(closeMinutes);
    if (now.isBefore(opensAt)) {
      return new CheckInWindow(
          WINDOW_STATUS_NOT_OPEN, opensAt, closesAt, "Online check-in is not open yet");
    }
    if (!now.isBefore(closesAt)) {
      return new CheckInWindow(
          WINDOW_STATUS_CLOSED, opensAt, closesAt, "Online check-in is closed");
    }
    return new CheckInWindow(WINDOW_STATUS_OPEN, opensAt, closesAt, "Online check-in is available");
  }

  private Integer nullableBaggageHandKg(FareClassEntity fareClass) {
    return fareClass == null ? null : fareClass.getBaggageHandKg();
  }

  private Integer nullableBaggageCheckedKg(FareClassEntity fareClass) {
    return fareClass == null ? null : fareClass.getBaggageCheckinKg();
  }

  private String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  public record BookingManageOptionsView(
      Long bookingId,
      String bookingStatus,
      String currency,
      List<PassengerView> passengers,
      List<ItineraryView> itineraries,
      List<SeatAssignmentView> seatAssignments,
      List<ServiceSelectionView> serviceSelections) {}

  public record PassengerView(
      Long passengerId,
      String firstName,
      String lastName,
      String passengerType,
      List<PassengerSegmentCheckInView> segments) {}

  public record ItineraryView(
      Long flightId,
      Long fareId,
      String flightNumber,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      String departureAirportCode,
      String departureCity,
      String departureTerminal,
      String arrivalAirportCode,
      String arrivalCity,
      String arrivalTerminal,
      String fareName,
      String cabinClass,
      Integer baggageHandKg,
      Integer baggageCheckinKg,
      String journeyType,
      CheckInWindowView checkInWindow,
      String segmentStatus) {}

  public record SeatAssignmentView(
      Long passengerId,
      Long flightId,
      Long seatId,
      String seatNumber,
      String status,
      BigDecimal surchargeAmount,
      String currency) {}

  public record ServiceSelectionView(
      Long passengerId,
      Long serviceId,
      String serviceType,
      String serviceName,
      Long optionId,
      String optionName,
      Integer quantity,
      BigDecimal price) {}

  public record PassengerSegmentCheckInView(
      Long flightId,
      boolean eligible,
      String checkInStatus,
      boolean seatAssigned,
      String seatNumber,
      List<String> warnings) {}

  public record CheckInWindowView(
      String status, OffsetDateTime opensAt, OffsetDateTime closesAt, String message) {}

  private record PassengerFlightKey(Long passengerId, Long flightId) {}

  private record PassengerCheckInResult(
      boolean eligible,
      String checkInStatus,
      boolean seatAssigned,
      String seatNumber,
      List<String> warnings) {}

  private record CheckInWindow(
      String status, OffsetDateTime opensAt, OffsetDateTime closesAt, String message) {}
}
