package com.example.moduleai.conversation;

import com.example.moduleai.config.AiChatProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChatConversationMaintenanceJob {

  private final ChatConversationFacade facade;
  private final AiChatProperties properties;

  public ChatConversationMaintenanceJob(
      ChatConversationFacade facade, AiChatProperties properties) {
    this.facade = facade;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${app.ai.chat.maintenance-delay:1m}")
  void maintain() {
    if (!properties.isPersistenceEnabled()) {
      return;
    }
    facade.archiveExpiredConversations();
    facade.recoverStaleTurns();
    facade.refreshSummaries();
    facade.purgeArchivedConversations();
  }
}
