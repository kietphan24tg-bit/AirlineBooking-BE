package com.example.moduleweb.application.booking;

import com.example.moduleweb.controller.booking.dto.PassengerServiceSelectionRequest;
import com.example.moduleweb.controller.booking.dto.PassengerServiceSelectionResponse;
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
import com.example.modulepayment.repository.PaymentRepository;
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
  private static final String PAYMENT_STATUS_PENDING = "PENDING";
  private static final List<String> ACTIVE_HOLD_STATUSES = List.of("HELD", "CONFIRMED");

  private final BookingRepository bookingRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerRepository passengerRepository;
  private final ServiceOptionRepository serviceOptionRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final PaymentRepository paymentRepository;
  private final BookingTotalRecalculationService bookingTotalRecalculationService;

  public BookingAncillarySelectionService(
      BookingRepository bookingRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerRepository passengerRepository,
      ServiceOptionRepository serviceOptionRepository,
      BookingServiceRepository bookingServiceRepository,
      PaymentRepository paymentRepository,
      BookingTotalRecalculationService bookingTotalRecalculationService) {
    this.bookingRepository = bookingRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerRepository = passengerRepository;
    this.serviceOptionRepository = serviceOptionRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.paymentRepository = paymentRepository;
    this.bookingTotalRecalculationService = bookingTotalRecalculationService;
  }

  @Transactional
  public PassengerServiceSelectionResponse updateServices(
      Long userId, Long bookingId, PassengerServiceSelectionRequest request) {
    BookingEntity booking =
        userId == null
            ? bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404))
            : bookingRepository
                .findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404));
    validateBookingEditable(booking);

    List<PassengerServiceSelectionRequest.PassengerServicesRequest> passengerRequests =
        request.passengers();
    validatePassengerRequestDuplicates(passengerRequests);

    Map<Long, PassengerEntity> passengersById = loadPassengers(bookingId, passengerRequests);
    Map<Long, ServiceOptionEntity> optionsById = loadOptions(passengerRequests);
    validateSelectionRules(passengerRequests, optionsById);
    validateFareIncludedServices(bookingId, optionsById);

    BigDecimal fareTotal = bookingTotalRecalculationService.captureFareTotal(booking);

    List<Long> passengerIds =
        passengerRequests.stream()
            .map(PassengerServiceSelectionRequest.PassengerServicesRequest::passengerId)
            .toList();
    bookingServiceRepository.deleteByBookingIdAndPassengerIdIn(bookingId, passengerIds);
    bookingServiceRepository.flush();

    OffsetDateTime now = OffsetDateTime.now();
    List<BookingServiceEntity> entities = new ArrayList<>();
    for (PassengerServiceSelectionRequest.PassengerServicesRequest passengerRequest :
        passengerRequests) {
      PassengerEntity passenger = passengersById.get(passengerRequest.passengerId());
      for (PassengerServiceSelectionRequest.ServiceSelectionRequest selection :
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
    return toResponse(booking, totalBreakdown.ancillaryTotal(), currentServices);
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
    if (paymentRepository.existsByBookingIdAndPaymentStatus(
        booking.getId(), PAYMENT_STATUS_PENDING)) {
      throw new AppException(
          "BOOKING_PAYMENT_ALREADY_STARTED",
          "Booking services cannot be changed after payment has started",
          409);
    }
  }

  private void validatePassengerRequestDuplicates(
      List<PassengerServiceSelectionRequest.PassengerServicesRequest> passengerRequests) {
    Set<Long> passengerIds = new HashSet<>();
    for (PassengerServiceSelectionRequest.PassengerServicesRequest passengerRequest :
        passengerRequests) {
      if (!passengerIds.add(passengerRequest.passengerId())) {
        throw new AppException(
            "DUPLICATE_PASSENGER", "Passenger services are submitted more than once", 400);
      }
      Set<Long> optionIds = new HashSet<>();
      for (PassengerServiceSelectionRequest.ServiceSelectionRequest selection :
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
      List<PassengerServiceSelectionRequest.PassengerServicesRequest> passengerRequests) {
    Map<Long, PassengerEntity> passengersById = new LinkedHashMap<>();
    for (PassengerServiceSelectionRequest.PassengerServicesRequest passengerRequest :
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
      List<PassengerServiceSelectionRequest.PassengerServicesRequest> passengerRequests) {
    Set<Long> optionIds = new HashSet<>();
    for (PassengerServiceSelectionRequest.PassengerServicesRequest passengerRequest :
        passengerRequests) {
      for (PassengerServiceSelectionRequest.ServiceSelectionRequest selection :
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
      List<PassengerServiceSelectionRequest.PassengerServicesRequest> passengerRequests,
      Map<Long, ServiceOptionEntity> optionsById) {
    for (PassengerServiceSelectionRequest.PassengerServicesRequest passengerRequest :
        passengerRequests) {
      Map<Long, Integer> optionCountByServiceId = new HashMap<>();
      for (PassengerServiceSelectionRequest.ServiceSelectionRequest selection :
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

  private PassengerServiceSelectionResponse toResponse(
      BookingEntity booking, BigDecimal ancillaryTotal, List<BookingServiceEntity> services) {
    Map<Long, List<PassengerServiceSelectionResponse.ServiceSelectionResponse>> byPassenger =
        new LinkedHashMap<>();
    for (BookingServiceEntity bookingService : services) {
      ServiceEntity service = bookingService.getService();
      ServiceOptionEntity option = bookingService.getServiceOption();
      byPassenger
          .computeIfAbsent(bookingService.getPassenger().getId(), ignored -> new ArrayList<>())
          .add(
              new PassengerServiceSelectionResponse.ServiceSelectionResponse(
                  service.getId(),
                  service.getType(),
                  service.getName(),
                  option == null ? null : option.getId(),
                  option == null ? null : option.getName(),
                  bookingService.getQuantity(),
                  bookingService.getPrice()));
    }
    return new PassengerServiceSelectionResponse(
        booking.getId(),
        ancillaryTotal,
        booking.getTotalAmount(),
        booking.getCurrency(),
        byPassenger.entrySet().stream()
            .map(
                entry ->
                    new PassengerServiceSelectionResponse.PassengerServicesResponse(
                        entry.getKey(), entry.getValue()))
            .toList());
  }
}
