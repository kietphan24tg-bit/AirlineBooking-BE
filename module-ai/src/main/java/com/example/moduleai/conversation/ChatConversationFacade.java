package com.example.moduleai.conversation;

import com.example.common.exception.AppException;
import com.example.moduleai.agent.AgentRequestContextFactory;
import com.example.moduleai.agent.AgentToolOrchestrator;
import com.example.moduleai.agent.IntentRouter;
import com.example.moduleai.agent.RoutingDecision;
import com.example.moduleai.chat.ChatResponseMapper;
import com.example.moduleai.chat.ChatService;
import com.example.moduleai.config.AiChatProperties;
import com.example.moduleai.confirmation.ToolConfirmationService;
import com.example.moduleai.dto.AnswerType;
import com.example.moduleai.dto.ChatConversationRef;
import com.example.moduleai.dto.ChatRequest;
import com.example.moduleai.dto.RagChatResponse;
import com.example.moduleai.guardrail.AgentConcurrencyLimiter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ChatConversationFacade {

  private final ChatConversationRepository conversationRepository;
  private final ChatTurnRepository turnRepository;
  private final ChatMessageRepository messageRepository;
  private final ChatOwnerResolver ownerResolver;
  private final ChatMemoryAssembler memoryAssembler;
  private final ChatContextualizationService contextualizationService;
  private final ChatSummaryService chatSummaryService;
  private final ChatStructuredStateProjector stateProjector;
  private final IntentRouter intentRouter;
  private final ChatService chatService;
  private final AgentToolOrchestrator agentToolOrchestrator;
  private final AgentRequestContextFactory contextFactory;
  private final ChatResponseMapper responseMapper;
  private final ToolConfirmationService toolConfirmationService;
  private final AgentConcurrencyLimiter concurrencyLimiter;
  private final AiChatProperties properties;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;

  public ChatConversationFacade(
      ChatConversationRepository conversationRepository,
      ChatTurnRepository turnRepository,
      ChatMessageRepository messageRepository,
      ChatOwnerResolver ownerResolver,
      ChatMemoryAssembler memoryAssembler,
      ChatContextualizationService contextualizationService,
      ChatSummaryService chatSummaryService,
      ChatStructuredStateProjector stateProjector,
      IntentRouter intentRouter,
      ChatService chatService,
      AgentToolOrchestrator agentToolOrchestrator,
      AgentRequestContextFactory contextFactory,
      ChatResponseMapper responseMapper,
      ToolConfirmationService toolConfirmationService,
      AgentConcurrencyLimiter concurrencyLimiter,
      AiChatProperties properties,
      ObjectMapper objectMapper,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.conversationRepository = conversationRepository;
    this.turnRepository = turnRepository;
    this.messageRepository = messageRepository;
    this.ownerResolver = ownerResolver;
    this.memoryAssembler = memoryAssembler;
    this.contextualizationService = contextualizationService;
    this.chatSummaryService = chatSummaryService;
    this.stateProjector = stateProjector;
    this.intentRouter = intentRouter;
    this.chatService = chatService;
    this.agentToolOrchestrator = agentToolOrchestrator;
    this.contextFactory = contextFactory;
    this.responseMapper = responseMapper;
    this.toolConfirmationService = toolConfirmationService;
    this.concurrencyLimiter = concurrencyLimiter;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public RagChatResponse chat(
      ChatRequest request,
      Authentication authentication,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    if (!properties.isPersistenceEnabled()) {
      return chatWithoutPersistence(request, authentication, servletRequest, servletResponse);
    }
    ChatOwner owner = ownerResolver.resolve(authentication, servletRequest, servletResponse);
    try (AgentConcurrencyLimiter.Slot ignored =
        concurrencyLimiter.acquireOwnerSlot(owner.ownerKey(), "chat")) {
      OffsetDateTime now = now();
      archiveExpiredActive(owner, now);
      ProcessingTurn turn = beginTurn(owner, request, now);
      if (turn.completedResponse() != null) {
        return withConversation(turn.completedResponse(), turn.conversation());
      }
      try {
        ChatMemorySnapshot memory = memoryAssembler.assemble(turn.conversation());
        String rewritten = contextualizationService.rewrite(request.message().trim(), memory);
        RoutingDecision decision = intentRouter.route(rewritten);
        RagChatResponse response =
            route(
                decision, request, rewritten, authentication, memory, turn.conversation().getId());
        completeTurn(turn, response);
        return withConversation(response, reloadConversation(turn.conversation().getId()));
      } catch (RuntimeException exception) {
        markTurnFailed(turn.turn().getId());
        throw exception;
      }
    }
  }

  public ChatConversationView getActive(
      Authentication authentication,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    ChatOwner owner = ownerResolver.resolve(authentication, servletRequest, servletResponse);
    archiveExpiredActive(owner, now());
    return findActive(owner).map(this::toView).orElse(null);
  }

  public ChatConversationListView list(
      Authentication authentication,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse,
      String cursor,
      int limit) {
    ChatOwner owner = ownerResolver.resolve(authentication, servletRequest, servletResponse);
    int page = parseCursor(cursor);
    int size = Math.max(1, Math.min(limit, 50));
    List<ChatConversationEntity> items =
        owner.authenticated()
            ? conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(
                owner.userId(), PageRequest.of(page, size))
            : conversationRepository.findAllByGuestTokenHashOrderByUpdatedAtDesc(
                owner.guestTokenHash(), PageRequest.of(page, size));
    String nextCursor = items.size() < size ? null : String.valueOf(page + 1);
    return new ChatConversationListView(items.stream().map(this::toView).toList(), nextCursor);
  }

  public ChatMessagePageView messages(
      UUID conversationId,
      Authentication authentication,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse,
      Long beforeSequence,
      int limit) {
    ChatOwner owner = ownerResolver.resolve(authentication, servletRequest, servletResponse);
    requireOwnedConversation(conversationId, owner);
    int size = Math.max(1, Math.min(limit, 50));
    List<ChatMessageEntity> page =
        messageRepository.findPage(conversationId, beforeSequence, PageRequest.of(0, size));
    Long nextBefore = page.size() < size ? null : page.get(page.size() - 1).getSequenceNumber();
    List<ChatMessageEntity> ordered =
        page.stream()
            .sorted(Comparator.comparingLong(ChatMessageEntity::getSequenceNumber))
            .toList();
    return new ChatMessagePageView(
        ordered.stream()
            .map(
                message ->
                    new ChatMessageView(
                        message.getSequenceNumber(),
                        message.getRole().name(),
                        message.getContent(),
                        message.getCreatedAt()))
            .toList(),
        nextBefore);
  }

  public ChatConversationView archiveActive(
      Authentication authentication,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    ChatOwner owner = ownerResolver.resolve(authentication, servletRequest, servletResponse);
    ChatConversationEntity conversation = findActive(owner).orElse(null);
    if (conversation == null) {
      return null;
    }
    return toView(archiveConversation(conversation, ChatArchiveReason.USER_ARCHIVED, now()));
  }

  public void deleteConversation(
      UUID conversationId,
      Authentication authentication,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    ChatOwner owner = ownerResolver.resolve(authentication, servletRequest, servletResponse);
    ChatConversationEntity conversation = requireOwnedConversation(conversationId, owner);
    if (conversation.getStatus() == ChatConversationStatus.ACTIVE) {
      archiveConversation(conversation, ChatArchiveReason.USER_DELETED, now());
    }
    conversationRepository.delete(conversation);
  }

  public int archiveExpiredConversations() {
    OffsetDateTime cutoff = now().minus(properties.getInactivityTimeout());
    int archived = 0;
    for (ChatConversationEntity conversation :
        conversationRepository.findByStatusAndLastActiveAtBefore(
            ChatConversationStatus.ACTIVE, cutoff)) {
      archiveConversation(conversation, ChatArchiveReason.INACTIVITY_TIMEOUT, now());
      archived++;
    }
    return archived;
  }

  public int purgeArchivedConversations() {
    OffsetDateTime now = now();
    int purged = 0;
    for (ChatConversationEntity conversation :
        conversationRepository.findByStatusAndArchivedAtBefore(
            ChatConversationStatus.ARCHIVED, now.minus(properties.getUserRetention()))) {
      if (conversation.getUserId() != null) {
        conversationRepository.delete(conversation);
        purged++;
      }
    }
    for (ChatConversationEntity conversation :
        conversationRepository.findByStatusAndArchivedAtBefore(
            ChatConversationStatus.ARCHIVED, now.minus(properties.getGuestRetention()))) {
      if (conversation.getGuestTokenHash() != null) {
        conversationRepository.delete(conversation);
        purged++;
      }
    }
    return purged;
  }

  public int refreshSummaries() {
    int updated = 0;
    OffsetDateTime claimAt = now();
    for (ChatConversationEntity conversation :
        conversationRepository.findBySummaryStatus(
            ChatSummaryStatus.PENDING, PageRequest.of(0, properties.getSummaryBatchSize()))) {
      int claimed =
          conversationRepository.updateSummaryStatus(
              conversation.getId(),
              ChatSummaryStatus.PENDING,
              ChatSummaryStatus.PROCESSING,
              claimAt);
      if (claimed != 1) {
        continue;
      }
      try {
        ChatConversationEntity fresh = reloadConversation(conversation.getId());
        String summary = summarize(fresh);
        fresh.setSummary(summary);
        fresh.setSummaryStatus(ChatSummaryStatus.IDLE);
        fresh.setSummaryAttempts(0);
        fresh.setSummarizedThroughSequence(fresh.getNextSequence() - 1);
        fresh.setSummaryClaimedAt(null);
        fresh.setUpdatedAt(now());
        conversationRepository.save(fresh);
        updated++;
      } catch (RuntimeException exception) {
        ChatConversationEntity failed = reloadConversation(conversation.getId());
        failed.setSummaryStatus(
            failed.getSummaryAttempts() < properties.getSummaryMaxAttempts()
                ? ChatSummaryStatus.PENDING
                : ChatSummaryStatus.FAILED);
        failed.setSummaryClaimedAt(null);
        failed.setUpdatedAt(now());
        conversationRepository.save(failed);
      }
    }
    for (ChatConversationEntity conversation :
        conversationRepository.findBySummaryStatus(
            ChatSummaryStatus.PROCESSING, PageRequest.of(0, properties.getSummaryBatchSize()))) {
      if (conversation.getSummaryClaimedAt() != null
          && conversation
              .getSummaryClaimedAt()
              .isBefore(now().minus(properties.getSummaryClaimTimeout()))) {
        conversation.setSummaryStatus(ChatSummaryStatus.PENDING);
        conversation.setSummaryClaimedAt(null);
        conversation.setUpdatedAt(now());
        conversationRepository.save(conversation);
      }
    }
    return updated;
  }

  public int recoverStaleTurns() {
    OffsetDateTime cutoff = now().minus(properties.getSummaryClaimTimeout());
    int recovered = 0;
    for (ChatTurnEntity turn :
        turnRepository.findByStatusAndUpdatedAtBefore(ChatTurnStatus.PROCESSING, cutoff)) {
      turn.setStatus(ChatTurnStatus.FAILED);
      turn.setMemoryEligible(false);
      turn.setUpdatedAt(now());
      turnRepository.save(turn);
      recovered++;
    }
    return recovered;
  }

  private void markTurnFailed(UUID turnId) {
    transactionTemplate.executeWithoutResult(
        status ->
            turnRepository
                .findById(turnId)
                .filter(turn -> turn.getStatus() == ChatTurnStatus.PROCESSING)
                .ifPresent(
                    turn -> {
                      turn.setStatus(ChatTurnStatus.FAILED);
                      turn.setMemoryEligible(false);
                      turn.setUpdatedAt(now());
                      turnRepository.save(turn);
                    }));
  }

  private RagChatResponse chatWithoutPersistence(
      ChatRequest request,
      Authentication authentication,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    ChatOwner owner = ownerResolver.resolve(authentication, servletRequest, servletResponse);
    try (AgentConcurrencyLimiter.Slot ignored =
        concurrencyLimiter.acquireOwnerSlot(owner.ownerKey(), "chat")) {
      RoutingDecision decision = intentRouter.route(request.message().trim());
      RagChatResponse response =
          switch (decision.category()) {
            case STATIC_POLICY_RAG -> chatService.chat(request);
            case DYNAMIC_API_REQUIRED ->
                agentToolOrchestrator.handle(request, decision, authentication);
            case UNSAFE_OR_UNCONFIRMED_FALLBACK ->
                responseMapper.fallback(
                    "Xin loi, toi chua co thong tin chinh thuc de xac nhan van de nay.",
                    "UNSAFE_OR_UNCONFIRMED");
          };
      return responseMapper.withConversation(response, null);
    }
  }

  private void archiveExpiredActive(ChatOwner owner, OffsetDateTime now) {
    findActive(owner)
        .filter(
            conversation ->
                conversation
                    .getLastActiveAt()
                    .plus(properties.getInactivityTimeout())
                    .isBefore(now))
        .ifPresent(
            conversation ->
                archiveConversation(conversation, ChatArchiveReason.INACTIVITY_TIMEOUT, now));
  }

  private ProcessingTurn beginTurn(ChatOwner owner, ChatRequest request, OffsetDateTime now) {
    return transactionTemplate.execute(
        status -> {
          ChatConversationEntity conversation =
              findActive(owner).orElseGet(() -> createActive(owner, request, now));
          String clientMessageId =
              request.clientMessageId() == null || request.clientMessageId().isBlank()
                  ? UUID.randomUUID().toString()
                  : request.clientMessageId().trim();
          ChatTurnEntity existing =
              turnRepository
                  .findByConversationIdAndClientMessageId(conversation.getId(), clientMessageId)
                  .orElse(null);
          if (existing != null) {
            if (existing.getStatus() == ChatTurnStatus.COMPLETED
                && existing.getSafeResponse() != null) {
              return new ProcessingTurn(
                  conversation,
                  existing,
                  readResponse(existing.getSafeResponse()),
                  clientMessageId,
                  conversation.getNextSequence());
            }
            if (existing.getStatus() == ChatTurnStatus.PROCESSING) {
              throw new AppException(
                  "CHAT_TURN_IN_PROGRESS",
                  "A previous chat turn with the same clientMessageId is still processing",
                  409);
            }
            throw new AppException(
                "CHAT_TURN_FAILED_RETRY_REQUIRES_NEW_ID",
                "The previous turn failed. Retry with a new clientMessageId.",
                409);
          }

          ChatTurnEntity turn = new ChatTurnEntity();
          turn.setId(UUID.randomUUID());
          turn.setConversationId(conversation.getId());
          turn.setClientMessageId(clientMessageId);
          turn.setStatus(ChatTurnStatus.PROCESSING);
          turn.setMemoryEligible(false);
          turn.setCreatedAt(now);
          turn.setUpdatedAt(now);
          turnRepository.save(turn);

          persistMessage(
              conversation,
              turn.getId(),
              conversation.getNextSequence(),
              ChatMessageRole.USER,
              request.message().trim(),
              null,
              now);
          conversation.setNextSequence(conversation.getNextSequence() + 1);
          conversation.setLastActiveAt(now);
          conversation.setUpdatedAt(now);
          conversationRepository.save(conversation);
          return new ProcessingTurn(
              conversation, turn, null, clientMessageId, conversation.getNextSequence());
        });
  }

  private void completeTurn(ProcessingTurn turn, RagChatResponse response) {
    transactionTemplate.executeWithoutResult(
        status -> {
          ChatConversationEntity conversation = reloadConversation(turn.conversation().getId());
          ChatTurnEntity persistentTurn =
              turnRepository.findById(turn.turn().getId()).orElseThrow();
          OffsetDateTime now = now();
          persistMessage(
              conversation,
              persistentTurn.getId(),
              conversation.getNextSequence(),
              ChatMessageRole.ASSISTANT,
              response.answer(),
              Map.of("answerType", response.answerType().name()),
              now);
          conversation.setNextSequence(conversation.getNextSequence() + 1);
          conversation.setLastActiveAt(now);
          conversation.setUpdatedAt(now);
          conversation.setStructuredState(
              writeState(stateProjector.update(readState(conversation), response)));
          maybeMarkSummaryPending(conversation);
          persistentTurn.setStatus(ChatTurnStatus.COMPLETED);
          persistentTurn.setAnswerType(response.answerType());
          persistentTurn.setMemoryEligible(isMemoryEligible(response));
          persistentTurn.setSafeResponse(writeResponse(response));
          persistentTurn.setCompletedAt(now);
          persistentTurn.setUpdatedAt(now);
          turnRepository.save(persistentTurn);
          conversationRepository.save(conversation);
        });
  }

  private RagChatResponse route(
      RoutingDecision decision,
      ChatRequest request,
      String rewritten,
      Authentication authentication,
      ChatMemorySnapshot memory,
      UUID conversationId) {
    RagChatResponse response =
        switch (decision.category()) {
          case STATIC_POLICY_RAG -> chatService.chat(request.message().trim(), rewritten, memory);
          case DYNAMIC_API_REQUIRED ->
              agentToolOrchestrator.handle(
                  request,
                  decision,
                  authentication,
                  memory,
                  contextFactory.from(authentication, conversationId));
          case UNSAFE_OR_UNCONFIRMED_FALLBACK ->
              responseMapper.fallback(
                  "Xin loi, toi chua co thong tin chinh thuc de xac nhan van de nay.",
                  "UNSAFE_OR_UNCONFIRMED");
        };
    return response;
  }

  private ChatConversationEntity createActive(
      ChatOwner owner, ChatRequest request, OffsetDateTime now) {
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setId(UUID.randomUUID());
    conversation.setUserId(owner.userId());
    conversation.setGuestTokenHash(owner.guestTokenHash());
    conversation.setStatus(ChatConversationStatus.ACTIVE);
    conversation.setTitle(titleOf(request.message()));
    conversation.setSummary(null);
    conversation.setStructuredState(writeState(ChatStructuredState.empty()));
    conversation.setSummaryStatus(ChatSummaryStatus.IDLE);
    conversation.setSummarizedThroughSequence(0);
    conversation.setSummaryAttempts(0);
    conversation.setNextSequence(1L);
    conversation.setLastActiveAt(now);
    conversation.setCreatedAt(now);
    conversation.setUpdatedAt(now);
    return conversationRepository.save(conversation);
  }

  private ChatConversationEntity archiveConversation(
      ChatConversationEntity conversation, ChatArchiveReason reason, OffsetDateTime now) {
    return transactionTemplate.execute(
        status -> {
          ChatConversationEntity persistent = reloadConversation(conversation.getId());
          if (persistent.getStatus() == ChatConversationStatus.ARCHIVED) {
            return persistent;
          }
          persistent.setStatus(ChatConversationStatus.ARCHIVED);
          persistent.setArchivedAt(now);
          persistent.setArchiveReason(reason);
          persistent.setUpdatedAt(now);
          ChatConversationEntity saved = conversationRepository.save(persistent);
          toolConfirmationService.expirePendingForConversation(saved.getId());
          return saved;
        });
  }

  private void persistMessage(
      ChatConversationEntity conversation,
      UUID turnId,
      long sequenceNumber,
      ChatMessageRole role,
      String content,
      Object metadata,
      OffsetDateTime now) {
    ChatMessageEntity message = new ChatMessageEntity();
    message.setConversationId(conversation.getId());
    message.setTurnId(turnId);
    message.setSequenceNumber(sequenceNumber);
    message.setRole(role);
    message.setContent(content);
    message.setSafeMetadata(writeJson(metadata));
    message.setCreatedAt(now);
    messageRepository.save(message);
  }

  private void maybeMarkSummaryPending(ChatConversationEntity conversation) {
    List<ChatTurnEntity> completed =
        turnRepository.findByConversationIdAndStatusOrderByCreatedAtDesc(
            conversation.getId(), ChatTurnStatus.COMPLETED);
    long eligibleCount = completed.stream().filter(ChatTurnEntity::isMemoryEligible).count();
    if (eligibleCount > properties.getRecentTurns()
        && conversation.getSummaryStatus() != ChatSummaryStatus.PROCESSING) {
      conversation.setSummaryStatus(ChatSummaryStatus.PENDING);
    }
  }

  private boolean isMemoryEligible(RagChatResponse response) {
    if (response == null) {
      return false;
    }
    return response.answerType() != AnswerType.FALLBACK
        && response.answerType() != AnswerType.AUTHENTICATION_REQUIRED;
  }

  private String summarize(ChatConversationEntity conversation) {
    List<ChatMessageEntity> messages =
        messageRepository.findRecentEligibleMessages(
            conversation.getId(),
            conversation.getSummarizedThroughSequence(),
            PageRequest.of(0, 200));
    messages =
        messages.stream()
            .sorted(Comparator.comparingLong(ChatMessageEntity::getSequenceNumber))
            .toList();
    int transcriptBudget = Math.max(1, properties.getContextBudgetChars());
    StringBuilder transcriptBuilder = new StringBuilder();
    for (ChatMessageEntity message : messages) {
      String line = message.getRole().name() + ": " + message.getContent();
      if (transcriptBuilder.length() > 0) {
        line = "\n" + line;
      }
      if (transcriptBuilder.length() + line.length() > transcriptBudget) {
        break;
      }
      transcriptBuilder.append(line);
    }
    String transcript = transcriptBuilder.toString().trim();
    if (transcript.isBlank()) {
      return conversation.getSummary();
    }
    String summary =
        chatSummaryService.summarize(
            transcript, properties.getSummaryMaxChars(), conversation.getSummary());
    return summary;
  }

  private ChatConversationEntity requireOwnedConversation(UUID conversationId, ChatOwner owner) {
    return owner.authenticated()
        ? conversationRepository
            .findByIdAndUserId(conversationId, owner.userId())
            .orElseThrow(
                () ->
                    new AppException(
                        "CHAT_CONVERSATION_NOT_FOUND", "Conversation was not found", 404))
        : conversationRepository
            .findByIdAndGuestTokenHash(conversationId, owner.guestTokenHash())
            .orElseThrow(
                () ->
                    new AppException(
                        "CHAT_CONVERSATION_NOT_FOUND", "Conversation was not found", 404));
  }

  private java.util.Optional<ChatConversationEntity> findActive(ChatOwner owner) {
    return owner.authenticated()
        ? conversationRepository.findByUserIdAndStatus(
            owner.userId(), ChatConversationStatus.ACTIVE)
        : conversationRepository.findByGuestTokenHashAndStatus(
            owner.guestTokenHash(), ChatConversationStatus.ACTIVE);
  }

  private ChatConversationEntity reloadConversation(UUID conversationId) {
    return conversationRepository.findById(conversationId).orElseThrow();
  }

  private ChatConversationView toView(ChatConversationEntity conversation) {
    return new ChatConversationView(
        conversation.getId(),
        conversation.getStatus().name(),
        conversation.getTitle(),
        conversation.getSummary(),
        conversation.getLastActiveAt(),
        conversation.getStatus() == ChatConversationStatus.ACTIVE
            ? conversation.getLastActiveAt().plus(properties.getInactivityTimeout())
            : null,
        conversation.getArchivedAt(),
        conversation.getArchiveReason() == null ? null : conversation.getArchiveReason().name());
  }

  private RagChatResponse withConversation(
      RagChatResponse response, ChatConversationEntity conversation) {
    ChatConversationRef ref =
        conversation == null
            ? null
            : new ChatConversationRef(
                conversation.getId(),
                conversation.getStatus().name(),
                conversation.getStatus() == ChatConversationStatus.ACTIVE
                    ? conversation.getLastActiveAt().plus(properties.getInactivityTimeout())
                    : null);
    return responseMapper.withConversation(response, ref);
  }

  private ChatStructuredState readState(ChatConversationEntity conversation) {
    if (conversation.getStructuredState() == null || conversation.getStructuredState().isBlank()) {
      return ChatStructuredState.empty();
    }
    try {
      return objectMapper.readValue(conversation.getStructuredState(), ChatStructuredState.class);
    } catch (JsonProcessingException exception) {
      return ChatStructuredState.empty();
    }
  }

  private RagChatResponse readResponse(String json) {
    try {
      return objectMapper.readValue(json, RagChatResponse.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read stored chat response", exception);
    }
  }

  private String writeResponse(RagChatResponse response) {
    return writeJson(
        new RagChatResponse(
            response.answer(),
            response.sources(),
            response.grounded(),
            response.fallbackReason(),
            response.answerType(),
            null,
            response.proposal(),
            response.trace(),
            response.confidence()));
  }

  private String writeState(ChatStructuredState state) {
    return writeJson(state == null ? ChatStructuredState.empty() : state);
  }

  private String writeJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize chat persistence payload", exception);
    }
  }

  private String titleOf(String message) {
    String trimmed = message == null ? "" : message.trim();
    if (trimmed.length() <= 80) {
      return trimmed;
    }
    return trimmed.substring(0, 80);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private int parseCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return 0;
    }
    try {
      int value = Integer.parseInt(cursor);
      if (value < 0) {
        throw new NumberFormatException("cursor must be non-negative");
      }
      return value;
    } catch (NumberFormatException exception) {
      throw new AppException("CHAT_CURSOR_INVALID", "Conversation cursor is invalid", 400);
    }
  }

  private record ProcessingTurn(
      ChatConversationEntity conversation,
      ChatTurnEntity turn,
      RagChatResponse completedResponse,
      String clientMessageId,
      long nextSequence) {}
}
