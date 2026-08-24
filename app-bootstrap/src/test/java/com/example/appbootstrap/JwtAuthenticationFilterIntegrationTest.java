package com.example.appbootstrap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.moduleidentity.entity.Role;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.repository.UserRepository;
import com.example.moduleweb.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(JwtAuthenticationFilterIntegrationTest.TestApiController.class)
class JwtAuthenticationFilterIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
  }

  @Test
  void shouldAuthenticateActiveAccessToken() throws Exception {
    UserEntity user = userRepository.save(user("active@example.com"));
    String token = jwtService.generateAccessToken(user.getEmail(), user.getId());

    mockMvc
        .perform(get("/jwt-test/protected").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.secured").value(true));
  }

  @Test
  void shouldRejectAccessTokenIssuedBeforeTokenInvalidBefore() throws Exception {
    UserEntity user = userRepository.save(user("revoked@example.com"));
    String token = jwtService.generateAccessToken(user.getEmail(), user.getId());

    Thread.sleep(25L);
    user.setTokenInvalidBefore(OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    user.setUpdatedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    userRepository.save(user);

    mockMvc
        .perform(get("/jwt-test/protected").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void shouldRejectMalformedBearerTokenForProtectedEndpoint() throws Exception {
    mockMvc
        .perform(get("/jwt-test/protected").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void shouldRejectRefreshTokenUsedForProtectedEndpoint() throws Exception {
    UserEntity user = userRepository.save(user("refresh-only@example.com"));
    String token = jwtService.generateRefreshToken(user.getEmail(), user.getId());

    mockMvc
        .perform(get("/jwt-test/protected").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void shouldRejectAccessTokenWithInvalidSignature() throws Exception {
    UserEntity user = userRepository.save(user("bad-signature@example.com"));
    String invalidSignatureToken =
        Jwts.builder()
            .subject(user.getEmail())
            .claim("userId", user.getId())
            .claim("type", "access")
            .id(java.util.UUID.randomUUID().toString())
            .issuedAt(java.util.Date.from(Instant.now()))
            .expiration(java.util.Date.from(Instant.now().plusSeconds(3600)))
            .signWith(
                Keys.hmacShaKeyFor(
                    Decoders.BASE64.decode("b3RoZXItc2VjcmV0LWtleS1mb3ItdGVzdGluZy0xMjM0NTY3ODkw")))
            .compact();

    mockMvc
        .perform(
            get("/jwt-test/protected").header("Authorization", "Bearer " + invalidSignatureToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void shouldRejectAccessTokenMissingTypeClaim() throws Exception {
    UserEntity user = userRepository.save(user("missing-type-access@example.com"));
    String tokenWithoutType =
        Jwts.builder()
            .subject(user.getEmail())
            .claim("userId", user.getId())
            .id(java.util.UUID.randomUUID().toString())
            .issuedAt(java.util.Date.from(Instant.now()))
            .expiration(java.util.Date.from(Instant.now().plusSeconds(3600)))
            .signWith(
                Keys.hmacShaKeyFor(
                    Decoders.BASE64.decode("dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW9ubHk=")))
            .compact();

    mockMvc
        .perform(get("/jwt-test/protected").header("Authorization", "Bearer " + tokenWithoutType))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void shouldRejectAccessTokenForUnknownUser() throws Exception {
    String unknownUserToken =
        Jwts.builder()
            .subject("ghost@example.com")
            .claim("userId", 999L)
            .claim("type", "access")
            .id(java.util.UUID.randomUUID().toString())
            .issuedAt(java.util.Date.from(Instant.now()))
            .expiration(java.util.Date.from(Instant.now().plusSeconds(3600)))
            .signWith(
                Keys.hmacShaKeyFor(
                    Decoders.BASE64.decode("dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW9ubHk=")))
            .compact();

    mockMvc
        .perform(get("/jwt-test/protected").header("Authorization", "Bearer " + unknownUserToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  private UserEntity user(String email) {
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    UserEntity user = new UserEntity();
    user.setEmail(email);
    user.setFirstName("Test");
    user.setLastName("User");
    user.setPasswordHash(passwordEncoder.encode("secret"));
    user.setRole(Role.USER);
    user.setAcceptTerms(true);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    return user;
  }

  @RestController
  @RequestMapping("/jwt-test")
  static class TestApiController {

    @GetMapping("/protected")
    Map<String, Object> protectedEndpoint() {
      return Map.of("secured", true);
    }
  }
}
