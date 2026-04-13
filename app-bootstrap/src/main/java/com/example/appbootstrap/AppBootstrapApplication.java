package com.example.appbootstrap;

import com.example.appbootstrap.config.AuthCookieProperties;
import com.example.appbootstrap.config.BookingProperties;
import com.example.appbootstrap.config.ClientIpProperties;
import com.example.appbootstrap.config.CorsProperties;
import com.example.appbootstrap.config.JwtProperties;
import com.example.appbootstrap.config.LoggingProperties;
import com.example.appbootstrap.config.MailProperties;
import com.example.appbootstrap.config.SwaggerProperties;
import com.example.moduleflight.config.AirportImportProperties;
import com.example.moduleidentity.config.RateLimitProperties;
import com.example.modulepayment.config.PaymentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.example")
@EntityScan(basePackages = "com.example")
@EnableJpaRepositories(basePackages = "com.example")
@EnableScheduling
@EnableConfigurationProperties({
  JwtProperties.class,
  AuthCookieProperties.class,
  CorsProperties.class,
  ClientIpProperties.class,
  BookingProperties.class,
  PaymentProperties.class,
  MailProperties.class,
  LoggingProperties.class,
  SwaggerProperties.class,
  RateLimitProperties.class,
  AirportImportProperties.class
})
public class AppBootstrapApplication {

  public static void main(String[] args) {
    SpringApplication.run(AppBootstrapApplication.class, args);
  }
}
