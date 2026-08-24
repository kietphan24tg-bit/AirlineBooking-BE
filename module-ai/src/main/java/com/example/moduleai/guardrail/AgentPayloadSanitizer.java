package com.example.moduleai.guardrail;

import com.example.moduleai.config.AiAgentGuardrailProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Produces bounded, presentation-safe data without changing scalar business values. */
@Component
public class AgentPayloadSanitizer {

  private static final String MASKED_VALUE = "***";

  private final ObjectMapper objectMapper;
  private final AiAgentGuardrailProperties properties;

  public AgentPayloadSanitizer(ObjectMapper objectMapper, AiAgentGuardrailProperties properties) {
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  public Object sanitize(Object value) {
    JsonNode sanitized = sanitizeNode(objectMapper.valueToTree(value), null);
    try {
      if (objectMapper.writeValueAsBytes(sanitized).length <= properties.getMaxOutputJsonBytes()) {
        return objectMapper.convertValue(sanitized, Object.class);
      }
    } catch (JsonProcessingException ignored) {
      // Return the bounded fallback below when serialization fails.
    }
    return Map.of("truncated", true, "reason", "PAYLOAD_TOO_LARGE");
  }

  public String sanitizeText(String value) {
    if (value == null) {
      return null;
    }
    return truncate(value);
  }

  private JsonNode sanitizeNode(JsonNode node, String fieldName) {
    if (node == null || node.isNull()) {
      return JsonNodeFactory.instance.nullNode();
    }
    if (isSensitive(fieldName)) {
      return TextNode.valueOf(MASKED_VALUE);
    }
    if (node.isTextual()) {
      return TextNode.valueOf(truncate(node.textValue()));
    }
    if (node.isArray()) {
      ArrayNode sanitized = JsonNodeFactory.instance.arrayNode();
      int limit = Math.max(0, properties.getMaxOutputCollectionItems());
      for (int index = 0; index < node.size() && index < limit; index++) {
        sanitized.add(sanitizeNode(node.get(index), fieldName));
      }
      if (node.size() > limit) {
        sanitized.addObject().put("truncated", true).put("omittedItems", node.size() - limit);
      }
      return sanitized;
    }
    if (node.isObject()) {
      ObjectNode sanitized = JsonNodeFactory.instance.objectNode();
      int limit = Math.max(0, properties.getMaxOutputCollectionItems());
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      int count = 0;
      while (fields.hasNext() && count < limit) {
        Map.Entry<String, JsonNode> field = fields.next();
        sanitized.set(field.getKey(), sanitizeNode(field.getValue(), field.getKey()));
        count++;
      }
      if (fields.hasNext()) {
        sanitized.put("truncated", true);
      }
      return sanitized;
    }
    return node;
  }

  private boolean isSensitive(String fieldName) {
    if (fieldName == null) {
      return false;
    }
    String key = fieldName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return key.contains("password")
        || key.contains("secret")
        || key.contains("token")
        || key.contains("authorization")
        || key.contains("cookie")
        || key.contains("jwt")
        || key.contains("apikey")
        || key.contains("email")
        || key.contains("phone")
        || key.equals("firstname")
        || key.equals("lastname")
        || key.equals("fullname")
        || key.equals("middlename")
        || key.equals("dateofbirth")
        || key.equals("dob")
        || key.contains("address")
        || key.contains("document")
        || key.contains("identity")
        || key.contains("cardnumber")
        || key.equals("cvv")
        || key.contains("accountnumber");
  }

  private String truncate(String value) {
    int limit = Math.max(0, properties.getMaxOutputStringLength());
    if (value.length() <= limit) {
      return value;
    }
    return value.substring(0, limit) + "...";
  }
}
