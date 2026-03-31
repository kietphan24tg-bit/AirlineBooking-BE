package com.example.appbootstrap;

import com.example.appbootstrap.config.BookingProperties;
import com.example.appbootstrap.config.JwtProperties;
import com.example.appbootstrap.config.LoggingProperties;
import com.example.appbootstrap.config.MailProperties;
import com.example.appbootstrap.config.PaymentProperties;
import com.example.appbootstrap.config.SwaggerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication(scanBasePackages = "com.example")
@EntityScan(basePackages = "com.example")
@EnableConfigurationProperties({
  JwtProperties.class,
  BookingProperties.class,
  PaymentProperties.class,
  MailProperties.class,
  LoggingProperties.class,
  SwaggerProperties.class
})
public class AppBootstrapApplication {

  public static void main(String[] args) {
    SpringApplication.run(AppBootstrapApplication.class, args);
  }
}
