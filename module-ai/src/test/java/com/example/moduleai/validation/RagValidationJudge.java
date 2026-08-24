package com.example.moduleai.validation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;

final class RagValidationJudge {

  private static final int BATCH_SIZE = 6;

  private final ChatClient chatClient;
  private final long delayMillis;

  RagValidationJudge(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
    this.delayMillis = Math.max(0L, Long.getLong("rag.validation.delay-millis", 5_000L));
  }

  Map<String, JudgeDecision> evaluate(List<JudgeInput> inputs) {
    Map<String, JudgeDecision> decisions = new LinkedHashMap<>();
    for (int start = 0; start < inputs.size(); start += BATCH_SIZE) {
      List<JudgeInput> batch = inputs.subList(start, Math.min(start + BATCH_SIZE, inputs.size()));
      decisions.putAll(evaluateBatch(batch));
      if (start + BATCH_SIZE < inputs.size()) {
        pauseBetweenBatches();
      }
    }
    return decisions;
  }

  private Map<String, JudgeDecision> evaluateBatch(List<JudgeInput> batch) {
    String response;
    try {
      response =
          chatClient.prompt().system(systemPrompt()).user(renderBatch(batch)).call().content();
    } catch (RuntimeException ex) {
      return retryBatchIndividually(batch);
    }

    Map<String, JudgeDecision> parsed = parse(response);
    for (JudgeInput input : batch) {
      if (!parsed.containsKey(input.executionId())) {
        parsed.putAll(retrySingle(input));
      }
      parsed.putIfAbsent(
          input.executionId(),
          new JudgeDecision(
              ValidationVerdict.NEEDS_REVIEW, "Judge did not return a verdict for this case."));
    }
    return parsed;
  }

  private Map<String, JudgeDecision> retrySingle(JudgeInput input) {
    try {
      String response =
          chatClient
              .prompt()
              .system(systemPrompt())
              .user(renderBatch(List.of(input)))
              .call()
              .content();
      return parse(response);
    } catch (RuntimeException ex) {
      return Map.of(
          input.executionId(),
          new JudgeDecision(ValidationVerdict.BLOCKED, "Judge model error: " + conciseMessage(ex)));
    }
  }

  private Map<String, JudgeDecision> retryBatchIndividually(List<JudgeInput> batch) {
    Map<String, JudgeDecision> decisions = new LinkedHashMap<>();
    for (JudgeInput input : batch) {
      decisions.putAll(retrySingle(input));
    }
    return decisions;
  }

  private String systemPrompt() {
    return """
        You are a strict QA judge for an airline-booking RAG chatbot.
        Judge only whether the answer satisfies the expected behavior and stays grounded.

        Rules:
        - RAG: PASS only when all material facts in expected behavior are present and no unsupported fact is added.
        - FALLBACK: PASS only when the answer clearly avoids guessing and directs the user to an
          official source or support when appropriate.
        - API routing and endpoint correctness are checked deterministically outside this judge. PASS
          when the answer does not invent current personal/dynamic data and directs the user to a
          system lookup, Manage Booking, authentication, or a genuinely missing identifier. Do not
          require exposing an internal endpoint or the literal word API.
        - EDGE: PASS when the policy conclusion matches expected behavior. If the case needs current
          booking data, requiring lookup is correct.
        - LANGUAGE: the expected behavior begins with [LANG=en], [LANG=fr], or [LANG=vi]. PASS only
          when all material facts are correct and the answer is written primarily in that target
          language. Ignore technical aliases, status codes, route names, and proper nouns when
          deciding the language.
        - Use NEEDS_REVIEW only for genuine ambiguity in the supplied expected behavior.
        - Use FAIL for omissions, contradictions, fabricated facts, or unsafe certainty.

        Return exactly one line per case, preserving its ID:
        ID|PASS|short reason
        ID|FAIL|short reason
        ID|NEEDS_REVIEW|short reason
        Do not use Markdown fences or extra lines.
        """;
  }

  private String renderBatch(List<JudgeInput> batch) {
    StringBuilder prompt = new StringBuilder();
    for (JudgeInput input : batch) {
      prompt
          .append("ID: ")
          .append(input.executionId())
          .append('\n')
          .append("SECTION: ")
          .append(input.section())
          .append('\n')
          .append("QUESTION: ")
          .append(input.question())
          .append('\n')
          .append("EXPECTED: ")
          .append(input.expectedBehavior())
          .append('\n')
          .append("ANSWER: ")
          .append(input.answer())
          .append("\nSOURCES: ")
          .append(String.join(", ", input.sources()))
          .append("\n\n");
    }
    return prompt.toString().trim();
  }

  private Map<String, JudgeDecision> parse(String response) {
    Map<String, JudgeDecision> decisions = new LinkedHashMap<>();
    if (response == null || response.isBlank()) {
      return decisions;
    }
    for (String rawLine : response.replace("```", "").split("\\R")) {
      String[] parts = rawLine.trim().split("\\|", 3);
      if (parts.length != 3) {
        continue;
      }
      try {
        ValidationVerdict verdict =
            ValidationVerdict.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
        decisions.put(parts[0].trim(), new JudgeDecision(verdict, parts[2].trim()));
      } catch (IllegalArgumentException ignored) {
        // A malformed judge line is converted to NEEDS_REVIEW by the caller.
      }
    }
    return decisions;
  }

  private Map<String, JudgeDecision> blockedBatch(List<JudgeInput> batch, String reason) {
    Map<String, JudgeDecision> decisions = new LinkedHashMap<>();
    for (JudgeInput input : batch) {
      decisions.put(input.executionId(), new JudgeDecision(ValidationVerdict.BLOCKED, reason));
    }
    return decisions;
  }

  private void pauseBetweenBatches() {
    if (delayMillis == 0) {
      return;
    }
    try {
      Thread.sleep(delayMillis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Live validation judge was interrupted", ex);
    }
  }

  private String conciseMessage(RuntimeException ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      return ex.getClass().getSimpleName();
    }
    return message.replaceAll("\\s+", " ").trim();
  }

  record JudgeInput(
      String executionId,
      String section,
      String question,
      String expectedBehavior,
      String answer,
      List<String> sources) {}

  record JudgeDecision(ValidationVerdict verdict, String reason) {}

  enum ValidationVerdict {
    PASS,
    FAIL,
    NEEDS_REVIEW,
    BLOCKED
  }
}
