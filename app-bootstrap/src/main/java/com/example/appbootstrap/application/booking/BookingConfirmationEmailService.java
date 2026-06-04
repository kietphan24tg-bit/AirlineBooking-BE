package com.example.appbootstrap.application.booking;

import com.example.appbootstrap.config.MailProperties;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingServiceEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.exception.BookingNotFoundException;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.entity.FlightEntity;
import com.example.modulepayment.application.BookingConfirmationNotifier;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.repository.PaymentRepository;
import jakarta.mail.MessagingException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BookingConfirmationEmailService implements BookingConfirmationNotifier {

  private static final Logger log = LoggerFactory.getLogger(BookingConfirmationEmailService.class);
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US);
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm, dd MMM yyyy", Locale.US);
  private static final DateTimeFormatter TIME_ONLY_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm", Locale.US);
  private static final List<String> SUCCESS_STATUSES = List.of("SUCCESS");
  private static final List<String> BOOKED_ASSIGNMENT_STATUSES = List.of("BOOKED");

  private final JavaMailSender mailSender;
  private final MailProperties mailProperties;
  private final BookingRepository bookingRepository;
  private final BookingContactRepository bookingContactRepository;
  private final TicketRepository ticketRepository;
  private final PaymentRepository paymentRepository;
  private final BookingServiceRepository bookingServiceRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;

  public BookingConfirmationEmailService(
      JavaMailSender mailSender,
      MailProperties mailProperties,
      BookingRepository bookingRepository,
      BookingContactRepository bookingContactRepository,
      TicketRepository ticketRepository,
      PaymentRepository paymentRepository,
      BookingServiceRepository bookingServiceRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository) {
    this.mailSender = mailSender;
    this.mailProperties = mailProperties;
    this.bookingRepository = bookingRepository;
    this.bookingContactRepository = bookingContactRepository;
    this.ticketRepository = ticketRepository;
    this.paymentRepository = paymentRepository;
    this.bookingServiceRepository = bookingServiceRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
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

    PaymentEntity payment = latestSuccessfulPayment(bookingId);
    List<BookingServiceEntity> services =
        bookingServiceRepository.findDetailedByBookingId(bookingId);
    Map<String, PassengerSeatAssignmentEntity> seatAssignments =
        seatAssignmentsByPassengerFlight(bookingId);
    EmailView emailView =
        new EmailView(
            booking,
            tickets,
            payment,
            services,
            seatAssignments,
            primaryContact(booking),
            leadPassenger(tickets));

    try {
      var message = mailSender.createMimeMessage();
      var helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(mailProperties.from());
      helper.setTo(recipients.toArray(String[]::new));
      helper.setSubject("The Sovereign Booking Confirmation - PNR " + booking.getPnrCode());
      helper.setText(buildHtml(emailView), true);
      mailSender.send(message);
    } catch (MailException | MessagingException exception) {
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
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(null);
    if (StringUtils.hasText(primaryEmail)) {
      return List.of(primaryEmail);
    }

    String fallback = normalizeEmail(booking.getContactEmail());
    return StringUtils.hasText(fallback) ? List.of(fallback) : List.of();
  }

  private PaymentEntity latestSuccessfulPayment(Long bookingId) {
    return paymentRepository
        .findByBookingIdAndPaymentStatusInOrderByLatest(bookingId, SUCCESS_STATUSES)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private Map<String, PassengerSeatAssignmentEntity> seatAssignmentsByPassengerFlight(
      Long bookingId) {
    Map<String, PassengerSeatAssignmentEntity> result = new LinkedHashMap<>();
    for (PassengerSeatAssignmentEntity seatAssignment :
        passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(
            bookingId, BOOKED_ASSIGNMENT_STATUSES)) {
      result.put(
          passengerFlightKey(
              seatAssignment.getPassenger().getId(),
              seatAssignment.getFlightSeat().getFlight().getId()),
          seatAssignment);
    }
    return result;
  }

  private BookingContactEntity primaryContact(BookingEntity booking) {
    return bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(booking.getId()).stream()
        .filter(BookingContactEntity::isPrimary)
        .findFirst()
        .orElse(null);
  }

  private PassengerEntity leadPassenger(List<TicketEntity> tickets) {
    return tickets.isEmpty() ? null : tickets.getFirst().getPassenger();
  }

  private String buildHtml(EmailView view) {
    String intro =
        "Your booking has been confirmed successfully. "
            + "Please review your itinerary, passenger details, "
            + "and payment summary below.";

    return """
        <div style="margin:0;padding:32px 16px;background:#f5f1ed;font-family:Arial,sans-serif;color:#1f2937;">
          <div
            style="max-width:760px;margin:0 auto;background:#fffdf9;border:1px solid #eadfce;
              border-radius:28px;overflow:hidden;box-shadow:0 18px 44px rgba(62,16,30,0.10);">
            <div
              style="background:linear-gradient(135deg,#5e1025 0%%,#7d0633 72%%,#b38728 100%%);padding:24px 28px;color:#ffffff;">
              <table role="presentation" style="width:100%%;border-collapse:collapse;">
                <tr>
                  <td>
                    <div style="font-size:30px;font-weight:800;letter-spacing:-0.4px;">
                      The Sovereign
                    </div>
                    <div style="margin-top:6px;font-size:12px;letter-spacing:0.22em;text-transform:uppercase;color:#f4df9d;">
                      Booking Confirmation
                    </div>
                  </td>
                  <td style="text-align:right;font-size:13px;line-height:1.6;font-weight:700;vertical-align:middle;color:#f9e8b7;">
                    Premium Guest Services<br>
                    1900 1886
                  </td>
                </tr>
              </table>
            </div>
            <div style="padding:24px 28px 8px;">
              <div
                style="border:1px solid #eadfce;border-radius:22px;
                  background:linear-gradient(180deg,#fffaf2 0%%,#fffdf9 100%%);
                  padding:20px 22px;">
                <div style="font-size:12px;font-weight:800;letter-spacing:0.18em;text-transform:uppercase;color:#7d0633;">
                  Confirmed and ticketed
                </div>
                <p style="margin:10px 0 0;font-size:15px;line-height:1.7;color:#4b5563;">%s</p>
              </div>
            </div>
            %s
            %s
            %s
            %s
            %s
          </div>
        </div>
        """
        .formatted(
            escapeHtml(intro),
            buildBookingSection(view),
            buildPassengerSection(view),
            buildFlightSection(view),
            buildFareSection(view),
            buildPaymentSection(view));
  }

  private String buildBookingSection(EmailView view) {
    String statusNote =
        view.payment() != null && view.payment().getPaidAt() != null
            ? "Payment completed at " + formatDateTime(view.payment().getPaidAt())
            : "Payment details have been recorded.";
    String contactName = passengerName(view.leadPassenger());
    String email =
        firstNonBlank(contactEmail(view.primaryContact()), view.booking().getContactEmail(), "--");
    String phone =
        firstNonBlank(contactPhone(view.primaryContact()), view.booking().getContactPhone(), "--");
    String qrCell =
        hasText(view.booking().getPnrCode())
            ? "<div style=\"font-size:34px;font-weight:800;color:#7d0633;letter-spacing:0.08em;\">"
                + escapeHtml(view.booking().getPnrCode())
                + "</div>"
            : "<div style=\"font-size:18px;color:#6b7280;\">--</div>";
    String qrImage =
        view.payment() != null && hasText(view.payment().getQrImageUrl())
            ? "<img src=\""
                + escapeHtml(view.payment().getQrImageUrl())
                + "\" alt=\"Booking QR code\" width=\"108\" height=\"108\" "
                + "style=\"display:block;border:0;outline:none;text-decoration:none;\">"
            : "<div style=\"width:108px;height:108px;border:1px dashed #d2b77a;"
                + "border-radius:18px;"
                + "color:#a68a57;font-size:11px;display:flex;align-items:center;"
                + "justify-content:center;background:#fff8e8;\">Reference QR</div>";

    return """
        <div style="padding:16px 28px 0;">
          <div style="font-size:12px;font-weight:800;letter-spacing:0.18em;text-transform:uppercase;color:#7d0633;">Booking details</div>
          <table
            role="presentation"
            style="width:100%%;border-collapse:separate;border-spacing:0 14px;">
            <tr>
              <td style="width:56%%;vertical-align:top;">
                <table role="presentation" style="width:100%%;border-collapse:separate;border-spacing:0;">
                  <tr>
                    <td
                      style="width:34%%;background:#f7f1e7;color:#6b4f19;padding:12px 14px;
                        font-weight:700;border:1px solid #eadfce;border-right:0;border-radius:16px 0 0 0;">
                      Booking status
                    </td>
                    <td
                      style="background:#fffdf9;padding:12px 14px;border:1px solid #eadfce;
                        border-radius:0 16px 0 0;">
                      <div style="font-weight:800;color:#111827;">Confirmed</div>
                      <div style="color:#7d0633;font-size:13px;margin-top:4px;">%s</div>
                    </td>
                  </tr>
                  <tr>
                    <td
                      style="background:#f7f1e7;color:#6b4f19;padding:12px 14px;font-weight:700;
                        border-left:1px solid #eadfce;border-right:0;">Booked on</td>
                    <td
                      style="background:#fffdf9;padding:12px 14px;border-right:1px solid #eadfce;">
                      %s
                    </td>
                  </tr>
                  <tr>
                    <td
                      style="background:#f7f1e7;color:#6b4f19;padding:12px 14px;font-weight:700;
                        border-left:1px solid #eadfce;border-right:0;">Lead passenger</td>
                    <td
                      style="background:#fffdf9;padding:12px 14px;border-right:1px solid #eadfce;">
                      %s
                    </td>
                  </tr>
                  <tr>
                    <td
                      style="background:#f7f1e7;color:#6b4f19;padding:12px 14px;font-weight:700;
                        border-left:1px solid #eadfce;border-right:0;">Contact email</td>
                    <td
                      style="background:#fffdf9;padding:12px 14px;border-right:1px solid #eadfce;">
                      %s
                    </td>
                  </tr>
                  <tr>
                    <td
                      style="background:#f7f1e7;color:#6b4f19;padding:12px 14px;font-weight:700;
                        border:1px solid #eadfce;border-right:0;border-top:0;
                        border-radius:0 0 0 16px;">
                      Contact phone
                    </td>
                    <td
                      style="background:#fffdf9;padding:12px 14px;border:1px solid #eadfce;
                        border-top:0;border-radius:0 0 16px 0;">
                      %s
                    </td>
                  </tr>
                </table>
              </td>
              <td style="width:44%%;vertical-align:top;">
                <table role="presentation" style="width:100%%;border-collapse:separate;border-spacing:0;">
                  <tr>
                    <td
                      colspan="2"
                      style="background:#fff7e7;color:#7d0633;padding:12px 14px;font-size:13px;
                        font-weight:800;letter-spacing:0.14em;text-transform:uppercase;
                        border:1px solid #eadfce;border-radius:16px 16px 0 0;">
                      Reference
                    </td>
                  </tr>
                  <tr>
                    <td
                      style="width:58%%;background:#fffdf9;padding:18px 16px;
                        border-left:1px solid #eadfce;border-bottom:1px solid #eadfce;
                        vertical-align:middle;text-align:center;border-right:0;border-radius:0 0 0 16px;">
                      %s
                    </td>
                    <td
                      style="background:#fffdf9;padding:16px;border-right:1px solid #eadfce;
                        border-bottom:1px solid #eadfce;vertical-align:middle;text-align:center;
                        border-radius:0 0 16px 0;">
                      %s
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </div>
        """
        .formatted(
            escapeHtml(statusNote),
            escapeHtml(formatDate(view.booking().getCreatedAt())),
            escapeHtml(contactName),
            escapeHtml(email),
            escapeHtml(phone),
            qrCell,
            qrImage);
  }

  private String buildPassengerSection(EmailView view) {
    StringBuilder rows = new StringBuilder();
    for (TicketEntity ticket : view.tickets()) {
      PassengerEntity passenger = ticket.getPassenger();
      FlightSeatEntity seat = resolvedSeat(view.seatAssignments(), ticket);
      String seatLabel =
          seat == null || !hasText(seat.getSeatNumber()) ? "Not selected" : seat.getSeatNumber();
      rows.append(
          """
          <tr>
            <td style="padding:14px 16px;border-bottom:1px solid #f0e7da;background:#fffdf9;font-size:15px;font-weight:700;">%s</td>
            <td style="padding:14px 16px;border-bottom:1px solid #f0e7da;background:#fffdf9;font-size:14px;">%s</td>
            <td style="padding:14px 16px;border-bottom:1px solid #f0e7da;background:#fffdf9;font-size:14px;">%s</td>
          </tr>
          """
              .formatted(
                  escapeHtml(passengerName(passenger)),
                  escapeHtml(ticket.getFlight().getFlightNumber()),
                  escapeHtml(seatLabel)));
    }

    return """
        <div style="padding:20px 28px 0;">
          <div style="font-size:12px;font-weight:800;letter-spacing:0.18em;text-transform:uppercase;color:#7d0633;">Passengers</div>
          <table
            role="presentation"
            style="width:100%%;margin-top:12px;border-collapse:separate;border-spacing:0;
              background:#fffdf9;border:1px solid #eadfce;border-radius:20px;overflow:hidden;">
            <tr>
              <td style="width:48%%;background:#fff7e7;color:#6b4f19;padding:12px 16px;font-weight:800;border-bottom:1px solid #eadfce;">
                Passenger
              </td>
              <td style="width:24%%;background:#fff7e7;color:#6b4f19;padding:12px 16px;font-weight:800;border-bottom:1px solid #eadfce;">
                Flight
              </td>
              <td style="background:#fff7e7;color:#6b4f19;padding:12px 16px;font-weight:800;border-bottom:1px solid #eadfce;">Seat</td>
            </tr>
            %s
          </table>
        </div>
        """
        .formatted(rows);
  }

  private String buildFlightSection(EmailView view) {
    StringBuilder rows = new StringBuilder();
    LinkedHashMap<Long, TicketEntity> uniqueFlightMap = new LinkedHashMap<>();
    for (TicketEntity ticket : view.tickets()) {
      uniqueFlightMap.putIfAbsent(ticket.getFlight().getId(), ticket);
    }
    List<TicketEntity> uniqueFlights =
        uniqueFlightMap.values().stream()
            .sorted(
                Comparator.comparing(
                        (TicketEntity ticket) -> ticket.getFlight().getDepartureTime(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ticket -> ticket.getFlight().getId()))
            .toList();

    for (TicketEntity ticket : uniqueFlights) {
      FlightEntity flight = ticket.getFlight();
      String route =
          firstNonBlank(flight.getDepartureAirport().getCode(), "--")
              + " -> "
              + firstNonBlank(flight.getArrivalAirport().getCode(), "--");
      rows.append(
          """
          <tr>
            <td style="padding:14px 16px;border-bottom:1px solid #f0e7da;background:#fffdf9;">
              <div style="font-weight:800;color:#111827;">%s</div>
              <div style="margin-top:4px;font-size:12px;color:#6b7280;">%s</div>
            </td>
            <td style="padding:14px 16px;border-bottom:1px solid #f0e7da;background:#fffdf9;">%s</td>
            <td style="padding:14px 16px;border-bottom:1px solid #f0e7da;background:#fffdf9;">%s</td>
            <td style="padding:14px 16px;border-bottom:1px solid #f0e7da;background:#fffdf9;">%s</td>
            <td style="padding:14px 16px;border-bottom:1px solid #f0e7da;background:#fffdf9;">%s</td>
          </tr>
          """
              .formatted(
                  escapeHtml(flight.getFlightNumber()),
                  escapeHtml(route),
                  escapeHtml(formatDate(flight.getDepartureTime())),
                  escapeHtml(nullToBlank(ticket.getFareClass().getCode())),
                  escapeHtml(
                      formatFlightMoment(
                          flight.getDepartureTime(),
                          flight.getDepartureAirport().getTimeZone(),
                          flight.getDepartureAirport().getCity(),
                          flight.getDepartureAirport().getCode(),
                          flight.getDepartureTerminal())),
                  escapeHtml(
                      formatFlightMoment(
                          flight.getArrivalTime(),
                          flight.getArrivalAirport().getTimeZone(),
                          flight.getArrivalAirport().getCity(),
                          flight.getArrivalAirport().getCode(),
                          flight.getArrivalTerminal()))));
    }

    return """
        <div style="padding:20px 28px 0;">
          <div style="font-size:12px;font-weight:800;letter-spacing:0.18em;text-transform:uppercase;color:#7d0633;">Flight summary</div>
          <table
            role="presentation"
            style="width:100%%;margin-top:12px;border-collapse:separate;border-spacing:0;
              background:#fffdf9;border:1px solid #eadfce;border-radius:20px;overflow:hidden;">
            <tr>
              <td style="background:#fff7e7;color:#6b4f19;padding:12px 16px;font-weight:800;border-bottom:1px solid #eadfce;">Flight</td>
              <td style="background:#fff7e7;color:#6b4f19;padding:12px 16px;font-weight:800;border-bottom:1px solid #eadfce;">Date</td>
              <td style="background:#fff7e7;color:#6b4f19;padding:12px 16px;font-weight:800;border-bottom:1px solid #eadfce;">Fare</td>
              <td style="background:#fff7e7;color:#6b4f19;padding:12px 16px;font-weight:800;border-bottom:1px solid #eadfce;">
                Departure
              </td>
              <td style="background:#fff7e7;color:#6b4f19;padding:12px 16px;font-weight:800;border-bottom:1px solid #eadfce;">
                Arrival
              </td>
            </tr>
            %s
          </table>
        </div>
        """
        .formatted(rows);
  }

  private String buildFareSection(EmailView view) {
    List<FareLine> lines = fareLines(view);
    BigDecimal fareSubtotal = BigDecimal.ZERO;
    BigDecimal tax = BigDecimal.ZERO;
    BigDecimal ancillary = BigDecimal.ZERO;
    BigDecimal total = BigDecimal.ZERO;
    StringBuilder rows = new StringBuilder();

    for (FareLine line : lines) {
      fareSubtotal = fareSubtotal.add(nonNullMoney(line.baseAmount()));
      tax = tax.add(nonNullMoney(line.tax()));
      ancillary = ancillary.add(nonNullMoney(line.ancillaryAmount()));
      total = total.add(nonNullMoney(line.total()));
      rows.append(
          """
          <tr>
            <td style="padding:12px 14px;border-bottom:1px solid #f0e7da;background:#fffdf9;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #f0e7da;background:#fffdf9;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #f0e7da;background:#fffdf9;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #f0e7da;background:#fffdf9;text-align:right;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #f0e7da;background:#fffdf9;text-align:right;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #f0e7da;background:#fffdf9;text-align:right;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #f0e7da;background:#fffdf9;text-align:right;font-weight:700;">%s</td>
          </tr>
          """
              .formatted(
                  escapeHtml(line.leg()),
                  escapeHtml(line.passengerName()),
                  escapeHtml(line.description()),
                  escapeHtml(formatMoney(line.baseAmount(), line.currency())),
                  escapeHtml(formatMoney(line.tax(), line.currency())),
                  escapeHtml(formatMoney(line.ancillaryAmount(), line.currency())),
                  escapeHtml(formatMoney(line.total(), line.currency()))));
    }

    rows.append(
        """
        <tr>
          <td
            colspan="3"
            style="padding:14px;border-bottom:0;background:#fff7e7;font-weight:800;text-align:right;color:#7d0633;">
            Total paid
          </td>
          <td style="padding:14px;border-bottom:0;background:#fff7e7;font-weight:800;text-align:right;color:#7d0633;">%s</td>
          <td style="padding:14px;border-bottom:0;background:#fff7e7;font-weight:800;text-align:right;color:#7d0633;">%s</td>
          <td style="padding:14px;border-bottom:0;background:#fff7e7;font-weight:800;text-align:right;color:#7d0633;">%s</td>
          <td style="padding:14px;border-bottom:0;background:#fff7e7;font-weight:800;text-align:right;color:#7d0633;">%s</td>
        </tr>
        """
            .formatted(
                escapeHtml(formatMoney(fareSubtotal, view.booking().getCurrency())),
                escapeHtml(formatMoney(tax, view.booking().getCurrency())),
                escapeHtml(formatMoney(ancillary, view.booking().getCurrency())),
                escapeHtml(formatMoney(total, view.booking().getCurrency()))));

    return """
        <div style="padding:20px 28px 0;">
          <div style="font-size:12px;font-weight:800;letter-spacing:0.18em;text-transform:uppercase;color:#7d0633;">Fare and add-ons</div>
          <table
            role="presentation"
            style="width:100%%;margin-top:12px;border-collapse:separate;border-spacing:0;
              background:#fffdf9;border:1px solid #eadfce;border-radius:20px;overflow:hidden;">
            <tr>
              <td style="background:#fff7e7;color:#6b4f19;padding:12px 14px;font-weight:800;border-bottom:1px solid #eadfce;">Leg</td>
              <td style="background:#fff7e7;color:#6b4f19;padding:12px 14px;font-weight:800;border-bottom:1px solid #eadfce;">Passenger</td>
              <td
                style="background:#fff7e7;color:#6b4f19;padding:12px 14px;font-weight:800;
                  border-bottom:1px solid #eadfce;">Description</td>
              <td
                style="background:#fff7e7;color:#6b4f19;padding:12px 14px;font-weight:800;
                  border-bottom:1px solid #eadfce;text-align:right;">
                Base fare
              </td>
              <td
                style="background:#fff7e7;color:#6b4f19;padding:12px 14px;font-weight:800;
                  border-bottom:1px solid #eadfce;text-align:right;">Taxes</td>
              <td
                style="background:#fff7e7;color:#6b4f19;padding:12px 14px;font-weight:800;
                  border-bottom:1px solid #eadfce;text-align:right;">
                Seat / services
              </td>
              <td
                style="background:#fff7e7;color:#6b4f19;padding:12px 14px;font-weight:800;
                  border-bottom:1px solid #eadfce;text-align:right;">Total</td>
            </tr>
            %s
          </table>
        </div>
        """
        .formatted(rows);
  }

  private String buildPaymentSection(EmailView view) {
    String paymentDate = view.payment() == null ? "--" : formatDateTime(view.payment().getPaidAt());
    String paymentMethod =
        view.payment() == null ? "--" : paymentMethodLabel(view.payment().getPaymentMethod());
    String amount =
        view.payment() == null
            ? formatMoney(view.booking().getTotalAmount(), view.booking().getCurrency())
            : formatMoney(view.payment().getAmount(), view.payment().getCurrency());

    return """
        <div style="padding:20px 28px 28px;">
          <div style="font-size:12px;font-weight:800;letter-spacing:0.18em;text-transform:uppercase;color:#7d0633;">Payment</div>
          <table
            role="presentation"
            style="width:100%%;margin-top:12px;border-collapse:separate;border-spacing:0;
              background:#fffdf9;border:1px solid #eadfce;border-radius:20px;overflow:hidden;">
            <tr>
              <td style="background:#fff7e7;color:#6b4f19;padding:12px 16px;font-weight:800;border-bottom:1px solid #eadfce;">
                Paid at
              </td>
              <td style="background:#fff7e7;color:#6b4f19;padding:12px 16px;font-weight:800;border-bottom:1px solid #eadfce;">
                Method
              </td>
              <td
                style="background:#fff7e7;color:#6b4f19;padding:12px 16px;font-weight:800;
                  border-bottom:1px solid #eadfce;text-align:right;">
                Amount
              </td>
            </tr>
            <tr>
              <td style="padding:14px 16px;background:#fffdf9;">%s</td>
              <td style="padding:14px 16px;background:#fffdf9;">%s</td>
              <td style="padding:14px 16px;background:#fffdf9;text-align:right;font-weight:800;color:#7d0633;">%s</td>
            </tr>
          </table>
        </div>
        """
        .formatted(escapeHtml(paymentDate), escapeHtml(paymentMethod), escapeHtml(amount));
  }

  private List<FareLine> fareLines(EmailView view) {
    List<FareLine> lines = new ArrayList<>();
    String currency = firstNonBlank(view.booking().getCurrency(), "VND");

    for (TicketEntity ticket : view.tickets()) {
      PassengerEntity passenger = ticket.getPassenger();
      String leg = ticket.getFlight().getFlightNumber();
      String passengerName = passengerName(passenger);

      lines.add(
          new FareLine(
              leg,
              passengerName,
              firstNonBlank(ticket.getFareClass().getCode(), "Fare")
                  + " - "
                  + firstNonBlank(ticket.getFareClass().getName(), "Standard fare"),
              nonNullMoney(ticket.getFlightFare().getBasePrice()),
              nonNullMoney(ticket.getFlightFare().getTotalTaxAmount()),
              BigDecimal.ZERO,
              nonNullMoney(ticket.getFlightFare().getTotalPrice()),
              currency));

      PassengerSeatAssignmentEntity seatAssignment =
          view.seatAssignments()
              .get(passengerFlightKey(passenger.getId(), ticket.getFlight().getId()));
      if (seatAssignment != null
          && seatAssignment.getSurchargeAmount() != null
          && seatAssignment.getSurchargeAmount().compareTo(BigDecimal.ZERO) > 0) {
        lines.add(
            new FareLine(
                leg,
                passengerName,
                "Seat selection - " + nullToBlank(seatAssignment.getFlightSeat().getSeatNumber()),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                seatAssignment.getSurchargeAmount(),
                seatAssignment.getSurchargeAmount(),
                firstNonBlank(seatAssignment.getCurrency(), currency)));
      }
    }

    for (BookingServiceEntity service : view.services()) {
      lines.add(
          new FareLine(
              "Add-on",
              passengerName(service.getPassenger()),
              serviceDescription(service),
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              nonNullMoney(service.getPrice()),
              nonNullMoney(service.getPrice()),
              currency));
    }

    return lines;
  }

  private String serviceDescription(BookingServiceEntity service) {
    String quantity =
        service.getQuantity() == null || service.getQuantity() <= 1
            ? ""
            : " x" + service.getQuantity();
    if (service.getServiceOption() != null && hasText(service.getServiceOption().getName())) {
      return service.getService().getName()
          + " - "
          + service.getServiceOption().getName()
          + quantity;
    }
    return service.getService().getName() + quantity;
  }

  private FlightSeatEntity resolvedSeat(
      Map<String, PassengerSeatAssignmentEntity> seatAssignments, TicketEntity ticket) {
    PassengerSeatAssignmentEntity assignment =
        seatAssignments.get(
            passengerFlightKey(ticket.getPassenger().getId(), ticket.getFlight().getId()));
    return assignment == null ? ticket.getFlightSeat() : assignment.getFlightSeat();
  }

  private String paymentMethodLabel(String method) {
    String normalized = normalizeUpper(method);
    if (normalized.contains("PAYOS") || normalized.contains("VIETQR")) {
      return "VietQR / PayOS";
    }
    if (normalized.contains("STRIPE")) {
      return "Stripe";
    }
    if (normalized.contains("MOMO")) {
      return "Momo";
    }
    return hasText(method) ? method : "--";
  }

  private String formatMoney(BigDecimal amount, String currency) {
    NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    formatter.setMinimumFractionDigits(0);
    formatter.setMaximumFractionDigits(2);
    return formatter.format(nonNullMoney(amount)) + " " + firstNonBlank(currency, "VND");
  }

  private String formatDate(OffsetDateTime value) {
    return value == null ? "--" : DATE_FORMATTER.format(value);
  }

  private String formatDateTime(OffsetDateTime value) {
    return value == null ? "--" : DATE_TIME_FORMATTER.format(value);
  }

  private String formatTime(OffsetDateTime value) {
    return value == null ? "--" : value.toLocalTime().format(TIME_ONLY_FORMATTER);
  }

  private String airportLabel(String city, String terminal) {
    String base = firstNonBlank(city, "--");
    return hasText(terminal) ? base + " " + terminal : base;
  }

  private String formatFlightMoment(
      OffsetDateTime value, String timeZone, String city, String airportCode, String terminal) {
    if (value == null) {
      return "--";
    }
    OffsetDateTime localized = toZone(value, timeZone);
    String time = TIME_ONLY_FORMATTER.format(localized);
    String zoneLabel = timeZoneSuffix(value, timeZone);
    String airport = firstNonBlank(airportCode, city, "--");
    String location = firstNonBlank(city, airportCode, "--");
    String terminalLabel = hasText(terminal) ? "Terminal " + terminal : null;
    StringBuilder builder = new StringBuilder();
    builder.append(time);
    if (hasText(zoneLabel)) {
      builder.append(" ").append(zoneLabel);
    }
    builder.append(" - ").append(airport).append(", ").append(location);
    if (hasText(terminalLabel)) {
      builder.append(" (").append(terminalLabel).append(")");
    }
    return builder.toString();
  }

  private OffsetDateTime toZone(OffsetDateTime value, String timeZone) {
    if (!hasText(timeZone)) {
      return value;
    }
    try {
      return value.atZoneSameInstant(ZoneId.of(timeZone)).toOffsetDateTime();
    } catch (DateTimeException ignored) {
      return value;
    }
  }

  private String timeZoneSuffix(OffsetDateTime value, String timeZone) {
    if (!hasText(timeZone)) {
      return "";
    }
    try {
      return value
          .atZoneSameInstant(ZoneId.of(timeZone))
          .format(DateTimeFormatter.ofPattern("xxx"));
    } catch (DateTimeException ignored) {
      return "";
    }
  }

  private String passengerName(PassengerEntity passenger) {
    if (passenger == null) {
      return "--";
    }
    return (nullToBlank(passenger.getLastName()) + ", " + nullToBlank(passenger.getFirstName()))
        .replaceAll("^,\\s*|,\\s*$", "")
        .trim();
  }

  private String contactEmail(BookingContactEntity contact) {
    return contact == null ? null : contact.getEmail();
  }

  private String contactPhone(BookingContactEntity contact) {
    return contact == null ? null : contact.getPhone();
  }

  private String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeUpper(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private BigDecimal nonNullMoney(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String passengerFlightKey(Long passengerId, Long flightId) {
    return passengerId + ":" + flightId;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (hasText(value)) {
        return value.trim();
      }
    }
    return "";
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private String nullToBlank(String value) {
    return value == null ? "" : value;
  }

  private String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private record EmailView(
      BookingEntity booking,
      List<TicketEntity> tickets,
      PaymentEntity payment,
      List<BookingServiceEntity> services,
      Map<String, PassengerSeatAssignmentEntity> seatAssignments,
      BookingContactEntity primaryContact,
      PassengerEntity leadPassenger) {}

  private record FareLine(
      String leg,
      String passengerName,
      String description,
      BigDecimal baseAmount,
      BigDecimal tax,
      BigDecimal ancillaryAmount,
      BigDecimal total,
      String currency) {}
}
