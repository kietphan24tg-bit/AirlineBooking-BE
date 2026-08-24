package com.example.appbootstrap;

import com.example.moduleai.config.AiAgentProperties;
import com.example.moduleai.config.AiProviderProperties;
import com.example.modulecheckin.config.CheckInProperties;
import com.example.moduleflight.config.AirlineImportProperties;
import com.example.moduleflight.config.AirportImportProperties;
import com.example.moduleflight.config.AirportMarketProperties;
import com.example.moduleflight.config.FlightCoverageProperties;
import com.example.moduleflight.config.FlightImportProperties;
import com.example.moduleflight.config.FlightMaintenanceProperties;
import com.example.moduleflight.config.FlightMockDataProperties;
import com.example.moduleflight.config.FlightSearchFallbackProperties;
import com.example.moduleidentity.config.RateLimitProperties;
import com.example.modulepayment.config.PaymentProperties;
import com.example.moduleweb.config.AuthCookieProperties;
import com.example.moduleweb.config.BookingProperties;
import com.example.moduleweb.config.ClientIpProperties;
import com.example.moduleweb.config.CorsProperties;
import com.example.moduleweb.config.JwtProperties;
import com.example.moduleweb.config.LoggingProperties;
import com.example.moduleweb.config.MailProperties;
import com.example.moduleweb.config.PasswordResetProperties;
import com.example.moduleweb.config.SwaggerProperties;
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
  AiAgentProperties.class,
  AiProviderProperties.class,
  JwtProperties.class,
  AuthCookieProperties.class,
  CorsProperties.class,
  ClientIpProperties.class,
  BookingProperties.class,
  CheckInProperties.class,
  PaymentProperties.class,
  MailProperties.class,
  PasswordResetProperties.class,
  LoggingProperties.class,
  SwaggerProperties.class,
  RateLimitProperties.class,
  AirportMarketProperties.class,
  AirportImportProperties.class,
  AirlineImportProperties.class,
  FlightImportProperties.class,
  FlightMaintenanceProperties.class,
  FlightCoverageProperties.class,
  FlightMockDataProperties.class,
  FlightSearchFallbackProperties.class
})
public class AppBootstrapApplication {

  public static void main(String[] args) {
    SpringApplication.run(AppBootstrapApplication.class, args);
  }
}
