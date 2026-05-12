package com.example.modulecheckin.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BoardingPassEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.exception.BookingNotFoundException;
import com.example.modulebooking.repository.BoardingPassRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.modulecheckin.config.CheckInProperties;
import com.example.moduleflight.entity.AirportEntity;
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
public class CheckInConfirmService {

  private static final String BOOKING_STATUS_CONFIRMED = "CONFIRMED";
  private static final String TICKET_STATUS_ISSUED = "ISSUED";
  private static final String JOURNEY_TYPE_DOMESTIC = "DOMESTIC";
  private static final String JOURNEY_TYPE_INTERNATIONAL = "INTERNATIONAL";
  private static final int INTERNATIONAL_CHECKIN_OPEN_HOURS = 48;
  private static final List<String> ACTIVE_ASSIGNMENT_STATUSES = List.of("RESERVED", "BOOKED");

  private final BookingRepository bookingRepository;
  private final PassengerRepository passengerRepository;
  private final TicketRepository ticketRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final BoardingPassRepository boardingPassRepository;
  private final CheckInProperties checkInProperties;

  public CheckInConfirmService(
      BookingRepository bookingRepository,
      PassengerRepository passengerRepository,
      TicketRepository ticketRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      BoardingPassRepository boardingPassRepository,
      CheckInProperties checkInProperties) {
    this.bookingRepository = bookingRepository;
    this.passengerRepository = passengerRepository;
    this.ticketRepository = ticketRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.boardingPassRepository = boardingPassRepository;
    this.checkInProperties = checkInProperties;
  }

  @Transactional
  public CheckInConfirmView confirmCheckIn(CheckInConfirmCommand command) {
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
            .map(CheckInConfirmCommand.SegmentSelection::passengerIds)
            .flatMap(Collection::stream)
            .collect(java.util.stream.Collectors.toCollection(HashSet::new));

    Map<PassengerFlightKey, FlightSeatEntity> activeSeatsByPassengerAndFlight =
        loadActiveSeatsByPassengerAndFlight(booking.getId(), selectedPassengerIds);
    Map<Long, BoardingPassEntity> existingBoardingPassesByTicketId =
        boardingPassRepository
            .findByTicketIdIn(tickets.stream().map(TicketEntity::getId).toList())
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
    List<SegmentDraft> drafts = new ArrayList<>();
    List<BoardingPassEntity> boardingPassesToSave = new ArrayList<>();

    for (CheckInConfirmCommand.SegmentSelection selection : command.segments()) {
      Map<Long, TicketEntity> ticketsByPassengerId =
          ticketsByFlightAndPassenger.get(selection.flightId());
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
      if (!"OPEN".equals(window.status())) {
        throw new AppException(
            "CHECKIN_NOT_AVAILABLE",
            "Online check-in is not available for the selected flight",
            409,
            Map.of(
                "flightId", flight.getId(),
                "windowStatus", window.status(),
                "opensAt", window.opensAt(),
                "closesAt", window.closesAt()));
      }

      List<PassengerDraft> passengerDrafts = new ArrayList<>();
      int sequenceNumber = 1;
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
        if (existingBoardingPassesByTicketId.containsKey(ticket.getId())) {
          throw new AppException(
              "ALREADY_CHECKED_IN",
              "Passenger has already checked in",
              409,
              Map.of(
                  "ticketId", ticket.getId(),
                  "flightId", flight.getId(),
                  "passengerId", passengerId));
        }

        PassengerEntity passenger = ticket.getPassenger();
        FlightSeatEntity seat =
            resolveSeat(
                ticket,
                activeSeatsByPassengerAndFlight.get(
                    new PassengerFlightKey(passengerId, flight.getId())));
        if (seat == null) {
          throw new AppException(
              "SEAT_SELECTION_REQUIRED",
              "Seat selection is required before check-in",
              409,
              Map.of(
                  "ticketId", ticket.getId(),
                  "flightId", flight.getId(),
                  "passengerId", passengerId));
        }

        BoardingPassEntity boardingPass = new BoardingPassEntity();
        boardingPass.setTicket(ticket);
        boardingPass.setQrCode(buildQrCode(booking, flight, passenger, ticket, seat));
        boardingPass.setGate(flight.getDepartureGate());
        boardingPass.setBoardingTime(flight.getDepartureTime().minusMinutes(30));
        boardingPass.setSequenceNumber(sequenceNumber++);
        boardingPass.setSeatLabel(seat.getSeatNumber());
        boardingPass.setBoarded(false);
        boardingPass.setCreatedAt(now);
        boardingPassesToSave.add(boardingPass);

        passengerDrafts.add(new PassengerDraft(passenger, ticket, seat, boardingPass, flight));
      }

      drafts.add(new SegmentDraft(flight, passengerDrafts));
    }

