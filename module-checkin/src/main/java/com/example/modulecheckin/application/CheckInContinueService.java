package com.example.modulecheckin.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BoardingPassEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingServiceEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.ServiceOptionEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.exception.BookingNotFoundException;
import com.example.modulebooking.repository.BoardingPassRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.modulecheckin.config.CheckInProperties;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckInContinueService {

  private static final String BOOKING_STATUS_CONFIRMED = "CONFIRMED";
  private static final String TICKET_STATUS_ISSUED = "ISSUED";
  private static final String JOURNEY_TYPE_DOMESTIC = "DOMESTIC";
  private static final String JOURNEY_TYPE_INTERNATIONAL = "INTERNATIONAL";
  private static final int INTERNATIONAL_CHECKIN_OPEN_HOURS = 48;
  private static final List<String> ACTIVE_ASSIGNMENT_STATUSES = List.of("RESERVED", "BOOKED");
  private static final String SEGMENT_STATUS_READY = "READY_FOR_CONFIRMATION";
  private static final String SEGMENT_STATUS_SEAT_SELECTION = "SEAT_SELECTION_REQUIRED";
  private static final String SEGMENT_STATUS_BLOCKED = "BLOCKED";

  private final BookingRepository bookingRepository;
  private final PassengerRepository passengerRepository;
  private final TicketRepository ticketRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final BoardingPassRepository boardingPassRepository;
  private final CheckInProperties checkInProperties;

  public CheckInContinueService(
      BookingRepository bookingRepository,
      PassengerRepository passengerRepository,
      TicketRepository ticketRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      BookingServiceRepository bookingServiceRepository,
      BoardingPassRepository boardingPassRepository,
      CheckInProperties checkInProperties) {
    this.bookingRepository = bookingRepository;
    this.passengerRepository = passengerRepository;
    this.ticketRepository = ticketRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.boardingPassRepository = boardingPassRepository;
    this.checkInProperties = checkInProperties;
  }

  @Transactional(readOnly = true)
  public CheckInContinueView continueCheckIn(CheckInContinueCommand command) {
    validateSelectionShape(command);

    String normalizedPnrCode = normalize(command.pnrCode());
    String normalizedLastName = normalize(command.lastName());
    BookingEntity booking =
        bookingRepository
            .findByPnrCode(normalizedPnrCode)
            .orElseThrow(() -> new BookingNotFoundException(normalizedPnrCode));

    List<PassengerEntity> bookingPassengers =
        passengerRepository.findByBookingIdOrderById(booking.getId());
    if (bookingPassengers.stream()
        .noneMatch(passenger -> normalizedLastName.equals(normalize(passenger.getLastName())))) {
      throw new BookingNotFoundException(normalizedPnrCode);
    }

    requireConfirmedBooking(booking);

    List<TicketEntity> tickets = ticketRepository.findByBookingIdWithDetails(booking.getId());
    if (tickets.isEmpty()) {
      throw new AppException(
          "TICKETS_NOT_ISSUED",
          "Tickets have not been issued for this booking yet",
          409,
          Map.of("bookingId", booking.getId()));
    }

    Set<Long> selectedPassengerIds =
        command.segments().stream()
            .map(CheckInContinueCommand.SegmentSelection::passengerIds)
            .flatMap(Collection::stream)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));

    Map<PassengerFlightKey, FlightSeatEntity> activeSeatsByPassengerAndFlight =
        loadActiveSeatsByPassengerAndFlight(booking.getId(), selectedPassengerIds);
    Map<Long, List<CheckInContinueView.ServiceView>> servicesByPassengerId =
        loadServicesByPassengerId(booking.getId());
    Map<Long, BoardingPassEntity> boardingPassesByTicketId =
        boardingPassRepository.findByTicketIdIn(
                tickets.stream().map(TicketEntity::getId).toList())
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    boardingPass -> boardingPass.getTicket().getId(),
                    boardingPass -> boardingPass,
                    (left, right) -> left,
                    LinkedHashMap::new));

    Map<Long, Map<Long, TicketEntity>> ticketsByFlightAndPassenger = new LinkedHashMap<>();
    for (TicketEntity ticket : tickets) {
      ticketsByFlightAndPassenger
          .computeIfAbsent(ticket.getFlight().getId(), ignored -> new LinkedHashMap<>())
          .put(ticket.getPassenger().getId(), ticket);
    }

    OffsetDateTime now = OffsetDateTime.now();
    List<CheckInContinueView.SegmentView> segments = new ArrayList<>();
    int readyPassengers = 0;
    int blockedPassengers = 0;
    int passengersWithoutSeat = 0;

    for (CheckInContinueCommand.SegmentSelection selection : command.segments()) {
      Map<Long, TicketEntity> ticketsByPassengerId = ticketsByFlightAndPassenger.get(selection.flightId());
      if (ticketsByPassengerId == null || ticketsByPassengerId.isEmpty()) {
        throw new AppException(
            "CHECKIN_FLIGHT_NOT_FOUND",
            "Selected flight does not belong to the booking",
            404,
            Map.of("bookingId", booking.getId(), "flightId", selection.flightId()));
      }

      FlightEntity flight = ticketsByPassengerId.values().iterator().next().getFlight();
      String journeyType = resolveJourneyType(flight);
      CheckInWindow window = buildCheckInWindow(flight, journeyType, now);

      List<CheckInContinueView.PassengerView> passengers = new ArrayList<>();
      boolean hasSeatSelectionIssue = false;
      boolean hasBlockingIssue = false;

      for (Long passengerId : selection.passengerIds()) {
        TicketEntity ticket = ticketsByPassengerId.get(passengerId);
        if (ticket == null) {
          throw new AppException(
              "CHECKIN_PASSENGER_NOT_FOUND",
              "Selected passenger does not have a ticket for the requested flight",
              404,
              Map.of(
                  "bookingId", booking.getId(),
                  "flightId", selection.flightId(),
                  "passengerId", passengerId));
        }
        if (!TICKET_STATUS_ISSUED.equalsIgnoreCase(ticket.getStatus())) {
          throw new AppException(
              "TICKET_NOT_ELIGIBLE_FOR_CHECKIN",
              "Ticket is not issued for check-in",
              409,
              Map.of("ticketId", ticket.getId(), "status", ticket.getStatus()));
        }

        PassengerEntity passenger = ticket.getPassenger();
        FlightSeatEntity seat =
            resolveSeat(
                ticket,
                activeSeatsByPassengerAndFlight.get(new PassengerFlightKey(passengerId, flight.getId())));
        List<String> warnings = new ArrayList<>();
        boolean eligible = true;
        String checkInStatus = "READY";

        if (!"OPEN".equals(window.status())) {
          eligible = false;
          checkInStatus =
              "NOT_OPEN".equals(window.status()) ? "CHECKIN_NOT_OPEN" : "CHECKIN_CLOSED";
          warnings.add(checkInStatus);
          hasBlockingIssue = true;
        } else if (boardingPassesByTicketId.containsKey(ticket.getId())) {
          eligible = false;
          checkInStatus = "ALREADY_CHECKED_IN";
          warnings.add(checkInStatus);
          hasBlockingIssue = true;
        } else if (seat == null) {
          eligible = false;
          checkInStatus = "SEAT_SELECTION_REQUIRED";
          warnings.add(checkInStatus);
          hasSeatSelectionIssue = true;
          passengersWithoutSeat++;
        }

        if (eligible) {
          readyPassengers++;
        } else {
          blockedPassengers++;
        }

        passengers.add(
            new CheckInContinueView.PassengerView(
                passenger.getId(),
                ticket.getId(),
                ticket.getTicketNumber(),
                fullName(passenger),
                passenger.getDateOfBirth(),
                passenger.getPassengerType(),
                eligible,
                checkInStatus,
                new CheckInContinueView.SeatView(
                    seat != null,
                    seat == null ? null : seat.getId(),
                    seat == null ? null : seat.getSeatNumber()),
                new CheckInContinueView.BaggageView(
                    nullableBaggageHandKg(ticket.getFareClass()),
                    nullableBaggageCheckedKg(ticket.getFareClass())),
                servicesByPassengerId.getOrDefault(passenger.getId(), List.of()),
                List.copyOf(warnings)));
      }

      String segmentStatus =
          hasBlockingIssue
              ? SEGMENT_STATUS_BLOCKED
              : hasSeatSelectionIssue ? SEGMENT_STATUS_SEAT_SELECTION : SEGMENT_STATUS_READY;

      TicketEntity firstTicket = ticketsByPassengerId.get(selection.passengerIds().get(0));
      segments.add(
          new CheckInContinueView.SegmentView(
              flight.getId(),
              flight.getFlightNumber(),
              journeyType,
              segmentStatus,
              new CheckInContinueView.CheckInWindowView(
                  window.status(), window.opensAt(), window.closesAt(), window.message()),
              toEndpoint(
                  flight.getDepartureAirport(),
                  flight.getDepartureTerminal(),
                  flight.getDepartureGate(),
                  flight.getDepartureTime()),
              toEndpoint(
                  flight.getArrivalAirport(),
                  flight.getArrivalTerminal(),
                  flight.getArrivalGate(),
                  flight.getArrivalTime()),
              new CheckInContinueView.AirlineView(
                  flight.getAirline().getCode(),
                  flight.getAirline().getName(),
                  flight.getAirline().getLogoUrl()),
              new CheckInContinueView.FareView(
                  firstTicket.getFareClass().getName(), firstTicket.getFareClass().getCabinClass()),
              passengers));
    }

    int selectedPassengers =
        command.segments().stream().mapToInt(segment -> segment.passengerIds().size()).sum();
    boolean allowConfirm = blockedPassengers == 0 && passengersWithoutSeat == 0;
    boolean allowSeatSelection = passengersWithoutSeat > 0;

    return new CheckInContinueView(
        new CheckInContinueView.BookingView(
            booking.getId(),
            booking.getPnrCode(),
            booking.getStatus(),
            booking.getContactEmail(),
            booking.getContactPhone(),
            bookingPassengers.size(),
            booking.getCurrency(),
            normalizedLastName),
        segments,
        new CheckInContinueView.SummaryView(
            command.segments().size(),
            selectedPassengers,
            readyPassengers,
            blockedPassengers,
            passengersWithoutSeat),
        new CheckInContinueView.ActionsView(
            allowConfirm,
            allowSeatSelection,
            allowConfirm ? "CONFIRM_CHECKIN" : allowSeatSelection ? "SEAT_SELECTION" : "BLOCKED"));
  }

  private void validateSelectionShape(CheckInContinueCommand command) {
    Set<Long> flightIds = new HashSet<>();
    for (CheckInContinueCommand.SegmentSelection segment : command.segments()) {
      if (!flightIds.add(segment.flightId())) {
        throw new AppException(
            "VALIDATION_FAILED",
            "flightId must not be duplicated",
            400,
            Map.of("flightId", segment.flightId()));
      }
      Set<Long> passengerIds = new HashSet<>();
      for (Long passengerId : segment.passengerIds()) {
        if (!passengerIds.add(passengerId)) {
          throw new AppException(
              "VALIDATION_FAILED",
              "passengerIds must not contain duplicates within the same flight",
              400,
              Map.of("flightId", segment.flightId(), "passengerId", passengerId));
        }
      }
    }
  }

  private void requireConfirmedBooking(BookingEntity booking) {
    if (!BOOKING_STATUS_CONFIRMED.equalsIgnoreCase(booking.getStatus())) {
      throw new AppException(
          "BOOKING_NOT_CONFIRMED",
          "Booking is not confirmed",
          409,
          Map.of("bookingId", booking.getId(), "status", booking.getStatus()));
    }
  }

  private Map<PassengerFlightKey, FlightSeatEntity> loadActiveSeatsByPassengerAndFlight(
      Long bookingId, Set<Long> passengerIds) {
    if (passengerIds.isEmpty()) {
      return Map.of();
    }
    Map<PassengerFlightKey, FlightSeatEntity> result = new HashMap<>();
    for (PassengerSeatAssignmentEntity assignment :
        passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            bookingId, passengerIds, ACTIVE_ASSIGNMENT_STATUSES)) {
      FlightSeatEntity flightSeat = assignment.getFlightSeat();
      if (flightSeat == null || flightSeat.getFlight() == null) {
        continue;
      }
      result.putIfAbsent(
          new PassengerFlightKey(assignment.getPassenger().getId(), flightSeat.getFlight().getId()),
          flightSeat);
    }
    return result;
  }

  private Map<Long, List<CheckInContinueView.ServiceView>> loadServicesByPassengerId(Long bookingId) {
    Map<Long, List<CheckInContinueView.ServiceView>> result = new LinkedHashMap<>();
    for (BookingServiceEntity bookingService : bookingServiceRepository.findAllByBookingId(bookingId)) {
      Long passengerId = bookingService.getPassenger().getId();
      ServiceOptionEntity option = bookingService.getServiceOption();
      result
          .computeIfAbsent(passengerId, ignored -> new ArrayList<>())
          .add(
              new CheckInContinueView.ServiceView(
                  bookingService.getService().getType(),
                  option == null ? bookingService.getService().getName() : option.getName(),
                  bookingService.getQuantity(),
                  "CONFIRMED"));
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

  private CheckInWindow buildCheckInWindow(FlightEntity flight, String journeyType, OffsetDateTime now) {
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
      return new CheckInWindow("NOT_OPEN", opensAt, closesAt, "Online check-in is not open yet");
    }
    if (!now.isBefore(closesAt)) {
      return new CheckInWindow("CLOSED", opensAt, closesAt, "Online check-in is closed");
    }
    return new CheckInWindow("OPEN", opensAt, closesAt, "Online check-in is available");
  }

  private CheckInContinueView.EndpointView toEndpoint(
      AirportEntity airport, String terminal, String gate, OffsetDateTime time) {
    return new CheckInContinueView.EndpointView(
        airport.getCode(), airport.getName(), airport.getCity(), terminal, gate, time);
  }

  private Integer nullableBaggageHandKg(FareClassEntity fareClass) {
    return fareClass == null ? null : fareClass.getBaggageHandKg();
  }

  private Integer nullableBaggageCheckedKg(FareClassEntity fareClass) {
    return fareClass == null ? null : fareClass.getBaggageCheckinKg();
  }

  private String fullName(PassengerEntity passenger) {
    return (nullToEmpty(passenger.getLastName()) + " " + nullToEmpty(passenger.getFirstName()))
        .trim();
  }

  private String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private record PassengerFlightKey(Long passengerId, Long flightId) {}

  private record CheckInWindow(
      String status, OffsetDateTime opensAt, OffsetDateTime closesAt, String message) {}
}
