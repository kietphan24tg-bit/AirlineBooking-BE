package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.BookingServiceEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.ServiceEntity;
import com.example.modulebooking.entity.ServiceOptionEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.ServiceOptionRepository;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.modulepayment.application.ServiceChangeConfirmView.SummaryView;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.entity.ServiceChangeSettlementEntity;
import com.example.modulepayment.entity.ServiceChangeSettlementItemEntity;
import com.example.modulepayment.gateway.PaymentRefundGateway.PaymentRefundResult;
import com.example.modulepayment.repository.BookingRepository;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.modulepayment.repository.ServiceChangeSettlementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ServiceChangeSettlementService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  private static final String STATUS_COMPLETED = "COMPLETED";
  private static final String STATUS_PAYMENT_REQUIRED = "PAYMENT_REQUIRED";
  private static final String STATUS_REFUND_FAILED = "REFUND_FAILED";
  private static final String STATUS_REFUND_PENDING = "REFUND_PENDING";
  private static final String SETTLEMENT_NONE = "NONE";
  private static final String SETTLEMENT_REFUND = "REFUND";
  private static final String SETTLEMENT_COLLECT_PAYMENT = "COLLECT_PAYMENT";
  private static final String PAYMENT_STATUS_PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
  private static final String PAYMENT_STATUS_REFUNDED = "REFUNDED";
  private static final String PAYMENT_STATUS_PENDING = "PENDING";
  private static final String SETTLEMENT_STATUS_CANCELLED = "CANCELLED";
  private static final String SETTLEMENT_STATUS_COMPLETED = "COMPLETED";
  private static final String SETTLEMENT_STATUS_EXPIRED = "EXPIRED";
  private static final String SETTLEMENT_STATUS_PAYMENT_FAILED = "PAYMENT_FAILED";
  private static final String SETTLEMENT_STATUS_PAYMENT_PENDING = "PAYMENT_PENDING";
  private static final String SETTLEMENT_STATUS_PAYMENT_REQUIRED = "PAYMENT_REQUIRED";
  private static final String SETTLEMENT_STATUS_REFUND_PENDING = "REFUND_PENDING";
  private static final String VND = "VND";
  private static final List<String> ACTIVE_HOLD_STATUSES = List.of("HELD", "CONFIRMED");
  private static final List<String> REFUNDABLE_PAYMENT_STATUSES =
      List.of("SUCCESS", "PARTIALLY_REFUNDED");

  private final BookingRepository bookingRepository;
  private final PassengerRepository passengerRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final ServiceOptionRepository serviceOptionRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final PaymentRepository paymentRepository;
  private final ServiceChangeSettlementRepository serviceChangeSettlementRepository;
  private final PaymentRefundService paymentRefundService;
  private final PaymentProperties paymentProperties;

  public ServiceChangeSettlementService(
      BookingRepository bookingRepository,
      PassengerRepository passengerRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      ServiceOptionRepository serviceOptionRepository,
      BookingServiceRepository bookingServiceRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      PaymentRepository paymentRepository,
      ServiceChangeSettlementRepository serviceChangeSettlementRepository,
      PaymentRefundService paymentRefundService,
      PaymentProperties paymentProperties) {
    this.bookingRepository = bookingRepository;
    this.passengerRepository = passengerRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.serviceOptionRepository = serviceOptionRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.paymentRepository = paymentRepository;
    this.serviceChangeSettlementRepository = serviceChangeSettlementRepository;
    this.paymentRefundService = paymentRefundService;
    this.paymentProperties = paymentProperties;
  }

  @Transactional(readOnly = true)
  public ServiceChangePreviewView preview(
      Long userId, Long bookingId, List<PassengerSelectionsCommand> passengers) {
    PreparedSettlement prepared = prepareSettlement(userId, bookingId, passengers, false);
    return prepared.preview();
  }

  @Transactional
  public ServiceChangeConfirmView confirm(
      Long userId,
      Long bookingId,
      List<PassengerSelectionsCommand> passengers,
      String previewToken,
      String idempotencyKey,
      BigDecimal expectedAdditionalAmount,
      BigDecimal expectedRefundAmount,
      BigDecimal expectedNetAmount) {
    if (!StringUtils.hasText(idempotencyKey)) {
      throw new AppException("VALIDATION_FAILED", "idempotencyKey is required", 400);
    }
    String normalizedIdempotencyKey = idempotencyKey.trim();
    var existing =
        serviceChangeSettlementRepository.findFirstByBookingIdAndIdempotencyKeyWithItems(
            bookingId, normalizedIdempotencyKey);
    if (existing.isPresent()) {
      return toConfirmView(existing.get());
    }

    PreparedSettlement prepared = prepareSettlement(userId, bookingId, passengers, true);
    if (StringUtils.hasText(previewToken)
        && !Objects.equals(prepared.preview().previewToken(), previewToken.trim())) {
      throw new AppException(
          "SERVICE_CHANGE_PREVIEW_STALE",
          "Service change totals are out of date. Please review the latest preview.",
          409);
    }
    validateExpectedSummary(
        prepared.preview().summary(),
        expectedAdditionalAmount,
        expectedRefundAmount,
        expectedNetAmount);

    ServiceChangePreviewView preview = prepared.preview();
    if (SETTLEMENT_NONE.equals(preview.settlementType())) {
      applyRequestedSelections(prepared.booking(), prepared.commands(), prepared.optionsById());
      return new ServiceChangeConfirmView(
          null,
          preview.bookingId(),
          preview.currency(),
          preview.settlementType(),
          STATUS_COMPLETED,
          null,
          null,
          null,
          null,
          null,
          null,
          List.of(),
          toSummary(preview.summary()));
    }

    if (SETTLEMENT_COLLECT_PAYMENT.equals(preview.settlementType())) {
      ServiceChangeSettlementEntity settlement =
          saveSettlement(
              prepared.booking(),
              preview,
              prepared.commands(),
              prepared.optionsById(),
              normalizedIdempotencyKey,
              SETTLEMENT_STATUS_PAYMENT_REQUIRED,
              null,
              null,
              null,
              null);
      return new ServiceChangeConfirmView(
          settlement.getId(),
          preview.bookingId(),
          preview.currency(),
          preview.settlementType(),
          STATUS_PAYMENT_REQUIRED,
          null,
          null,
          null,
          null,
          null,
          null,
          supportedPaymentMethods(preview.currency()),
          toSummary(preview.summary()));
    }

    applyRequestedSelections(prepared.booking(), prepared.commands(), prepared.optionsById());
    PaymentEntity payment = requireRefundablePayment(bookingId, true);
    ServiceChangeSettlementEntity settlement =
        saveSettlement(
            prepared.booking(),
            preview,
            prepared.commands(),
            prepared.optionsById(),
            normalizedIdempotencyKey,
            SETTLEMENT_STATUS_REFUND_PENDING,
            null,
            null,
            null,
            null);
    try {
      paymentRefundService.preparePaymentForRefund(payment);
      paymentRepository.save(payment);
      PaymentRefundResult refundResult =
          paymentRefundService.refund(
              bookingId,
              normalizedIdempotencyKey,
              payment,
              preview.summary().refundAmount(),
              "Service change refund");
      payment.setPaymentStatus(
          resolveRefundedPaymentStatus(payment, preview.summary().refundAmount()));
      paymentRepository.save(payment);
      settlement.setStatus(SETTLEMENT_STATUS_COMPLETED);
      settlement.setProvider(paymentRefundService.providerOf(payment));
      settlement.setProviderRefundId(refundResult.providerRefundId());
      settlement.setUpdatedAt(OffsetDateTime.now());
      serviceChangeSettlementRepository.save(settlement);
      return new ServiceChangeConfirmView(
          settlement.getId(),
          preview.bookingId(),
          preview.currency(),
          preview.settlementType(),
          STATUS_REFUND_PENDING,
          "SUCCEEDED",
          paymentRefundService.providerOf(payment),
          refundResult.providerRefundId(),
          null,
          null,
          buildRefundMethod(payment, paymentRefundService.providerOf(payment)),
          List.of(),
          toSummary(preview.summary()));
    } catch (RuntimeException exception) {
      settlement.setStatus(SETTLEMENT_STATUS_PAYMENT_FAILED);
      settlement.setErrorCode(
          exception instanceof AppException appException
              ? appException.getCode()
              : "REFUND_FAILED");
      settlement.setErrorMessage(exception.getMessage());
      settlement.setUpdatedAt(OffsetDateTime.now());
      serviceChangeSettlementRepository.save(settlement);
      return new ServiceChangeConfirmView(
          settlement.getId(),
          preview.bookingId(),
          preview.currency(),
          preview.settlementType(),
          STATUS_REFUND_FAILED,
          "FAILED",
          null,
          null,
          exception instanceof AppException appException ? appException.getCode() : "REFUND_FAILED",
          exception.getMessage(),
          null,
          List.of(),
          toSummary(preview.summary()));
    }
  }

  @Transactional
  public ServiceChangeSettlementEntity requireSettlementForPayment(
      Long bookingId, Long settlementId, Long userId) {
    ServiceChangeSettlementEntity settlement =
        serviceChangeSettlementRepository
            .findByIdWithItemsForUpdate(settlementId)
            .orElseThrow(
                () ->
                    new AppException(
                        "SERVICE_CHANGE_SETTLEMENT_NOT_FOUND",
                        "Service change settlement not found",
                        404));
    if (!Objects.equals(settlement.getBooking().getId(), bookingId)) {
      throw new AppException(
          "SERVICE_CHANGE_SETTLEMENT_NOT_FOUND", "Service change settlement not found", 404);
    }
    if (userId != null
        && settlement.getBooking().getUser() != null
        && !Objects.equals(settlement.getBooking().getUser().getId(), userId)) {
      throw new AppException(
          "SERVICE_CHANGE_SETTLEMENT_NOT_FOUND", "Service change settlement not found", 404);
    }
    if (!SETTLEMENT_COLLECT_PAYMENT.equals(settlement.getSettlementType())) {
      throw new AppException(
          "SERVICE_CHANGE_SETTLEMENT_NOT_PAYABLE",
          "Service change settlement does not require payment",
          409);
    }
    if (SETTLEMENT_STATUS_COMPLETED.equals(settlement.getStatus())) {
      throw new AppException(
          "SERVICE_CHANGE_SETTLEMENT_COMPLETED",
          "Service change settlement has already been completed",
          409);
    }
    return settlement;
  }

  @Transactional
  public void markPaymentCreated(ServiceChangeSettlementEntity settlement) {
    settlement.setStatus(SETTLEMENT_STATUS_PAYMENT_PENDING);
    settlement.setUpdatedAt(OffsetDateTime.now());
    serviceChangeSettlementRepository.save(settlement);
  }

  @Transactional
  public void handlePaymentSuccess(PaymentEntity payment, String providerPaymentReference) {
    ServiceChangeSettlementEntity settlement = payment.getServiceChangeSettlement();
    if (settlement == null) {
      return;
    }
    settlement =
        serviceChangeSettlementRepository
            .findByIdWithItemsForUpdate(settlement.getId())
            .orElseThrow();
    if (SETTLEMENT_STATUS_COMPLETED.equals(settlement.getStatus())) {
      return;
    }
    BookingEntity booking = settlement.getBooking();
    Map<Long, PassengerSelectionsCommand> commands = restoreCommands(settlement);
    Map<Long, ServiceOptionEntity> optionsById =
        loadOptionsById(commands.values().stream().toList());
    applyRequestedSelections(booking, commands.values().stream().toList(), optionsById);
    settlement.setStatus(SETTLEMENT_STATUS_COMPLETED);
    settlement.setUpdatedAt(OffsetDateTime.now());
    serviceChangeSettlementRepository.save(settlement);
  }

  @Transactional
  public void handlePaymentFailure(PaymentEntity payment, String status) {
    ServiceChangeSettlementEntity settlement = payment.getServiceChangeSettlement();
    if (settlement == null) {
      return;
    }
    settlement =
        serviceChangeSettlementRepository
            .findByIdWithItemsForUpdate(settlement.getId())
            .orElseThrow();
    settlement.setStatus(
        "EXPIRED".equalsIgnoreCase(status)
            ? SETTLEMENT_STATUS_EXPIRED
            : SETTLEMENT_STATUS_PAYMENT_FAILED);
    settlement.setUpdatedAt(OffsetDateTime.now());
    serviceChangeSettlementRepository.save(settlement);
  }

  @Transactional
  public void handlePaymentCancelled(PaymentEntity payment) {
    ServiceChangeSettlementEntity settlement = payment.getServiceChangeSettlement();
    if (settlement == null) {
      return;
    }
    settlement =
        serviceChangeSettlementRepository
            .findByIdWithItemsForUpdate(settlement.getId())
            .orElseThrow();
    settlement.setStatus(SETTLEMENT_STATUS_CANCELLED);
    settlement.setUpdatedAt(OffsetDateTime.now());
    serviceChangeSettlementRepository.save(settlement);
  }

  private PreparedSettlement prepareSettlement(
      Long userId,
      Long bookingId,
      List<PassengerSelectionsCommand> rawPassengers,
      boolean forUpdate) {
    List<PassengerSelectionsCommand> commands = normalizeCommands(rawPassengers);
    BookingEntity booking = requireBooking(userId, bookingId, forUpdate);
    validateBookingAssignable(booking);

    Map<Long, PassengerEntity> passengersById =
        passengerRepository.findByBookingIdOrderById(bookingId).stream()
            .collect(Collectors.toMap(PassengerEntity::getId, passenger -> passenger));
    Map<Long, ServiceOptionEntity> optionsById = loadOptionsById(commands);
    validateSelectionRules(commands, optionsById);
    validateFareIncludedServices(bookingId, optionsById);

    Map<Long, List<BookingServiceEntity>> currentByPassenger =
        bookingServiceRepository.findDetailedByBookingId(bookingId).stream()
            .collect(
                Collectors.groupingBy(
                    bs -> bs.getPassenger().getId(), LinkedHashMap::new, Collectors.toList()));

    List<ServiceChangePreviewView.ItemView> items = new ArrayList<>();
    BigDecimal additionalAmount = ZERO;
    BigDecimal refundAmount = ZERO;
    for (PassengerSelectionsCommand passengerCommand : commands) {
      PassengerEntity passenger = passengersById.get(passengerCommand.passengerId());
      if (passenger == null) {
        throw new AppException("PASSENGER_NOT_FOUND", "Passenger not found", 404);
      }
      Map<Long, BookingServiceEntity> currentSelections =
          (currentByPassenger.get(passengerCommand.passengerId()) == null
                  ? List.<BookingServiceEntity>of()
                  : currentByPassenger.get(passengerCommand.passengerId()))
              .stream()
                  .filter(selection -> selection.getServiceOption() != null)
                  .collect(
                      Collectors.toMap(
                          selection -> selection.getServiceOption().getId(),
                          selection -> selection,
                          (left, right) -> left,
                          LinkedHashMap::new));
      Map<Long, SelectionCommand> requestedSelections =
          passengerCommand.selections().stream()
              .collect(
                  Collectors.toMap(
                      SelectionCommand::optionId,
                      selection -> selection,
                      (left, right) -> left,
                      LinkedHashMap::new));
      Set<Long> optionIds =
          new LinkedHashMap<Long, Long>() {
            {
            }
          }.keySet();
      Set<Long> mergedOptionIds = new HashSet<>();
      mergedOptionIds.addAll(currentSelections.keySet());
      mergedOptionIds.addAll(requestedSelections.keySet());

      for (Long optionId : mergedOptionIds) {
        BookingServiceEntity current = currentSelections.get(optionId);
        SelectionCommand requested = requestedSelections.get(optionId);
        ServiceOptionEntity option =
            requested != null
                ? optionsById.get(optionId)
                : current == null ? null : current.getServiceOption();
        ServiceEntity service =
            option != null ? option.getService() : current == null ? null : current.getService();
        BigDecimal oldAmount = current == null ? ZERO : normalizeMoney(current.getPrice());
        BigDecimal newAmount =
            requested == null || option == null ? ZERO : linePrice(option, requested.quantity());
        BigDecimal delta = normalizeMoney(newAmount.subtract(oldAmount));
        int quantity =
            requested != null ? requested.quantity() : current == null ? 0 : current.getQuantity();
        if (delta.signum() == 0
            && current != null
            && requested != null
            && Objects.equals(current.getQuantity(), requested.quantity())) {
          continue;
        }
        if (delta.signum() > 0) {
          if (!VND.equalsIgnoreCase(booking.getCurrency())) {
            throw new AppException(
                "SERVICE_SURCHARGE_CURRENCY_UNSUPPORTED",
                "Service surcharge is only supported for VND bookings",
                409);
          }
          additionalAmount = normalizeMoney(additionalAmount.add(delta));
        } else if (delta.signum() < 0) {
          refundAmount = normalizeMoney(refundAmount.add(delta.abs()));
        }
        items.add(
            new ServiceChangePreviewView.ItemView(
                passengerCommand.passengerId(),
                buildPassengerName(passenger),
                service == null ? null : service.getId(),
                service == null ? null : service.getType(),
                service == null ? null : service.getName(),
                optionId,
                option == null ? null : option.getName(),
                quantity,
                oldAmount,
                newAmount,
                delta,
                "VALID",
                null));
      }
    }

    BigDecimal netAmount = normalizeMoney(additionalAmount.subtract(refundAmount));
    String settlementType =
        netAmount.signum() > 0
            ? SETTLEMENT_COLLECT_PAYMENT
            : netAmount.signum() < 0 ? SETTLEMENT_REFUND : SETTLEMENT_NONE;
    ServiceChangePreviewView preview =
        new ServiceChangePreviewView(
            booking.getId(),
            booking.getCurrency(),
            buildPreviewToken(booking.getId(), commands),
            settlementType,
            items.stream()
                .sorted(
                    java.util.Comparator.comparing(ServiceChangePreviewView.ItemView::passengerId)
                        .thenComparing(item -> item.optionId() == null ? 0L : item.optionId()))
                .toList(),
            new ServiceChangePreviewView.SummaryView(additionalAmount, refundAmount, netAmount));
    return new PreparedSettlement(booking, preview, commands, optionsById);
  }

  private void applyRequestedSelections(
      BookingEntity booking,
      List<PassengerSelectionsCommand> commands,
      Map<Long, ServiceOptionEntity> optionsById) {
    BigDecimal fareTotal = captureFareTotal(booking);
    List<Long> passengerIds =
        commands.stream().map(PassengerSelectionsCommand::passengerId).toList();
    bookingServiceRepository.deleteByBookingIdAndPassengerIdIn(booking.getId(), passengerIds);
    bookingServiceRepository.flush();
    Map<Long, PassengerEntity> passengersById =
        passengerRepository.findByBookingIdOrderById(booking.getId()).stream()
            .collect(Collectors.toMap(PassengerEntity::getId, passenger -> passenger));
    OffsetDateTime now = OffsetDateTime.now();
    List<BookingServiceEntity> entities = new ArrayList<>();
    for (PassengerSelectionsCommand command : commands) {
      PassengerEntity passenger = passengersById.get(command.passengerId());
      if (passenger == null) {
        throw new AppException("PASSENGER_NOT_FOUND", "Passenger not found", 404);
      }
      for (SelectionCommand selection : command.selections()) {
        if (selection.quantity() <= 0) {
          continue;
        }
        ServiceOptionEntity option = optionsById.get(selection.optionId());
        if (option == null) {
          throw new AppException("SERVICE_OPTION_NOT_FOUND", "Service option not found", 404);
        }
        BookingServiceEntity entity = new BookingServiceEntity();
        entity.setBooking(booking);
        entity.setPassenger(passenger);
        entity.setService(option.getService());
        entity.setServiceOption(option);
        entity.setQuantity(selection.quantity());
        entity.setPrice(linePrice(option, selection.quantity()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entities.add(entity);
      }
    }
    bookingServiceRepository.saveAll(entities);
    refreshTotal(booking, fareTotal);
  }

  private Map<Long, ServiceOptionEntity> loadOptionsById(
      List<PassengerSelectionsCommand> commands) {
    Set<Long> optionIds = new HashSet<>();
    for (PassengerSelectionsCommand passenger : commands) {
      for (SelectionCommand selection : passenger.selections()) {
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

  private List<PassengerSelectionsCommand> normalizeCommands(
      List<PassengerSelectionsCommand> rawPassengers) {
    if (rawPassengers == null || rawPassengers.isEmpty()) {
      throw new AppException("VALIDATION_FAILED", "passengers is required", 400);
    }
    Map<Long, PassengerSelectionsCommand> deduped = new LinkedHashMap<>();
    for (PassengerSelectionsCommand passenger : rawPassengers) {
      if (passenger == null || passenger.passengerId() == null || passenger.passengerId() <= 0) {
        throw new AppException(
            "VALIDATION_FAILED", "Each passenger selection must include a valid passengerId", 400);
      }
      List<SelectionCommand> selections =
          passenger.selections() == null ? List.of() : passenger.selections();
      Map<Long, SelectionCommand> dedupedSelections = new LinkedHashMap<>();
      for (SelectionCommand selection : selections) {
        if (selection == null
            || selection.optionId() == null
            || selection.optionId() <= 0
            || selection.quantity() == null
            || selection.quantity() <= 0) {
          throw new AppException(
              "VALIDATION_FAILED",
              "Each service selection must include a valid optionId and quantity",
              400);
        }
        dedupedSelections.put(selection.optionId(), selection);
      }
      deduped.put(
          passenger.passengerId(),
          new PassengerSelectionsCommand(
              passenger.passengerId(), List.copyOf(dedupedSelections.values())));
    }
    return List.copyOf(deduped.values());
  }

  private BookingEntity requireBooking(Long userId, Long bookingId, boolean forUpdate) {
    if (userId == null) {
      return forUpdate
          ? bookingRepository
              .findByIdForUpdate(bookingId)
              .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404))
          : bookingRepository
              .findById(bookingId)
              .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404));
    }
    return forUpdate
        ? bookingRepository
            .findByIdAndUserIdForUpdate(bookingId, userId)
            .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404))
        : bookingRepository
            .findByIdAndUserId(bookingId, userId)
            .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404));
  }

  private void validateBookingAssignable(BookingEntity booking) {
    String status = normalize(booking.getStatus());
    if (!"PENDING_PAYMENT".equals(status) && !"CONFIRMED".equals(status)) {
      throw new AppException(
          "BOOKING_NOT_EDITABLE", "Booking is not eligible for service selection", 409);
    }
    if ("PENDING_PAYMENT".equals(status)
        && booking.getPnrExpiresAt() != null
        && OffsetDateTime.now().isAfter(booking.getPnrExpiresAt())) {
      throw new AppException("BOOKING_EXPIRED", "Booking payment window has expired", 410);
    }
    if ("PENDING_PAYMENT".equals(status)
        && paymentRepository.existsByBookingIdAndPaymentStatus(
            booking.getId(), PAYMENT_STATUS_PENDING)) {
      throw new AppException(
          "BOOKING_PAYMENT_ALREADY_STARTED",
          "Booking services cannot be changed after payment has started",
          409);
    }
  }

  private void validateSelectionRules(
      List<PassengerSelectionsCommand> passengers, Map<Long, ServiceOptionEntity> optionsById) {
    for (PassengerSelectionsCommand passenger : passengers) {
      Map<Long, Integer> optionCountByServiceId = new HashMap<>();
      for (SelectionCommand selection : passenger.selections()) {
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

  private PaymentEntity requireRefundablePayment(Long bookingId, boolean forUpdate) {
    List<PaymentEntity> preferredPayments =
        forUpdate
            ? paymentRepository
                .findBookingLevelPaymentsByBookingIdAndPaymentStatusInForUpdateOrderByLatest(
                    bookingId, REFUNDABLE_PAYMENT_STATUSES)
            : paymentRepository.findBookingLevelPaymentsByBookingIdAndPaymentStatusInOrderByLatest(
                bookingId, REFUNDABLE_PAYMENT_STATUSES);
    if (!preferredPayments.isEmpty()) {
      return preferredPayments.getFirst();
    }
    List<PaymentEntity> fallbackPayments =
        forUpdate
            ? paymentRepository.findByBookingIdAndPaymentStatusInForUpdateOrderByLatest(
                bookingId, REFUNDABLE_PAYMENT_STATUSES)
            : paymentRepository.findByBookingIdAndPaymentStatusInOrderByLatest(
                bookingId, REFUNDABLE_PAYMENT_STATUSES);
    return fallbackPayments.stream()
        .findFirst()
        .orElseThrow(
            () ->
                new AppException(
                    "PAYMENT_NOT_REFUNDABLE", "Booking does not have a refundable payment", 409));
  }

  private void validateExpectedSummary(
      ServiceChangePreviewView.SummaryView summary,
      BigDecimal expectedAdditionalAmount,
      BigDecimal expectedRefundAmount,
      BigDecimal expectedNetAmount) {
    if (normalizeMoney(expectedAdditionalAmount).compareTo(summary.additionalAmount()) != 0
        || normalizeMoney(expectedRefundAmount).compareTo(summary.refundAmount()) != 0
        || normalizeMoney(expectedNetAmount).compareTo(summary.netAmount()) != 0) {
      throw new AppException(
          "SERVICE_CHANGE_PREVIEW_STALE",
          "Service change totals are out of date. Please review the latest preview.",
          409);
    }
  }

  private SummaryView toSummary(ServiceChangePreviewView.SummaryView summary) {
    return new SummaryView(summary.additionalAmount(), summary.refundAmount(), summary.netAmount());
  }

  private ServiceChangeConfirmView toConfirmView(ServiceChangeSettlementEntity settlement) {
    String normalizedStatus = normalize(settlement.getStatus());
    boolean hasRefund = settlement.getRefundAmount().signum() > 0;
    String paymentStatus =
        switch (normalizedStatus) {
          case SETTLEMENT_STATUS_PAYMENT_REQUIRED, SETTLEMENT_STATUS_PAYMENT_PENDING ->
              STATUS_PAYMENT_REQUIRED;
          case SETTLEMENT_STATUS_REFUND_PENDING, SETTLEMENT_STATUS_COMPLETED ->
              hasRefund ? STATUS_REFUND_PENDING : STATUS_COMPLETED;
          case SETTLEMENT_STATUS_PAYMENT_FAILED,
                  SETTLEMENT_STATUS_EXPIRED,
                  SETTLEMENT_STATUS_CANCELLED ->
              STATUS_REFUND_FAILED;
          default -> STATUS_COMPLETED;
        };
    String refundStatus;
    if (hasRefund && SETTLEMENT_STATUS_PAYMENT_FAILED.equals(normalizedStatus)) {
      refundStatus = "FAILED";
    } else if (hasRefund) {
      refundStatus = "SUCCEEDED";
    } else {
      refundStatus = null;
    }
    return new ServiceChangeConfirmView(
        settlement.getId(),
        settlement.getBooking().getId(),
        settlement.getCurrency(),
        settlement.getSettlementType(),
        paymentStatus,
        refundStatus,
        settlement.getProvider(),
        settlement.getProviderRefundId(),
        settlement.getErrorCode(),
        settlement.getErrorMessage(),
        hasRefund ? buildRefundMethod(null, settlement.getProvider()) : null,
        SETTLEMENT_COLLECT_PAYMENT.equals(settlement.getSettlementType())
            ? supportedPaymentMethods(settlement.getCurrency())
            : List.of(),
        new SummaryView(
            settlement.getAdditionalAmount(),
            settlement.getRefundAmount(),
            settlement.getNetAmount()));
  }

  private static ServiceChangeConfirmView.RefundMethodView buildRefundMethod(
      PaymentEntity payment, String provider) {
    String rawMethod = payment == null ? null : payment.getPaymentMethod();
    String type = rawMethod == null ? "" : rawMethod.trim().toUpperCase(Locale.ROOT);
    String label =
        switch (type) {
          case "CREDIT_CARD", "CARD", "STRIPE_CARD" -> "Credit card";
          case "DEBIT_CARD" -> "Debit card";
          case "BANK_TRANSFER", "BANK", "PAYOS_QR" -> "Bank transfer";
          case "EWALLET", "E_WALLET", "WALLET" -> "E-wallet";
          case "QR", "QR_CODE" -> "QR Code";
          default -> type.isEmpty() ? "Refund" : rawMethod;
        };
    String maskedAccount = buildMaskedAccount(payment);
    String estimatedDays = resolveEstimatedDays(provider);
    return new ServiceChangeConfirmView.RefundMethodView(
        type.isEmpty() ? null : type, label, maskedAccount, estimatedDays);
  }

  private static String buildMaskedAccount(PaymentEntity payment) {
    if (payment == null) {
      return null;
    }
    String accountNumber = payment.getRefundCounterAccountNumber();
    String bankName = payment.getRefundCounterAccountBankName();
    if (accountNumber != null && !accountNumber.isBlank()) {
      String trimmed = accountNumber.trim();
      String tail = trimmed.length() <= 4 ? trimmed : trimmed.substring(trimmed.length() - 4);
      String masked = "**** " + tail;
      return bankName != null && !bankName.isBlank() ? bankName + " " + masked : masked;
    }
    if (bankName != null && !bankName.isBlank()) {
      return bankName;
    }
    return null;
  }

  private static String resolveEstimatedDays(String provider) {
    String normalized = provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "STRIPE" -> "5-10 business days";
      case "PAYOS" -> "Processed 24/7";
      default -> "3-7 business days";
    };
  }

  private ServiceChangeSettlementEntity saveSettlement(
      BookingEntity booking,
      ServiceChangePreviewView preview,
      List<PassengerSelectionsCommand> commands,
      Map<Long, ServiceOptionEntity> optionsById,
      String idempotencyKey,
      String status,
      String provider,
      String providerRefundId,
      String errorCode,
      String errorMessage) {
    Map<Long, PassengerSelectionsCommand> commandsByPassenger =
        commands.stream()
            .collect(
                Collectors.toMap(
                    PassengerSelectionsCommand::passengerId,
                    command -> command,
                    (left, right) -> left,
                    LinkedHashMap::new));
    Map<Long, SelectionCommand> requestedByPassengerOption = new HashMap<>();
    for (PassengerSelectionsCommand command : commands) {
      for (SelectionCommand selection : command.selections()) {
        requestedByPassengerOption.put(
            selectionKey(command.passengerId(), selection.optionId()), selection);
      }
    }

    ServiceChangeSettlementEntity settlement = new ServiceChangeSettlementEntity();
    settlement.setBooking(booking);
    settlement.setPreviewToken(preview.previewToken());
    settlement.setIdempotencyKey(idempotencyKey);
    settlement.setSettlementType(preview.settlementType());
    settlement.setStatus(status);
    settlement.setAdditionalAmount(preview.summary().additionalAmount());
    settlement.setRefundAmount(preview.summary().refundAmount());
    settlement.setNetAmount(preview.summary().netAmount());
    settlement.setCurrency(preview.currency());
    settlement.setProvider(provider);
    settlement.setProviderRefundId(providerRefundId);
    settlement.setErrorCode(errorCode);
    settlement.setErrorMessage(errorMessage);
    OffsetDateTime now = OffsetDateTime.now();
    settlement.setCreatedAt(now);
    settlement.setUpdatedAt(now);
    for (ServiceChangePreviewView.ItemView itemView : preview.items()) {
      ServiceChangeSettlementItemEntity item = new ServiceChangeSettlementItemEntity();
      item.setSettlement(settlement);
      item.setPassengerId(itemView.passengerId());
      item.setServiceId(itemView.serviceId());
      item.setOptionId(itemView.optionId());
      SelectionCommand requested =
          requestedByPassengerOption.get(selectionKey(itemView.passengerId(), itemView.optionId()));
      item.setQuantity(requested == null ? 0 : requested.quantity());
      item.setServiceType(itemView.serviceType());
      item.setServiceName(itemView.serviceName());
      item.setOptionName(itemView.optionName());
      item.setOldAmount(itemView.oldAmount());
      item.setNewAmount(itemView.newAmount());
      item.setDeltaAmount(itemView.delta());
      settlement.getItems().add(item);
    }
    return serviceChangeSettlementRepository.save(settlement);
  }

  private List<ServiceChangeConfirmView.PaymentMethodItemView> supportedPaymentMethods(
      String currency) {
    if (!VND.equalsIgnoreCase(currency)) {
      return List.of();
    }
    List<ServiceChangeConfirmView.PaymentMethodItemView> methods = new ArrayList<>();
    if (paymentProperties.isPayosConfigured()) {
      methods.add(
          new ServiceChangeConfirmView.PaymentMethodItemView(
              "PAYOS_QR", "Mobile Banking (VietQR)", "PAYOS", null, true));
    }
    if (paymentProperties.isStripeConfigured()) {
      methods.add(
          new ServiceChangeConfirmView.PaymentMethodItemView(
              "STRIPE_CARD", "International credit or debit card", "STRIPE", null, true));
    }
    return List.copyOf(methods);
  }

  private Map<Long, PassengerSelectionsCommand> restoreCommands(
      ServiceChangeSettlementEntity settlement) {
    Map<Long, List<SelectionCommand>> byPassenger = new LinkedHashMap<>();
    for (ServiceChangeSettlementItemEntity item : settlement.getItems()) {
      if (item.getQuantity() == null
          || item.getQuantity() <= 0
          || item.getNewAmount().signum() <= 0) {
        continue;
      }
      byPassenger
          .computeIfAbsent(item.getPassengerId(), ignored -> new ArrayList<>())
          .add(new SelectionCommand(item.getOptionId(), item.getQuantity()));
    }
    Map<Long, PassengerSelectionsCommand> commands = new LinkedHashMap<>();
    for (Map.Entry<Long, List<SelectionCommand>> entry : byPassenger.entrySet()) {
      commands.put(
          entry.getKey(),
          new PassengerSelectionsCommand(entry.getKey(), List.copyOf(entry.getValue())));
    }
    for (ServiceChangeSettlementItemEntity item : settlement.getItems()) {
      commands.putIfAbsent(
          item.getPassengerId(), new PassengerSelectionsCommand(item.getPassengerId(), List.of()));
    }
    return commands;
  }

  private BigDecimal captureFareTotal(BookingEntity booking) {
    BigDecimal fareTotal =
        normalizeMoney(booking.getTotalAmount())
            .subtract(normalizeMoney(bookingServiceRepository.sumPriceByBookingId(booking.getId())))
            .subtract(
                normalizeMoney(
                    passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(
                        booking.getId())));
    if (fareTotal.signum() < 0) {
      throw new AppException(
          "BOOKING_TOTAL_INVALID", "Booking total is lower than selected add-ons", 409);
    }
    return fareTotal.setScale(2, RoundingMode.HALF_UP);
  }

  private void refreshTotal(BookingEntity booking, BigDecimal fareTotal) {
    BigDecimal serviceTotal =
        normalizeMoney(bookingServiceRepository.sumPriceByBookingId(booking.getId()));
    BigDecimal seatSurchargeTotal =
        normalizeMoney(
            passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(booking.getId()));
    BigDecimal totalAmount =
        normalizeMoney(fareTotal)
            .add(serviceTotal)
            .add(seatSurchargeTotal)
            .setScale(2, RoundingMode.HALF_UP);
    booking.setTotalAmount(totalAmount);
    booking.setUpdatedAt(OffsetDateTime.now());
    bookingRepository.save(booking);
  }

  private BigDecimal linePrice(ServiceOptionEntity option, int quantity) {
    return normalizeMoney(option.getPrice())
        .multiply(BigDecimal.valueOf(quantity))
        .setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal normalizeMoney(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  private String resolveRefundedPaymentStatus(PaymentEntity payment, BigDecimal refundAmount) {
    if (payment.getAmount() != null
        && normalizeMoney(refundAmount).compareTo(normalizeMoney(payment.getAmount())) >= 0) {
      return PAYMENT_STATUS_REFUNDED;
    }
    return PAYMENT_STATUS_PARTIALLY_REFUNDED;
  }

  private String buildPassengerName(PassengerEntity passenger) {
    String lastName = passenger.getLastName() == null ? "" : passenger.getLastName().trim();
    String firstName = passenger.getFirstName() == null ? "" : passenger.getFirstName().trim();
    String fullName = (lastName + " " + firstName).trim();
    return fullName.isEmpty() ? "Passenger " + passenger.getId() : fullName;
  }

  private String buildPreviewToken(Long bookingId, List<PassengerSelectionsCommand> passengers) {
    String payload =
        bookingId
            + "|"
            + passengers.stream()
                .map(
                    passenger ->
                        passenger.passengerId()
                            + ":"
                            + passenger.selections().stream()
                                .map(selection -> selection.optionId() + "x" + selection.quantity())
                                .sorted()
                                .collect(Collectors.joining(",")))
                .collect(Collectors.joining("|"));
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return "svcchg_prev_"
          + HexFormat.of()
              .formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)))
              .substring(0, 16);
    } catch (NoSuchAlgorithmException exception) {
      return "svcchg_prev_" + Math.abs(payload.hashCode());
    }
  }

  private Long selectionKey(Long passengerId, Long optionId) {
    return passengerId * 1_000_000L + optionId;
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  public record PassengerSelectionsCommand(Long passengerId, List<SelectionCommand> selections) {}

  public record SelectionCommand(Long optionId, Integer quantity) {}

  private record PreparedSettlement(
      BookingEntity booking,
      ServiceChangePreviewView preview,
      List<PassengerSelectionsCommand> commands,
      Map<Long, ServiceOptionEntity> optionsById) {}
}
