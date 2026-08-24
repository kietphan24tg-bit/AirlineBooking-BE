package com.example.moduleai.confirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolPolicy;
import com.example.moduleai.tool.core.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = ToolConfirmationServicePostgresIntegrationTest.JpaConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ToolConfirmationServicePostgresIntegrationTest {

  private static final String HMAC_SECRET = "postgres-agent-confirmation-secret-32+";
  private static final long USER_ID = 91L;

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("agent_tool_test")
          .withUsername("postgres")
          .withPassword("postgres");

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("spring.flyway.enabled", () -> "false");
    registry.add("spring.sql.init.mode", () -> "never");
  }

  @Autowired private ToolConfirmationService service;
  @Autowired private ToolProposalRepository proposalRepository;
  @Autowired private ToolProposalSigner signer;

  @MockitoBean private ToolCatalog toolCatalog;
  @MockitoBean private ToolExecutionGuard toolExecutionGuard;
  @MockitoBean private ToolConfirmationRegistry confirmationRegistry;
  @MockitoBean private ToolExecutionReconcilerRegistry reconcilerRegistry;
  @MockitoBean private AiObservability observability;

  private ExecutorService executorService;

  @AfterEach
  void tearDown() {
    proposalRepository.deleteAll();
    if (executorService != null) {
      executorService.shutdownNow();
    }
  }

  @Test
  void concurrentConfirmClaimsPendingProposalOnlyOnce() throws Exception {
    UUID proposalId = UUID.randomUUID();
    ToolProposalEntity proposal =
        savePendingProposal(proposalId, OffsetDateTime.now().plusMinutes(5));
    AtomicInteger invocations = new AtomicInteger();

    ToolDescriptor descriptor = mock(ToolDescriptor.class);
    when(descriptor.inputType()).thenReturn((Class) Map.class);
    when(descriptor.policy())
        .thenReturn(
            new ToolPolicy(
                "confirm_seat_change",
                ToolOperation.FINANCIAL,
                ToolAccess.AUTHENTICATED,
                true,
                true));
    when(descriptor.invoke(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              invocations.incrementAndGet();
              Thread.sleep(150);
              return ToolResult.success("confirm_seat_change", Map.of("ok", true));
            });
    when(toolCatalog.descriptor("confirm_seat_change")).thenReturn(descriptor);

    CountDownLatch startLatch = new CountDownLatch(1);
    executorService = Executors.newFixedThreadPool(2);

    Callable<Object> task =
        () -> {
          startLatch.await(5, TimeUnit.SECONDS);
          try {
            return service.confirm(proposalId, USER_ID);
          } catch (Exception exception) {
            return exception;
          }
        };

    Future<Object> first = executorService.submit(task);
    Future<Object> second = executorService.submit(task);
    startLatch.countDown();

    List<Object> results =
        List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    long successCount =
        results.stream()
            .filter(ToolProposalView.class::isInstance)
            .map(ToolProposalView.class::cast)
            .filter(view -> "EXECUTED".equals(view.status()))
            .count();
    long claimedCount =
        results.stream()
            .filter(AppException.class::isInstance)
            .map(AppException.class::cast)
            .filter(error -> "PROPOSAL_ALREADY_CLAIMED".equals(error.getCode()))
            .count();

    assertThat(successCount).isEqualTo(1);
    assertThat(claimedCount).isEqualTo(1);
    assertThat(invocations.get()).isEqualTo(1);

    ToolProposalEntity saved =
        proposalRepository.findByIdAndUserId(proposal.getId(), USER_ID).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(ToolProposalStatus.EXECUTED);
  }

  @Test
  void wrongUserCannotConfirmAnotherUsersProposal() {
    UUID proposalId = UUID.randomUUID();
    savePendingProposal(proposalId, OffsetDateTime.now().plusMinutes(5));

    assertThatThrownBy(() -> service.confirm(proposalId, USER_ID + 1))
        .isInstanceOf(AppException.class)
        .satisfies(
            error -> assertThat(((AppException) error).getCode()).isEqualTo("PROPOSAL_NOT_FOUND"));
  }

  @Test
  void expiredProposalIsRejectedBeforeClaim() {
    UUID proposalId = UUID.randomUUID();
    savePendingProposal(proposalId, OffsetDateTime.now().minusSeconds(5));

    assertThatThrownBy(() -> service.confirm(proposalId, USER_ID))
        .isInstanceOf(AppException.class)
        .satisfies(
            error -> assertThat(((AppException) error).getCode()).isEqualTo("PROPOSAL_EXPIRED"));

    ToolProposalEntity saved =
        proposalRepository.findByIdAndUserId(proposalId, USER_ID).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(ToolProposalStatus.PENDING);
  }

  @Test
  void tamperedHmacTransitionsProposalToFailedWithoutInvokingTool() {
    UUID proposalId = UUID.randomUUID();
    ToolProposalEntity proposal =
        savePendingProposal(proposalId, OffsetDateTime.now().plusMinutes(5));
    proposal.setConfirmationHmac("tampered");
    proposalRepository.save(proposal);

    ToolDescriptor descriptor = mock(ToolDescriptor.class);
    when(descriptor.inputType()).thenReturn((Class) Map.class);
    when(descriptor.policy())
        .thenReturn(
            new ToolPolicy(
                "confirm_seat_change",
                ToolOperation.FINANCIAL,
                ToolAccess.AUTHENTICATED,
                true,
                true));
    when(toolCatalog.descriptor("confirm_seat_change")).thenReturn(descriptor);

    ToolProposalView view = service.confirm(proposalId, USER_ID);

    assertThat(view.status()).isEqualTo("FAILED");
    verify(descriptor, never()).invoke(any(), any(), any());
  }

  private ToolProposalEntity savePendingProposal(UUID proposalId, OffsetDateTime expiresAt) {
    OffsetDateTime now = OffsetDateTime.now();
    ToolProposalEntity proposal = new ToolProposalEntity();
    proposal.setId(proposalId);
    proposal.setUserId(USER_ID);
    proposal.setToolName("confirm_seat_change");
    proposal.setOperation(ToolOperation.FINANCIAL);
    proposal.setAccess(ToolAccess.AUTHENTICATED);
    proposal.setCanonicalArguments("{}");
    proposal.setConfirmationSnapshot("{\"summary\":\"Confirm seat change\"}");
    proposal.setIdempotencyKey("ai-proposal:" + proposalId);
    proposal.setStatus(ToolProposalStatus.PENDING);
    proposal.setExpiresAt(expiresAt);
    proposal.setCreatedAt(now);
    proposal.setUpdatedAt(now);
    proposal.setConfirmationHmac(
        signer.sign(
            proposalId,
            USER_ID,
            "confirm_seat_change",
            ToolOperation.FINANCIAL.name(),
            "{}",
            expiresAt));
    return proposalRepository.save(proposal);
  }

  @TestConfiguration
  @EnableAutoConfiguration
  @EnableJpaRepositories(
      basePackageClasses = {ToolProposalRepository.class, ToolAuditEventRepository.class})
  static class JpaConfig {

    @Bean
    AiAgentProperties aiAgentProperties() {
      AiAgentProperties properties = new AiAgentProperties();
      properties.setActionsEnabled(true);
      properties.setConfirmationHmacSecret(HMAC_SECRET);
      properties.setProposalTtl(Duration.ofMinutes(5));
      properties.setExecutingTimeout(Duration.ofMinutes(2));
      return properties;
    }

    @Bean
    AgentRolloutProperties rolloutProperties() {
      AgentRolloutProperties properties = new AgentRolloutProperties();
      properties.setMode(AgentRolloutMode.LIVE);
      return properties;
    }

    @Bean
    ToolProposalSigner toolProposalSigner(AiAgentProperties properties) {
      return new ToolProposalSigner(properties);
    }

    @Bean
    AgentPayloadSanitizer payloadSanitizer(ObjectMapper objectMapper) {
      return new AgentPayloadSanitizer(objectMapper, new AiAgentGuardrailProperties());
    }

    @Bean
    ToolProposalSnapshotFactory snapshotFactory(
        ObjectMapper objectMapper,
        AgentPayloadSanitizer payloadSanitizer,
        ToolConfirmationRegistry confirmationRegistry) {
      return new ToolProposalSnapshotFactory(objectMapper, payloadSanitizer, confirmationRegistry);
    }

    @Bean
    ToolConfirmationService toolConfirmationService(
        ToolCatalog toolCatalog,
        ToolExecutionGuard toolExecutionGuard,
        ToolProposalRepository proposalRepository,
        ToolAuditEventRepository auditEventRepository,
        ToolProposalSigner signer,
        ToolProposalSnapshotFactory snapshotFactory,
        ToolConfirmationRegistry confirmationRegistry,
        ToolExecutionReconcilerRegistry reconcilerRegistry,
        ObjectMapper objectMapper,
        AiAgentProperties properties,
        AgentRolloutProperties rolloutProperties,
        AiObservability observability,
        AgentPayloadSanitizer payloadSanitizer) {
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
}
