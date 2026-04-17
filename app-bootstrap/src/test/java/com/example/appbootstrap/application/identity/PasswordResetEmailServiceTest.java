package com.example.appbootstrap.application.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.appbootstrap.config.MailProperties;
import com.example.appbootstrap.config.PasswordResetProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class PasswordResetEmailServiceTest {

  @Mock private JavaMailSender mailSender;

  @Test
  void shouldSendPasswordResetOtp() {
    PasswordResetEmailService service =
        new PasswordResetEmailService(
            mailSender,
            new MailProperties("noreply@example.com"),
            new PasswordResetProperties(true));

    service.sendResetOtp("user@example.com", "123456");

    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getFrom()).isEqualTo("noreply@example.com");
    assertThat(message.getTo()).containsExactly("user@example.com");
    assertThat(message.getSubject()).isEqualTo("Reset your Airline Booking password");
    assertThat(message.getText())
        .contains("Your password reset code is: 123456")
        .contains("15 minutes")
        .doesNotContain("reset-password?token=");
  }

  @Test
  void shouldSkipSendingWhenPasswordResetEmailIsDisabled() {
    PasswordResetEmailService service =
        new PasswordResetEmailService(
            mailSender,
            new MailProperties("noreply@example.com"),
            new PasswordResetProperties(false));

    service.sendResetOtp("user@example.com", "123456");

    verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
  }
}
