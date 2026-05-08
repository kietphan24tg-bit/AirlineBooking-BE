package com.example.appbootstrap.application.booking;

import com.example.appbootstrap.config.MailProperties;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.exception.BookingNotFoundException;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.modulepayment.application.BookingConfirmationNotifier;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingConfirmationEmailService implements BookingConfirmationNotifier {

  private static final Logger log = LoggerFactory.getLogger(BookingConfirmationEmailService.class);
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm XXX");

  private final JavaMailSender mailSender;
  private final MailProperties mailProperties;
  private final BookingRepository bookingRepository;
  private final BookingContactRepository bookingContactRepository;
  private final TicketRepository ticketRepository;

  public BookingConfirmationEmailService(
      JavaMailSender mailSender,
      MailProperties mailProperties,
      BookingRepository bookingRepository,
      BookingContactRepository bookingContactRepository,
      TicketRepository ticketRepository) {
    this.mailSender = mailSender;
    this.mailProperties = mailProperties;
    this.bookingRepository = bookingRepository;
    this.bookingContactRepository = bookingContactRepository;
    this.ticketRepository = ticketRepository;
  }

  @Override
  @Transactional
  public void sendIfNeeded(Long bookingId) {
    BookingEntity booking =
        bookingRepository
            .findById(bookingId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));
    if (booking.getTicketEmailSentAt() != null) {
      return;
    }

    List<TicketEntity> tickets = ticketRepository.findByBookingIdWithDetails(bookingId);
    if (tickets.isEmpty()) {
      log.warn("Booking confirmation email skipped: no tickets found bookingId={}", bookingId);
      return;
    }
    List<String> recipients = recipientsFor(booking);
    if (recipients.isEmpty()) {
      log.warn("Booking confirmation email skipped: no recipients found bookingId={}", bookingId);
      return;
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailProperties.from());
    message.setTo(recipients.toArray(String[]::new));
    message.setSubject("Your booking is confirmed - PNR " + booking.getPnrCode());
    message.setText(buildBody(booking, tickets));

    try {
      mailSender.send(message);
    } catch (MailException exception) {
      log.warn(
          "Booking confirmation email failed bookingId={} recipients={}",
          bookingId,
          recipients,
          exception);
      return;
    }

    OffsetDateTime now = OffsetDateTime.now();
    booking.setTicketEmailSentAt(now);
    booking.setUpdatedAt(now);
    bookingRepository.save(booking);
  }

  private List<String> recipientsFor(BookingEntity booking) {
    String primaryEmail =
        bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(booking.getId()).stream()
            .filter(BookingContactEntity::isPrimary)
            .map(BookingContactEntity::getEmail)
            .map(this::normalizeEmail)
            .filter(email -> email != null && !email.isBlank())
            .findFirst()
            .orElse(null);
    if (primaryEmail != null && !primaryEmail.isBlank()) {
      return List.of(primaryEmail);
    }

    String fallback = normalizeEmail(booking.getContactEmail());
    return fallback == null || fallback.isBlank() ? List.of() : List.of(fallback);
  }

  private String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  private String buildBody(BookingEntity booking, List<TicketEntity> tickets) {
    FlightEntity flight = tickets.getFirst().getFlight();
    AirportEntity departure = flight.getDepartureAirport();
    AirportEntity arrival = flight.getArrivalAirport();

    StringBuilder body = new StringBuilder();
    body.append("Your booking is confirmed.\n\n");
    body.append("PNR: ").append(booking.getPnrCode()).append('\n');
    body.append("Booking ID: ").append(booking.getId()).append('\n');
    body.append("Payment: Paid\n");
    body.append("Total: ")
        .append(booking.getTotalAmount())
        .append(' ')
        .append(booking.getCurrency())
        .append("\n\n");
    body.append("Flight: ").append(flight.getFlightNumber()).append('\n');
    body.append("Route: ")
        .append(departure.getCode())
        .append(" -> ")
        .append(arrival.getCode())
        .append('\n');
    body.append("Departure: ").append(format(flight.getDepartureTime())).append('\n');
    body.append("Arrival: ").append(format(flight.getArrivalTime())).append("\n\n");
    body.append("Passengers:\n");
    for (TicketEntity ticket : tickets) {
      body.append("- ")
          .append(ticket.getPassenger().getLastName())
          .append(' ')
          .append(ticket.getPassenger().getFirstName())
          .append('\n');
      body.append("  Ticket: ").append(ticket.getTicketNumber()).append('\n');
      if (ticket.getFlightSeat() != null) {
        body.append("  Seat: ").append(ticket.getFlightSeat().getSeatNumber()).append('\n');
      }
    }
    body.append(
        "\nPlease bring a valid ID/passport and arrive at the airport at least 2 hours before departure.");
    return body.toString();
  }

  private String format(OffsetDateTime dateTime) {
    return dateTime == null ? "TBD" : DATE_TIME_FORMATTER.format(dateTime);
  }
}
