package com.example.moduleweb.application.booking;

import com.example.common.exception.AppException;
import com.example.modulepayment.application.FlightChangeConfirmView;
import com.example.modulepayment.application.FlightChangeSettlementService;
import com.example.moduleweb.controller.booking.dto.FlightChangeConfirmRequest;
import com.example.moduleweb.controller.booking.dto.FlightChangeConfirmResponse;
import com.example.moduleweb.controller.booking.dto.FlightChangeQuoteRequest;
import com.example.moduleweb.controller.booking.dto.FlightChangeQuoteResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightChangeConfirmService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

  private final FlightChangeQuoteService flightChangeQuoteService;
  private final FlightChangeSettlementService flightChangeSettlementService;

  public FlightChangeConfirmService(
      FlightChangeQuoteService flightChangeQuoteService,
      FlightChangeSettlementService flightChangeSettlementService) {
    this.flightChangeQuoteService = flightChangeQuoteService;
    this.flightChangeSettlementService = flightChangeSettlementService;
  }

  @Transactional
  public FlightChangeConfirmResponse confirm(
      Long userId, Long bookingId, FlightChangeConfirmRequest request) {
    if (!StringUtils.hasText(request.idempotencyKey())) {
      throw new AppException("VALIDATION_FAILED", "idempotencyKey is required", 400);
    }

    FlightChangeQuoteService.QuoteSnapshotView snapshot =
        flightChangeQuoteService.requireQuoteSnapshot(request.quoteToken());
    if (!bookingId.equals(snapshot.bookingId())) {
      throw new AppException("BOOKING_NOT_FOUND", "Booking not found", 404);
    }

    FlightChangeQuoteResponse refreshedQuote =
        flightChangeQuoteService.quote(
            userId,
            bookingId,
            new FlightChangeQuoteRequest(
                snapshot.ticketIds(),
                snapshot.currentFlightId(),
                snapshot.newFlightId(),
                snapshot.newFareId()));
    validateExpectedSummary(refreshedQuote.summary(), request);

    FlightChangeConfirmView view =
        flightChangeSettlementService.confirm(
            new FlightChangeSettlementService.ConfirmCommand(
                userId,
                bookingId,
                request.quoteToken(),
                request.idempotencyKey(),
                snapshot.currentFlightId(),
                snapshot.newFlightId(),
                snapshot.newFareId(),
                normalizeSettlementType(refreshedQuote.settlementType()),
                refreshedQuote.currency(),
                positive(refreshedQuote.summary().netAmount()),
                negativeAbs(refreshedQuote.summary().netAmount()),
                money(refreshedQuote.summary().netAmount()),
                money(request.expectedAdditionalAmount()),
                money(request.expectedRefundAmount()),
                money(request.expectedNetAmount()),
                refreshedQuote.items().stream()
                    .map(
                        item ->
                            new FlightChangeSettlementService.ItemCommand(
                                item.ticketId(),
                                item.passengerId(),
                                item.passengerName(),
                                item.passengerType(),
                                money(item.originalFareAmount()),
                                money(item.newFareAmount()),
                                money(item.fareDifference()),
                                money(item.changeFee()),
                                money(item.taxDifference()),
                                money(item.netAmount())))
                    .toList()));

    return toResponse(view);
  }

  private void validateExpectedSummary(
      FlightChangeQuoteResponse.SummaryResponse summary, FlightChangeConfirmRequest request) {
    BigDecimal netAmount = money(summary.netAmount());
    BigDecimal additionalAmount = positive(netAmount);
    BigDecimal refundAmount = negativeAbs(netAmount);
    if (additionalAmount.compareTo(money(request.expectedAdditionalAmount())) != 0
        || refundAmount.compareTo(money(request.expectedRefundAmount())) != 0
        || netAmount.compareTo(money(request.expectedNetAmount())) != 0) {
      throw new AppException(
          "FLIGHT_CHANGE_QUOTE_STALE",
          "Flight change totals are out of date. Please review the latest quote.",
          409);
    }
  }

  private FlightChangeConfirmResponse toResponse(FlightChangeConfirmView view) {
    return new FlightChangeConfirmResponse(
        view.settlementId(),
        view.bookingId(),
        view.currency(),
        view.settlementType(),
        view.status(),
        view.refundStatus(),
        view.provider(),
        view.providerRefundId(),
        view.errorCode(),
        view.errorMessage(),
        view.refundMethod() == null
            ? null
            : new FlightChangeConfirmResponse.RefundMethodResponse(
                view.refundMethod().type(),
                view.refundMethod().label(),
                view.refundMethod().maskedAccount(),
                view.refundMethod().estimatedDays()),
        view.paymentMethods().stream()
            .map(
                item ->
                    new FlightChangeConfirmResponse.PaymentMethodItemResponse(
                        item.code(), item.label(), item.provider(), item.icon(), item.supported()))
            .toList(),
        new FlightChangeConfirmResponse.SummaryResponse(
            view.summary().additionalAmount(),
            view.summary().refundAmount(),
            view.summary().netAmount()));
  }

  private String normalizeSettlementType(String value) {
    if (!StringUtils.hasText(value)) {
      return "NO_CHANGE";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private BigDecimal positive(BigDecimal value) {
    BigDecimal amount = money(value);
    return amount.signum() > 0 ? amount : ZERO;
  }

  private BigDecimal negativeAbs(BigDecimal value) {
    BigDecimal amount = money(value);
    return amount.signum() < 0 ? amount.abs() : ZERO;
  }

  private BigDecimal money(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }
}
