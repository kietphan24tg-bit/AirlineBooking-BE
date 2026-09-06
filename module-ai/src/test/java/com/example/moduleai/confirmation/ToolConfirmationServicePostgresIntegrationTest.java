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
import com.example.moduleai.conversation.ChatConversationRepository;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = ToolConfirmationServicePostgresIntegrationTest.JpaConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("postgres-integration")
@EnabledIf("isPostgresTestAvailable")
class ToolConfirmationServicePostgresIntegrationTest {

  private static final String HMAC_SECRET = "postgres-agent-confirmation-secret-32+";
  private static final long USER_ID = 91L;

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("agent_tool_test")
          .withUsername("postgres")
          .withPassword("postgres");

  static boolean isPostgresTestAvailable() {
    boolean optInExternal =
        "true".equalsIgnoreCase(System.getenv("AGENT_TEST_USE_EXTERNAL_DATABASE"))
            && System.getenv("SPRING_DATASOURCE_URL") != null
            && System.getenv("SPRING_DATASOURCE_URL").startsWith("jdbc:postgresql:");
    if (optInExternal) {
      return true;
    }
    try {
      return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    boolean optInExternal =
        "true".equalsIgnoreCase(System.getenv("AGENT_TEST_USE_EXTERNAL_DATABASE"))
            && System.getenv("SPRING_DATASOURCE_URL") != null
            && System.getenv("SPRING_DATASOURCE_URL").startsWith("jdbc:postgresql:");

    if (optInExternal) {
      String externalUrl = System.getenv("SPRING_DATASOURCE_URL");
      registry.add("spring.datasource.url", () -> disablePreparedStatements(externalUrl));
      registry.add(
          "spring.datasource.username",
          () -> valueOrDefault(System.getenv("SPRING_DATASOURCE_USERNAME"), "postgres"));
      registry.add(
          "spring.datasource.password",
          () -> valueOrDefault(System.getenv("SPRING_DATASOURCE_PASSWORD"), "postgres"));
      registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
      registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
      registry.add("spring.flyway.enabled", () -> "false");
    } else {
      POSTGRES.start();
      registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
      registry.add("spring.datasource.username", POSTGRES::getUsername);
      registry.add("spring.datasource.password", POSTGRES::getPassword);
      registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
      registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
      registry.add("spring.flyway.enabled", () -> "true");
    }
    registry.add("spring.sql.init.mode", () -> "never");
  }

  private static String valueOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String disablePreparedStatements(String jdbcUrl) {
    return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "prepareThreshold=0";
  }

  @Autowired private ToolConfirmationService service;
  @Autowired private ToolProposalRepository proposalRepository;
  @Autowired private ToolProposalSigner signer;

  @MockitoBean private ToolCatalog toolCatalog;
  @MockitoBean private ToolExecutionGuard toolExecutionGuard;
  @MockitoBean private ChatConversationRepository conversationRepository;
  @MockitoBean private ToolConfirmationRegistry confirmationRegistry;
  @MockitoBean private ToolExecutionReconcilerRegistry reconcilerRegistry;
  @MockitoBean private AiObservability observability;

  private final List<UUID> createdProposalIds = new java.util.concurrent.CopyOnWriteArrayList<>();
  private ExecutorService executorService;

  @AfterEach
  void tearDown() {
    if (!createdProposalIds.isEmpty()) {
      proposalRepository.deleteAllById(createdProposalIds);
      createdProposalIds.clear();
    }
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
    ToolProposalEntity saved =
        proposalRepository.findByIdAndUserId(proposal.getId(), USER_ID).orElseThrow();
    String diagnostics = results + ", persistedError=" + saved.getSafeError();

    assertThat(successCount).as("concurrent confirmation results: %s", diagnostics).isEqualTo(1);
    assertThat(claimedCount).as("concurrent confirmation results: %s", diagnostics).isEqualTo(1);
    assertThat(invocations.get()).isEqualTo(1);

    assertThat(saved.getStatus()).isEqualTo(ToolProposalStatus.EXECUTED);
  }

  @Test
  void replayConfirmFailsWithAlreadyClaimedAndNoSecondMutation() {
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
              return ToolResult.success("confirm_seat_change", Map.of("ok", true));
            });
    when(toolCatalog.descriptor("confirm_seat_change")).thenReturn(descriptor);

    ToolProposalView firstView = service.confirm(proposalId, USER_ID);
    assertThat(firstView.status()).isEqualTo("EXECUTED");
    assertThat(invocations.get()).isEqualTo(1);

    // Replay confirm attempt must fail with PROPOSAL_ALREADY_CLAIMED and NOT call invoke again
    assertThatThrownBy(() -> service.confirm(proposalId, USER_ID))
        .isInstanceOf(AppException.class)
        .satisfies(
            error ->
                assertThat(((AppException) error).getCode()).isEqualTo("PROPOSAL_ALREADY_CLAIMED"));

    assertThat(invocations.get()).as("Replay must not trigger a second mutation").isEqualTo(1);
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
    createdProposalIds.add(proposalId);
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
            null,
            proposalId,
            USER_ID,
            "confirm_seat_change",
            ToolOperation.FINANCIAL.name(),
            "{}",
            expiresAt));
    return proposalRepository.save(proposal);
  }

  @SpringBootConfiguration
  @ImportAutoConfiguration({
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    DataJpaRepositoriesAutoConfiguration.class,
    TransactionAutoConfiguration.class
  })
  @EntityScan(basePackageClasses = {ToolProposalEntity.class, ToolAuditEventEntity.class})
  @EnableJpaRepositories(
      basePackageClasses = {ToolProposalRepository.class, ToolAuditEventRepository.class})
  static class JpaConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

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
        ChatConversationRepository conversationRepository,
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
          conversationRepository,
          objectMapper,
          properties,
          rolloutProperties,
          observability,
          payloadSanitizer);
    }
  }
}
