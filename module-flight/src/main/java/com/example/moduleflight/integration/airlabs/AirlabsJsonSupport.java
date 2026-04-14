package com.example.moduleflight.integration.airlabs;

import com.example.common.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * AirLabs docs often show a top-level JSON array; live responses may use {@code {"response":
 * [...]}}. See https://airlabs.co/docs/airlines and https://airlabs.co/docs/countries
 */
public final class AirlabsJsonSupport {

  private AirlabsJsonSupport() {}

  public static void throwIfErrorEnvelope(JsonNode root) {
    if (root != null && root.isObject() && root.has("error")) {
      String message = root.path("error").path("message").asText("AirLabs API error");
      throw new AppException("AIRLINE_IMPORT_API_ERROR", message, 502);
    }
  }

  public static <T> List<T> readList(JsonNode root, Class<T> elementClass, ObjectMapper mapper) {
    JsonNode arrayNode = unwrapArray(root);
    if (arrayNode == null || !arrayNode.isArray()) {
      return List.of();
    }
    try {
      List<T> out = new ArrayList<>();
      for (JsonNode node : arrayNode) {
        out.add(mapper.treeToValue(node, elementClass));
      }
      return out;
    } catch (Exception exception) {
      throw new AppException(
          "AIRLINE_IMPORT_API_ERROR", "Unable to parse AirLabs JSON payload", 502);
    }
  }

  private static JsonNode unwrapArray(JsonNode root) {
    if (root == null || root.isNull()) {
      return null;
    }
    if (root.isArray()) {
      return root;
    }
    if (root.isObject() && root.has("response")) {
      return root.get("response");
    }
    return null;
  }
}
