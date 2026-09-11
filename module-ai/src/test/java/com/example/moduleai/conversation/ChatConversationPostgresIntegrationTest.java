package com.example.moduleai.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = ChatConversationPostgresIntegrationTest.DatabaseConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("postgres-integration")
@EnabledIf("isPostgresTestAvailable")
class ChatConversationPostgresIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("chat_memory_test")
          .withUsername("postgres")
          .withPassword("postgres");

  private final List<UUID> conversationIds = new java.util.concurrent.CopyOnWriteArrayList<>();
  private ExecutorService executorService;

  @Autowired private JdbcTemplate jdbcTemplate;

  static boolean isPostgresTestAvailable() {
    if (usesExternalDatabase()) {
      return true;
    }
    try {
      return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable ignored) {
      return false;
    }
  }

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    if (usesExternalDatabase()) {
      registry.add(
          "spring.datasource.url",
          () -> disablePreparedStatements(System.getenv("SPRING_DATASOURCE_URL")));
      registry.add("spring.datasource.username", () -> System.getenv("SPRING_DATASOURCE_USERNAME"));
      registry.add("spring.datasource.password", () -> System.getenv("SPRING_DATASOURCE_PASSWORD"));
      registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
      return;
    }
    POSTGRES.start();
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
  }

  @BeforeAll
  void createSchema() {
    jdbcTemplate.execute(
        """
        create table if not exists ai_chat_conversations (
          id uuid primary key,
          user_id bigint null,
          guest_token_hash varchar(128) null,
          status varchar(20) not null,
          title varchar(255) null,
          summary text null,
          structured_state jsonb null,
          summary_status varchar(20) not null,
          summarized_through_sequence bigint not null default 0,
          summary_claimed_at timestamptz null,
          summary_attempts integer not null default 0,
          next_sequence bigint not null default 1,
          last_active_at timestamptz not null,
          archived_at timestamptz null,
          archive_reason varchar(40) null,
          created_at timestamptz not null,
          updated_at timestamptz not null,
          version bigint not null default 0,
          constraint chk_ai_chat_owner_present check (
            (user_id is not null and guest_token_hash is null)
            or (user_id is null and guest_token_hash is not null)
          )
        )
        """);
    jdbcTemplate.execute(
        """
        create unique index if not exists uq_ai_chat_active_user
          on ai_chat_conversations (user_id)
          where status = 'ACTIVE' and user_id is not null
        """);
    jdbcTemplate.execute(
        """
        create unique index if not exists uq_ai_chat_active_guest
          on ai_chat_conversations (guest_token_hash)
          where status = 'ACTIVE' and guest_token_hash is not null
        """);
    jdbcTemplate.execute(
        """
        create table if not exists ai_chat_turns (
          id uuid primary key,
          conversation_id uuid not null references ai_chat_conversations(id) on delete cascade,
          client_message_id varchar(100) not null,
          status varchar(20) not null,
          answer_type varchar(40) null,
          safe_response jsonb null,
          memory_eligible boolean not null default true,
          created_at timestamptz not null,
          completed_at timestamptz null,
          updated_at timestamptz not null
        )
        """);
    jdbcTemplate.execute(
        """
        create unique index if not exists uq_ai_chat_turns_conversation_client
          on ai_chat_turns (conversation_id, client_message_id)
        """);
    jdbcTemplate.execute(
        """
        create table if not exists ai_chat_messages (
          id bigserial primary key,
          conversation_id uuid not null references ai_chat_conversations(id) on delete cascade,
          turn_id uuid not null references ai_chat_turns(id) on delete cascade,
          sequence_number bigint not null,
          role varchar(20) not null,
          content text not null,
          safe_metadata jsonb null,
          created_at timestamptz not null
        )
        """);
    jdbcTemplate.execute(
        """
        create unique index if not exists uq_ai_chat_messages_sequence
          on ai_chat_messages (conversation_id, sequence_number)
        """);
  }

  @AfterEach
  void cleanUp() {
    for (UUID id : conversationIds) {
      jdbcTemplate.update("delete from ai_chat_conversations where id = ?", id);
    }
    conversationIds.clear();
    if (executorService != null) {
      executorService.shutdownNow();
      executorService = null;
    }
  }

  @Test
  void concurrentCreationAllowsOnlyOneActiveConversationForAUser() throws Exception {
    long userId = Math.abs(UUID.randomUUID().getMostSignificantBits());
    UUID firstId = track(UUID.randomUUID());
    UUID secondId = track(UUID.randomUUID());
    CountDownLatch start = new CountDownLatch(1);
    executorService = Executors.newFixedThreadPool(2);
    Callable<Boolean> first = () -> insertActiveAfter(start, firstId, userId);
    Callable<Boolean> second = () -> insertActiveAfter(start, secondId, userId);

    Future<Boolean> firstResult = executorService.submit(first);
    Future<Boolean> secondResult = executorService.submit(second);
    start.countDown();

    assertThat(
            List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(true, false);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from ai_chat_conversations where user_id = ? and status ="
                    + " 'ACTIVE'",
                Integer.class,
                userId))
        .isEqualTo(1);
  }

  @Test
  void archivedConversationDoesNotBlockANewActiveConversation() {
    long userId = Math.abs(UUID.randomUUID().getLeastSignificantBits());
    UUID archivedId = track(UUID.randomUUID());
    UUID activeId = track(UUID.randomUUID());
    insertConversation(archivedId, userId, "ARCHIVED");

    insertConversation(activeId, userId, "ACTIVE");

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from ai_chat_conversations where user_id = ?",
                Integer.class,
                userId))
        .isEqualTo(2);
  }

  @Test
  void rejectsDuplicateClientMessageIdAndDuplicateMessageSequence() {
    long userId = Math.abs(UUID.randomUUID().getMostSignificantBits());
    UUID conversationId = track(UUID.randomUUID());
    UUID firstTurn = UUID.randomUUID();
    UUID secondTurn = UUID.randomUUID();
    insertConversation(conversationId, userId, "ACTIVE");
    insertTurn(firstTurn, conversationId, "client-message-1");

    assertThatThrownBy(() -> insertTurn(secondTurn, conversationId, "client-message-1"))
        .isInstanceOf(DataIntegrityViolationException.class);

    insertTurn(secondTurn, conversationId, "client-message-2");
    insertMessage(conversationId, firstTurn, 1L);
    assertThatThrownBy(() -> insertMessage(conversationId, secondTurn, 1L))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private boolean insertActiveAfter(CountDownLatch start, UUID id, long userId) throws Exception {
    start.await(5, TimeUnit.SECONDS);
    try {
      insertConversation(id, userId, "ACTIVE");
      return true;
    } catch (DataIntegrityViolationException exception) {
      return false;
    }
  }

  private void insertConversation(UUID id, long userId, String status) {
    OffsetDateTime now = OffsetDateTime.now();
    jdbcTemplate.update(
        """
        insert into ai_chat_conversations (
          id, user_id, status, summary_status, summarized_through_sequence,
          summary_attempts, next_sequence, last_active_at, created_at, updated_at, version
        ) values (?, ?, ?, 'IDLE', 0, 0, 1, ?, ?, ?, 0)
        """,
        id,
        userId,
        status,
        now,
        now,
        now);
  }

  private void insertTurn(UUID id, UUID conversationId, String clientMessageId) {
    OffsetDateTime now = OffsetDateTime.now();
    jdbcTemplate.update(
        """
        insert into ai_chat_turns (
          id, conversation_id, client_message_id, status, memory_eligible, created_at, updated_at
        ) values (?, ?, ?, 'PROCESSING', false, ?, ?)
        """,
        id,
        conversationId,
        clientMessageId,
        now,
        now);
  }

  private void insertMessage(UUID conversationId, UUID turnId, long sequence) {
    jdbcTemplate.update(
        """
        insert into ai_chat_messages (
          conversation_id, turn_id, sequence_number, role, content, created_at
        ) values (?, ?, ?, 'USER', 'test', ?)
        """,
        conversationId,
        turnId,
        sequence,
        OffsetDateTime.now());
  }

  private UUID track(UUID id) {
    conversationIds.add(id);
    return id;
  }

  private static boolean usesExternalDatabase() {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    return "true".equalsIgnoreCase(System.getenv("AGENT_TEST_USE_EXTERNAL_DATABASE"))
        && url != null
        && url.startsWith("jdbc:postgresql:");
  }

  private static String disablePreparedStatements(String jdbcUrl) {
    return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "prepareThreshold=0";
  }

  @SpringBootConfiguration
  @ImportAutoConfiguration(DataSourceAutoConfiguration.class)
  static class DatabaseConfig {

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }
  }
}
