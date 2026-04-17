package com.example.appbootstrap.config;

import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailSenderConfig {

  @Bean
  @ConditionalOnMissingBean(JavaMailSender.class)
  JavaMailSender javaMailSender(Environment environment) {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost(environment.getProperty("spring.mail.host", "localhost"));
    mailSender.setPort(environment.getProperty("spring.mail.port", Integer.class, 25));

    String username = environment.getProperty("spring.mail.username");
    if (username != null && !username.isBlank()) {
      mailSender.setUsername(username);
    }
    String password = environment.getProperty("spring.mail.password");
    if (password != null && !password.isBlank()) {
      mailSender.setPassword(password);
    }

    Properties javaMailProperties = mailSender.getJavaMailProperties();
    javaMailProperties.put(
        "mail.smtp.auth",
        environment.getProperty("spring.mail.properties.mail.smtp.auth", "false"));
    javaMailProperties.put(
        "mail.smtp.starttls.enable",
        environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "false"));
    return mailSender;
  }
}
