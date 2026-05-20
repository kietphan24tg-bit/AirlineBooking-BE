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
import java.time.OffsetDateTime;
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
      DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));
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
      helper.setSubject("Xac nhan dat cho - Ma dat cho " + booking.getPnrCode());
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
        "Dat cho cua quy khach da duoc ghi nhan thanh cong. Vui long kiem tra cac thong tin chuyen bay ben duoi.";

    return """
        <div style="margin:0;padding:24px;background:#f4f4f4;font-family:Arial,sans-serif;color:#1f2937;">
          <div style="max-width:860px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;">
            <div style="background:linear-gradient(90deg,#cf141b 0%%,#ef222a 78%%,#ffcc29 100%%);padding:18px 24px;color:#ffffff;">
              <table role="presentation" style="width:100%%;border-collapse:collapse;">
                <tr>
                  <td style="font-size:34px;font-weight:800;letter-spacing:-0.5px;">AirlineBooking.com</td>
                  <td style="text-align:right;font-size:16px;font-weight:700;vertical-align:middle;">1900 1886</td>
                </tr>
              </table>
            </div>
            <div style="padding:20px 24px 8px;">
              <p style="margin:0;font-size:15px;line-height:1.6;color:#374151;">%s</p>
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
            ? "Thanh toan thanh cong luc " + formatDateTime(view.payment().getPaidAt())
            : "Thong tin thanh toan da duoc ghi nhan.";
    String contactName = passengerName(view.leadPassenger());
    String email =
        firstNonBlank(contactEmail(view.primaryContact()), view.booking().getContactEmail(), "--");
    String qrCell =
        hasText(view.booking().getPnrCode())
            ? "<div style=\"font-size:42px;font-weight:400;color:#cf141b;letter-spacing:1px;\">"
                + escapeHtml(view.booking().getPnrCode())
                + "</div>"
            : "<div style=\"font-size:18px;color:#6b7280;\">--</div>";
    String qrImage =
        view.payment() != null && hasText(view.payment().getQrImageUrl())
            ? "<img src=\""
                + escapeHtml(view.payment().getQrImageUrl())
                + "\" alt=\"QR code\" width=\"120\" height=\"120\" style=\"display:block;border:0;outline:none;text-decoration:none;\">"
            : "<div style=\"width:120px;height:120px;border:2px dashed #d1d5db;"
                + "color:#9ca3af;font-size:12px;display:flex;align-items:center;"
                + "justify-content:center;\">QR</div>";

    return """
        <div style="padding:12px 24px 0;">
          <div style="background:#ef222a;color:#ffffff;font-size:18px;font-weight:700;padding:8px 10px;">1. Thong tin dat cho</div>
          <table role="presentation" style="width:100%%;border-collapse:collapse;">
            <tr>
              <td style="width:58%%;vertical-align:top;">
                <table role="presentation" style="width:100%%;border-collapse:collapse;">
                  <tr>
                    <td style="width:31%%;background:#595959;color:#ffffff;padding:10px;font-weight:700;border:1px solid #ffffff;">
                      Trang thai dat cho
                    </td>
                    <td style="background:#ececec;padding:10px;border:1px solid #ffffff;">
                      <div style="font-weight:700;color:#111827;">Da xac nhan</div>
                      <div style="color:#cf141b;font-size:13px;margin-top:4px;">%s</div>
                    </td>
                  </tr>
                  <tr>
                    <td style="background:#595959;color:#ffffff;padding:10px;font-weight:700;border:1px solid #ffffff;">Ngay dat</td>
                    <td style="background:#ececec;padding:10px;border:1px solid #ffffff;">%s</td>
                  </tr>
                  <tr>
                    <td style="background:#595959;color:#ffffff;padding:10px;font-weight:700;border:1px solid #ffffff;">Ten</td>
                    <td style="background:#ececec;padding:10px;border:1px solid #ffffff;">%s</td>
                  </tr>
                  <tr>
                    <td style="background:#595959;color:#ffffff;padding:10px;font-weight:700;border:1px solid #ffffff;">Email</td>
                    <td style="background:#ececec;padding:10px;border:1px solid #ffffff;">%s</td>
                  </tr>
                </table>
              </td>
              <td style="width:42%%;vertical-align:top;">
                <table role="presentation" style="width:100%%;border-collapse:collapse;">
                  <tr>
                    <td
                      colspan="2"
                      style="background:#595959;color:#ffffff;padding:10px;font-size:18px;font-weight:700;border:1px solid #ffffff;">
                      Ma dat cho (so ve)
                    </td>
                  </tr>
                  <tr>
                    <td
                      style="width:52%%;background:#e6e6e6;padding:16px;border:1px solid #ffffff;vertical-align:middle;text-align:center;">
                      %s
                    </td>
                    <td style="background:#ffffff;padding:12px;border:1px solid #ffffff;vertical-align:middle;text-align:center;">%s</td>
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
            qrCell,
            qrImage);
  }

  private String buildPassengerSection(EmailView view) {
    StringBuilder rows = new StringBuilder();
    for (TicketEntity ticket : view.tickets()) {
      PassengerEntity passenger = ticket.getPassenger();
      FlightSeatEntity seat = resolvedSeat(view.seatAssignments(), ticket);
      rows.append(
          """
          <tr>
            <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;font-size:16px;font-weight:700;">%s</td>
            <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;font-size:16px;">%s</td>
          </tr>
          """
              .formatted(
                  escapeHtml(passengerName(passenger)),
                  escapeHtml(
                      ticket.getFlight().getFlightNumber()
                          + " "
                          + (seat == null ? "--" : nullToBlank(seat.getSeatNumber())))));
    }

    return """
        <div style="padding:20px 24px 0;">
          <div style="background:#ef222a;color:#ffffff;font-size:18px;font-weight:700;padding:8px 10px;">2. Thong tin hanh khach</div>
          <table role="presentation" style="width:100%%;border-collapse:collapse;">
            <tr>
              <td style="width:60%%;background:#595959;color:#ffffff;padding:8px 10px;font-weight:700;border:1px solid #ffffff;">
                Ten hanh khach
              </td>
              <td style="background:#595959;color:#ffffff;padding:8px 10px;font-weight:700;border:1px solid #ffffff;">So ghe</td>
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
      rows.append(
          """
          <tr>
            <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;">%s</td>
            <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;">%s</td>
            <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;">%s</td>
            <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;">%s</td>
            <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;">%s</td>
          </tr>
          """
              .formatted(
                  escapeHtml(flight.getFlightNumber()),
                  escapeHtml(formatDate(flight.getDepartureTime())),
                  escapeHtml(nullToBlank(ticket.getFareClass().getCode())),
                  escapeHtml(
                      formatTime(flight.getDepartureTime())
                          + " - "
                          + airportLabel(
                              flight.getDepartureAirport().getCity(),
                              flight.getDepartureTerminal())),
                  escapeHtml(
                      formatTime(flight.getArrivalTime())
                          + " - "
                          + airportLabel(
                              flight.getArrivalAirport().getCity(), flight.getArrivalTerminal()))));
    }

    return """
        <div style="padding:20px 24px 0;">
          <div style="background:#ef222a;color:#ffffff;font-size:18px;font-weight:700;padding:8px 10px;">3. Thong tin chuyen bay</div>
          <table role="presentation" style="width:100%%;border-collapse:collapse;">
            <tr>
              <td style="background:#595959;color:#ffffff;padding:8px 10px;font-weight:700;border:1px solid #ffffff;">Chuyen bay</td>
              <td style="background:#595959;color:#ffffff;padding:8px 10px;font-weight:700;border:1px solid #ffffff;">Ngay</td>
              <td style="background:#595959;color:#ffffff;padding:8px 10px;font-weight:700;border:1px solid #ffffff;">Loai ve</td>
              <td style="background:#595959;color:#ffffff;padding:8px 10px;font-weight:700;border:1px solid #ffffff;">Khoi hanh</td>
              <td style="background:#595959;color:#ffffff;padding:8px 10px;font-weight:700;border:1px solid #ffffff;">Den</td>
            </tr>
            %s
          </table>
        </div>
        """
        .formatted(rows);
  }

  private String buildFareSection(EmailView view) {
    List<FareLine> lines = fareLines(view);
    BigDecimal subtotal = BigDecimal.ZERO;
    BigDecimal tax = BigDecimal.ZERO;
    BigDecimal total = BigDecimal.ZERO;
    StringBuilder rows = new StringBuilder();

    for (FareLine line : lines) {
      subtotal = subtotal.add(nonNullMoney(line.amount()));
      tax = tax.add(nonNullMoney(line.tax()));
      total = total.add(nonNullMoney(line.total()));
      rows.append(
          """
          <tr>
            <td style="padding:8px;border:1px solid #ffffff;background:#ffffff;">%s</td>
            <td style="padding:8px;border:1px solid #ffffff;background:#ffffff;">%s</td>
            <td style="padding:8px;border:1px solid #ffffff;background:#ffffff;">%s</td>
            <td style="padding:8px;border:1px solid #ffffff;background:#ffffff;text-align:right;">%s</td>
            <td style="padding:8px;border:1px solid #ffffff;background:#ffffff;text-align:right;">%s</td>
            <td style="padding:8px;border:1px solid #ffffff;background:#ffffff;text-align:right;">%s</td>
          </tr>
          """
              .formatted(
                  escapeHtml(line.leg()),
                  escapeHtml(line.passengerName()),
                  escapeHtml(line.description()),
                  escapeHtml(formatMoney(line.amount(), line.currency())),
                  escapeHtml(formatMoney(line.tax(), line.currency())),
                  escapeHtml(formatMoney(line.total(), line.currency()))));
    }

    rows.append(
        """
        <tr>
          <td colspan="3" style="padding:10px;border:1px solid #ffffff;background:#ffffff;font-weight:700;text-align:right;">Tong cong</td>
          <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;font-weight:700;text-align:right;">%s</td>
          <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;font-weight:700;text-align:right;">%s</td>
          <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;font-weight:700;text-align:right;">%s</td>
        </tr>
        """
            .formatted(
                escapeHtml(formatMoney(subtotal, view.booking().getCurrency())),
                escapeHtml(formatMoney(tax, view.booking().getCurrency())),
                escapeHtml(formatMoney(total, view.booking().getCurrency()))));

    return """
        <div style="padding:20px 24px 0;">
          <div style="background:#ef222a;color:#ffffff;font-size:18px;font-weight:700;padding:8px 10px;">Gia ve va le phi</div>
          <table role="presentation" style="width:100%%;border-collapse:collapse;">
            <tr>
              <td style="background:#595959;color:#ffffff;padding:8px;font-weight:700;border:1px solid #ffffff;">Leg</td>
              <td style="background:#595959;color:#ffffff;padding:8px;font-weight:700;border:1px solid #ffffff;">Ten hanh khach</td>
              <td style="background:#595959;color:#ffffff;padding:8px;font-weight:700;border:1px solid #ffffff;">Mo ta</td>
              <td style="background:#595959;color:#ffffff;padding:8px;font-weight:700;border:1px solid #ffffff;text-align:right;">
                So tien
              </td>
              <td style="background:#595959;color:#ffffff;padding:8px;font-weight:700;border:1px solid #ffffff;text-align:right;">Thue</td>
              <td style="background:#595959;color:#ffffff;padding:8px;font-weight:700;border:1px solid #ffffff;text-align:right;">Cong</td>
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
        <div style="padding:20px 24px 24px;">
          <div style="background:#ef222a;color:#ffffff;font-size:18px;font-weight:700;padding:8px 10px;">Tinh trang thanh toan</div>
          <table role="presentation" style="width:100%%;border-collapse:collapse;">
            <tr>
              <td style="background:#595959;color:#ffffff;padding:8px 10px;font-weight:700;border:1px solid #ffffff;">
                Ngay thanh toan
              </td>
              <td style="background:#595959;color:#ffffff;padding:8px 10px;font-weight:700;border:1px solid #ffffff;">
                Hinh thuc thanh toan
              </td>
              <td style="background:#595959;color:#ffffff;padding:8px 10px;font-weight:700;border:1px solid #ffffff;text-align:right;">
                So tien
              </td>
            </tr>
            <tr>
              <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;">%s</td>
              <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;">%s</td>
              <td style="padding:10px;border:1px solid #ffffff;background:#ffffff;text-align:right;font-weight:700;">%s</td>
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
              ticket.getFareClass().getCode()
                  + " - "
                  + nullToBlank(ticket.getFareClass().getName()),
              nonNullMoney(ticket.getFlightFare().getBasePrice()),
              nonNullMoney(ticket.getFlightFare().getTotalTaxAmount()),
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
                "Ghe " + nullToBlank(seatAssignment.getFlightSeat().getSeatNumber()),
                seatAssignment.getSurchargeAmount(),
                BigDecimal.ZERO,
                seatAssignment.getSurchargeAmount(),
                firstNonBlank(seatAssignment.getCurrency(), currency)));
      }
    }

    for (BookingServiceEntity service : view.services()) {
      lines.add(
          new FareLine(
              "-",
              passengerName(service.getPassenger()),
              serviceDescription(service),
              nonNullMoney(service.getPrice()),
              BigDecimal.ZERO,
              nonNullMoney(service.getPrice()),
              currency));
    }

    return lines;
  }

  private String serviceDescription(BookingServiceEntity service) {
    if (service.getServiceOption() != null && hasText(service.getServiceOption().getName())) {
      return service.getService().getName() + " - " + service.getServiceOption().getName();
    }
    return service.getService().getName();
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
    return value == null ? "--" : value.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
  }

  private String airportLabel(String city, String terminal) {
    String base = firstNonBlank(city, "--");
    return hasText(terminal) ? base + " " + terminal : base;
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
      BigDecimal amount,
      BigDecimal tax,
      BigDecimal total,
      String currency) {}
}
