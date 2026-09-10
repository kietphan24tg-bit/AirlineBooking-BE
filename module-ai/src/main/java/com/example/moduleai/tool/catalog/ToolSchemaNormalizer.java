package com.example.moduleai.tool.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

final class ToolSchemaNormalizer {

  private static final String DEFINITIONS_FIELD = "$defs";
  private static final String DEFINITIONS_REFERENCE_PREFIX = "#/$defs/";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private ToolSchemaNormalizer() {}

  static String normalize(String schema) {
    if (schema == null || !schema.contains('"' + DEFINITIONS_FIELD + '"')) {
      return schema;
    }

    try {
      JsonNode root = OBJECT_MAPPER.readTree(schema);
      if (!(root instanceof ObjectNode rootObject)) {
        return schema;
      }
      JsonNode definitions = rootObject.get(DEFINITIONS_FIELD);
      if (!(definitions instanceof ObjectNode definitionObjects)) {
        return schema;
      }

      ObjectNode normalized = (ObjectNode) resolve(rootObject, definitionObjects, new HashSet<>());
      normalized.remove(DEFINITIONS_FIELD);
      return OBJECT_MAPPER.writeValueAsString(normalized);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invalid tool JSON schema", exception);
    }
  }

  private static JsonNode resolve(
      JsonNode node, ObjectNode definitions, Set<String> referencesBeingResolved) {
    if (node instanceof ArrayNode arrayNode) {
      ArrayNode resolvedArray = OBJECT_MAPPER.createArrayNode();
      for (JsonNode item : arrayNode) {
        resolvedArray.add(resolve(item, definitions, referencesBeingResolved));
      }
      return resolvedArray;
    }
    if (!(node instanceof ObjectNode objectNode)) {
      return node.deepCopy();
    }

    JsonNode referenceNode = objectNode.get("$ref");
    if (referenceNode != null && referenceNode.isTextual()) {
      String reference = referenceNode.textValue();
      if (reference.startsWith(DEFINITIONS_REFERENCE_PREFIX)) {
        String definitionName = reference.substring(DEFINITIONS_REFERENCE_PREFIX.length());
        JsonNode definition = definitions.get(definitionName);
        if (definition == null) {
          throw new IllegalStateException("Unknown tool schema reference: " + reference);
        }
        if (!referencesBeingResolved.add(definitionName)) {
          throw new IllegalStateException("Recursive tool schema reference: " + reference);
        }
        JsonNode resolvedDefinition = resolve(definition, definitions, referencesBeingResolved);
        referencesBeingResolved.remove(definitionName);
        if (!(resolvedDefinition instanceof ObjectNode resolvedObject)) {
          return resolvedDefinition;
        }
        mergeReferenceSiblings(resolvedObject, objectNode, definitions, referencesBeingResolved);
        return resolvedObject;
      }
    }

    ObjectNode resolvedObject = OBJECT_MAPPER.createObjectNode();
    Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      if (!DEFINITIONS_FIELD.equals(field.getKey())) {
        resolvedObject.set(
            field.getKey(), resolve(field.getValue(), definitions, referencesBeingResolved));
      }
    }
    return resolvedObject;
  }

  private static void mergeReferenceSiblings(
      ObjectNode target,
      ObjectNode referenceNode,
      ObjectNode definitions,
      Set<String> referencesBeingResolved) {
    Iterator<Map.Entry<String, JsonNode>> fields = referenceNode.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      if (!"$ref".equals(field.getKey()) && !DEFINITIONS_FIELD.equals(field.getKey())) {
        target.set(field.getKey(), resolve(field.getValue(), definitions, referencesBeingResolved));
      }
    }
  }
}
