package com.example.appbootstrap.application.identity;

import com.example.appbootstrap.config.MailProperties;
import com.example.appbootstrap.config.PasswordResetProperties;
import com.example.common.exception.AppException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailService {

  private final JavaMailSender mailSender;
  private final MailProperties mailProperties;
  private final PasswordResetProperties passwordResetProperties;

  public PasswordResetEmailService(
      JavaMailSender mailSender,
      MailProperties mailProperties,
      PasswordResetProperties passwordResetProperties) {
    this.mailSender = mailSender;
    this.mailProperties = mailProperties;
    this.passwordResetProperties = passwordResetProperties;
  }

  public void sendResetOtp(String email, String otp) {
    if (!passwordResetProperties.emailEnabled() || otp == null || otp.isBlank()) {
      return;
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailProperties.from());
    message.setTo(email);
    message.setSubject("Reset your Airline Booking password");
    message.setText(buildBody(otp));

    try {
      mailSender.send(message);
    } catch (MailException exception) {
      throw new AppException(
          "PASSWORD_RESET_EMAIL_FAILED", "Unable to send password reset email", 502);
    }
  }

  private String buildBody(String otp) {
    return "We received a request to reset your Airline Booking password.\n\n"
        + "Your password reset code is: "
        + otp
        + "\n\nThis code expires in 15 minutes."
        + "\n\nIf you did not request this, you can ignore this email.";
  }
}
