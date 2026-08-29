package com.example.moduleai.conversation;

import com.example.moduleai.resilience.AiProviderGuard;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ChatSummaryService {

  private final ChatClient chatClient;
  private final AiProviderGuard providerGuard;

  public ChatSummaryService(ChatClient.Builder chatClientBuilder, AiProviderGuard providerGuard) {
    this.chatClient = chatClientBuilder.build();
    this.providerGuard = providerGuard;
  }

  public String summarize(String transcript, int maxChars, String previousSummary) {
    if (transcript == null || transcript.isBlank()) {
      return previousSummary;
    }
    String response =
        providerGuard.execute(
            () ->
                chatClient
                    .prompt()
                    .system(
                        """
                        Summarize the conversation for future follow-up routing.
                        Keep only stable user intent, constraints, selected booking context, and pending actions.
                        Exclude secrets, JWT, raw tool payloads, stack traces, and low-signal chit-chat.
                        Preserve the user's working language when possible.
                        """)
                    .user(
                        """
                        Previous summary:
                        %s

                        Transcript:
                        %s

                        Return only the updated summary in plain text.
                        """
                            .formatted(previousSummary == null ? "" : previousSummary, transcript))
                    .call()
                    .content());
    if (response == null || response.isBlank()) {
      return previousSummary;
    }
    String summary = response.trim();
    return summary.length() <= maxChars ? summary : summary.substring(0, maxChars);
  }
}
