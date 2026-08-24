package com.example.modulebooking.application;

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
import com.example.modulebooking.repository.ServiceOptionRepository;
import com.example.moduleflight.entity.FareClassEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingAncillarySelectionService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  private static final List<String> ACTIVE_HOLD_STATUSES = List.of("HELD", "CONFIRMED");

  private final BookingRepository bookingRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerRepository passengerRepository;
  private final ServiceOptionRepository serviceOptionRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final BookingPaymentStatusPort bookingPaymentStatusPort;
  private final BookingTotalRecalculationService bookingTotalRecalculationService;

  public BookingAncillarySelectionService(
      BookingRepository bookingRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerRepository passengerRepository,
      ServiceOptionRepository serviceOptionRepository,
      BookingServiceRepository bookingServiceRepository,
      BookingPaymentStatusPort bookingPaymentStatusPort,
      BookingTotalRecalculationService bookingTotalRecalculationService) {
    this.bookingRepository = bookingRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerRepository = passengerRepository;
    this.serviceOptionRepository = serviceOptionRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.bookingPaymentStatusPort = bookingPaymentStatusPort;
    this.bookingTotalRecalculationService = bookingTotalRecalculationService;
  }

  @Transactional
  public PassengerServiceSelectionView updateServices(
      Long userId, Long bookingId, PassengerServiceSelectionCommand command) {
    BookingEntity booking =
        userId == null
            ? bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404))
            : bookingRepository
                .findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404));
    validateBookingEditable(booking);

    List<PassengerServiceSelectionCommand.PassengerServicesCommand> passengerRequests =
        command.passengers();
    validatePassengerRequestDuplicates(passengerRequests);

    Map<Long, PassengerEntity> passengersById = loadPassengers(bookingId, passengerRequests);
    Map<Long, ServiceOptionEntity> optionsById = loadOptions(passengerRequests);
    validateSelectionRules(passengerRequests, optionsById);
    validateFareIncludedServices(bookingId, optionsById);

    BigDecimal fareTotal = bookingTotalRecalculationService.captureFareTotal(booking);

    List<Long> passengerIds =
        passengerRequests.stream()
            .map(PassengerServiceSelectionCommand.PassengerServicesCommand::passengerId)
            .toList();
    bookingServiceRepository.deleteByBookingIdAndPassengerIdIn(bookingId, passengerIds);
    bookingServiceRepository.flush();

    OffsetDateTime now = OffsetDateTime.now();
    List<BookingServiceEntity> entities = new ArrayList<>();
    for (PassengerServiceSelectionCommand.PassengerServicesCommand passengerRequest :
        passengerRequests) {
      PassengerEntity passenger = passengersById.get(passengerRequest.passengerId());
      for (PassengerServiceSelectionCommand.ServiceSelectionCommand selection :
          passengerRequest.selections()) {
        ServiceOptionEntity option = optionsById.get(selection.optionId());
        ServiceEntity service = option.getService();
        BookingServiceEntity entity = new BookingServiceEntity();
        entity.setBooking(booking);
        entity.setPassenger(passenger);
        entity.setService(service);
        entity.setServiceOption(option);
        entity.setQuantity(selection.quantity());
        entity.setPrice(linePrice(option, selection.quantity()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entities.add(entity);
      }
    }
    bookingServiceRepository.saveAll(entities);

    BookingTotalRecalculationService.BookingTotalBreakdown totalBreakdown =
        bookingTotalRecalculationService.refreshTotal(booking, fareTotal);

    List<BookingServiceEntity> currentServices =
        bookingServiceRepository.findAllByBookingId(bookingId);
    return toView(booking, totalBreakdown.ancillaryTotal(), currentServices);
  }

  private void validateBookingEditable(BookingEntity booking) {
    if (!"PENDING_PAYMENT".equals(booking.getStatus())) {
      throw new AppException(
          "BOOKING_NOT_EDITABLE", "Booking is not eligible for service selection", 409);
    }
    if (booking.getPnrExpiresAt() != null
        && OffsetDateTime.now().isAfter(booking.getPnrExpiresAt())) {
      throw new AppException("BOOKING_EXPIRED", "Booking payment window has expired", 410);
    }
    if (bookingPaymentStatusPort.hasPendingBookingPayment(booking.getId())) {
      throw new AppException(
          "BOOKING_PAYMENT_ALREADY_STARTED",
          "Booking services cannot be changed after payment has started",
          409);
    }
  }

  private void validatePassengerRequestDuplicates(
      List<PassengerServiceSelectionCommand.PassengerServicesCommand> passengerRequests) {
    Set<Long> passengerIds = new HashSet<>();
    for (PassengerServiceSelectionCommand.PassengerServicesCommand passengerRequest :
        passengerRequests) {
      if (!passengerIds.add(passengerRequest.passengerId())) {
        throw new AppException(
            "DUPLICATE_PASSENGER", "Passenger services are submitted more than once", 400);
      }
      Set<Long> optionIds = new HashSet<>();
      for (PassengerServiceSelectionCommand.ServiceSelectionCommand selection :
          passengerRequest.selections()) {
        if (selection.quantity() == null || selection.quantity() <= 0) {
          throw new AppException("INVALID_SERVICE_QUANTITY", "quantity must be positive", 400);
        }
        if (!optionIds.add(selection.optionId())) {
          throw new AppException(
              "DUPLICATE_SERVICE_OPTION",
              "Passenger service option is submitted more than once",
              400);
        }
      }
    }
  }

  private Map<Long, PassengerEntity> loadPassengers(
      Long bookingId,
      List<PassengerServiceSelectionCommand.PassengerServicesCommand> passengerRequests) {
    Map<Long, PassengerEntity> passengersById = new LinkedHashMap<>();
    for (PassengerServiceSelectionCommand.PassengerServicesCommand passengerRequest :
        passengerRequests) {
      PassengerEntity passenger =
          passengerRepository
              .findByIdAndBookingId(passengerRequest.passengerId(), bookingId)
              .orElseThrow(
                  () -> new AppException("PASSENGER_NOT_FOUND", "Passenger not found", 404));
      passengersById.put(passenger.getId(), passenger);
    }
    return passengersById;
  }

  private Map<Long, ServiceOptionEntity> loadOptions(
      List<PassengerServiceSelectionCommand.PassengerServicesCommand> passengerRequests) {
    Set<Long> optionIds = new HashSet<>();
    for (PassengerServiceSelectionCommand.PassengerServicesCommand passengerRequest :
        passengerRequests) {
      for (PassengerServiceSelectionCommand.ServiceSelectionCommand selection :
          passengerRequest.selections()) {
        optionIds.add(selection.optionId());
      }
    }
    if (optionIds.isEmpty()) {
      return Map.of();
    }
    List<ServiceOptionEntity> options =
        serviceOptionRepository.findActiveByIdsWithService(optionIds);
    if (options.size() != optionIds.size()) {
      throw new AppException(
          "SERVICE_OPTION_NOT_FOUND", "One or more service options were not found", 404);
    }
    Map<Long, ServiceOptionEntity> optionsById = new HashMap<>();
    for (ServiceOptionEntity option : options) {
      optionsById.put(option.getId(), option);
    }
    return optionsById;
  }

  private void validateSelectionRules(
      List<PassengerServiceSelectionCommand.PassengerServicesCommand> passengerRequests,
      Map<Long, ServiceOptionEntity> optionsById) {
    for (PassengerServiceSelectionCommand.PassengerServicesCommand passengerRequest :
        passengerRequests) {
      Map<Long, Integer> optionCountByServiceId = new HashMap<>();
      for (PassengerServiceSelectionCommand.ServiceSelectionCommand selection :
          passengerRequest.selections()) {
        ServiceOptionEntity option = optionsById.get(selection.optionId());
        ServiceEntity service = option.getService();
        optionCountByServiceId.merge(service.getId(), 1, Integer::sum);
      }
      for (Map.Entry<Long, Integer> entry : optionCountByServiceId.entrySet()) {
        ServiceEntity service =
            optionsById.values().stream()
                .map(ServiceOptionEntity::getService)
                .filter(s -> Objects.equals(s.getId(), entry.getKey()))
                .findFirst()
                .orElseThrow();
        if (!"MEAL".equalsIgnoreCase(service.getType()) && entry.getValue() > 1) {
          throw new AppException(
              "SERVICE_SELECTION_LIMIT_EXCEEDED",
              "Only one option is allowed for service type " + service.getType(),
              400);
        }
      }
    }
  }

  private void validateFareIncludedServices(
      Long bookingId, Map<Long, ServiceOptionEntity> optionsById) {
    boolean hasFareSensitiveService =
        optionsById.values().stream()
            .map(ServiceOptionEntity::getService)
            .anyMatch(this::isFareSensitiveService);
    if (!hasFareSensitiveService) {
      return;
    }

    BookingFareHoldEntity hold =
        bookingFareHoldRepository
            .findFirstByBookingIdAndStatusIn(bookingId, ACTIVE_HOLD_STATUSES)
            .orElseThrow(() -> new AppException("FARE_HOLD_NOT_FOUND", "Fare hold not found", 409));
    FareClassEntity fareClass = hold.getFlightFare().getFareClass();
    for (ServiceOptionEntity option : optionsById.values()) {
      ServiceEntity service = option.getService();
      if (isIncludedInFare(service, fareClass)) {
        throw new AppException(
            "SERVICE_INCLUDED_IN_FARE",
            "Service " + service.getType() + " is already included in selected fare",
            409);
      }
    }
  }

  private boolean isFareSensitiveService(ServiceEntity service) {
    if (service == null || service.getType() == null) {
      return false;
    }
    return "LOUNGE".equalsIgnoreCase(service.getType())
        || "PRIORITY_BOARDING".equalsIgnoreCase(service.getType());
  }

  private boolean isIncludedInFare(ServiceEntity service, FareClassEntity fareClass) {
    if (fareClass == null || service == null || service.getType() == null) {
      return false;
    }
    if ("LOUNGE".equalsIgnoreCase(service.getType())) {
      return fareClass.isLoungeAccess();
    }
    if ("PRIORITY_BOARDING".equalsIgnoreCase(service.getType())) {
      return fareClass.isPriorityBoarding();
    }
    return false;
  }

  private BigDecimal linePrice(ServiceOptionEntity option, int quantity) {
    return normalizeMoney(option.getPrice())
        .multiply(BigDecimal.valueOf(quantity))
        .setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal normalizeMoney(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  private PassengerServiceSelectionView toView(
      BookingEntity booking, BigDecimal ancillaryTotal, List<BookingServiceEntity> services) {
    Map<Long, List<PassengerServiceSelectionView.ServiceSelectionView>> byPassenger =
        new LinkedHashMap<>();
    for (BookingServiceEntity bookingService : services) {
      ServiceEntity service = bookingService.getService();
      ServiceOptionEntity option = bookingService.getServiceOption();
      byPassenger
          .computeIfAbsent(bookingService.getPassenger().getId(), ignored -> new ArrayList<>())
          .add(
              new PassengerServiceSelectionView.ServiceSelectionView(
                  service.getId(),
                  service.getType(),
                  service.getName(),
                  option == null ? null : option.getId(),
                  option == null ? null : option.getName(),
                  bookingService.getQuantity(),
                  bookingService.getPrice()));
    }
    return new PassengerServiceSelectionView(
        booking.getId(),
        ancillaryTotal,
        booking.getTotalAmount(),
        booking.getCurrency(),
        byPassenger.entrySet().stream()
            .map(
                entry ->
                    new PassengerServiceSelectionView.PassengerServicesView(
                        entry.getKey(), entry.getValue()))
            .toList());
  }
}
