package com.example.moduleai.conversation;

import com.example.moduleai.resilience.AiProviderGuard;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ChatContextualizationService {

  private final ChatClient chatClient;
  private final AiProviderGuard providerGuard;

  public ChatContextualizationService(
      ChatClient.Builder chatClientBuilder, AiProviderGuard providerGuard) {
    this.chatClient = chatClientBuilder.build();
    this.providerGuard = providerGuard;
  }

  public String rewrite(String message, ChatMemorySnapshot memory) {
    if (memory == null || !memory.hasHistory()) {
      return message;
    }
    try {
      String rewritten =
          providerGuard.execute(
              () ->
                  chatClient
                      .prompt()
                      .system(
                          """
                           Rewrite the user's last message into a standalone question for retrieval and routing.
                           Preserve the original language. Do not answer. Do not add facts.
                           Preserve the requested action explicitly. For a flight-search follow-up, include the
                           words "tim chuyen bay" or "search flights", plus the prior route and date.
                           If the message is already standalone, return it unchanged.
                           """)
                      .user(buildPrompt(message, memory))
                      .call()
                      .content());
      return rewritten == null || rewritten.isBlank() ? message : rewritten.trim();
    } catch (RuntimeException exception) {
      return message;
    }
  }

  private String buildPrompt(String message, ChatMemorySnapshot memory) {
    String recent =
        memory.recentMessages().stream()
            .map(item -> item.role() + ": " + item.content())
            .collect(Collectors.joining("\n"));
    return """
        Summary:
        %s

        Structured state:
        %s

        Recent messages:
        %s

        Last user message:
        %s
        """
        .formatted(
            memory.summary() == null ? "" : memory.summary(), memory.state(), recent, message);
  }
}
