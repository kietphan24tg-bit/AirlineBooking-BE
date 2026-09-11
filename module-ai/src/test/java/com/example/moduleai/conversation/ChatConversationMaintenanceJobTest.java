package com.example.moduleai.conversation;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.moduleai.config.AiChatProperties;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class ChatConversationMaintenanceJobTest {

  @Test
  void skipsAllMaintenanceWhenPersistenceIsDisabled() {
    ChatConversationFacade facade = Mockito.mock(ChatConversationFacade.class);
    AiChatProperties properties = new AiChatProperties();
    properties.setPersistenceEnabled(false);

    new ChatConversationMaintenanceJob(facade, properties).maintain();

    verify(facade, never()).archiveExpiredConversations();
    verify(facade, never()).recoverStaleTurns();
    verify(facade, never()).refreshSummaries();
    verify(facade, never()).purgeArchivedConversations();
  }

  @Test
  void runsLifecycleRecoverySummaryAndPurgeInOrder() {
    ChatConversationFacade facade = Mockito.mock(ChatConversationFacade.class);
    AiChatProperties properties = new AiChatProperties();
    properties.setPersistenceEnabled(true);

    new ChatConversationMaintenanceJob(facade, properties).maintain();

    InOrder order = inOrder(facade);
    order.verify(facade).archiveExpiredConversations();
    order.verify(facade).recoverStaleTurns();
    order.verify(facade).refreshSummaries();
    order.verify(facade).purgeArchivedConversations();
  }
}