    List<BoardingPassEntity> savedBoardingPasses =
        boardingPassRepository.saveAll(boardingPassesToSave);
    Map<Long, BoardingPassEntity> boardingPassByTicketId =
        savedBoardingPasses.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    boardingPass -> boardingPass.getTicket().getId(),
                    boardingPass -> boardingPass,
                    (left, right) -> left,
                    LinkedHashMap::new));

    List<CheckInConfirmView.SegmentView> segments = new ArrayList<>();
    List<CheckInConfirmView.BoardingPassView> boardingPasses = new ArrayList<>();
    int confirmedPassengers = 0;

    for (SegmentDraft draft : drafts) {
      List<CheckInConfirmView.PassengerView> passengers = new ArrayList<>();
      for (PassengerDraft passengerDraft : draft.passengers()) {
        BoardingPassEntity saved = boardingPassByTicketId.get(passengerDraft.ticket().getId());
        confirmedPassengers++;
        passengers.add(
            new CheckInConfirmView.PassengerView(
                passengerDraft.passenger().getId(),
                passengerDraft.ticket().getId(),
                passengerDraft.ticket().getTicketNumber(),
                fullName(passengerDraft.passenger()),
                passengerDraft.seat().getSeatNumber(),
                saved.getId(),
                "CHECKED_IN"));
        boardingPasses.add(
            new CheckInConfirmView.BoardingPassView(
                saved.getId(),
                passengerDraft.ticket().getId(),
                passengerDraft.passenger().getId(),
                passengerDraft.flight().getId(),
                saved.getQrCode(),
                saved.getGate(),
                saved.getBoardingTime(),
                saved.getSequenceNumber(),
                saved.getSeatLabel()));
      }
      segments.add(
          new CheckInConfirmView.SegmentView(
              draft.flight().getId(), draft.flight().getFlightNumber(), passengers));
    }

    return new CheckInConfirmView(
        new CheckInConfirmView.BookingView(
            booking.getId(), booking.getPnrCode(), "CHECKED_IN_COMPLETED"),
        segments,
        boardingPasses,
        new CheckInConfirmView.SummaryView(confirmedPassengers, segments.size()));
  }

  private void validateSelectionShape(CheckInConfirmCommand command) {
    Set<Long> flightIds = new HashSet<>();
    for (CheckInConfirmCommand.SegmentSelection segment : command.segments()) {
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
      return new CheckInWindow("NOT_OPEN", opensAt, closesAt, "Online check-in is not open yet");
    }
    if (!now.isBefore(closesAt)) {
      return new CheckInWindow("CLOSED", opensAt, closesAt, "Online check-in is closed");
    }
    return new CheckInWindow("OPEN", opensAt, closesAt, "Online check-in is available");
  }

  private String buildQrCode(
      BookingEntity booking,
      FlightEntity flight,
      PassengerEntity passenger,
      TicketEntity ticket,
      FlightSeatEntity seat) {
    return String.format(
        Locale.ROOT,
        "BP|%s|%d|%d|%d|%s|%s",
        booking.getPnrCode(),
        flight.getId(),
        passenger.getId(),
        ticket.getId(),
        seat.getSeatNumber(),
        flight.getDepartureTime());
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

  private record SegmentDraft(FlightEntity flight, List<PassengerDraft> passengers) {}

  private record PassengerDraft(
      PassengerEntity passenger,
      TicketEntity ticket,
      FlightSeatEntity seat,
      BoardingPassEntity boardingPass,
      FlightEntity flight) {}
}
