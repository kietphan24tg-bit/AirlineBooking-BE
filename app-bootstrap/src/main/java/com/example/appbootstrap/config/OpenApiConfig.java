package com.example.appbootstrap.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class OpenApiConfig {

  private static final String SECURITY_SCHEME_NAME = "bearerAuth";

  @Bean
  OpenAPI airlineBookingOpenApi(SwaggerProperties swaggerProperties) {
    return new OpenAPI()
        .info(
            new Info()
                .title(swaggerProperties.title())
                .description(swaggerProperties.description())
                .version(swaggerProperties.version())
                .contact(new Contact().name("Airline Booking ")))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
        .components(
            new Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME_NAME,
                    new SecurityScheme()
                        .name(SECURITY_SCHEME_NAME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addTagsItem(new Tag().name("Identity").description("Authentication and user endpoints"))
        .addTagsItem(new Tag().name("Flights").description("Flight search, schedules, and fares"))
        .addTagsItem(new Tag().name("Bookings").description("Booking, passenger, and ticket flows"))
        .addTagsItem(new Tag().name("Payments").description("Payment and transaction endpoints"))
        .addTagsItem(
            new Tag().name("Check-in").description("Check-in and boarding pass endpoints"));
  }

  @Bean
  GroupedOpenApi identityApi() {
    return GroupedOpenApi.builder()
        .group("identity")
        .pathsToMatch("/api/identity/**", "/api/auth/**", "/api/users/**")
        .build();
  }

  @Bean
  GroupedOpenApi flightsApi() {
    return GroupedOpenApi.builder()
        .group("flights")
        .pathsToMatch("/api/flights/**", "/api/airports/**", "/api/airlines/**")
        .build();
  }

  @Bean
  GroupedOpenApi bookingsApi() {
    return GroupedOpenApi.builder()
        .group("bookings")
        .pathsToMatch("/api/bookings/**", "/api/passengers/**", "/api/tickets/**")
        .build();
  }

  @Bean
  GroupedOpenApi paymentsApi() {
    return GroupedOpenApi.builder().group("payments").pathsToMatch("/api/payments/**").build();
  }

  @Bean
  GroupedOpenApi checkInApi() {
    return GroupedOpenApi.builder()
        .group("check-in")
        .pathsToMatch("/api/check-in/**", "/api/boarding-passes/**")
        .build();
  }
}
