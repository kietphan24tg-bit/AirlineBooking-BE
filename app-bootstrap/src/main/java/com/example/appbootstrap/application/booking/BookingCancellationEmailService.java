package com.example.appbootstrap.application.booking;

import com.example.appbootstrap.config.MailProperties;
import com.example.modulebooking.entity.BookingContactEntity;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.repository.BookingContactRepository;
import com.example.modulepayment.application.BookingCancellationRefundEstimate;
import com.example.modulepayment.application.BookingCancellationNotifier;
import com.example.modulepayment.entity.BookingCancellationEntity;
import com.example.modulepayment.entity.BookingCancellationItemEntity;
import com.example.modulepayment.repository.BookingCancellationRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BookingCancellationEmailService implements BookingCancellationNotifier {

  private static final Logger log = LoggerFactory.getLogger(BookingCancellationEmailService.class);

  private final JavaMailSender mailSender;
  private final MailProperties mailProperties;
  private final BookingCancellationRepository bookingCancellationRepository;
  private final BookingContactRepository bookingContactRepository;

  public BookingCancellationEmailService(
      JavaMailSender mailSender,
      MailProperties mailProperties,
      BookingCancellationRepository bookingCancellationRepository,
      BookingContactRepository bookingContactRepository) {
    this.mailSender = mailSender;
    this.mailProperties = mailProperties;
    this.bookingCancellationRepository = bookingCancellationRepository;
    this.bookingContactRepository = bookingContactRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public void sendCancellationSucceeded(Long cancellationId) {
    BookingCancellationEntity cancellation =
        bookingCancellationRepository
            .findByIdWithItems(cancellationId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Cancellation not found: " + cancellationId));
    if (!"SUCCEEDED".equalsIgnoreCase(cancellation.getRefundStatus())) {
      return;
    }

    BookingEntity booking = cancellation.getBooking();
    List<String> recipients = recipientsFor(booking);
    if (recipients.isEmpty()) {
      log.warn(
          "Booking cancellation email skipped: no recipients found cancellationId={} bookingId={}",
          cancellationId,
          booking.getId());
      return;
    }

    try {
      var message = mailSender.createMimeMessage();
      var helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(mailProperties.from());
      helper.setTo(recipients.toArray(String[]::new));
      helper.setSubject("Xác nhận hủy chuyến bay - Mã đặt chỗ " + booking.getPnrCode());
      helper.setText(buildHtml(booking, cancellation), true);
      mailSender.send(message);
    } catch (MailException | jakarta.mail.MessagingException exception) {
      log.warn(
          "Booking cancellation email failed cancellationId={} recipients={}",
          cancellationId,
          recipients,
          exception);
    }
  }

  private List<String> recipientsFor(BookingEntity booking) {
    List<String> contactEmails =
        bookingContactRepository.findByBookingIdOrderByPrimaryDescIdAsc(booking.getId()).stream()
            .map(BookingContactEntity::getEmail)
            .map(this::normalizeEmail)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    if (!contactEmails.isEmpty()) {
      return contactEmails;
    }

    String fallback = normalizeEmail(booking.getContactEmail());
    return StringUtils.hasText(fallback) ? List.of(fallback) : List.of();
  }

  private String buildHtml(BookingEntity booking, BookingCancellationEntity cancellation) {
    BookingCancellationRefundEstimate refundEstimate =
        BookingCancellationRefundEstimate.forProvider(cancellation.getProvider());
    String passengerItems =
        cancellation.getItems().stream()
            .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
            .map(BookingCancellationItemEntity::getPassenger)
            .map(
                passenger ->
                    "<li style=\"margin:0 0 6px;\">"
                        + escapeHtml(
                            (passenger.getFirstName() + " " + passenger.getLastName()).trim())
                        + "</li>")
            .reduce("", String::concat);

    return """
        <div style="margin:0;padding:24px;background:#f8f8f8;font-family:Arial,sans-serif;color:#222;">
          <div style="max-width:700px;margin:0 auto;background:#ffffff;border:1px solid #e8e8e8;">
            <div style="background:linear-gradient(90deg,#c8102e 0%,#e31b23 70%,#f4b400 100%);padding:18px 24px;color:#ffffff;">
              <div style="font-size:28px;font-weight:700;line-height:1.2;">Xác nhận hủy chuyến bay</div>
              <div style="margin-top:6px;font-size:14px;">Mã đặt chỗ: %s</div>
            </div>
            <div style="padding:24px;">
              <p style="margin:0 0 12px;">Kính gửi Quý khách,</p>
              <p style="margin:0 0 20px;">
                Yêu cầu hủy chuyến bay của Quý khách đã được xử lý thành công.
                Thông tin hoàn tiền được hiển thị dưới đây.
              </p>
              <table role="presentation" style="width:100%%;border-collapse:collapse;">
                <tr>
                  <td style="vertical-align:top;padding:16px;border:1px solid #ededed;">
                    <div style="font-size:14px;font-weight:700;margin-bottom:10px;">Hành khách hủy</div>
                    <ul style="padding-left:18px;margin:0;">%s</ul>
                  </td>
                  <td style="vertical-align:top;padding:16px;border:1px solid #ededed;width:260px;">
                    <div style="font-size:14px;color:#666;">Tổng tiền hoàn</div>
                    <div style="font-size:28px;font-weight:700;color:#c8102e;margin:8px 0 16px;">%s %s</div>
                    <div style="font-size:14px;color:#666;">%s</div>
                    <div style="font-size:15px;font-weight:600;margin-top:8px;">%s</div>
                    <div style="font-size:13px;line-height:1.5;color:#666;margin-top:8px;">%s</div>
                  </td>
                </tr>
              </table>
              <p style="margin:20px 0 0;">Cảm ơn Quý khách đã lựa chọn hãng hàng không của chúng tôi.</p>
            </div>
          </div>
        </div>
        """
        .formatted(
            escapeHtml(booking.getPnrCode()),
            passengerItems,
            formatMoney(cancellation.getNetRefundTotal()),
            escapeHtml(cancellation.getCurrency()),
            escapeHtml(refundEstimate.title()),
            escapeHtml(refundEstimate.message()),
            escapeHtml(refundEstimate.note()));
  }

  private String formatMoney(BigDecimal amount) {
    NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
    formatter.setMinimumFractionDigits(0);
    formatter.setMaximumFractionDigits(2);
    return formatter.format(amount);
  }

  private String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
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
}
