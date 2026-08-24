package com.example.moduleai.confirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.moduleai.config.AiAgentGuardrailProperties;
import com.example.moduleai.config.AiAgentProperties;
import com.example.moduleai.guardrail.AgentPayloadSanitizer;
import com.example.moduleai.observability.AiObservability;
import com.example.moduleai.rollout.AgentRolloutMode;
import com.example.moduleai.rollout.AgentRolloutProperties;
import com.example.moduleai.tool.catalog.ToolCatalog;
import com.example.moduleai.tool.catalog.ToolDescriptor;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolPolicy;
import com.example.moduleai.tool.core.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ToolConfirmationService}.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>createProposal: happy path (HMAC signed, saved, returned as view)
 *   <li>createProposal: blocked when actionsEnabled=false
 *   <li>createProposal: blocked when userId is null (unauthenticated)
 *   <li>createProposal: blocked for READ-only tool (no confirmation needed)
 *   <li>confirm: happy path (PENDING → EXECUTING → EXECUTED)
 *   <li>confirm: throws PROPOSAL_EXPIRED when TTL passed
 *   <li>confirm: throws PROPOSAL_ALREADY_CLAIMED on double-submit (replay attack)
 *   <li>confirm: throws PROPOSAL_HMAC_INVALID on tampered payload
 *   <li>reject: happy path (PENDING → REJECTED)
 *   <li>reject: throws PROPOSAL_EXPIRED when TTL passed
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ToolConfirmationServiceTest {

  // ── Fixed test data ──────────────────────────────────────────────────────────
  private static final String HMAC_SECRET = "this-is-a-test-secret-at-least-32-chars!!";
  private static final long USER_ID = 42L;
  private static final UUID PROPOSAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  // ── Mocks ────────────────────────────────────────────────────────────────────
  @Mock private ToolCatalog toolCatalog;
  @Mock private ToolExecutionGuard toolExecutionGuard;
  @Mock private ToolProposalRepository proposalRepository;
  @Mock private ToolAuditEventRepository auditEventRepository;
  @Mock private AiObservability observability;
  @Mock private ToolConfirmationRegistry confirmationRegistry;
  @Mock private ToolExecutionReconcilerRegistry reconcilerRegistry;

  // ── Real collaborators (lightweight) ────────────────────────────────────────
  private AiAgentProperties properties;
  private AgentRolloutProperties rolloutProperties;
  private ToolProposalSigner signer;
  private ToolProposalSnapshotFactory snapshotFactory;
  private AgentPayloadSanitizer payloadSanitizer;
  private ObjectMapper objectMapper;
  private ToolConfirmationService service;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    properties = new AiAgentProperties();
    properties.setActionsEnabled(true);
    properties.setConfirmationHmacSecret(HMAC_SECRET);
    properties.setProposalTtl(Duration.ofMinutes(5));
    properties.setExecutingTimeout(Duration.ofMinutes(2));
    rolloutProperties = new AgentRolloutProperties();
    rolloutProperties.setMode(AgentRolloutMode.LIVE);

    signer = new ToolProposalSigner(properties);
    payloadSanitizer = new AgentPayloadSanitizer(objectMapper, new AiAgentGuardrailProperties());
    lenient()
        .when(confirmationRegistry.present(any(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    snapshotFactory =
        new ToolProposalSnapshotFactory(objectMapper, payloadSanitizer, confirmationRegistry);

    service =
        new ToolConfirmationService(
            toolCatalog,
            toolExecutionGuard,
            proposalRepository,
            auditEventRepository,
            signer,
            snapshotFactory,
            confirmationRegistry,
            reconcilerRegistry,
            objectMapper,
            properties,
            rolloutProperties,
            observability,
            payloadSanitizer);
  }

  // ──────────────────────────────────────────────────────────────────────────
  // createProposal
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void createProposal_happyPath_savesAndReturnsView() {
    ToolDescriptor descriptor = writeDescriptor("seat_change_confirm");
    AgentToolContext ctx = authenticatedContext(USER_ID);
    Object input = Map.of("seatId", "12A", "bookingId", 99);

    when(toolCatalog.descriptor("seat_change_confirm")).thenReturn(descriptor);
    when(toolExecutionGuard.validateForProposal(any(), any(), any())).thenReturn(null);
    when(proposalRepository.existsByUserIdAndStatusIn(eq(USER_ID), any())).thenReturn(false);
    when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(auditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ToolProposalView view = service.createProposal("seat_change_confirm", input, ctx);

    assertThat(view.tool()).isEqualTo("seat_change_confirm");
    assertThat(view.status()).isEqualTo("PENDING");
    assertThat(view.confirmPath()).contains("/confirm");
    assertThat(view.rejectPath()).contains("/reject");
    assertThat(view.expiresAt()).isAfter(OffsetDateTime.now());

    verify(proposalRepository).save(any(ToolProposalEntity.class));
    verify(observability).recordToolProposal("seat_change_confirm", "WRITE", "PENDING");
  }

  @Test
  void createProposal_throwsWhenActionsDisabled() {
    properties.setActionsEnabled(false);
    // Rebuild service with disabled properties
    service = rebuildService();
    AgentToolContext ctx = authenticatedContext(USER_ID);

    assertThatThrownBy(() -> service.createProposal("seat_change_confirm", Map.of(), ctx))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("disabled");
  }

  @Test
  void createProposal_throwsWhenUnauthenticated() {
    AgentToolContext ctx = AgentToolContext.empty();
    ToolDescriptor descriptor = writeDescriptor("seat_change_confirm");
    when(toolCatalog.descriptor("seat_change_confirm")).thenReturn(descriptor);

    assertThatThrownBy(() -> service.createProposal("seat_change_confirm", Map.of(), ctx))
        .isInstanceOf(AppException.class)
        .satisfies(
            ex -> assertThat(((AppException) ex).getCode()).isEqualTo("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void createProposal_throwsForReadOnlyTool() {
    ToolDescriptor readDescriptor =
        new ToolDescriptor(
            "search_flights",
            "Search",
            readPolicy("search_flights"),
            null,
            "{}",
            null,
            null,
            null,
            true);
    when(toolCatalog.descriptor("search_flights")).thenReturn(readDescriptor);
    AgentToolContext ctx = authenticatedContext(USER_ID);

    assertThatThrownBy(() -> service.createProposal("search_flights", Map.of(), ctx))
        .isInstanceOf(AppException.class)
        .satisfies(
            ex -> assertThat(((AppException) ex).getCode()).isEqualTo("PROPOSAL_NOT_REQUIRED"));
  }

  @Test
  void createProposal_throwsWhenAnotherProposalIsStillActive() {
    ToolDescriptor descriptor = writeDescriptor("seat_change_confirm");
    when(toolCatalog.descriptor("seat_change_confirm")).thenReturn(descriptor);
    when(proposalRepository.existsByUserIdAndStatusIn(eq(USER_ID), any())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.createProposal(
                    "seat_change_confirm", Map.of("bookingId", 99), authenticatedContext(USER_ID)))
        .isInstanceOf(AppException.class)
        .satisfies(
            ex -> assertThat(((AppException) ex).getCode()).isEqualTo("ACTIVE_PROPOSAL_EXISTS"));
  }

  // ──────────────────────────────────────────────────────────────────────────
  // confirm
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void confirm_happyPath_executesToolAndSetsExecuted() {
    ToolProposalEntity proposal = pendingProposal(OffsetDateTime.now().plusMinutes(5));
    ToolDescriptor descriptor =
        writeDescriptorWithResult(
            "seat_change_confirm", ToolResult.success("seat_change_confirm", Map.of("ok", true)));

    when(proposalRepository.findByIdAndUserId(PROPOSAL_ID, USER_ID))
        .thenReturn(Optional.of(proposal));
    when(proposalRepository.updateStatusForUser(
            eq(PROPOSAL_ID),
            eq(USER_ID),
            eq(ToolProposalStatus.PENDING),
            eq(ToolProposalStatus.EXECUTING),
            any()))
        .thenReturn(1);
    when(toolCatalog.descriptor("seat_change_confirm")).thenReturn(descriptor);
    when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(auditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ToolProposalView view = service.confirm(PROPOSAL_ID, USER_ID);

    assertThat(view.status()).isEqualTo("EXECUTED");
    verify(observability).recordConfirmationDuration(any());
    verify(observability)
        .recordToolExecution(eq("seat_change_confirm"), eq("WRITE"), eq("EXECUTED"), any());
  }

  @Test
  void confirm_throwsProposalExpiredWhenTtlPassed() {
    ToolProposalEntity expired = pendingProposal(OffsetDateTime.now().minusSeconds(1));
    when(proposalRepository.findByIdAndUserId(PROPOSAL_ID, USER_ID))
        .thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.confirm(PROPOSAL_ID, USER_ID))
        .isInstanceOf(AppException.class)
        .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("PROPOSAL_EXPIRED"));
  }

  @Test
  void confirm_throwsWhenRolloutIsShadow() {
    rolloutProperties.setMode(AgentRolloutMode.SHADOW);

    assertThatThrownBy(() -> service.confirm(PROPOSAL_ID, USER_ID))
        .isInstanceOf(AppException.class)
        .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("AGENT_SHADOW_MODE"));
  }

  @Test
  void confirm_throwsOnDoubleSubmitReplayAttack() {
    ToolProposalEntity proposal = pendingProposal(OffsetDateTime.now().plusMinutes(5));
    when(proposalRepository.findByIdAndUserId(PROPOSAL_ID, USER_ID))
        .thenReturn(Optional.of(proposal));
    // Simulate race condition: another thread already claimed it
    when(proposalRepository.updateStatusForUser(
            eq(PROPOSAL_ID),
            eq(USER_ID),
            eq(ToolProposalStatus.PENDING),
            eq(ToolProposalStatus.EXECUTING),
            any()))
        .thenReturn(0); // 0 rows updated = already claimed

    assertThatThrownBy(() -> service.confirm(PROPOSAL_ID, USER_ID))
        .isInstanceOf(AppException.class)
        .satisfies(
            ex -> assertThat(((AppException) ex).getCode()).isEqualTo("PROPOSAL_ALREADY_CLAIMED"));

    verify(observability).recordConfirmationReplay("seat_change_confirm");
  }

  @Test
  void confirm_returnsFailedViewWhenHmacTampered() {
    ToolProposalEntity proposal = pendingProposal(OffsetDateTime.now().plusMinutes(5));
    proposal.setConfirmationHmac("tampered-hmac-value");

    ToolProposalEntity executing = pendingProposal(OffsetDateTime.now().plusMinutes(5));
    executing.setStatus(ToolProposalStatus.EXECUTING);
    executing.setConfirmationHmac("tampered-hmac-value");

    when(proposalRepository.findByIdAndUserId(PROPOSAL_ID, USER_ID))
        .thenReturn(Optional.of(proposal)) // 1st call: PENDING check
        .thenReturn(Optional.of(executing)); // 2nd call after updateStatus: EXECUTING
    when(proposalRepository.updateStatusForUser(any(), any(), any(), any(), any())).thenReturn(1);
    when(auditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // validateIntegrity will fail since HMAC doesn't match → caught and mapped to FAILED
    ToolProposalView view = service.confirm(PROPOSAL_ID, USER_ID);
    assertThat(view.status()).isEqualTo("FAILED");

    verify(observability).recordToolIntegrityFailure("seat_change_confirm");
  }

  // ──────────────────────────────────────────────────────────────────────────
  // reject
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void reject_happyPath_setsRejectedStatus() {
    ToolProposalEntity proposal = pendingProposal(OffsetDateTime.now().plusMinutes(5));
    ToolProposalEntity rejected = pendingProposal(OffsetDateTime.now().plusMinutes(5));
    rejected.setStatus(ToolProposalStatus.REJECTED);

    when(proposalRepository.findByIdAndUserId(PROPOSAL_ID, USER_ID))
        .thenReturn(Optional.of(proposal))
        .thenReturn(Optional.of(rejected));
    when(proposalRepository.updateStatusForUser(
            eq(PROPOSAL_ID),
            eq(USER_ID),
            eq(ToolProposalStatus.PENDING),
            eq(ToolProposalStatus.REJECTED),
            any()))
        .thenReturn(1);
    when(proposalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(auditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ToolProposalView view = service.reject(PROPOSAL_ID, USER_ID);

    assertThat(view.status()).isEqualTo("REJECTED");
  }

  @Test
  void reject_throwsProposalExpiredWhenTtlPassed() {
    ToolProposalEntity expired = pendingProposal(OffsetDateTime.now().minusSeconds(1));
    when(proposalRepository.findByIdAndUserId(PROPOSAL_ID, USER_ID))
        .thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.reject(PROPOSAL_ID, USER_ID))
        .isInstanceOf(AppException.class)
        .satisfies(ex -> assertThat(((AppException) ex).getCode()).isEqualTo("PROPOSAL_EXPIRED"));
  }

  // ──────────────────────────────────────────────────────────────────────────
  // ToolProposalSigner standalone tests
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void signer_signAndMatchRoundTrip() {
    UUID id = UUID.randomUUID();
    OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);
    String hmac = signer.sign(id, USER_ID, "seat_change_confirm", "WRITE", "{}", expiresAt);

    assertThat(hmac).isNotBlank();
    assertThat(signer.matches(hmac, id, USER_ID, "seat_change_confirm", "WRITE", "{}", expiresAt))
        .isTrue();
  }

  @Test
  void signer_detectsTamperedArguments() {
    UUID id = UUID.randomUUID();
    OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);
    String hmac = signer.sign(id, USER_ID, "seat_change_confirm", "WRITE", "{}", expiresAt);

    // Tamper the canonical arguments
    assertThat(
            signer.matches(
                hmac, id, USER_ID, "seat_change_confirm", "WRITE", "{\"evil\":true}", expiresAt))
        .isFalse();
  }

  @Test
  void signer_detectsTamperedUserId() {
    UUID id = UUID.randomUUID();
    OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);
    String hmac = signer.sign(id, USER_ID, "seat_change_confirm", "WRITE", "{}", expiresAt);

    assertThat(signer.matches(hmac, id, 999L, "seat_change_confirm", "WRITE", "{}", expiresAt))
        .isFalse();
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Helpers
  // ──────────────────────────────────────────────────────────────────────────

  private ToolProposalEntity pendingProposal(OffsetDateTime expiresAt) {
    OffsetDateTime now = OffsetDateTime.now();
    ToolProposalEntity entity = new ToolProposalEntity();
    entity.setId(PROPOSAL_ID);
    entity.setUserId(USER_ID);
    entity.setToolName("seat_change_confirm");
    entity.setOperation(ToolOperation.WRITE);
    entity.setAccess(ToolAccess.AUTHENTICATED);
    entity.setCanonicalArguments("{}");
    entity.setConfirmationSnapshot("{\"tool\":\"seat_change_confirm\"}");
    entity.setIdempotencyKey("ai-proposal:" + PROPOSAL_ID);
    entity.setStatus(ToolProposalStatus.PENDING);
    entity.setExpiresAt(expiresAt);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    // Set a valid HMAC so integrity check passes by default
    entity.setConfirmationHmac(
        signer.sign(PROPOSAL_ID, USER_ID, "seat_change_confirm", "WRITE", "{}", expiresAt));
    return entity;
  }

  private static AgentToolContext authenticatedContext(Long userId) {
    return new AgentToolContext(null, userId, Set.of(), false, null, null, null, Map.of());
  }

  private static ToolDescriptor writeDescriptor(String toolName) {
    ToolPolicy policy =
        new ToolPolicy(toolName, ToolOperation.WRITE, ToolAccess.AUTHENTICATED, true, true);
    return new ToolDescriptor(toolName, "Write tool", policy, null, "{}", null, null, null, false);
  }

  private static ToolPolicy readPolicy(String toolName) {
    return new ToolPolicy(toolName, ToolOperation.READ, ToolAccess.PUBLIC, false, false);
  }

  private ToolDescriptor writeDescriptorWithResult(String toolName, ToolResult<?> result) {
    ToolPolicy policy =
        new ToolPolicy(toolName, ToolOperation.WRITE, ToolAccess.AUTHENTICATED, true, true);
    ToolDescriptor mock = org.mockito.Mockito.mock(ToolDescriptor.class);
    org.mockito.Mockito.lenient().when(mock.name()).thenReturn(toolName);
    org.mockito.Mockito.lenient().when(mock.policy()).thenReturn(policy);
    org.mockito.Mockito.lenient().doReturn(Map.class).when(mock).inputType();
    org.mockito.Mockito.doReturn(result).when(mock).invoke(any(), any(), any());
    return mock;
  }

  private ToolConfirmationService rebuildService() {
    return new ToolConfirmationService(
        toolCatalog,
        toolExecutionGuard,
        proposalRepository,
        auditEventRepository,
        signer,
        snapshotFactory,
        confirmationRegistry,
        reconcilerRegistry,
        objectMapper,
        properties,
        rolloutProperties,
        observability,
        payloadSanitizer);
  }
}
