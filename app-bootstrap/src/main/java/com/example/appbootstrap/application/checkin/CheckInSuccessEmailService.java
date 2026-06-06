package com.example.appbootstrap.application.checkin;

import com.example.appbootstrap.config.MailProperties;
import com.example.modulebooking.entity.BoardingPassEntity;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BoardingPassRepository;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.modulecheckin.application.CheckInConfirmView;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import jakarta.mail.MessagingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CheckInSuccessEmailService {

  private static final Logger log = LoggerFactory.getLogger(CheckInSuccessEmailService.class);
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US);
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm", Locale.US);

  private final JavaMailSender mailSender;
  private final MailProperties mailProperties;
  private final BookingContactRepository bookingContactRepository;
  private final TicketRepository ticketRepository;
  private final BoardingPassRepository boardingPassRepository;
  private final String publicBaseUrl;

  public CheckInSuccessEmailService(
      JavaMailSender mailSender,
      MailProperties mailProperties,
      BookingContactRepository bookingContactRepository,
      TicketRepository ticketRepository,
      BoardingPassRepository boardingPassRepository,
      @Value("${APP_PUBLIC_BASE_URL:http://localhost:5173}") String publicBaseUrl) {
    this.mailSender = mailSender;
    this.mailProperties = mailProperties;
    this.bookingContactRepository = bookingContactRepository;
    this.ticketRepository = ticketRepository;
    this.boardingPassRepository = boardingPassRepository;
    this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
  }

  @Transactional(readOnly = true)
  public void sendSuccess(CheckInConfirmView view) {
    if (view == null || view.boardingPasses().isEmpty()) {
      return;
    }

    Long bookingId = view.booking().bookingId();
    List<Long> ticketIds =
        view.boardingPasses().stream()
            .map(CheckInConfirmView.BoardingPassView::ticketId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (ticketIds.isEmpty()) {
      return;
    }

    List<TicketEntity> tickets =
        ticketRepository.findByBookingIdAndTicketIdInWithDetails(bookingId, ticketIds);
    if (tickets.isEmpty()) {
      log.warn("Check-in success email skipped: no tickets found bookingId={}", bookingId);
      return;
    }

    BookingEntity booking = tickets.getFirst().getBooking();
    List<String> recipients = recipientsFor(booking);
    if (recipients.isEmpty()) {
      log.warn("Check-in success email skipped: no recipients found bookingId={}", bookingId);
      return;
    }

    Map<Long, CheckInConfirmView.BoardingPassView> boardingPassViewByTicketId =
        view.boardingPasses().stream()
            .collect(
                Collectors.toMap(
                    CheckInConfirmView.BoardingPassView::ticketId,
                    item -> item,
                    (left, right) -> right,
                    LinkedHashMap::new));

    Map<Long, BoardingPassEntity> boardingPassEntityByTicketId =
        boardingPassRepository.findByTicketIdIn(ticketIds).stream()
            .collect(
                Collectors.toMap(
                    item -> item.getTicket().getId(),
                    item -> item,
                    (left, right) -> right,
                    LinkedHashMap::new));

    List<PassengerBoardingPassCard> cards = new ArrayList<>();
    for (TicketEntity ticket : tickets) {
      CheckInConfirmView.BoardingPassView boardingPassView =
          boardingPassViewByTicketId.get(ticket.getId());
      if (boardingPassView == null) {
        continue;
      }
      BoardingPassEntity entity = boardingPassEntityByTicketId.get(ticket.getId());
      cards.add(
          new PassengerBoardingPassCard(
              ticket,
              boardingPassView,
              entity != null ? entity.getQrCode() : boardingPassView.qrCode()));
    }
    if (cards.isEmpty()) {
      log.warn(
          "Check-in success email skipped: no boarding pass cards built bookingId={}", bookingId);
      return;
    }

    cards.sort(
        Comparator.comparing(
                (PassengerBoardingPassCard card) -> card.ticket().getFlight().getDepartureTime(),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(card -> card.ticket().getId()));

    try {
      var message = mailSender.createMimeMessage();
      var helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(mailProperties.from());
      helper.setTo(recipients.toArray(String[]::new));
      helper.setSubject("The Sovereign Check-in Successful - PNR " + booking.getPnrCode());
      helper.setText(buildHtml(booking, cards), true);
      mailSender.send(message);
    } catch (MailException | MessagingException exception) {
      log.warn(
          "Check-in success email failed bookingId={} recipients={}",
          bookingId,
          recipients,
          exception);
    }
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

  private String buildHtml(BookingEntity booking, List<PassengerBoardingPassCard> cards) {
    PassengerBoardingPassCard leadCard = cards.getFirst();
    PassengerEntity leadPassenger = leadCard.ticket().getPassenger();

    return """
        <div style="margin:0;padding:24px 12px;background:#f7f2ea;font-family:Arial,sans-serif;color:#22191b;">
          <div
            style="max-width:820px;margin:0 auto;background:#fffdfa;border:1px solid #ead9b8;
              border-radius:28px;overflow:hidden;box-shadow:0 26px 60px rgba(75,15,36,0.10);">
            %s
            %s
            %s
            %s
            %s
            %s
          </div>
        </div>
        """
        .formatted(
            buildHeader(booking),
            buildHero(leadPassenger),
            buildFlightSection(cards),
            buildBoardingPassSection(cards),
            buildNotesSection(leadCard.ticket().getFlight()),
            buildFooter(leadCard.ticket()));
  }

  private String buildHeader(BookingEntity booking) {
    return """
        <div style="padding:28px 32px 20px;background:linear-gradient(180deg,#fffdfa 0%%,#f9f2e7 100%%);border-bottom:1px solid #efe2c8;">
          <table role="presentation" style="width:100%%;border-collapse:collapse;">
            <tr>
              <td style="vertical-align:top;">
                <div style="font-size:15px;font-weight:900;letter-spacing:0.02em;color:#8d0f3a;">THE SOVEREIGN</div>
                <div style="margin-top:8px;font-size:11px;letter-spacing:0.28em;text-transform:uppercase;color:#d1a62d;">
                  Check-in confirmation
                </div>
              </td>
              <td style="width:190px;vertical-align:top;text-align:right;">
                <div style="font-size:11px;letter-spacing:0.12em;text-transform:uppercase;color:#8f7d68;">
                  Booking reference (PNR)
                </div>
                <div style="margin-top:6px;font-size:28px;font-weight:900;color:#8d0f3a;">
                  %s
                </div>
              </td>
            </tr>
          </table>
        </div>
        """
        .formatted(escapeHtml(firstNonBlank(booking.getPnrCode(), "--")));
  }

  private String buildHero(PassengerEntity passenger) {
    return """
        <div style="padding:22px 32px 10px;">
          <table
            role="presentation"
            style="width:100%%;border-collapse:separate;border-spacing:0;
              background:linear-gradient(135deg,#fffdf9 0%%,#f7efe3 100%%);
              border:1px solid #efe2c8;border-radius:24px;">
            <tr>
              <td style="padding:28px 20px 28px 28px;vertical-align:top;">
                <div
                  style="width:68px;height:68px;border-radius:999px;background:#d8fae9;
                    color:#047857;font-size:40px;line-height:68px;text-align:center;
                    font-weight:700;">
                  &#10003;
                </div>
              </td>
              <td style="padding:28px 10px 28px 0;vertical-align:top;">
                <div style="font-size:17px;color:#7f6a56;">Passenger</div>
                <div style="margin-top:6px;font-size:21px;font-weight:900;color:#101828;">%s</div>
                <div style="margin-top:18px;font-size:42px;font-weight:900;color:#172033;">
                  Check-in successful!
                </div>
                <div style="margin-top:14px;font-size:15px;line-height:1.8;color:#614f43;max-width:420px;">
                  Your online check-in is complete. Your boarding pass details are below and can be reviewed any time in Manage Booking.
                </div>
              </td>
              <td style="width:220px;padding:20px 24px 20px 10px;vertical-align:middle;">
                <div
                  style="height:180px;border-radius:24px;
                    background:linear-gradient(180deg,#f9f4ec 0%%,#e9f0f9 100%%);
                    border:1px solid #ebdcc0;position:relative;overflow:hidden;">
                  <div
                    style="position:absolute;left:22px;bottom:18px;width:56px;height:96px;
                      background:#8d0f3a;border-radius:16px 16px 10px 10px;
                      box-shadow:10px 16px 24px rgba(0,0,0,0.14);"></div>
                  <div
                    style="position:absolute;left:38px;bottom:110px;width:22px;height:8px;
                      border:4px solid #8d0f3a;border-bottom:none;border-radius:10px 10px 0 0;"></div>
                  <div
                    style="position:absolute;right:18px;bottom:28px;width:84px;height:24px;
                      background:#8d0f3a;border-radius:14px 18px 10px 10px;"></div>
                  <div style="position:absolute;right:44px;bottom:38px;width:22px;height:8px;background:#d4a72c;border-radius:12px;"></div>
                  <div style="position:absolute;right:32px;top:42px;font-size:40px;color:#d4a72c;">&#9992;</div>
                  <div
                    style="position:absolute;left:110px;top:58px;right:28px;height:1px;
                      background:linear-gradient(90deg,rgba(212,167,44,0) 0%%,#d4a72c 52%%,
                        rgba(212,167,44,0) 100%%);"></div>
                </div>
              </td>
            </tr>
          </table>
        </div>
        """
        .formatted(escapeHtml(passengerName(passenger)));
  }

  private String buildFlightSection(List<PassengerBoardingPassCard> cards) {
    LinkedHashMap<Long, FlightEntity> uniqueFlights = new LinkedHashMap<>();
    for (PassengerBoardingPassCard card : cards) {
      uniqueFlights.putIfAbsent(card.ticket().getFlight().getId(), card.ticket().getFlight());
    }

    StringBuilder content = new StringBuilder();
    for (FlightEntity flight : uniqueFlights.values()) {
      content.append(
          """
          <div style="padding:0 32px 16px;">
            <table
              role="presentation"
              style="width:100%%;border-collapse:separate;border-spacing:0;background:#fffdfa;
                border:1px solid #efe2c8;border-radius:22px;overflow:hidden;">
              <tr>
                <td
                  style="padding:18px 22px;border-bottom:1px solid #f2e6d2;font-size:13px;
                    font-weight:800;letter-spacing:0.10em;text-transform:uppercase;
                    color:#8d0f3a;">
                  Flight information
                </td>
              </tr>
              <tr>
                <td style="padding:22px;">
                  <table role="presentation" style="width:100%%;border-collapse:collapse;">
                    <tr>
                      <td style="width:34%%;vertical-align:top;text-align:left;">
                        <div style="font-size:44px;font-weight:900;color:#172033;">%s</div>
                        <div style="margin-top:10px;font-size:14px;letter-spacing:0.08em;text-transform:uppercase;color:#7f6a56;">%s</div>
                        <div style="margin-top:14px;font-size:30px;font-weight:900;color:#8d0f3a;">%s</div>
                        <div style="margin-top:8px;font-size:15px;color:#4f3f39;">%s</div>
                      </td>
                      <td style="width:32%%;vertical-align:middle;text-align:center;padding:0 12px;">
                        <div style="font-size:15px;font-weight:700;color:#8f7d68;">%s</div>
                        <div style="margin-top:14px;font-size:46px;color:#d4a72c;line-height:1;">&#9992;</div>
                        <div
                          style="margin-top:6px;height:1px;
                            background:linear-gradient(90deg,rgba(212,167,44,0) 0%%,#d4a72c 50%%,
                              rgba(212,167,44,0) 100%%);"></div>
                        <div style="margin-top:10px;font-size:13px;color:#7f6a56;">Direct flight</div>
                      </td>
                      <td style="width:34%%;vertical-align:top;text-align:right;">
                        <div style="font-size:44px;font-weight:900;color:#172033;">%s</div>
                        <div style="margin-top:10px;font-size:14px;letter-spacing:0.08em;text-transform:uppercase;color:#7f6a56;">%s</div>
                        <div style="margin-top:14px;font-size:30px;font-weight:900;color:#8d0f3a;">%s</div>
                        <div style="margin-top:8px;font-size:15px;color:#4f3f39;">%s</div>
                      </td>
                    </tr>
                  </table>
                  <table role="presentation" style="width:100%%;border-collapse:collapse;margin-top:22px;border-top:1px solid #f2e6d2;">
                    <tr>%s</tr>
                  </table>
                </td>
              </tr>
            </table>
          </div>
          """
              .formatted(
                  escapeHtml(airportCode(flight.getDepartureAirport())),
                  escapeHtml(cityName(flight.getDepartureAirport())),
                  escapeHtml(formatTime(flight.getDepartureTime(), flight.getDepartureAirport())),
                  escapeHtml(formatDate(flight.getDepartureTime(), flight.getDepartureAirport())),
                  escapeHtml(firstNonBlank(flight.getFlightNumber(), "--")),
                  escapeHtml(airportCode(flight.getArrivalAirport())),
                  escapeHtml(cityName(flight.getArrivalAirport())),
                  escapeHtml(formatTime(flight.getArrivalTime(), flight.getArrivalAirport())),
                  escapeHtml(formatDate(flight.getArrivalTime(), flight.getArrivalAirport())),
                  buildFlightMetaCells(flight)));
    }
    return content.toString();
  }

  private String buildFlightMetaCells(FlightEntity flight) {
    List<MetaCell> cells =
        List.of(
            new MetaCell("Flight", firstNonBlank(flight.getFlightNumber(), "--")),
            new MetaCell(
                "Date", formatDate(flight.getDepartureTime(), flight.getDepartureAirport())),
            new MetaCell(
                "Departure", formatTime(flight.getDepartureTime(), flight.getDepartureAirport())),
            new MetaCell("Terminal", formatTerminal(flight.getDepartureTerminal())),
            new MetaCell(
                "Arrival", formatTime(flight.getArrivalTime(), flight.getArrivalAirport())));

    StringBuilder markup = new StringBuilder();
    for (int index = 0; index < cells.size(); index++) {
      markup.append(renderFlightMetaCell(cells.get(index), index == cells.size() - 1));
    }
    return markup.toString();
  }

  private String renderFlightMetaCell(MetaCell cell, boolean lastCell) {
    String border = lastCell ? "" : "border-right:1px solid #f2e6d2;";
    return """
        <td style="width:20%%;padding:18px 8px 4px;text-align:center;%s">
          <div style="font-size:12px;letter-spacing:0.08em;text-transform:uppercase;color:#8f7d68;">%s</div>
          <div style="margin-top:10px;font-size:21px;font-weight:800;color:#172033;">%s</div>
        </td>
        """
        .formatted(border, escapeHtml(cell.label()), escapeHtml(cell.value()));
  }

  private String buildBoardingPassSection(List<PassengerBoardingPassCard> cards) {
    StringBuilder content = new StringBuilder();
    for (PassengerBoardingPassCard card : cards) {
      content.append(buildPassengerCard(card));
    }
    return content.toString();
  }

  private String buildPassengerCard(PassengerBoardingPassCard card) {
    TicketEntity ticket = card.ticket();
    FlightEntity flight = ticket.getFlight();
    CheckInConfirmView.BoardingPassView boardingPass = card.boardingPass();

    return """
        <div style="padding:0 32px 16px;">
          <table
            role="presentation"
            style="width:100%%;border-collapse:separate;border-spacing:0;background:#fffdfa;
              border:1px solid #efe2c8;border-radius:22px;overflow:hidden;">
            <tr>
              <td
                style="padding:18px 22px;border-bottom:1px solid #f2e6d2;font-size:13px;
                  font-weight:800;letter-spacing:0.10em;text-transform:uppercase;
                  color:#8d0f3a;">
                Passenger & boarding pass
              </td>
            </tr>
            <tr>
              <td style="padding:20px 22px;">
                <table role="presentation" style="width:100%%;border-collapse:collapse;">
                  <tr>
                    <td style="vertical-align:top;padding-right:18px;">
                      <div style="font-size:14px;letter-spacing:0.08em;text-transform:uppercase;color:#8f7d68;">Passenger</div>
                      <div style="margin-top:8px;font-size:38px;font-weight:900;color:#101828;">%s</div>
                      <table role="presentation" style="width:100%%;border-collapse:collapse;margin-top:18px;">
                        <tr>%s</tr>
                        <tr>%s</tr>
                      </table>
                      <div style="margin-top:18px;">
                        <a
                          href="%s"
                          style="display:inline-block;background:#fffdfa;border:1px solid #c9a768;
                            border-radius:14px;padding:12px 16px;color:#8d0f3a;
                            text-decoration:none;font-size:14px;font-weight:800;">
                          View boarding pass
                        </a>
                      </div>
                    </td>
                    <td style="width:230px;vertical-align:top;">
                      <div
                        style="background:#ffffff;border:1px solid #eadfcd;border-radius:20px;
                          padding:16px;text-align:center;box-shadow:0 12px 26px rgba(23,32,51,0.08);">
                        <img
                          src="%s"
                          alt="Boarding pass QR code"
                          width="180"
                          height="180"
                          style="display:block;margin:0 auto;border-radius:12px;background:#ffffff;">
                        <div style="margin-top:12px;font-size:12px;line-height:1.6;color:#6c5b50;">
                          Scan this code to review the boarding pass details.
                        </div>
                      </div>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </div>
        """
        .formatted(
            escapeHtml(passengerName(ticket.getPassenger())),
            buildPassengerMetaTopRow(ticket, boardingPass),
            buildPassengerMetaBottomRow(flight, boardingPass),
            escapeHtml(buildCheckInUrl(ticket)),
            escapeHtml(buildQrImageUrl(card.qrCode())));
  }

  private String buildPassengerMetaTopRow(
      TicketEntity ticket, CheckInConfirmView.BoardingPassView boardingPass) {
    return List.of(
            new MetaCell("PNR", firstNonBlank(ticket.getBooking().getPnrCode(), "--")),
            new MetaCell("Seat", firstNonBlank(boardingPass.seatLabel(), "--")),
            new MetaCell("Flight", firstNonBlank(ticket.getFlight().getFlightNumber(), "--")),
            new MetaCell("Class", formatCabin(ticket)))
        .stream()
        .map(this::renderPassengerMetaCell)
        .collect(Collectors.joining());
  }

  private String buildPassengerMetaBottomRow(
      FlightEntity flight, CheckInConfirmView.BoardingPassView boardingPass) {
    return List.of(
            new MetaCell("Gate", firstNonBlank(boardingPass.gate(), "--")),
            new MetaCell(
                "Boarding time",
                formatTime(boardingPass.boardingTime(), flight.getDepartureAirport())),
            new MetaCell("SEQ", formatSequence(boardingPass.sequenceNumber())),
            new MetaCell(
                "Route",
                airportCode(flight.getDepartureAirport())
                    + " -> "
                    + airportCode(flight.getArrivalAirport())))
        .stream()
        .map(this::renderPassengerMetaCell)
        .collect(Collectors.joining());
  }

  private String renderPassengerMetaCell(MetaCell cell) {
    return """
        <td style="width:25%%;padding:14px 10px 0 0;vertical-align:top;">
          <div style="font-size:12px;letter-spacing:0.08em;text-transform:uppercase;color:#8f7d68;">%s</div>
          <div style="margin-top:10px;font-size:21px;font-weight:800;color:#172033;">%s</div>
        </td>
        """
        .formatted(escapeHtml(cell.label()), escapeHtml(cell.value()));
  }

  private String buildNotesSection(FlightEntity flight) {
    String departureTime = formatTime(flight.getDepartureTime(), flight.getDepartureAirport());
    return """
        <div style="padding:0 32px 18px;">
          <table
            role="presentation"
            style="width:100%%;border-collapse:separate;border-spacing:0;background:#fffdfa;
              border:1px solid #efe2c8;border-radius:22px;overflow:hidden;">
            <tr>
              <td
                style="padding:18px 22px;border-bottom:1px solid #f2e6d2;font-size:13px;
                  font-weight:800;letter-spacing:0.10em;text-transform:uppercase;
                  color:#8d0f3a;">
                Important notes
              </td>
            </tr>
            <tr>
              <td style="padding:16px 12px 8px;">
                <table role="presentation" style="width:100%%;border-collapse:collapse;">
                  <tr>
                    %s
                    %s
                    %s
                    %s
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </div>
        """
        .formatted(
            renderNoteCell(
                "&#128337;",
                "Arrive early",
                "Please be at the airport at least 90 minutes before departure."),
            renderNoteCell(
                "&#128717;", "Carry-on", "Cabin baggage rules still apply at the boarding gate."),
            renderNoteCell(
                "&#128179;",
                "Travel documents",
                "Bring your ID or passport for verification at the airport."),
            renderNoteCell(
                "&#128276;",
                "Gate updates",
                "Boarding gate closes 15 minutes before " + departureTime + "."));
  }

  private String renderNoteCell(String icon, String title, String body) {
    return """
        <td style="width:25%%;padding:10px 10px 16px;vertical-align:top;">
          <table
            role="presentation"
            style="width:100%%;border-collapse:separate;border-spacing:0;background:#fffaf2;
              border:1px solid #f0e2c8;border-radius:18px;">
            <tr>
              <td style="padding:16px 14px;">
                <div style="font-size:26px;line-height:1;color:#8d0f3a;">%s</div>
                <div style="margin-top:14px;font-size:14px;font-weight:800;color:#172033;">%s</div>
                <div style="margin-top:8px;font-size:12px;line-height:1.7;color:#6c5b50;">%s</div>
              </td>
            </tr>
          </table>
        </td>
        """
        .formatted(icon, escapeHtml(title), escapeHtml(body));
  }

  private String buildFooter(TicketEntity ticket) {
    return """
        <div style="padding:12px 32px 28px;">
          <table role="presentation" style="width:100%%;border-collapse:collapse;">
            <tr>
              <td style="padding-right:8px;">
                <a
                  href="%s"
                  style="display:block;background:#8d0f3a;border:1px solid #8d0f3a;
                    border-radius:16px;padding:16px 18px;text-align:center;font-size:15px;
                    font-weight:800;color:#fffaf2;text-decoration:none;">
                  View flight details
                </a>
              </td>
              <td style="padding-left:8px;">
                <a
                  href="%s"
                  style="display:block;background:#fffdfa;border:1px solid #c9a768;
                    border-radius:16px;padding:16px 18px;text-align:center;font-size:15px;
                    font-weight:800;color:#8d0f3a;text-decoration:none;">
                  Manage booking
                </a>
              </td>
            </tr>
          </table>
          <div style="padding-top:24px;text-align:center;color:#7b6b62;font-size:13px;line-height:1.8;">
            Thank you for flying with The Sovereign.<br>
            Please arrive at the airport early and monitor any gate updates before departure.
          </div>
        </div>
        """
        .formatted(escapeHtml(buildCheckInUrl(ticket)), escapeHtml(buildManageBookingUrl()));
  }

  private String buildQrImageUrl(String qrCode) {
    String encoded = URLEncoder.encode(firstNonBlank(qrCode, "--"), StandardCharsets.UTF_8);
    return "https://chart.googleapis.com/chart?cht=qr&chs=220x220&chl=" + encoded + "&choe=UTF-8";
  }

  private String buildManageBookingUrl() {
    return publicBaseUrl + "/manage-booking";
  }

  private String buildCheckInUrl(TicketEntity ticket) {
    return publicBaseUrl
        + "/manage-booking/check-in/"
        + ticket.getBooking().getId()
        + "/"
        + ticket.getFlight().getId()
        + "?pnr="
        + urlEncode(ticket.getBooking().getPnrCode())
        + "&lastName="
        + urlEncode(ticket.getPassenger().getLastName());
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(firstNonBlank(value, ""), StandardCharsets.UTF_8);
  }

  private String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  private String passengerName(PassengerEntity passenger) {
    if (passenger == null) {
      return "--";
    }
    return (nullToBlank(passenger.getLastName()) + " " + nullToBlank(passenger.getFirstName()))
        .trim();
  }

  private String airportCode(AirportEntity airport) {
    return firstNonBlank(airport == null ? null : airport.getCode(), "--");
  }

  private String cityName(AirportEntity airport) {
    return firstNonBlank(
        airport == null ? null : airport.getCity(),
        airport == null ? null : airport.getName(),
        "--");
  }

  private String formatDate(OffsetDateTime value, AirportEntity airport) {
    OffsetDateTime localized = localize(value, airport);
    return localized == null ? "--" : DATE_FORMATTER.format(localized);
  }

  private String formatTime(OffsetDateTime value, AirportEntity airport) {
    OffsetDateTime localized = localize(value, airport);
    return localized == null ? "--" : TIME_FORMATTER.format(localized);
  }

  private OffsetDateTime localize(OffsetDateTime value, AirportEntity airport) {
    if (value == null) {
      return null;
    }
    String timeZone = airport == null ? null : airport.getTimeZone();
    if (!StringUtils.hasText(timeZone)) {
      return value;
    }
    try {
      return value.atZoneSameInstant(ZoneId.of(timeZone)).toOffsetDateTime();
    } catch (DateTimeException ignored) {
      return value;
    }
  }

  private String formatCabin(TicketEntity ticket) {
    String cabin = ticket.getFareClass() == null ? null : ticket.getFareClass().getName();
    if (!StringUtils.hasText(cabin) && ticket.getFareClass() != null) {
      cabin = ticket.getFareClass().getCode();
    }
    return firstNonBlank(cabin, "Economy");
  }

  private String formatTerminal(String terminal) {
    if (!StringUtils.hasText(terminal)) {
      return "--";
    }
    String value = terminal.trim().toUpperCase(Locale.ROOT);
    return value.startsWith("T") ? value : "T" + value;
  }

  private String formatSequence(Integer sequenceNumber) {
    if (sequenceNumber == null) {
      return "--";
    }
    return String.format(Locale.ROOT, "%03d", sequenceNumber);
  }

  private String trimTrailingSlash(String value) {
    if (!StringUtils.hasText(value)) {
      return "http://localhost:5173";
    }
    String trimmed = value.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value.trim();
      }
    }
    return "";
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

  private record PassengerBoardingPassCard(
      TicketEntity ticket, CheckInConfirmView.BoardingPassView boardingPass, String qrCode) {}

  private record MetaCell(String label, String value) {}
}
