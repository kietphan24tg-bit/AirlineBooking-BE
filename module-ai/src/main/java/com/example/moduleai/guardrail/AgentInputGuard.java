package com.example.moduleai.guardrail;

import com.example.moduleai.config.AiAgentGuardrailProperties;
import com.example.moduleai.dto.ChatRequest;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AgentInputGuard {

  private final AiAgentGuardrailProperties properties;

  public AgentInputGuard(AiAgentGuardrailProperties properties) {
    this.properties = properties;
  }

  public GuardrailDecision assess(ChatRequest request) {
    String message = request.message();
    if (message == null || message.isBlank()) {
      return GuardrailDecision.block("EMPTY_MESSAGE", "Vui long gui noi dung can ho tro.");
    }
    if (message.length() > properties.getMaxMessageLength()) {
      return GuardrailDecision.block(
          "MESSAGE_TOO_LARGE",
          "Nội dung quá dài cho một yêu cầu chatbot. Bạn vui lòng rút gọn và gửi lại.");
    }
    if (containsForbiddenControlCharacters(message)) {
      return GuardrailDecision.block(
          "MALFORMED_INPUT",
          "Yêu cầu chứa ký tự không hợp lệ. Bạn vui lòng gửi lại nội dung dạng văn bản thông thường.");
    }
    if (containsInvalidSurrogatePair(message)) {
      return GuardrailDecision.block(
          "MALFORMED_INPUT", "Yeu cau chua ky tu Unicode khong hop le. Ban vui long gui lai.");
    }
    if (looksLikeSecurityBypass(message)) {
      return GuardrailDecision.block(
          "PROMPT_INJECTION_ATTEMPT",
          "Toi khong the thuc hien yeu cau thay doi quy tac he thong hoac bo qua xac nhan.");
    }
    return GuardrailDecision.allow();
  }

  private boolean containsForbiddenControlCharacters(String value) {
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (Character.isISOControl(current)
          && current != '\n'
          && current != '\r'
          && current != '\t') {
        return true;
      }
    }
    return false;
  }

  private boolean containsInvalidSurrogatePair(String value) {
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (Character.isHighSurrogate(current)) {
        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
          return true;
        }
        index++;
      } else if (Character.isLowSurrogate(current)) {
        return true;
      }
    }
    return false;
  }

  private boolean looksLikeSecurityBypass(String value) {
    String normalized = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    return normalized.contains("ignore previous instructions")
        || normalized.contains("ignore all instructions")
        || normalized.contains("reveal system prompt")
        || normalized.contains("show system prompt")
        || normalized.contains("bypass confirmation")
        || normalized.contains("disable confirmation")
        || normalized.contains("bypass authentication")
        || normalized.contains("reveal jwt")
        || normalized.contains("reveal api key");
  }
}
