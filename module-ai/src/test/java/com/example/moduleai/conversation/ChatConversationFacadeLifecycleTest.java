package com.example.moduleai.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.moduleai.agent.AgentRequestContextFactory;
import com.example.moduleai.agent.AgentToolOrchestrator;
import com.example.moduleai.agent.IntentRouter;
import com.example.moduleai.chat.ChatResponseMapper;
import com.example.moduleai.chat.ChatService;
import com.example.moduleai.config.AiChatProperties;
import com.example.moduleai.confirmation.ToolConfirmationService;
import com.example.moduleai.guardrail.AgentConcurrencyLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class ChatConversationFacadeLifecycleTest {

  private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");

  private ChatConversationRepository conversationRepository;
  private ChatTurnRepository turnRepository;
  private ChatMessageRepository messageRepository;
  private ChatOwnerResolver ownerResolver;
  private ChatSummaryService summaryService;
  private ToolConfirmationService confirmationService;
  private AiChatProperties properties;
  private ChatConversationFacade facade;

  @BeforeEach
  void setUp() {
    conversationRepository = mock(ChatConversationRepository.class);
    turnRepository = mock(ChatTurnRepository.class);
    messageRepository = mock(ChatMessageRepository.class);
    ownerResolver = mock(ChatOwnerResolver.class);
    summaryService = mock(ChatSummaryService.class);
    confirmationService = mock(ToolConfirmationService.class);
    properties = new AiChatProperties();
    properties.setInactivityTimeout(Duration.ofHours(24));
    properties.setUserRetention(Duration.ofDays(30));
    properties.setGuestRetention(Duration.ofDays(7));
    properties.setSummaryClaimTimeout(Duration.ofMinutes(2));
    properties.setSummaryMaxAttempts(3);

    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(mock(TransactionStatus.class));
    when(conversationRepository.save(any(ChatConversationEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(turnRepository.save(any(ChatTurnEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    facade =
        new ChatConversationFacade(
            conversationRepository,
            turnRepository,
            messageRepository,
            ownerResolver,
            mock(ChatMemoryAssembler.class),
            mock(ChatContextualizationService.class),
            summaryService,
            mock(ChatStructuredStateProjector.class),
            mock(IntentRouter.class),
            mock(ChatService.class),
            mock(AgentToolOrchestrator.class),
            mock(AgentRequestContextFactory.class),
            mock(ChatResponseMapper.class),
            confirmationService,
            mock(AgentConcurrencyLimiter.class),
            properties,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            transactionManager);
  }

  @Test
  void archivesOnlyConversationsInactiveForMoreThan24HoursAndExpiresProposals() {
    ChatConversationEntity stale = conversation(10L, null, ChatConversationStatus.ACTIVE);
    stale.setLastActiveAt(atNow().minusHours(25));
    when(conversationRepository.findByStatusAndLastActiveAtBefore(any(), any()))
        .thenReturn(List.of(stale));
    when(conversationRepository.findById(stale.getId())).thenReturn(Optional.of(stale));

    int archived = facade.archiveExpiredConversations();

    assertThat(archived).isEqualTo(1);
    assertThat(stale.getStatus()).isEqualTo(ChatConversationStatus.ARCHIVED);
    assertThat(stale.getArchiveReason()).isEqualTo(ChatArchiveReason.INACTIVITY_TIMEOUT);
    assertThat(stale.getArchivedAt()).isEqualTo(atNow());
    ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(conversationRepository)
        .findByStatusAndLastActiveAtBefore(
            org.mockito.ArgumentMatchers.eq(ChatConversationStatus.ACTIVE), cutoff.capture());
    assertThat(cutoff.getValue()).isEqualTo(atNow().minusHours(24));
    verify(confirmationService).expirePendingForConversation(stale.getId());
  }

  @Test
  void purgesAuthenticatedHistoryAfter30DaysAndGuestHistoryAfter7Days() {
    ChatConversationEntity user = conversation(10L, null, ChatConversationStatus.ARCHIVED);
    ChatConversationEntity guest =
        conversation(null, "guest-hash", ChatConversationStatus.ARCHIVED);
    when(conversationRepository.findByStatusAndArchivedAtBefore(
            ChatConversationStatus.ARCHIVED, atNow().minusDays(30)))
        .thenReturn(List.of(user, guest));
    when(conversationRepository.findByStatusAndArchivedAtBefore(
            ChatConversationStatus.ARCHIVED, atNow().minusDays(7)))
        .thenReturn(List.of(user, guest));

    int purged = facade.purgeArchivedConversations();

    assertThat(purged).isEqualTo(2);
    verify(conversationRepository).delete(user);
    verify(conversationRepository).delete(guest);
  }

  @Test
  void recoversCrashedProcessingTurnsWithoutMakingThemMemoryEligible() {
    ChatTurnEntity stale = new ChatTurnEntity();
    stale.setId(UUID.randomUUID());
    stale.setStatus(ChatTurnStatus.PROCESSING);
    stale.setMemoryEligible(true);
    stale.setUpdatedAt(atNow().minusMinutes(3));
    when(turnRepository.findByStatusAndUpdatedAtBefore(
            ChatTurnStatus.PROCESSING, atNow().minusMinutes(2)))
        .thenReturn(List.of(stale));

    int recovered = facade.recoverStaleTurns();

    assertThat(recovered).isEqualTo(1);
    assertThat(stale.getStatus()).isEqualTo(ChatTurnStatus.FAILED);
    assertThat(stale.isMemoryEligible()).isFalse();
    assertThat(stale.getUpdatedAt()).isEqualTo(atNow());
    verify(turnRepository).save(stale);
  }

  @Test
  void staleSummaryClaimReturnsToPendingWithoutCallingTheProvider() {
    ChatConversationEntity stale = conversation(10L, null, ChatConversationStatus.ACTIVE);
    stale.setSummaryStatus(ChatSummaryStatus.PROCESSING);
    stale.setSummaryClaimedAt(atNow().minusMinutes(3));
    when(conversationRepository.findBySummaryStatus(
            org.mockito.ArgumentMatchers.eq(ChatSummaryStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of());
    when(conversationRepository.findBySummaryStatus(
            org.mockito.ArgumentMatchers.eq(ChatSummaryStatus.PROCESSING), any(Pageable.class)))
        .thenReturn(List.of(stale));

    int updated = facade.refreshSummaries();

    assertThat(updated).isZero();
    assertThat(stale.getSummaryStatus()).isEqualTo(ChatSummaryStatus.PENDING);
    assertThat(stale.getSummaryClaimedAt()).isNull();
    verify(conversationRepository).save(stale);
    verify(summaryService, never()).summarize(any(), anyInt(), any());
  }

  @Test
  void summaryFailureStopsRetryingAtConfiguredAttemptLimit() {
    ChatConversationEntity pending = conversation(10L, null, ChatConversationStatus.ACTIVE);
    pending.setSummaryStatus(ChatSummaryStatus.PENDING);
    pending.setSummaryAttempts(3);
    pending.setNextSequence(4L);
    ChatMessageEntity message = new ChatMessageEntity();
    message.setSequenceNumber(1L);
    message.setRole(ChatMessageRole.USER);
    message.setContent("Need a morning flight");
    when(conversationRepository.findBySummaryStatus(
            org.mockito.ArgumentMatchers.eq(ChatSummaryStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of(pending));
    when(conversationRepository.findBySummaryStatus(
            org.mockito.ArgumentMatchers.eq(ChatSummaryStatus.PROCESSING), any(Pageable.class)))
        .thenReturn(List.of());
    when(conversationRepository.updateSummaryStatus(
            pending.getId(), ChatSummaryStatus.PENDING, ChatSummaryStatus.PROCESSING, atNow()))
        .thenReturn(1);
    when(conversationRepository.findById(pending.getId()))
        .thenReturn(Optional.of(pending), Optional.of(pending));
    when(messageRepository.findRecentEligibleMessages(
            org.mockito.ArgumentMatchers.eq(pending.getId()),
            org.mockito.ArgumentMatchers.eq(0L),
            any(Pageable.class)))
        .thenReturn(List.of(message));
    when(summaryService.summarize(any(), anyInt(), any()))
        .thenThrow(new IllegalStateException("provider unavailable"));

    int updated = facade.refreshSummaries();

    assertThat(updated).isZero();
    assertThat(pending.getSummaryStatus()).isEqualTo(ChatSummaryStatus.FAILED);
    assertThat(pending.getSummaryClaimedAt()).isNull();
  }

  @Test
  void authenticatedUserCannotReadAnotherUsersConversation() {
    UUID conversationId = UUID.randomUUID();
    ChatOwner owner = new ChatOwner(42L, null, null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(ownerResolver.resolve(null, request, response)).thenReturn(owner);
    when(conversationRepository.findByIdAndUserId(conversationId, 42L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> facade.messages(conversationId, null, request, response, null, 20))
        .isInstanceOf(AppException.class)
        .satisfies(
            exception -> {
              AppException appException = (AppException) exception;
              assertThat(appException.getCode()).isEqualTo("CHAT_CONVERSATION_NOT_FOUND");
              assertThat(appException.getStatusCode()).isEqualTo(404);
            });
    verify(conversationRepository, never()).findByIdAndGuestTokenHash(any(), any());
  }

  private ChatConversationEntity conversation(
      Long userId, String guestHash, ChatConversationStatus status) {
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setId(UUID.randomUUID());
    conversation.setUserId(userId);
    conversation.setGuestTokenHash(guestHash);
    conversation.setStatus(status);
    conversation.setSummaryStatus(ChatSummaryStatus.IDLE);
    conversation.setLastActiveAt(atNow());
    conversation.setCreatedAt(atNow());
    conversation.setUpdatedAt(atNow());
    return conversation;
  }

  private OffsetDateTime atNow() {
    return OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
  }
}
