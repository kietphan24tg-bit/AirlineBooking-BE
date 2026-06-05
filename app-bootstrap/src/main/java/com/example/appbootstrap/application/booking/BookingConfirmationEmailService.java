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
        "Cam on "
            + firstNonBlank(passengerName(view.leadPassenger()), "quy khach")
            + ". Chung toi da gui xac nhan dat cho den "
            + firstNonBlank(
                contactEmail(view.primaryContact()), view.booking().getContactEmail(), "email dang ky")
            + ".";

    return """
        <div style="margin:0;padding:24px 12px;background:#1d1412;font-family:Arial,sans-serif;color:#f6ead6;">
          <div
            style="max-width:640px;margin:0 auto;background:linear-gradient(180deg,#5b0d25 0%%,#45101c 100%%);
              border:1px solid #8f6a33;border-radius:24px;overflow:hidden;
              box-shadow:0 24px 54px rgba(0,0,0,0.35);">
            <div style="padding:18px 22px 14px;color:#ffffff;text-align:center;">
              <div style="font-family:Georgia,'Times New Roman',serif;font-size:26px;font-weight:500;color:#e6c472;">
                The Sovereign
              </div>
              <div style="margin-top:6px;font-size:11px;letter-spacing:0.22em;text-transform:uppercase;color:#d9b467;">
                Booking Confirmation
              </div>
              <div style="margin:12px auto 0;width:140px;height:1px;background:linear-gradient(90deg,rgba(230,196,114,0),#e6c472,rgba(230,196,114,0));"></div>
              <div style="margin-top:7px;font-size:14px;color:#c9a25b;">&#9671;</div>
            </div>
            <div style="padding:0 14px 14px;">
              <table
                role="presentation"
                style="width:100%%;border-collapse:separate;border-spacing:0;background:#6a132c;
                  border:1px solid rgba(230,196,114,0.22);border-radius:16px;overflow:hidden;">
                <tr>
                  <td style="width:78px;padding:18px 0 18px 18px;vertical-align:top;">
                    <div style="width:48px;height:48px;border-radius:999px;border:1px solid #d7b164;color:#d7b164;
                      text-align:center;line-height:48px;font-size:28px;font-weight:700;">&#10003;</div>
                  </td>
                  <td style="padding:18px 14px 18px 0;vertical-align:top;">
                    <table role="presentation" style="width:100%%;border-collapse:collapse;">
                      <tr>
                        <td style="vertical-align:top;">
                          <div style="font-family:Georgia,'Times New Roman',serif;font-size:30px;line-height:1.1;color:#f1d58c;">
                            Dat cho thanh cong!
                          </div>
                          <div style="margin-top:8px;font-size:13px;line-height:1.7;color:#f3e5cb;">%s</div>
                        </td>
                        <td style="width:180px;vertical-align:top;text-align:right;padding-left:10px;">
                          <div style="font-size:11px;letter-spacing:0.14em;text-transform:uppercase;color:#e7c87e;">
                            Ma dat cho (PNR)
                          </div>
                          <div style="margin-top:6px;font-family:Georgia,'Times New Roman',serif;font-size:28px;line-height:1.1;color:#f1d58c;">
                            %s
                          </div>
                          <div style="margin-top:10px;font-size:11px;letter-spacing:0.08em;text-transform:uppercase;color:#d6b676;">
                            Ngay dat cho
                          </div>
                          <div style="margin-top:4px;font-size:13px;color:#fff7e6;">%s</div>
                          <div style="margin-top:10px;font-size:11px;letter-spacing:0.08em;text-transform:uppercase;color:#d6b676;">
                            Thanh toan xac nhan luc
                          </div>
                          <div style="margin-top:4px;font-size:13px;color:#fff7e6;">%s</div>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </div>
            %s
            %s
            %s
            %s
            %s
            %s
            <div style="padding:0 18px 18px;text-align:center;font-size:10px;line-height:1.6;color:#c9a980;">
              &copy; 2026 The Sovereign Airlines. All rights reserved.<br>
              Day la email tu dong, vui long khong tra loi.
            </div>
          </div>
        </div>
        """
        .formatted(
            escapeHtml(intro),
            escapeHtml(firstNonBlank(view.booking().getPnrCode(), "--")),
            escapeHtml(formatDate(view.booking().getCreatedAt())),
            escapeHtml(
                view.payment() != null && view.payment().getPaidAt() != null
                    ? formatDateTime(view.payment().getPaidAt())
                    : "--"),
            buildBookingSection(view),
            buildFlightSection(view),
            buildPassengerSection(view),
            buildFareSection(view),
            buildPaymentSection(view),
            buildFooterSection());
  }

  private String buildBookingSection(EmailView view) {
    String contactName = passengerName(view.leadPassenger());
    String email =
        firstNonBlank(contactEmail(view.primaryContact()), view.booking().getContactEmail(), "--");
    String phone =
        firstNonBlank(contactPhone(view.primaryContact()), view.booking().getContactPhone(), "--");
    String emailMarkup =
        hasText(email)
            ? "<a href=\"mailto:"
                + escapeHtml(email)
                + "\" style=\"color:#7a4f17;text-decoration:none;font-weight:700;\">"
                + escapeHtml(email)
                + "</a>"
            : "--";

    return """
        <div style="padding:0 14px 10px;">
          <table role="presentation" style="width:100%%;border-collapse:separate;border-spacing:0;background:#fbf3e6;border:1px solid #d4b06a;border-radius:12px;overflow:hidden;">
            <tr>
              <td colspan="4" style="padding:12px 14px;border-bottom:1px solid #e7d2ad;font-size:13px;font-weight:800;letter-spacing:0.08em;text-transform:uppercase;color:#6a132c;">
                THONG TIN LIEN HE
              </td>
            </tr>
            <tr>
              <td style="width:23%%;padding:12px 14px;background:#f7ead3;color:#7a4f17;font-size:11px;text-transform:uppercase;font-weight:700;">Hanh khach</td>
              <td style="padding:12px 14px;background:#fbf3e6;color:#241616;font-size:14px;font-weight:700;">%s</td>
              <td style="width:23%%;padding:12px 14px;background:#f7ead3;color:#7a4f17;font-size:11px;text-transform:uppercase;font-weight:700;">So dien thoai</td>
              <td style="padding:12px 14px;background:#fbf3e6;color:#241616;font-size:14px;">%s</td>
            </tr>
            <tr>
              <td style="padding:12px 14px;background:#f7ead3;color:#7a4f17;font-size:11px;text-transform:uppercase;font-weight:700;border-top:1px solid #e7d2ad;">Email</td>
              <td style="padding:12px 14px;background:#fbf3e6;color:#241616;font-size:14px;border-top:1px solid #e7d2ad;">%s</td>
              <td style="padding:12px 14px;background:#f7ead3;color:#7a4f17;font-size:11px;text-transform:uppercase;font-weight:700;border-top:1px solid #e7d2ad;">Ngay dat cho</td>
              <td style="padding:12px 14px;background:#fbf3e6;color:#241616;font-size:14px;border-top:1px solid #e7d2ad;">%s</td>
            </tr>
          </table>
        </div>
        """
        .formatted(
            escapeHtml(contactName),
            escapeHtml(phone),
            emailMarkup,
            escapeHtml(formatDate(view.booking().getCreatedAt())));
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
            <td style="padding:14px 16px;border-bottom:1px solid #efe0c5;background:#fbf3e6;font-size:15px;font-weight:700;color:#241616;">%s</td>
            <td style="padding:14px 16px;border-bottom:1px solid #efe0c5;background:#fbf3e6;font-size:14px;color:#241616;">%s</td>
            <td style="padding:14px 16px;border-bottom:1px solid #efe0c5;background:#fbf3e6;font-size:14px;color:#241616;">%s</td>
          </tr>
          """
              .formatted(
                  escapeHtml(passengerName(passenger)),
                  escapeHtml(ticket.getFlight().getFlightNumber()),
                  escapeHtml(seatLabel)));
    }

    return """
        <div style="padding:0 14px 10px;">
          <table
            role="presentation"
            style="width:100%%;border-collapse:separate;border-spacing:0;
              background:#fbf3e6;border:1px solid #d4b06a;border-radius:12px;overflow:hidden;">
            <tr>
              <td colspan="3" style="padding:12px 14px;border-bottom:1px solid #e7d2ad;font-size:13px;font-weight:800;letter-spacing:0.08em;text-transform:uppercase;color:#6a132c;">
                THONG TIN HANH KHACH
              </td>
            </tr>
            <tr>
              <td style="width:48%%;background:#f7ead3;color:#7a4f17;padding:12px 16px;font-weight:800;border-bottom:1px solid #e7d2ad;">
                Passenger
              </td>
              <td style="width:24%%;background:#f7ead3;color:#7a4f17;padding:12px 16px;font-weight:800;border-bottom:1px solid #e7d2ad;">
                Flight
              </td>
              <td style="background:#f7ead3;color:#7a4f17;padding:12px 16px;font-weight:800;border-bottom:1px solid #e7d2ad;">Seat</td>
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
            <td style="padding:14px 16px;border-bottom:1px solid #efe0c5;background:#fbf3e6;">
              <div style="font-weight:800;color:#241616;">%s</div>
              <div style="margin-top:4px;font-size:12px;color:#6e5843;">%s</div>
            </td>
            <td style="padding:14px 16px;border-bottom:1px solid #efe0c5;background:#fbf3e6;color:#241616;">%s</td>
            <td style="padding:14px 16px;border-bottom:1px solid #efe0c5;background:#fbf3e6;color:#241616;">%s</td>
            <td style="padding:14px 16px;border-bottom:1px solid #efe0c5;background:#fbf3e6;color:#241616;">%s</td>
            <td style="padding:14px 16px;border-bottom:1px solid #efe0c5;background:#fbf3e6;color:#241616;">%s</td>
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
        <div style="padding:0 14px 10px;">
          <table
            role="presentation"
            style="width:100%%;border-collapse:separate;border-spacing:0;
              background:#fbf3e6;border:1px solid #d4b06a;border-radius:12px;overflow:hidden;">
            <tr>
              <td colspan="5" style="padding:12px 14px;border-bottom:1px solid #e7d2ad;font-size:13px;font-weight:800;letter-spacing:0.08em;text-transform:uppercase;color:#6a132c;">
                THONG TIN CHUYEN BAY
              </td>
            </tr>
            <tr>
              <td style="background:#f7ead3;color:#7a4f17;padding:12px 16px;font-weight:800;border-bottom:1px solid #e7d2ad;">Flight</td>
              <td style="background:#f7ead3;color:#7a4f17;padding:12px 16px;font-weight:800;border-bottom:1px solid #e7d2ad;">Date</td>
              <td style="background:#f7ead3;color:#7a4f17;padding:12px 16px;font-weight:800;border-bottom:1px solid #e7d2ad;">Fare</td>
              <td style="background:#f7ead3;color:#7a4f17;padding:12px 16px;font-weight:800;border-bottom:1px solid #e7d2ad;">
                Departure
              </td>
              <td style="background:#f7ead3;color:#7a4f17;padding:12px 16px;font-weight:800;border-bottom:1px solid #e7d2ad;">
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
            <td style="padding:12px 14px;border-bottom:1px solid #efe0c5;background:#fbf3e6;color:#241616;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #efe0c5;background:#fbf3e6;color:#241616;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #efe0c5;background:#fbf3e6;color:#241616;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #efe0c5;background:#fbf3e6;text-align:right;color:#241616;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #efe0c5;background:#fbf3e6;text-align:right;color:#241616;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #efe0c5;background:#fbf3e6;text-align:right;color:#241616;">%s</td>
            <td style="padding:12px 14px;border-bottom:1px solid #efe0c5;background:#fbf3e6;text-align:right;font-weight:700;color:#241616;">%s</td>
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
            style="padding:14px;border-bottom:0;background:#f7ead3;font-weight:800;text-align:right;color:#6a132c;">
            Total paid
          </td>
          <td style="padding:14px;border-bottom:0;background:#f7ead3;font-weight:800;text-align:right;color:#6a132c;">%s</td>
          <td style="padding:14px;border-bottom:0;background:#f7ead3;font-weight:800;text-align:right;color:#6a132c;">%s</td>
          <td style="padding:14px;border-bottom:0;background:#f7ead3;font-weight:800;text-align:right;color:#6a132c;">%s</td>
          <td style="padding:14px;border-bottom:0;background:#f7ead3;font-weight:800;text-align:right;color:#6a132c;">%s</td>
        </tr>
        """
            .formatted(
                escapeHtml(formatMoney(fareSubtotal, view.booking().getCurrency())),
                escapeHtml(formatMoney(tax, view.booking().getCurrency())),
                escapeHtml(formatMoney(ancillary, view.booking().getCurrency())),
                escapeHtml(formatMoney(total, view.booking().getCurrency()))));

    return """
        <div style="padding:0 14px 10px;">
          <table
            role="presentation"
            style="width:100%%;border-collapse:separate;border-spacing:0;
              background:#fbf3e6;border:1px solid #d4b06a;border-radius:12px;overflow:hidden;">
            <tr>
              <td colspan="7" style="padding:12px 14px;border-bottom:1px solid #e7d2ad;font-size:13px;font-weight:800;letter-spacing:0.08em;text-transform:uppercase;color:#6a132c;">
                CHI TIET GIA VE
              </td>
            </tr>
            <tr>
              <td style="background:#f7ead3;color:#7a4f17;padding:12px 14px;font-weight:800;border-bottom:1px solid #e7d2ad;">Leg</td>
              <td style="background:#f7ead3;color:#7a4f17;padding:12px 14px;font-weight:800;border-bottom:1px solid #e7d2ad;">Passenger</td>
              <td
                style="background:#f7ead3;color:#7a4f17;padding:12px 14px;font-weight:800;
                  border-bottom:1px solid #e7d2ad;">Description</td>
              <td
                style="background:#f7ead3;color:#7a4f17;padding:12px 14px;font-weight:800;
                  border-bottom:1px solid #e7d2ad;text-align:right;">
                Base fare
              </td>
              <td
                style="background:#f7ead3;color:#7a4f17;padding:12px 14px;font-weight:800;
                  border-bottom:1px solid #e7d2ad;text-align:right;">Taxes</td>
              <td
                style="background:#f7ead3;color:#7a4f17;padding:12px 14px;font-weight:800;
                  border-bottom:1px solid #e7d2ad;text-align:right;">
                Seat / services
              </td>
              <td
                style="background:#f7ead3;color:#7a4f17;padding:12px 14px;font-weight:800;
                  border-bottom:1px solid #e7d2ad;text-align:right;">Total</td>
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
        <div style="padding:0 14px 10px;">
          <table
            role="presentation"
            style="width:100%%;border-collapse:separate;border-spacing:0;
              background:#fbf3e6;border:1px solid #d4b06a;border-radius:12px;overflow:hidden;">
            <tr>
              <td colspan="3" style="padding:12px 14px;border-bottom:1px solid #e7d2ad;font-size:13px;font-weight:800;letter-spacing:0.08em;text-transform:uppercase;color:#6a132c;">
                THONG TIN THANH TOAN
              </td>
            </tr>
            <tr>
              <td style="background:#f7ead3;color:#7a4f17;padding:12px 16px;font-weight:800;border-bottom:1px solid #e7d2ad;">
                Paid at
              </td>
              <td style="background:#f7ead3;color:#7a4f17;padding:12px 16px;font-weight:800;border-bottom:1px solid #e7d2ad;">
                Method
              </td>
              <td
                style="background:#f7ead3;color:#7a4f17;padding:12px 16px;font-weight:800;
                  border-bottom:1px solid #e7d2ad;text-align:right;">
                Amount
              </td>
            </tr>
            <tr>
              <td style="padding:14px 16px;background:#fbf3e6;color:#241616;">%s</td>
              <td style="padding:14px 16px;background:#fbf3e6;color:#241616;">%s</td>
              <td style="padding:14px 16px;background:#fbf3e6;text-align:right;font-weight:800;color:#6a132c;">%s</td>
            </tr>
          </table>
        </div>
        """
        .formatted(escapeHtml(paymentDate), escapeHtml(paymentMethod), escapeHtml(amount));
  }

  private String buildFooterSection() {
    return """
        <div style="padding:0 14px 16px;">
          <table role="presentation" style="width:100%%;border-collapse:separate;border-spacing:0;background:#5e1027;border:1px solid rgba(230,196,114,0.24);border-radius:12px;overflow:hidden;">
            <tr>
              <td style="padding:14px 16px;color:#f3e5cb;">
                <div style="font-size:12px;font-weight:800;color:#f1d58c;">Can ho tro?</div>
                <div style="margin-top:6px;font-size:12px;line-height:1.6;">
                  Lien he tong dai 1900 1886<br>
                  hoac truy cap thesovereign.com
                </div>
              </td>
              <td style="width:180px;padding:14px 16px;text-align:right;">
                <a href="https://thesovereign.com" style="display:inline-block;background:#7a1532;border:1px solid #d9b467;border-radius:999px;padding:10px 16px;color:#f8ebcf;text-decoration:none;font-size:12px;font-weight:800;">
                  Quan ly dat cho
                </a>
              </td>
            </tr>
          </table>
        </div>
        """;
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
