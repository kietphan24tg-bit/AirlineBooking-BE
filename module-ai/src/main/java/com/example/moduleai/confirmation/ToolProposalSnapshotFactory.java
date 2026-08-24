package com.example.moduleai.confirmation;

import com.example.moduleai.guardrail.AgentPayloadSanitizer;
import com.example.moduleai.tool.core.AgentToolContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ToolProposalSnapshotFactory {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;
  private final AgentPayloadSanitizer payloadSanitizer;
  private final ToolConfirmationRegistry confirmationRegistry;

  public ToolProposalSnapshotFactory(
      ObjectMapper objectMapper,
      AgentPayloadSanitizer payloadSanitizer,
      ToolConfirmationRegistry confirmationRegistry) {
    this.objectMapper = objectMapper;
    this.payloadSanitizer = payloadSanitizer;
    this.confirmationRegistry = confirmationRegistry;
  }

  public Map<String, Object> createSnapshot(
      String toolName, Object input, AgentToolContext context) {
    Object presented = confirmationRegistry.present(toolName, input, context);
    Map<String, Object> raw = objectMapper.convertValue(presented, MAP_TYPE);
    if (raw == null) {
      raw = new LinkedHashMap<>();
    }
    raw.put("tool", toolName);
    return objectMapper.convertValue(payloadSanitizer.sanitize(raw), MAP_TYPE);
  }

  public String summary(String toolName, Map<String, Object> snapshot) {
    Object explicitSummary = snapshot.get("summary");
    if (explicitSummary instanceof String summary && !summary.isBlank()) {
      return summary;
    }
    Object bookingId = snapshot.get("bookingId");
    if (bookingId != null) {
      return "Confirm " + toolName.replace('_', ' ') + " for booking " + bookingId;
    }
    return "Confirm " + toolName.replace('_', ' ');
  }
}
