package com.example.moduleai.conversation;

import java.util.List;

public record ChatMemorySnapshot(
    String summary, ChatStructuredState state, List<ChatMessageView> recentMessages) {

  public boolean hasHistory() {
    return (summary != null && !summary.isBlank())
        || (recentMessages != null && !recentMessages.isEmpty());
  }
}
