package com.example.moduleai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Tag("staging-mutation-e2e")
@EnabledIf("isStagingMutationEnabled")
@Execution(ExecutionMode.SAME_THREAD)
class AgentStagingMutationE2ETest {

  private static final Set<String> ALLOWED_CLEANUP_TABLES =
      Set.of(
          "ai_tool_audit_events",
          "ai_tool_proposals",
          "ai_chat_messages",
          "ai_chat_turns",
          "ai_chat_conversations",
          "boarding_passes",
          "seat_change_settlement_items",
          "seat_change_settlements",
          "service_change_settlement_items",
          "service_change_settlements",
          "booking_cancellations",
          "booking_cancellation_items",
          "ticket_financial_snapshots",
          "tickets",
          "passenger_seat_assignments",
          "booking_services",
          "booking_fare_holds",
          "payments",
          "passengers",
          "bookings",
          "refresh_token_sessions",
          "password_reset_tokens",
          "users",
          "flight_seats",
          "flight_fares",
          "flights");

  static boolean isStagingMutationEnabled() {
    return "true".equalsIgnoreCase(System.getenv("AGENT_STAGING_MUTATION_ENABLED"))
        || Boolean.getBoolean("agent.staging.mutation.enabled");
  }

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private String baseUrl;
  private String dbUrl;
  private String dbUser;
  private String dbPassword;

  private Connection dbConnection;
  private String runId;
  private String testEmail;
  private Long testUserId;
  private String authToken;

  private final List<LedgerEntry> ledger = new ArrayList<>();
  private final List<SeatStateBackup> seatBackups = new ArrayList<>();
  private final List<FlightScheduleBackup> flightScheduleBackups = new ArrayList<>();
  private final Set<Long> fixtureBookingIds = new HashSet<>();

  private record LedgerEntry(String tableName, Object id) {}

  private record SeatStateBackup(
      Long seatId,
      String originalStatus,
      Long originalBookingId,
      OffsetDateTime originalReservedUntil) {}

  private record FlightScheduleBackup(
      Long flightId,
      String originalStatus,
      OffsetDateTime originalDepartureTime,
      OffsetDateTime originalArrivalTime,
      OffsetDateTime originalUpdatedAt,
      OffsetDateTime fixtureDepartureTime,
      OffsetDateTime fixtureArrivalTime) {}

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = System.getenv("STAGING_BASE_URL");
    dbUrl = System.getenv("STAGING_DB_URL");
    dbUser = System.getenv("STAGING_DB_USER");
    dbPassword = System.getenv("STAGING_DB_PASSWORD");

    if (baseUrl == null
        || baseUrl.isBlank()
        || dbUrl == null
        || dbUrl.isBlank()
        || dbUser == null
        || dbUser.isBlank()
        || dbPassword == null
        || dbPassword.isBlank()) {
      throw new IllegalStateException(
          "STAGING_BASE_URL, STAGING_DB_URL, STAGING_DB_USER, and STAGING_DB_PASSWORD environment"
              + " variables must all be specified when AGENT_STAGING_MUTATION_ENABLED=true");
    }
    baseUrl = baseUrl.replaceAll("/+$", "");

    runId = UUID.randomUUID().toString().substring(0, 8);
    testEmail = "agent-e2e-" + runId + "@example.test";

