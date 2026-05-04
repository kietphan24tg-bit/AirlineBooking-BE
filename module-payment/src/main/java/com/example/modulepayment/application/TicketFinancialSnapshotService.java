package com.example.modulepayment.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.modulepayment.entity.TicketFinancialSnapshotEntity;
import com.example.modulepayment.repository.TicketFinancialSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TicketFinancialSnapshotService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  private static final BigDecimal CHILD_RATE = new BigDecimal("0.75");
  private static final BigDecimal INFANT_RATE = new BigDecimal("0.10");

  private final TicketRepository ticketRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final TicketFinancialSnapshotRepository ticketFinancialSnapshotRepository;

  public TicketFinancialSnapshotService(
      TicketRepository ticketRepository,
      BookingServiceRepository bookingServiceRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      TicketFinancialSnapshotRepository ticketFinancialSnapshotRepository) {
    this.ticketRepository = ticketRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.ticketFinancialSnapshotRepository = ticketFinancialSnapshotRepository;
  }

  public void ensureSnapshotsForBooking(Long bookingId) {
    List<TicketEntity> tickets = ticketRepository.findByBookingIdWithDetails(bookingId);
    if (tickets.isEmpty()) {
      throw new AppException(
          "TICKETS_NOT_ISSUED", "Tickets have not been issued for this booking yet", 409);
    }
    ensureSnapshotsForBooking(tickets.getFirst().getBooking(), tickets);
  }

  public void ensureSnapshotsForBooking(BookingEntity booking) {
    List<TicketEntity> tickets = ticketRepository.findByBookingIdWithDetails(booking.getId());
    if (tickets.isEmpty()) {
      throw new AppException(
          "TICKETS_NOT_ISSUED", "Tickets have not been issued for this booking yet", 409);
    }
    ensureSnapshotsForBooking(booking, tickets);
  }

  private void ensureSnapshotsForBooking(BookingEntity booking, List<TicketEntity> tickets) {
    Map<Long, TicketFinancialSnapshotEntity> existingByTicketId =
        ticketFinancialSnapshotRepository.findByBookingIdWithDetails(booking.getId()).stream()
            .collect(
                LinkedHashMap::new,
                (map, snapshot) -> map.put(snapshot.getTicket().getId(), snapshot),
                Map::putAll);
    if (existingByTicketId.size() == tickets.size()) {
      return;
    }

    Map<Long, BigDecimal> serviceTotalByPassenger =
        bookingServiceRepository.sumPriceByBookingIdGroupedByPassenger(booking.getId()).stream()
            .collect(
                HashMap::new,
                (map, projection) ->
                    map.put(
                        projection.getPassengerId(),
                        normalizeMoney(projection.getTotalPrice())),
                Map::putAll);
    Map<Long, BigDecimal> seatTotalByPassenger =
        passengerSeatAssignmentRepository
            .sumBookedSurchargeByBookingIdGroupedByPassenger(booking.getId())
            .stream()
            .collect(
                HashMap::new,
                (map, projection) ->
                    map.put(
                        projection.getPassengerId(),
                        normalizeMoney(projection.getTotalSurcharge())),
                Map::putAll);

    BigDecimal bookingDiscount = normalizeMoney(booking.getDiscountAmount());
    BigDecimal totalGross =
        tickets.stream()
            .map(this::fareGrossAmount)
            .reduce(ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal allocatedDiscount = ZERO;
    OffsetDateTime now = OffsetDateTime.now();

    for (int i = 0; i < tickets.size(); i++) {
      TicketEntity ticket = tickets.get(i);
      if (existingByTicketId.containsKey(ticket.getId())) {
        continue;
      }

      BigDecimal fareGross = fareGrossAmount(ticket);
      BigDecimal promoAllocated;
      if (bookingDiscount.signum() <= 0 || totalGross.signum() <= 0) {
        promoAllocated = ZERO;
      } else if (i == tickets.size() - 1) {
        promoAllocated = bookingDiscount.subtract(allocatedDiscount);
      } else {
        promoAllocated =
            bookingDiscount
                .multiply(fareGross)
                .divide(totalGross, 2, RoundingMode.HALF_UP)
                .min(bookingDiscount.subtract(allocatedDiscount));
      }
      promoAllocated = normalizeMoney(promoAllocated);
      allocatedDiscount = normalizeMoney(allocatedDiscount.add(promoAllocated));

      TicketFinancialSnapshotEntity snapshot = new TicketFinancialSnapshotEntity();
      snapshot.setBooking(booking);
      snapshot.setTicket(ticket);
      snapshot.setPassenger(ticket.getPassenger());
      snapshot.setFareGrossAmount(fareGross);
      snapshot.setPromoAllocatedAmount(promoAllocated);
      snapshot.setFareNetAmount(normalizeMoney(fareGross.subtract(promoAllocated).max(ZERO)));
      snapshot.setSeatRefundAmount(
          normalizeMoney(serviceAmountForPassenger(seatTotalByPassenger, ticket.getPassenger().getId())));
      snapshot.setServiceRefundAmount(
          normalizeMoney(
              serviceAmountForPassenger(serviceTotalByPassenger, ticket.getPassenger().getId())));
      snapshot.setRefundFeeAmount(normalizeMoney(ticket.getFareClass().getRefundFee()));
      snapshot.setRefundable(ticket.getFareClass().isRefundable());
      snapshot.setPassengerType(ticket.getPassenger().getPassengerType());
      snapshot.setCurrency(ticket.getBooking().getCurrency());
      snapshot.setCreatedAt(now);
      snapshot.setUpdatedAt(now);
      ticketFinancialSnapshotRepository.save(snapshot);
    }
  }

  private BigDecimal fareGrossAmount(TicketEntity ticket) {
    BigDecimal oneAdultTotal =
        ticket.getFlightFare() == null ? ZERO : normalizeMoney(ticket.getFlightFare().getTotalPrice());
    String passengerType =
        ticket.getPassenger() == null ? null : ticket.getPassenger().getPassengerType();
    if ("CHILD".equalsIgnoreCase(passengerType)) {
      return normalizeMoney(oneAdultTotal.multiply(CHILD_RATE));
    }
    if ("INFANT".equalsIgnoreCase(passengerType)) {
      return normalizeMoney(oneAdultTotal.multiply(INFANT_RATE));
    }
    return oneAdultTotal;
  }

  private BigDecimal serviceAmountForPassenger(Map<Long, BigDecimal> map, Long passengerId) {
    if (passengerId == null) {
      return ZERO;
    }
    return normalizeMoney(map.get(passengerId));
  }

  private BigDecimal normalizeMoney(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }
}
