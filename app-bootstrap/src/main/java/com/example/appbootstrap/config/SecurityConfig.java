package com.example.appbootstrap.config;

import com.example.appbootstrap.security.CustomAccessDeniedHandler;
import com.example.appbootstrap.security.CustomAuthenticationEntryPoint;
import com.example.appbootstrap.security.JwtAuthenticationFilter;
import com.example.appbootstrap.security.JwtService;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
  private final CustomAccessDeniedHandler customAccessDeniedHandler;
  private final CorsProperties corsProperties;

  public SecurityConfig(
      CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
      CustomAccessDeniedHandler customAccessDeniedHandler,
      CorsProperties corsProperties) {
    this.customAuthenticationEntryPoint = customAuthenticationEntryPoint;
    this.customAccessDeniedHandler = customAccessDeniedHandler;
    this.corsProperties = corsProperties;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtService jwtService, UserDetailsService userDetailsService)
      throws Exception {
    JwtAuthenticationFilter jwtAuthenticationFilter =
        new JwtAuthenticationFilter(jwtService, userDetailsService);
    http.csrf(csrf -> csrf.disable())
        .httpBasic(httpBasic -> httpBasic.disable())
        .formLogin(formLogin -> formLogin.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->

                // catch exception overrided
                exceptions
                    .authenticationEntryPoint(customAuthenticationEntryPoint)
                    .accessDeniedHandler(customAccessDeniedHandler))
        .authorizeHttpRequests(
            auth ->

                // public api
                auth.requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/health",
                        "/error",
                        "/api/auth/**",
                        "/api/payments/webhooks/payos",
                        "/api/payments/webhooks/stripe",
                        "/api/flights/**",
                        "/api/airports/**",
                        "/api/services",
                        "/api/services/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/assets/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.HEAD, "/assets/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/bookings")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/bookings/lookup")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/check-in/continue")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/bookings/*/tickets")
                    .permitAll()
                    .requestMatchers(HttpMethod.PUT, "/api/bookings/*/seat-assignments")
                    .permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/api/bookings/*/seat-assignments")
                    .permitAll()
                    .requestMatchers(HttpMethod.PATCH, "/api/bookings/*/seat-assignment")
                    .permitAll()
                    .requestMatchers(HttpMethod.PUT, "/api/bookings/*/services")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/bookings/*/cancellations/preview")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/payment-methods")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/payments")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/payments/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/payments/*/cancel")
                    .permitAll()
                    // admin api
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    // manager api
                    .requestMatchers("/api/management/**")
                    .hasAnyRole("MANAGER", "ADMIN")
                    // all other api require authentication
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    List<String> allowedOrigins = corsProperties.allowedOriginPatterns();
    if (allowedOrigins == null || allowedOrigins.isEmpty()) {
      allowedOrigins =
          Arrays.asList("http://localhost:3000", "http://localhost:5173", "https://*.railway.app");
    }
    configuration.setAllowedOriginPatterns(allowedOrigins);
    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