    try {
      dbConnection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Staging database connection failed: " + sanitizeError(exception.getMessage()),
          exception);
    }

    provisionTestUser();
  }

  @AfterEach
  void tearDown() {
    List<String> cleanupErrors = new ArrayList<>();
    if (dbConnection != null) {
      try {
        if (testUserId == null && testEmail != null) {
          testUserId = queryLong("SELECT id FROM users WHERE email = ?", testEmail);
          if (testUserId != null) {
            addToLedger("users", testUserId);
          }
        }
        discoverFixtureCreatedEntities();
        discoverUserCreatedEntities();
      } catch (Exception e) {
        cleanupErrors.add(
            "Failed to discover user created entities: " + sanitizeError(e.getMessage()));
      }
      cleanupLedgerAndSeats(cleanupErrors);
      try {
        dbConnection.close();
      } catch (SQLException e) {
        cleanupErrors.add("Failed to close DB connection: " + sanitizeError(e.getMessage()));
      }
    }
    if (!cleanupErrors.isEmpty()) {
      throw new IllegalStateException("Cleanup encountered failures: " + cleanupErrors);
    }
  }

  @Test
  void validateCheckInMutationFlow() throws Exception {
    ReferenceData ref = discoverCheckInReferenceData();
    if (ref == null || ref.initialSeatId == null) {
      throw new IllegalStateException(
          "Required check-in reference flight data not found in staging DB");
    }

    BookingFixture fixture = createBookingFixture(ref, "CONFIRMED", true, false, false);

    String chatPrompt =
        "T\u00F4i mu\u1ED1n l\u00E0m th\u1EE7 t\u1EE5c chuy\u1EBFn bay cho h\u00E0nh kh\u00E1ch "
            + fixture.passengerFirstName
            + " "
            + fixture.passengerLastName
            + "; passenger last name "
            + fixture.passengerLastName
            + "; PNR "
            + fixture.pnrCode;
    UUID proposalId = executePromptAndExtractProposalId(chatPrompt, "confirm_check_in");
    assertThat(proposalId).as("Proposal ID should be created for check-in prompt").isNotNull();

    ConfirmationRaceResult race = runConcurrentConfirmations(proposalId);
    assertThat(race.hasServerError)
        .as("Concurrent confirmation should not cause 5xx error")
        .isFalse();
    assertThat(race.successCount).as("Exactly one confirmation must succeed").isEqualTo(1);
    assertThat(race.conflictCount).as("Concurrent request must be refused").isEqualTo(1);

    HttpResponse<String> replayRes = sendConfirmRequest(proposalId);
    assertThat(replayRes.statusCode())
        .as("Replay confirmation must be refused with 409 conflict")
        .isEqualTo(409);

    long checkedInCount =
        queryCount(
            "SELECT count(*) FROM boarding_passes bp JOIN tickets t ON t.id = bp.ticket_id"
                + " WHERE t.booking_id = ?",
            fixture.bookingId);
    assertThat(checkedInCount).as("Reconciliation: Boarding pass count must be 1").isEqualTo(1L);
    assertThat(queryString("SELECT status FROM tickets WHERE id = ?", fixture.ticketId))
        .as("Reconciliation: Check-in preserves the issued ticket lifecycle")
        .isEqualTo("ISSUED");
    assertThat(
            queryCount(
                "SELECT count(*) FROM boarding_passes WHERE ticket_id = ?", fixture.ticketId))
        .as("Reconciliation: Exactly one boarding pass must belong to the selected ticket")
        .isEqualTo(1L);
  }

  @Test
  void validateCancellationMutationFlow() throws Exception {
    ReferenceData ref = discoverFutureReferenceData();
    if (ref == null) {
      throw new IllegalStateException(
          "Required future reference flight data not found in staging DB");
    }

    BookingFixture fixture = createBookingFixture(ref, "CONFIRMED", true, true, false);

    String chatPrompt =
        "I want to cancel my ticket for passenger "
            + fixture.passengerFirstName
            + " "
            + fixture.passengerLastName
            + "; passenger last name "
            + fixture.passengerLastName
            + "; booking PNR "
            + fixture.pnrCode;
    UUID proposalId = executePromptAndExtractProposalId(chatPrompt, "confirm_cancellation");
    assertThat(proposalId).as("Proposal ID should be created for cancellation prompt").isNotNull();

    ConfirmationRaceResult race = runConcurrentConfirmations(proposalId);
    assertThat(race.hasServerError).isFalse();
    assertThat(race.successCount).isEqualTo(1);
    assertThat(race.conflictCount).isEqualTo(1);

    HttpResponse<String> replayRes = sendConfirmRequest(proposalId);
    assertThat(replayRes.statusCode()).isEqualTo(409);

    long cancellationCount =
        queryCount(
            "SELECT count(*) FROM booking_cancellations WHERE booking_id = ?", fixture.bookingId);
    assertThat(cancellationCount)
        .as("Reconciliation: Cancellation row count must be 1")
        .isEqualTo(1L);

    String ticketStatus = queryString("SELECT status FROM tickets WHERE id = ?", fixture.ticketId);
    assertThat(ticketStatus)
        .as("Reconciliation: Ticket status must be CANCELLED")
        .isEqualTo("CANCELLED");

    String providerStatus =
        queryString(
            "SELECT provider_status FROM booking_cancellations WHERE booking_id = ?",
            fixture.bookingId);
    assertThat(providerStatus)
        .as("Reconciliation: Zero-refund cancellation provider status must be NOT_REQUIRED")
        .isEqualTo("NOT_REQUIRED");
    assertThat(queryString("SELECT status FROM bookings WHERE id = ?", fixture.bookingId))
        .as("Reconciliation: Fully cancelled booking must be CANCELLED")
        .isEqualTo("CANCELLED");
    assertThat(
            queryString(
                "SELECT payment_status FROM payments WHERE booking_id = ?", fixture.bookingId))
        .as("Reconciliation: Zero-refund cancellation must not alter the settled payment")
        .isEqualTo("SUCCESS");
  }

  @Test
  void validateSeatChangeMutationFlow() throws Exception {
    ReferenceData ref = discoverFutureReferenceData();
    if (ref == null || ref.targetSeatId == null) {
      throw new IllegalStateException("Required seat reference data not found in staging DB");
    }

    BookingFixture fixture = createBookingFixture(ref, "CONFIRMED", true, false, false);

    String chatPrompt =
        "T\u00F4i mu\u1ED1n \u0111\u1ED5i gh\u1EBF cho h\u00E0nh kh\u00E1ch "
            + fixture.passengerFirstName
            + " "
            + fixture.passengerLastName
            + " sang gh\u1EBF "
            + ref.targetSeatNumber
            + "; passenger last name "
            + fixture.passengerLastName
            + "; booking PNR "
            + fixture.pnrCode;
    UUID proposalId = executePromptAndExtractProposalId(chatPrompt, "confirm_seat_change");
    assertThat(proposalId).as("Proposal ID should be created for seat change prompt").isNotNull();

    ConfirmationRaceResult race = runConcurrentConfirmations(proposalId);
    assertThat(race.hasServerError).isFalse();
    assertThat(race.successCount).isEqualTo(1);
    assertThat(race.conflictCount).isEqualTo(1);

    HttpResponse<String> replayRes = sendConfirmRequest(proposalId);
    assertThat(replayRes.statusCode()).isEqualTo(409);

    long settlementCount =
        queryCount(
            "SELECT count(*) FROM seat_change_settlements WHERE booking_id = ?", fixture.bookingId);
    assertThat(settlementCount)
        .as("Reconciliation: Seat change settlement count must be 1")
        .isEqualTo(1L);

    String oldAssignmentStatus =
        queryString(
            "SELECT status FROM passenger_seat_assignments WHERE booking_id = ? AND flight_seat_id"
                + " = ?",
            fixture.bookingId,
            ref.initialSeatId);
    assertThat(oldAssignmentStatus)
        .as("Reconciliation: Old seat assignment status must be RELEASED")
        .isEqualTo("RELEASED");

    String newAssignmentStatus =
        queryString(
            "SELECT status FROM passenger_seat_assignments WHERE booking_id = ? AND flight_seat_id"
                + " = ?",
            fixture.bookingId,
            ref.targetSeatId);
    assertThat(newAssignmentStatus)
        .as("Reconciliation: New seat assignment status must be BOOKED")
        .isEqualTo("BOOKED");
    assertThat(queryString("SELECT status FROM flight_seats WHERE id = ?", ref.initialSeatId))
        .as("Reconciliation: Released source seat must be AVAILABLE")
        .isEqualTo("AVAILABLE");
    assertThat(queryString("SELECT status FROM flight_seats WHERE id = ?", ref.targetSeatId))
        .as("Reconciliation: Target seat must be BOOKED")
        .isEqualTo("BOOKED");
    assertThat(
            queryCount(
                "SELECT count(*) FROM passenger_seat_assignments WHERE booking_id = ?"
                    + " AND flight_seat_id = ? AND status IN ('RESERVED', 'BOOKED')",
                fixture.bookingId,
                ref.targetSeatId))
        .as("Reconciliation: Exactly one active assignment must own the target seat")
        .isEqualTo(1L);
  }

  @RepeatedTest(3)
  void validateNaturalServiceChangeAndExpiredProposal() throws Exception {
    ReferenceData ref = discoverFutureReferenceData();
    assertThat(ref).isNotNull();
    assertThat(ref.sourceServiceOptionName).isNotBlank();
    assertThat(ref.targetServiceOptionName).isNotBlank();
    BookingFixture fixture = createBookingFixture(ref, "CONFIRMED", true, false, true);
    String prompt =
        "For my booking PNR "
            + fixture.pnrCode
            + ", passenger first name "
            + fixture.passengerFirstName
            + ", last name "
            + fixture.passengerLastName
            + ", replace "
            + ref.sourceServiceQuantity
            + " of the service option named \""
            + ref.sourceServiceOptionName
            + "\" with "
            + ref.targetServiceQuantity
            + " of the option named \""
            + ref.targetServiceOptionName
            + "\". Please prepare the change for my confirmation.";
    UUID proposalId = executePromptAndExtractProposalId(prompt, "confirm_service_change");
    JsonNode arguments =
        objectMapper.readTree(
            queryString(
                "SELECT canonical_arguments::text FROM ai_tool_proposals WHERE id = ?"
                    + " AND user_id = ?",
                proposalId,
                testUserId));
    assertThat(arguments.path("bookingId").asLong()).isEqualTo(fixture.bookingId);
    assertThat(arguments.path("passengers").size()).isEqualTo(1);
    JsonNode passenger = arguments.path("passengers").get(0);
    assertThat(passenger.path("passengerId").asLong()).isEqualTo(fixture.passengerId);
    assertThat(passenger.path("selections").size()).isEqualTo(1);
    JsonNode selection = passenger.path("selections").get(0);
    assertThat(selection.path("optionId").asLong()).isEqualTo(ref.targetServiceOptionId);
    assertThat(selection.path("quantity").asInt()).isEqualTo(ref.targetServiceQuantity);
    assertThat(
            queryCount(
                "SELECT count(*) FROM service_change_settlements WHERE booking_id = ?",
                fixture.bookingId))
        .isZero();
    HttpRequest foreign =
        HttpRequest.newBuilder(
                URI.create(baseUrl + "/api/chatbot/proposals/" + proposalId + "/confirm"))
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    assertThat(httpClient.send(foreign, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isIn(401, 403);
    try (PreparedStatement statement =
        dbConnection.prepareStatement(
            "UPDATE ai_tool_proposals SET expires_at = ? WHERE id = ? AND user_id = ?")) {
      statement.setObject(1, OffsetDateTime.now().minusMinutes(1));
      statement.setObject(2, proposalId);
      statement.setLong(3, testUserId);
      assertThat(statement.executeUpdate()).isEqualTo(1);
    }
    HttpResponse<String> expired = sendConfirmRequest(proposalId);
    assertThat(expired.statusCode()).isEqualTo(410);
    assertThat(expired.body()).contains("PROPOSAL_EXPIRED");
    assertThat(
            queryCount(
                "SELECT count(*) FROM service_change_settlements WHERE booking_id = ?",
                fixture.bookingId))
        .isZero();
  }

  @Test
  void validateServiceChangeMutationFlow() throws Exception {
    ReferenceData ref = discoverFutureReferenceData();
    if (ref == null
        || ref.sourceServiceId == null
        || ref.sourceServiceOptionId == null
        || ref.sourceServiceQuantity == null
        || ref.targetServiceOptionId == null) {
      throw new IllegalStateException("Required service reference data not found in staging DB");
    }

    BookingFixture fixture = createBookingFixture(ref, "CONFIRMED", true, false, true);

    String chatPrompt =
        "Create one confirm_service_change proposal for my bookingId "
            + fixture.bookingId
            + ", PNR "
            + fixture.pnrCode
            + ", passenger last name "
            + fixture.passengerLastName
            + ", passengerId "
            + fixture.passengerId
            + ". Replace service optionId "
            + ref.sourceServiceOptionId
            + " quantity "
            + ref.sourceServiceQuantity
            + " with optionId "
            + ref.targetServiceOptionId
            + " quantity "
            + ref.targetServiceQuantity
            + ". Do not call list_services; use the supplied IDs.";
    UUID proposalId = executePromptAndExtractProposalId(chatPrompt, "confirm_service_change");
    assertThat(proposalId)
        .as("Proposal ID should be created for service change prompt")
        .isNotNull();

    ConfirmationRaceResult race = runConcurrentConfirmations(proposalId);
    assertThat(race.hasServerError).isFalse();
    assertThat(race.successCount).isEqualTo(1);
    assertThat(race.conflictCount).isEqualTo(1);

    HttpResponse<String> replayRes = sendConfirmRequest(proposalId);
    assertThat(replayRes.statusCode()).isEqualTo(409);

    long targetServiceCount =
        queryCount(
            "SELECT count(*) FROM booking_services WHERE booking_id = ? AND service_option_id = ?",
            fixture.bookingId,
            ref.targetServiceOptionId);
    assertThat(targetServiceCount)
        .as("Reconciliation: Target service selection count must be 1")
        .isEqualTo(1L);
    assertThat(
            queryCount(
                "SELECT count(*) FROM booking_services WHERE booking_id = ?"
                    + " AND service_option_id = ? AND quantity = ?",
                fixture.bookingId,
                ref.targetServiceOptionId,
                ref.targetServiceQuantity))
        .as("Reconciliation: Target service quantity must match the confirmed proposal")
        .isEqualTo(1L);
    long sourceServiceCount =
        queryCount(
            "SELECT count(*) FROM booking_services WHERE booking_id = ? AND service_option_id = ?",
            fixture.bookingId,
            ref.sourceServiceOptionId);
    assertThat(sourceServiceCount)
        .as("Reconciliation: Source service selection must be removed")
        .isZero();
  }

  @Test
  void validateBookingPaymentMutationFlow() throws Exception {
    ReferenceData ref = discoverFutureReferenceData();
    if (ref == null) {
      throw new IllegalStateException("Required future reference data not found in staging DB");
    }

    BookingFixture fixture = createBookingFixture(ref, "PENDING_PAYMENT", false, false, false);

    long initialPaymentCount =
        queryCount("SELECT count(*) FROM payments WHERE booking_id = ?", fixture.bookingId);
    assertThat(initialPaymentCount).as("Initial payment count must be 0").isEqualTo(0L);

    String chatPrompt =
        "T\u00F4i mu\u1ED1n thanh to\u00E1n; passenger last name "
            + fixture.passengerLastName
            + "; PNR "
            + fixture.pnrCode
            + " b\u1EB1ng ph\u01B0\u01A1ng th\u1EE9c PAYOS";
    UUID proposalId = executePromptAndExtractProposalId(chatPrompt, "create_booking_payment");
    assertThat(proposalId).as("Proposal ID should be created for payment prompt").isNotNull();

    ConfirmationRaceResult race = runConcurrentConfirmations(proposalId);
    assertThat(race.hasServerError).isFalse();
    assertThat(race.successCount).isEqualTo(1);
    assertThat(race.conflictCount).isEqualTo(1);

    HttpResponse<String> replayRes = sendConfirmRequest(proposalId);
    assertThat(replayRes.statusCode()).isEqualTo(409);

    long paymentCount =
        queryCount("SELECT count(*) FROM payments WHERE booking_id = ?", fixture.bookingId);
    assertThat(paymentCount).as("Reconciliation: Payment count must be 1").isEqualTo(1L);
    assertThat(
            queryString(
                "SELECT payment_status FROM payments WHERE booking_id = ?", fixture.bookingId))
        .as("Reconciliation: Checkout must remain unpaid")
        .isIn("PENDING", "SUSPENDED");
    assertThat(
            queryString("SELECT request_key FROM payments WHERE booking_id = ?", fixture.bookingId))
        .as("Reconciliation: Payment must carry the proposal idempotency key")
        .isNotBlank();
    assertThat(
            queryString(
                "SELECT checkout_url FROM payments WHERE booking_id = ?", fixture.bookingId))
        .as("Reconciliation: Payment provider must return a checkout URL")
        .isNotBlank();
  }

  private void provisionTestUser() throws Exception {
    String registerPayload =
        objectMapper.writeValueAsString(
            Map.of(
                "email", testEmail,
                "password", "StagingTestPass123!",
                "firstName", "Agent",
                "lastName", "E2E",
                "acceptTerms", true));

    HttpRequest regRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/auth/register"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(registerPayload))
            .build();

    HttpResponse<String> regResponse =
        httpClient.send(regRequest, HttpResponse.BodyHandlers.ofString());
    if (regResponse.statusCode() != 200 && regResponse.statusCode() != 201) {
      throw new IllegalStateException(
          "Registration via public API failed ("
              + regResponse.statusCode()
              + "): "
              + sanitizeError(regResponse.body()));
    }

    JsonNode regJson = objectMapper.readTree(regResponse.body());
    JsonNode userIdNode = regJson.at("/data/user/id");
    if (!userIdNode.isMissingNode() && !userIdNode.isNull()) {
      testUserId = userIdNode.asLong();
    } else {
      testUserId = queryLong("SELECT id FROM users WHERE email = ?", testEmail);
    }
    if (testUserId == null) {
      throw new IllegalStateException(
          "Test user ID could not be resolved after registration for email " + testEmail);
    }
    addToLedger("users", testUserId);

    String loginPayload =
        objectMapper.writeValueAsString(
            Map.of("email", testEmail, "password", "StagingTestPass123!"));
    HttpRequest loginReq =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/auth/login"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
            .build();
    HttpResponse<String> loginRes = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());

    if (loginRes.statusCode() == 200) {
      JsonNode loginJson = objectMapper.readTree(loginRes.body());
      JsonNode tokenNode = loginJson.at("/data/accessToken");
      if (!tokenNode.isMissingNode() && !tokenNode.isNull()) {
        authToken = tokenNode.asText();
      }
    }
    if (authToken == null || authToken.isBlank()) {
      throw new IllegalStateException("Auth token must be non-blank after login");
    }
  }

  private record ReferenceData(
      Long flightId,
      Long flightFareId,
      Long fareClassId,
      Long initialSeatId,
      String initialSeatNumber,
      Long targetSeatId,
      String targetSeatNumber,
      Long sourceServiceId,
      Long sourceServiceOptionId,
      String sourceServiceOptionName,
      Integer sourceServiceQuantity,
      BigDecimal sourceServiceTotal,
      Long targetServiceOptionId,
      String targetServiceOptionName,
      Integer targetServiceQuantity) {}

  private ReferenceData discoverCheckInReferenceData() throws Exception {
    String sql =
        "SELECT f.id, ff.id as fare_id, ff.fare_class_id "
            + "FROM flights f "
            + "JOIN flight_fares ff ON ff.flight_id = f.id "
            + "WHERE f.status = 'SCHEDULED' "
            + "AND f.departure_time >= (now() + interval '2 hours') "
            + "AND f.departure_time <= (now() + interval '24 hours') "
            + "AND 2 <= (SELECT count(*) FROM flight_seats fs "
            + "WHERE fs.flight_id = f.id AND fs.status = 'AVAILABLE' "
            + "AND EXISTS (SELECT 1 FROM fare_class_seat_classes allowed "
            + "WHERE allowed.fare_class_id = ff.fare_class_id "
            + "AND allowed.seat_class_id = fs.seat_class_id)) "
            + "ORDER BY f.departure_time ASC LIMIT 1";
    ReferenceData referenceData = executeReferenceQuery(sql);
    if (referenceData != null) {
      return referenceData;
    }
    moveUnusedReferenceFlight(OffsetDateTime.now().plusHours(12));
    return executeReferenceQuery(sql);
  }

  private ReferenceData discoverFutureReferenceData() throws Exception {
    String sql =
        "SELECT f.id, ff.id as fare_id, ff.fare_class_id "
            + "FROM flights f "
            + "JOIN flight_fares ff ON ff.flight_id = f.id "
            + "WHERE f.status = 'SCHEDULED' "
            + "AND f.departure_time > (now() + interval '24 hours') "
            + "AND 2 <= (SELECT count(*) FROM flight_seats fs "
            + "WHERE fs.flight_id = f.id AND fs.status = 'AVAILABLE' "
            + "AND EXISTS (SELECT 1 FROM fare_class_seat_classes allowed "
            + "WHERE allowed.fare_class_id = ff.fare_class_id "
            + "AND allowed.seat_class_id = fs.seat_class_id)) "
            + "ORDER BY f.departure_time ASC LIMIT 1";
    ReferenceData referenceData = executeReferenceQuery(sql);
    if (referenceData != null) {
      return referenceData;
    }
    moveUnusedReferenceFlight(OffsetDateTime.now().plusHours(72));
    return executeReferenceQuery(sql);
  }

  private void moveUnusedReferenceFlight(OffsetDateTime fixtureDepartureTime) throws Exception {
    String sql =
        "WITH candidate AS ("
            + " SELECT f.id, f.status, f.departure_time, f.arrival_time, f.updated_at"
            + " FROM flights f"
            + " JOIN flight_fares ff ON ff.flight_id = f.id"
            + " WHERE f.status IN ('SCHEDULED', 'COMPLETED')"
            + " AND f.departure_time <= now()"
            + " AND 2 <= (SELECT count(*) FROM flight_seats fs"
            + " WHERE fs.flight_id = f.id AND fs.status = 'AVAILABLE'"
            + " AND EXISTS (SELECT 1 FROM fare_class_seat_classes allowed"
            + " WHERE allowed.fare_class_id = ff.fare_class_id"
            + " AND allowed.seat_class_id = fs.seat_class_id))"
            + " AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.flight_id = f.id)"
            + " ORDER BY f.departure_time DESC LIMIT 1"
            + "), updated AS ("
            + " UPDATE flights f SET status = 'SCHEDULED', departure_time = ?,"
            + " arrival_time = ? + (candidate.arrival_time - candidate.departure_time),"
            + " updated_at = now() FROM candidate"
            + " WHERE f.id = candidate.id"
            + " AND f.departure_time = candidate.departure_time"
            + " AND f.arrival_time = candidate.arrival_time"
            + " AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.flight_id = f.id)"
            + " RETURNING f.id, candidate.departure_time AS original_departure_time,"
            + " candidate.status AS original_status,"
            + " candidate.arrival_time AS original_arrival_time,"
            + " candidate.updated_at AS original_updated_at,"
            + " f.departure_time AS fixture_departure_time,"
            + " f.arrival_time AS fixture_arrival_time"
            + ") SELECT * FROM updated";
    try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
      stmt.setObject(1, fixtureDepartureTime);
      stmt.setObject(2, fixtureDepartureTime);
      ResultSet rs = stmt.executeQuery();
      if (!rs.next()) {
        createFixtureReferenceFlight(fixtureDepartureTime);
        return;
      }
      flightScheduleBackups.add(
          new FlightScheduleBackup(
              rs.getLong("id"),
              rs.getString("original_status"),
              rs.getObject("original_departure_time", OffsetDateTime.class),
              rs.getObject("original_arrival_time", OffsetDateTime.class),
              rs.getObject("original_updated_at", OffsetDateTime.class),
              rs.getObject("fixture_departure_time", OffsetDateTime.class),
              rs.getObject("fixture_arrival_time", OffsetDateTime.class)));
    }
  }

  private void createFixtureReferenceFlight(OffsetDateTime fixtureDepartureTime) throws Exception {
    Long templateFlightId = findTemplateFlightId();
    if (templateFlightId == null) {
      throw new IllegalStateException(referenceFlightDiagnostics());
    }

    Long fixtureFlightId;
    String sqlFlight =
        "INSERT INTO flights (airline_id, flight_number, flight_date, aircraft_id,"
            + " departure_airport_id, arrival_airport_id, departure_time, arrival_time,"
            + " departure_terminal, departure_gate, arrival_terminal, arrival_gate, status,"
            + " flight_source, created_at, updated_at) SELECT airline_id, ?, ?, aircraft_id,"
            + " departure_airport_id, arrival_airport_id, ?, ? + (arrival_time - departure_time),"
            + " departure_terminal, departure_gate, arrival_terminal, arrival_gate, 'SCHEDULED',"
            + " 'MOCK', now(), now() FROM flights WHERE id = ? RETURNING id";
    try (PreparedStatement stmt = dbConnection.prepareStatement(sqlFlight)) {
      stmt.setString(1, "E2E" + runId.toUpperCase());
      stmt.setObject(2, fixtureDepartureTime.toLocalDate());
      stmt.setObject(3, fixtureDepartureTime);
      stmt.setObject(4, fixtureDepartureTime);
      stmt.setLong(5, templateFlightId);
      ResultSet rs = stmt.executeQuery();
      if (!rs.next()) {
        throw new IllegalStateException("Failed to clone fixture-owned reference flight");
      }
      fixtureFlightId = rs.getLong("id");
      addToLedger("flights", fixtureFlightId);
    }

    cloneFlightFares(templateFlightId, fixtureFlightId);
    cloneFlightSeats(templateFlightId, fixtureFlightId);
  }

  private Long findTemplateFlightId() throws Exception {
    String sql =
        "SELECT f.id FROM flights f JOIN flight_fares ff ON ff.flight_id = f.id"
            + " WHERE 2 <= (SELECT count(*) FROM flight_seats fs"
            + " WHERE fs.flight_id = f.id AND fs.status = 'AVAILABLE'"
            + " AND EXISTS (SELECT 1 FROM fare_class_seat_classes allowed"
            + " WHERE allowed.fare_class_id = ff.fare_class_id"
            + " AND allowed.seat_class_id = fs.seat_class_id))"
            + " ORDER BY f.departure_time DESC LIMIT 1";
    try (Statement stmt = dbConnection.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {
      return rs.next() ? rs.getLong("id") : null;
    }
  }

  private void cloneFlightFares(Long templateFlightId, Long fixtureFlightId) throws Exception {
    String sql =
        "INSERT INTO flight_fares (flight_id, fare_class_id, base_price, tax_breakdown,"
            + " total_tax_amount, total_price, status, updated_at)"
            + " SELECT ?, fare_class_id, base_price, tax_breakdown, total_tax_amount, total_price,"
            + " status, now() FROM flight_fares WHERE flight_id = ? RETURNING id";
    try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
      stmt.setLong(1, fixtureFlightId);
      stmt.setLong(2, templateFlightId);
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        addToLedger("flight_fares", rs.getLong("id"));
      }
    }
  }

  private void cloneFlightSeats(Long templateFlightId, Long fixtureFlightId) throws Exception {
    String sql =
        "INSERT INTO flight_seats (flight_id, status, fare_class_id, booking_id, reserved_until,"
            + " created_at, seat_number, seat_class_id, seat_type_id, seat_category_id)"
            + " SELECT ?, 'AVAILABLE', fare_class_id, null, null, now(), seat_number,"
            + " seat_class_id, seat_type_id, seat_category_id FROM flight_seats"
            + " WHERE flight_id = ? RETURNING id";
    try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
      stmt.setLong(1, fixtureFlightId);
      stmt.setLong(2, templateFlightId);
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        addToLedger("flight_seats", rs.getLong("id"));
      }
    }
  }

  private String referenceFlightDiagnostics() throws Exception {
    long flights = queryCount("SELECT count(*) FROM flights");
    long past =
        queryCount(
            "SELECT count(*) FROM flights f WHERE f.status IN ('SCHEDULED', 'COMPLETED')"
                + " AND f.departure_time <= now()");
    long withFare =
        queryCount(
            "SELECT count(DISTINCT f.id) FROM flights f JOIN flight_fares ff"
                + " ON ff.flight_id = f.id WHERE f.status IN ('SCHEDULED', 'COMPLETED')"
                + " AND f.departure_time <= now()");
    long withAvailableSeats =
        queryCount(
            "SELECT count(*) FROM flights f WHERE f.status IN ('SCHEDULED', 'COMPLETED')"
                + " AND f.departure_time <= now() AND 2 <= (SELECT count(*)"
                + " FROM flight_seats fs WHERE fs.flight_id = f.id"
                + " AND fs.status = 'AVAILABLE')");
    long withoutTickets =
        queryCount(
            "SELECT count(*) FROM flights f WHERE f.status IN ('SCHEDULED', 'COMPLETED')"
                + " AND f.departure_time <= now() AND NOT EXISTS"
                + " (SELECT 1 FROM tickets t WHERE t.flight_id = f.id)");
    return "No safe reference flight found: flights="
        + flights
        + ", past="
        + past
        + ", withFare="
        + withFare
        + ", withAvailableSeats="
        + withAvailableSeats
        + ", withoutTickets="
        + withoutTickets;
  }

  private ReferenceData executeReferenceQuery(String sqlFlight) throws Exception {
    Long flightId = null;
    Long flightFareId = null;
    Long fareClassId = null;

    try (Statement stmt = dbConnection.createStatement();
        ResultSet rs = stmt.executeQuery(sqlFlight)) {
      if (rs.next()) {
        flightId = rs.getLong("id");
        flightFareId = rs.getLong("fare_id");
        fareClassId = rs.getLong("fare_class_id");
      }
    }
    if (flightId == null) {
      return null;
    }

    Long initialSeatId = null;
    String initialSeatNumber = null;
    Long targetSeatId = null;
    String targetSeatNumber = null;

    String sqlSeat =
        "WITH eligible AS (SELECT fs.id, fs.seat_number, fs.status, fs.booking_id,"
            + " fs.reserved_until, CASE WHEN fc.seat_selection_policy = 'INCLUDED'"
            + " AND included_cat.pricing_rank IS NOT NULL"
            + " AND sc.pricing_rank <= included_cat.pricing_rank THEN 0::numeric"
            + " ELSE coalesce(sc.surcharge_amount, 0) + coalesce(st.surcharge_amount, 0) END"
            + " AS effective_surcharge FROM flight_seats fs"
            + " JOIN fare_classes fc ON fc.id = ?"
            + " LEFT JOIN seat_categories included_cat ON included_cat.id ="
            + " fc.included_seat_category_id"
            + " JOIN seat_categories sc ON sc.id = fs.seat_category_id"
            + " JOIN seat_types st ON st.id = fs.seat_type_id"
            + " WHERE fs.flight_id = ? AND fs.status = 'AVAILABLE'"
            + " AND EXISTS (SELECT 1 FROM fare_class_seat_classes allowed"
            + " WHERE allowed.fare_class_id = fc.id"
            + " AND allowed.seat_class_id = fs.seat_class_id))"
            + " SELECT id, seat_number, status, booking_id, reserved_until FROM eligible"
            + " WHERE effective_surcharge = (SELECT effective_surcharge FROM eligible"
            + " GROUP BY effective_surcharge HAVING count(*) >= 2"
            + " ORDER BY effective_surcharge LIMIT 1) ORDER BY id LIMIT 2";
    try (PreparedStatement stmt = dbConnection.prepareStatement(sqlSeat)) {
      stmt.setLong(1, fareClassId);
      stmt.setLong(2, flightId);
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        initialSeatId = rs.getLong("id");
        initialSeatNumber = rs.getString("seat_number");
        backupSeat(
            initialSeatId,
            rs.getString("status"),
            rs.getObject("booking_id", Long.class),
            rs.getObject("reserved_until", OffsetDateTime.class));
      }
      if (rs.next()) {
        targetSeatId = rs.getLong("id");
        targetSeatNumber = rs.getString("seat_number");
        backupSeat(
            targetSeatId,
            rs.getString("status"),
            rs.getObject("booking_id", Long.class),
            rs.getObject("reserved_until", OffsetDateTime.class));
      }
    }

    Long sourceServiceId = null;
    Long sourceOptionId = null;
    String sourceOptionName = null;
    Integer sourceOptionQuantity = null;
    BigDecimal sourceOptionTotal = null;
    Long targetOptionId = null;
    String targetOptionName = null;
    Integer targetOptionQuantity = null;

    String sqlService =
        "SELECT source.service_id, source.id AS source_id, source.name AS source_name,"
            + " source.price AS source_price, target.id AS target_id, target.name AS target_name,"
            + " target.price AS target_price FROM service_options source"
            + " JOIN service_options target ON target.service_id = source.service_id"
            + " AND target.id > source.id AND target.is_active = true"
            + " WHERE source.is_active = true AND source.price > 0 AND target.price > 0"
            + " ORDER BY source.service_id, source.id, target.id";
    try (Statement stmt = dbConnection.createStatement();
        ResultSet rs = stmt.executeQuery(sqlService)) {
      while (rs.next()) {
        long sourceMinor = rs.getBigDecimal("source_price").movePointRight(2).longValueExact();
        long targetMinor = rs.getBigDecimal("target_price").movePointRight(2).longValueExact();
        long divisor = greatestCommonDivisor(sourceMinor, targetMinor);
        int candidateSourceQuantity = Math.toIntExact(targetMinor / divisor);
        int candidateTargetQuantity = Math.toIntExact(sourceMinor / divisor);
        if (candidateSourceQuantity > 20 || candidateTargetQuantity > 20) {
          continue;
        }
        sourceServiceId = rs.getLong("service_id");
        sourceOptionId = rs.getLong("source_id");
        sourceOptionName = rs.getString("source_name");
        targetOptionId = rs.getLong("target_id");
        targetOptionName = rs.getString("target_name");
        sourceOptionQuantity = candidateSourceQuantity;
        targetOptionQuantity = candidateTargetQuantity;
        sourceOptionTotal =
            rs.getBigDecimal("source_price").multiply(BigDecimal.valueOf(sourceOptionQuantity));
        break;
      }
    }

    return new ReferenceData(
        flightId,
        flightFareId,
        fareClassId,
        initialSeatId,
        initialSeatNumber,
        targetSeatId,
        targetSeatNumber,
        sourceServiceId,
        sourceOptionId,
        sourceOptionName,
        sourceOptionQuantity,
        sourceOptionTotal,
        targetOptionId,
        targetOptionName,
        targetOptionQuantity);
  }

  private long greatestCommonDivisor(long left, long right) {
    long a = Math.abs(left);
    long b = Math.abs(right);
    while (b != 0) {
      long remainder = a % b;
      a = b;
      b = remainder;
    }
    return a;
  }

  private void backupSeat(
      Long seatId, String status, Long bookingId, OffsetDateTime reservedUntil) {
    seatBackups.add(new SeatStateBackup(seatId, status, bookingId, reservedUntil));
  }

  private record BookingFixture(
      Long bookingId,
      String pnrCode,
      Long passengerId,
      String passengerFirstName,
      String passengerLastName,
      Long ticketId) {}

  private BookingFixture createBookingFixture(
      ReferenceData ref,
      String bookingStatus,
      Boolean withPayment,
      Boolean withTicketSnapshot,
      Boolean withService)
      throws Exception {
    String pnrCode = "E2E" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
    OffsetDateTime now = OffsetDateTime.now();
    String firstName = "Staging";
    String lastName = "User" + runId;
    BigDecimal bookingTotal = BigDecimal.valueOf(1_000_000L);
    if (Boolean.TRUE.equals(withService) && ref.sourceServiceTotal != null) {
      bookingTotal = bookingTotal.add(ref.sourceServiceTotal);
    }

    Long bookingId;
    String sqlBooking =
        "INSERT INTO bookings (user_id, pnr_code, contact_email, contact_phone, status,"
            + " total_amount, currency, pnr_expires_at, discount_amount, created_at, updated_at)"
            + " VALUES (?, ?, ?, '+84900000000', ?, ?, 'VND', ?, 0.00, ?, ?)"
            + " RETURNING id";
    try (PreparedStatement stmt = dbConnection.prepareStatement(sqlBooking)) {
      stmt.setObject(1, testUserId);
      stmt.setString(2, pnrCode);
      stmt.setString(3, testEmail);
      stmt.setString(4, bookingStatus);
      stmt.setBigDecimal(5, bookingTotal);
      stmt.setObject(6, now.plusMinutes(30));
      stmt.setObject(7, now);
      stmt.setObject(8, now);
      ResultSet rs = stmt.executeQuery();
      rs.next();
      bookingId = rs.getLong(1);
      fixtureBookingIds.add(bookingId);
      addToLedger("bookings", bookingId);
    }

    Long passengerId;
    String sqlPassenger =
        "INSERT INTO passengers (booking_id, first_name, last_name, date_of_birth, gender,"
            + " nationality, passenger_type, identify_type, identify_number, created_at,"
            + " updated_at) VALUES (?, ?, ?, DATE '1990-01-01', 'OTHER', 'VN', 'ADULT',"
            + " 'PASSPORT', ?, ?, ?) RETURNING id";
    try (PreparedStatement stmt = dbConnection.prepareStatement(sqlPassenger)) {
      stmt.setLong(1, bookingId);
      stmt.setString(2, firstName);
      stmt.setString(3, lastName);
      stmt.setString(4, "E2E" + runId);
      stmt.setObject(5, now);
      stmt.setObject(6, now);
      ResultSet rs = stmt.executeQuery();
      rs.next();
      passengerId = rs.getLong(1);
      addToLedger("passengers", passengerId);
    }

    Long fareHoldId;
    String sqlHold =
        "INSERT INTO booking_fare_holds (booking_id, flight_fare_id, quantity, status, created_at,"
            + " updated_at) VALUES (?, ?, 1, 'CONFIRMED', ?, ?) RETURNING id";
    try (PreparedStatement stmt = dbConnection.prepareStatement(sqlHold)) {
      stmt.setLong(1, bookingId);
      stmt.setLong(2, ref.flightFareId);
      stmt.setObject(3, now);
      stmt.setObject(4, now);
      ResultSet rs = stmt.executeQuery();
      rs.next();
      fareHoldId = rs.getLong(1);
      addToLedger("booking_fare_holds", fareHoldId);
    }

    Long ticketId = null;
    if ("CONFIRMED".equals(bookingStatus)) {
      String sqlTicket =
          "INSERT INTO tickets (booking_id, flight_fare_id, passenger_id, flight_id,"
              + " flight_seat_id, fare_class_id, ticket_number, status, issued_at, created_at,"
              + " updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'ISSUED', ?, ?, ?) RETURNING id";
      try (PreparedStatement stmt = dbConnection.prepareStatement(sqlTicket)) {
        stmt.setLong(1, bookingId);
        stmt.setLong(2, ref.flightFareId);
        stmt.setLong(3, passengerId);
        stmt.setLong(4, ref.flightId);
        if (ref.initialSeatId == null) {
          stmt.setNull(5, Types.BIGINT);
        } else {
          stmt.setLong(5, ref.initialSeatId);
        }
        stmt.setLong(6, ref.fareClassId);
        stmt.setString(7, "738-E2E" + runId);
        stmt.setObject(8, now);
        stmt.setObject(9, now);
        stmt.setObject(10, now);
        ResultSet rs = stmt.executeQuery();
        rs.next();
        ticketId = rs.getLong(1);
        addToLedger("tickets", ticketId);
      }
    }

    if (ref.initialSeatId != null) {
      String assignmentStatus = "CONFIRMED".equals(bookingStatus) ? "BOOKED" : "RESERVED";
      String sqlAssign =
          "INSERT INTO passenger_seat_assignments (booking_id, passenger_id, flight_seat_id,"
              + " flight_id, status, surcharge_amount, currency, created_at, updated_at)"
              + " VALUES (?, ?, ?, ?, ?, 0.00, 'VND', ?, ?) RETURNING id";
      try (PreparedStatement stmt = dbConnection.prepareStatement(sqlAssign)) {
        stmt.setLong(1, bookingId);
        stmt.setLong(2, passengerId);
        stmt.setLong(3, ref.initialSeatId);
        stmt.setLong(4, ref.flightId);
        stmt.setString(5, assignmentStatus);
        stmt.setObject(6, now);
        stmt.setObject(7, now);
        ResultSet rs = stmt.executeQuery();
        rs.next();
        addToLedger("passenger_seat_assignments", rs.getLong(1));
      }

      String sqlUpdateSeat =
          "UPDATE flight_seats SET status = ?, booking_id = ?, reserved_until = ?"
              + " WHERE id = ? AND status = 'AVAILABLE'";
      try (PreparedStatement stmt = dbConnection.prepareStatement(sqlUpdateSeat)) {
        stmt.setString(1, assignmentStatus);
        stmt.setLong(2, bookingId);
        if ("RESERVED".equals(assignmentStatus)) {
          stmt.setObject(3, now.plusMinutes(30));
        } else {
          stmt.setNull(3, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        stmt.setLong(4, ref.initialSeatId);
        if (stmt.executeUpdate() != 1) {
          throw new IllegalStateException("Fixture seat was claimed concurrently");
        }
      }
    }

    if (Boolean.TRUE.equals(withPayment)) {
      String sqlPayment =
          "INSERT INTO payments (booking_id, amount, currency, payment_method, payment_status,"
              + " created_at, updated_at) VALUES (?, ?, 'VND', 'PAYOS', 'SUCCESS', ?, ?)"
              + " RETURNING id";
      try (PreparedStatement stmt = dbConnection.prepareStatement(sqlPayment)) {
        stmt.setLong(1, bookingId);
        stmt.setBigDecimal(2, bookingTotal);
        stmt.setObject(3, now);
        stmt.setObject(4, now);
        ResultSet rs = stmt.executeQuery();
        rs.next();
        addToLedger("payments", rs.getLong(1));
      }
    }

    if (Boolean.TRUE.equals(withTicketSnapshot) && ticketId != null) {
      String sqlSnap =
          "INSERT INTO ticket_financial_snapshots (booking_id, ticket_id, passenger_id,"
              + " fare_gross_amount, promo_allocated_amount, fare_net_amount, seat_refund_amount,"
              + " service_refund_amount, refund_fee_amount, is_refundable, passenger_type,"
              + " currency, created_at, updated_at) VALUES (?, ?, ?, 500000.00, 0.00, 500000.00,"
              + " 0.00, 0.00, 500000.00, true, 'ADULT', 'VND', ?, ?) RETURNING id";
      try (PreparedStatement stmt = dbConnection.prepareStatement(sqlSnap)) {
        stmt.setLong(1, bookingId);
        stmt.setLong(2, ticketId);
        stmt.setLong(3, passengerId);
        stmt.setObject(4, now);
        stmt.setObject(5, now);
        ResultSet rs = stmt.executeQuery();
        rs.next();
        addToLedger("ticket_financial_snapshots", rs.getLong(1));
      }
    }

    if (Boolean.TRUE.equals(withService) && ref.sourceServiceOptionId != null) {
      String sqlService =
          "INSERT INTO booking_services (booking_id, passenger_id, service_id, service_option_id,"
              + " quantity, price, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
              + " RETURNING id";
      try (PreparedStatement stmt = dbConnection.prepareStatement(sqlService)) {
        stmt.setLong(1, bookingId);
        stmt.setLong(2, passengerId);
        stmt.setLong(3, ref.sourceServiceId);
        stmt.setLong(4, ref.sourceServiceOptionId);
        stmt.setInt(5, ref.sourceServiceQuantity);
        stmt.setBigDecimal(6, ref.sourceServiceTotal);
        stmt.setObject(7, now);
        stmt.setObject(8, now);
        ResultSet rs = stmt.executeQuery();
        rs.next();
        addToLedger("booking_services", rs.getLong(1));
      }
    }

    return new BookingFixture(bookingId, pnrCode, passengerId, firstName, lastName, ticketId);
  }

  private UUID executePromptAndExtractProposalId(String prompt, String expectedTool)
      throws Exception {
    JsonNode lastResponse = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      String cooldown = System.getenv("AGENT_STAGING_MODEL_COOLDOWN_SECONDS");
      if (cooldown != null) {
        TimeUnit.SECONDS.sleep(Long.parseLong(cooldown));
      }
      String body =
          objectMapper.writeValueAsString(
              Map.of("message", prompt, "clientMessageId", UUID.randomUUID().toString()));

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/api/chatbot/messages"))
              .timeout(Duration.ofSeconds(90))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body));

      if (authToken != null) {
        builder.header("Authorization", "Bearer " + authToken);
      }

      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);

      JsonNode json = objectMapper.readTree(response.body());
      lastResponse = json;
      JsonNode proposalNode = json.at("/data/proposal");
      if (proposalNode.isMissingNode() || proposalNode.isNull()) {
        proposalNode = json.at("/proposal");
      }
      if (!proposalNode.isMissingNode() && !proposalNode.isNull() && proposalNode.has("id")) {
        assertThat(proposalNode.path("tool").asText())
            .as("LLM must select the expected mutation tool")
            .isEqualTo(expectedTool);
        return UUID.fromString(proposalNode.get("id").asText());
      }
      if (attempt == 2 || !isRetryableModelFallback(json)) {
        break;
      }
    }

    assertThat(false)
        .as(
            "Expected proposal for tool %s; response summary: %s",
            expectedTool, summarizeChatResponse(lastResponse))
        .isTrue();
    throw new IllegalStateException("Unreachable proposal assertion");
  }

  private boolean isRetryableModelFallback(JsonNode json) {
    JsonNode payload = json.has("data") ? json.path("data") : json;
    return Set.of(
            "TOOL_CALL_BUDGET_EXCEEDED", "AI_PROVIDER_RATE_LIMITED", "AI_PROVIDER_UNAVAILABLE")
        .contains(payload.path("fallbackReason").asText());
  }

  private String summarizeChatResponse(JsonNode json) {
    JsonNode payload = json.has("data") ? json.path("data") : json;
    return "answerType="
        + payload.path("answerType").asText("<missing>")
        + ", fallbackReason="
        + payload.path("fallbackReason").asText("<none>")
        + ", answer="
        + truncate(payload.path("answer").asText("<missing>"), 500);
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "...";
  }

  private record ConfirmationRaceResult(
      int successCount, int conflictCount, boolean hasServerError) {}

  private ConfirmationRaceResult runConcurrentConfirmations(UUID proposalId) throws Exception {
    CountDownLatch barrier = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);

    Callable<HttpResponse<String>> confirmTask =
        () -> {
          barrier.await(5, TimeUnit.SECONDS);
          return sendConfirmRequest(proposalId);
        };

    Future<HttpResponse<String>> future1 = pool.submit(confirmTask);
    Future<HttpResponse<String>> future2 = pool.submit(confirmTask);

    barrier.countDown();

    HttpResponse<String> res1;
    HttpResponse<String> res2;
    try {
      res1 = future1.get(60, TimeUnit.SECONDS);
      res2 = future2.get(60, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }

    int success = 0;
    int conflict = 0;
    boolean serverError = res1.statusCode() >= 500 || res2.statusCode() >= 500;

    if (isExecuted(res1)) {
      success++;
    } else if (res1.statusCode() == 409 || res1.statusCode() == 429) {
      conflict++;
    }

    if (isExecuted(res2)) {
      success++;
    } else if (res2.statusCode() == 409 || res2.statusCode() == 429) {
      conflict++;
    }

    assertThat(queryString("SELECT status FROM ai_tool_proposals WHERE id = ?", proposalId))
        .as(
            "Proposal must reach the terminal executed state; HTTP statuses=%s/%s; safeError=%s;"
                + " responses=%s/%s",
            res1.statusCode(),
            res2.statusCode(),
            queryString("SELECT safe_error FROM ai_tool_proposals WHERE id = ?", proposalId),
            summarizeHttpResponse(res1),
            summarizeHttpResponse(res2))
        .isEqualTo("EXECUTED");
    assertThat(
            queryCount(
                "SELECT count(*) FROM ai_tool_audit_events WHERE proposal_id = ?"
                    + " AND event_type = 'EXECUTED'",
                proposalId))
        .as("Exactly one executed audit event must be recorded")
        .isEqualTo(1L);

    return new ConfirmationRaceResult(success, conflict, serverError);
  }

  private boolean isExecuted(HttpResponse<String> response) {
    if (response.statusCode() != 200) {
      return false;
    }
    try {
      JsonNode json = objectMapper.readTree(response.body());
      JsonNode status = json.at("/data/status");
      if (status.isMissingNode()) {
        status = json.at("/status");
      }
      return "EXECUTED".equals(status.asText());
    } catch (Exception exception) {
      return false;
    }
  }

  private String summarizeHttpResponse(HttpResponse<String> response) {
    return response.statusCode() + ":" + truncate(response.body(), 600);
  }

  private HttpResponse<String> sendConfirmRequest(UUID proposalId) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/chatbot/proposals/" + proposalId + "/confirm"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody());

    if (authToken != null) {
      builder.header("Authorization", "Bearer " + authToken);
    }

    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private void discoverUserCreatedEntities() throws Exception {
    if (testUserId == null) {
      return;
    }

    List<UUID> convIds =
        queryUuidList("SELECT id FROM ai_chat_conversations WHERE user_id = ?", testUserId);
    for (UUID convId : convIds) {
      List<UUID> turnIds =
          queryUuidList("SELECT id FROM ai_chat_turns WHERE conversation_id = ?", convId);
      for (UUID turnId : turnIds) {
        List<Long> msgIds =
            queryLongList("SELECT id FROM ai_chat_messages WHERE turn_id = ?", turnId);
        for (Long msgId : msgIds) {
          addToLedger("ai_chat_messages", msgId);
        }
        addToLedger("ai_chat_turns", turnId);
      }
      addToLedger("ai_chat_conversations", convId);
    }

    List<UUID> propIds =
        queryUuidList("SELECT id FROM ai_tool_proposals WHERE user_id = ?", testUserId);
    for (UUID propId : propIds) {
      List<Long> auditIds =
          queryLongList("SELECT id FROM ai_tool_audit_events WHERE proposal_id = ?", propId);
      for (Long auditId : auditIds) {
        addToLedger("ai_tool_audit_events", auditId);
      }
      addToLedger("ai_tool_proposals", propId);
    }
    addLongRows(
        "refresh_token_sessions",
        "SELECT id FROM refresh_token_sessions WHERE user_id = ?",
        testUserId);
    addLongRows(
        "password_reset_tokens",
        "SELECT id FROM password_reset_tokens WHERE user_id = ?",
        testUserId);
  }

  private void discoverFixtureCreatedEntities() throws Exception {
    for (Long bookingId : fixtureBookingIds) {
      addLongRows("payments", "SELECT id FROM payments WHERE booking_id = ?", bookingId);
      addLongRows("tickets", "SELECT id FROM tickets WHERE booking_id = ?", bookingId);
      addLongRows(
          "ticket_financial_snapshots",
          "SELECT id FROM ticket_financial_snapshots WHERE booking_id = ?",
          bookingId);
      addLongRows(
          "boarding_passes",
          "SELECT bp.id FROM boarding_passes bp JOIN tickets t ON t.id = bp.ticket_id"
              + " WHERE t.booking_id = ?",
          bookingId);

      List<Long> cancellationIds =
          queryLongList("SELECT id FROM booking_cancellations WHERE booking_id = ?", bookingId);
      for (Long cancellationId : cancellationIds) {
        addToLedger("booking_cancellations", cancellationId);
        addLongRows(
            "booking_cancellation_items",
            "SELECT id FROM booking_cancellation_items WHERE cancellation_id = ?",
            cancellationId);
      }

      List<Long> seatSettlementIds =
          queryLongList("SELECT id FROM seat_change_settlements WHERE booking_id = ?", bookingId);
      for (Long settlementId : seatSettlementIds) {
        addToLedger("seat_change_settlements", settlementId);
        addLongRows(
            "seat_change_settlement_items",
            "SELECT id FROM seat_change_settlement_items WHERE settlement_id = ?",
            settlementId);
      }

      List<Long> serviceSettlementIds =
          queryLongList(
              "SELECT id FROM service_change_settlements WHERE booking_id = ?", bookingId);
      for (Long settlementId : serviceSettlementIds) {
        addToLedger("service_change_settlements", settlementId);
        addLongRows(
            "service_change_settlement_items",
            "SELECT id FROM service_change_settlement_items WHERE settlement_id = ?",
            settlementId);
      }

      addLongRows(
          "booking_services", "SELECT id FROM booking_services WHERE booking_id = ?", bookingId);
      addLongRows(
          "passenger_seat_assignments",
          "SELECT id FROM passenger_seat_assignments WHERE booking_id = ?",
          bookingId);
    }
  }

  private void addLongRows(String tableName, String sql, Object parameter) throws Exception {
    for (Long id : queryLongList(sql, parameter)) {
      addToLedger(tableName, id);
    }
  }

  private void cleanupLedgerAndSeats(List<String> errors) {
    for (SeatStateBackup backup : seatBackups) {
      if (!isSeatSafeToRestore(backup, errors)) {
        continue;
      }
      String sql =
          "UPDATE flight_seats SET status = ?, booking_id = ?, reserved_until = ? WHERE id = ?";
      try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
        stmt.setString(1, backup.originalStatus());
        if (backup.originalBookingId() != null) {
          stmt.setLong(2, backup.originalBookingId());
        } else {
          stmt.setNull(2, Types.BIGINT);
        }
        if (backup.originalReservedUntil() != null) {
          stmt.setObject(3, backup.originalReservedUntil());
        } else {
          stmt.setNull(3, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        stmt.setLong(4, backup.seatId());
        stmt.executeUpdate();
      } catch (SQLException exception) {
        errors.add(
            "Failed to restore seat "
                + backup.seatId()
                + ": "
                + sanitizeError(exception.getMessage()));
      }
    }

    List<LedgerEntry> cleanupEntries = new ArrayList<>(ledger);
    cleanupEntries.sort(
        java.util.Comparator.comparingInt(entry -> cleanupPriority(entry.tableName())));

    for (LedgerEntry entry : cleanupEntries) {
      if (!ALLOWED_CLEANUP_TABLES.contains(entry.tableName())) {
        errors.add("Disallowed table in cleanup ledger: " + entry.tableName());
        continue;
      }
      String sql = "DELETE FROM " + entry.tableName() + " WHERE id = ?";
      try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
        if (entry.id() instanceof UUID uuid) {
          stmt.setObject(1, uuid);
        } else if (entry.id() instanceof Long l) {
          stmt.setLong(1, l);
        } else {
          stmt.setObject(1, entry.id());
        }
        stmt.executeUpdate();
      } catch (SQLException exception) {
        errors.add(
            "Failed to delete "
                + entry.tableName()
                + " ("
                + entry.id()
                + "): "
                + sanitizeError(exception.getMessage()));
      }
    }

    restoreFlightSchedules(errors);
  }

  private void restoreFlightSchedules(List<String> errors) {
    for (FlightScheduleBackup backup : flightScheduleBackups) {
      String sql =
          "UPDATE flights SET status = ?, departure_time = ?, arrival_time = ?, updated_at = ?"
              + " WHERE id = ? AND status = 'SCHEDULED'"
              + " AND departure_time = ? AND arrival_time = ?"
              + " AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.flight_id = flights.id)";
      try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
        stmt.setString(1, backup.originalStatus());
        stmt.setObject(2, backup.originalDepartureTime());
        stmt.setObject(3, backup.originalArrivalTime());
        stmt.setObject(4, backup.originalUpdatedAt());
        stmt.setLong(5, backup.flightId());
        stmt.setObject(6, backup.fixtureDepartureTime());
        stmt.setObject(7, backup.fixtureArrivalTime());
        if (stmt.executeUpdate() != 1) {
          errors.add(
              "Refused to restore flight "
                  + backup.flightId()
                  + " because its schedule or ticket ownership changed");
        }
      } catch (SQLException exception) {
        errors.add(
            "Failed to restore flight "
                + backup.flightId()
                + ": "
                + sanitizeError(exception.getMessage()));
      }
    }
  }

  private int cleanupPriority(String tableName) {
    return switch (tableName) {
      case "ai_tool_audit_events",
              "ai_chat_messages",
              "boarding_passes",
              "booking_cancellation_items" ->
          10;
      case "ai_tool_proposals",
              "ai_chat_turns",
              "booking_cancellations",
              "ticket_financial_snapshots" ->
          20;
      case "ai_chat_conversations", "payments" -> 30;
      case "seat_change_settlement_items", "service_change_settlement_items" -> 40;
      case "seat_change_settlements", "service_change_settlements" -> 50;
      case "booking_services", "passenger_seat_assignments" -> 60;
      case "tickets", "booking_fare_holds" -> 70;
      case "passengers" -> 80;
      case "bookings" -> 90;
      case "refresh_token_sessions", "password_reset_tokens" -> 100;
      case "users" -> 110;
      case "flight_seats", "flight_fares" -> 120;
      case "flights" -> 130;
      default -> Integer.MAX_VALUE;
    };
  }

  private boolean isSeatSafeToRestore(SeatStateBackup backup, List<String> errors) {
    String sql = "SELECT status, booking_id, reserved_until FROM flight_seats WHERE id = ?";
    try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
      stmt.setLong(1, backup.seatId());
      ResultSet rs = stmt.executeQuery();
      if (!rs.next()) {
        errors.add("Seat disappeared before cleanup: " + backup.seatId());
        return false;
      }
      String currentStatus = rs.getString("status");
      Long currentBookingId = rs.getObject("booking_id", Long.class);
      OffsetDateTime currentReservedUntil = rs.getObject("reserved_until", OffsetDateTime.class);
      if (java.util.Objects.equals(currentStatus, backup.originalStatus())
          && java.util.Objects.equals(currentBookingId, backup.originalBookingId())
          && java.util.Objects.equals(currentReservedUntil, backup.originalReservedUntil())) {
        return false;
      }
      if (currentBookingId != null && fixtureBookingIds.contains(currentBookingId)) {
        return true;
      }
      errors.add(
          "Refused to restore seat "
              + backup.seatId()
              + " because it is no longer owned by this fixture");
      return false;
    } catch (SQLException exception) {
      errors.add(
          "Failed to verify seat ownership before restore: "
              + sanitizeError(exception.getMessage()));
      return false;
    }
  }

  private void addToLedger(String tableName, Object id) {
    if (!ALLOWED_CLEANUP_TABLES.contains(tableName)) {
      throw new IllegalArgumentException(
          "Table " + tableName + " is not in ALLOWED_CLEANUP_TABLES");
    }
    LedgerEntry entry = new LedgerEntry(tableName, id);
    if (!ledger.contains(entry)) {
      ledger.add(entry);
    }
  }

  private long queryCount(String sql, Object... params) throws Exception {
    try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        stmt.setObject(i + 1, params[i]);
      }
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        return rs.getLong(1);
      }
    }
    return 0L;
  }

  private Long queryLong(String sql, String param) throws Exception {
    try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
      stmt.setString(1, param);
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        return rs.getLong(1);
      }
    }
    return null;
  }

  private String queryString(String sql, Object... params) throws Exception {
    try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        stmt.setObject(i + 1, params[i]);
      }
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        return rs.getString(1);
      }
    }
    return null;
  }

  private List<Long> queryLongList(String sql, Object param) throws Exception {
    List<Long> results = new ArrayList<>();
    try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
      stmt.setObject(1, param);
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        results.add(rs.getLong(1));
      }
    }
    return results;
  }

  private List<UUID> queryUuidList(String sql, Object param) throws Exception {
    List<UUID> results = new ArrayList<>();
    try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
      stmt.setObject(1, param);
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        results.add((UUID) rs.getObject(1));
      }
    }
    return results;
  }

  private String sanitizeError(String message) {
    if (message == null) {
      return "Unknown error";
    }
    return message.replaceAll("password=[^&;\\s]+", "password=***");
  }
}
