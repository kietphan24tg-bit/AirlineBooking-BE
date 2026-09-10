package com.example.moduleai.tool.catalog;

import com.example.moduleai.tool.core.AgentToolPolicy;
import com.example.moduleai.tool.core.ToolPolicy;
import com.example.moduleai.tool.core.ToolResult;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;

public class ToolCatalog implements SmartInitializingSingleton {

  private static final Set<String> TRUSTED_CONTEXT_FIELDS =
      Set.of(
          "userId",
          "roles",
          "proposalId",
          "confirmed",
          "confirmationToken",
          "idempotencyKey",
          "verifiedBookingId");

  private static final Set<String> DISABLED_LLM_TOOLS =
      Set.of(
          "replace_seat_assignments",
          "remove_seat_assignment",
          "patch_seat_assignment",
          "replace_booking_services",
          "quote_flight_change",
          "confirm_flight_change");

  private ListableBeanFactory beanFactory;
  private List<ToolCallback> callbacks = List.of();
  private List<ToolCallback> llmCallbacks = List.of();
  private Map<String, ToolPolicy> policies = Map.of();
  private Map<String, ToolDescriptor> descriptors = Map.of();

  @Autowired
  void setBeanFactory(ListableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (beanFactory == null) {
      return;
    }

    Map<String, ToolPolicy> discoveredPolicies = new LinkedHashMap<>();
    Map<String, ToolDescriptor> discoveredDescriptors = new LinkedHashMap<>();
    List<ToolCallback> discoveredCallbacks = new ArrayList<>();
    List<ToolCallback> discoveredLlmCallbacks = new ArrayList<>();

    for (Object bean :
        beanFactory
            .getBeansWithAnnotation(org.springframework.stereotype.Component.class)
            .values()) {
      Map<String, ToolPolicy> beanPolicies = extractPolicies(bean.getClass());
      if (beanPolicies.isEmpty()) {
        continue;
      }
      MethodToolCallbackProvider callbackProvider =
          MethodToolCallbackProvider.builder().toolObjects(bean).build();
      Map<String, ToolCallback> callbacksByName = new LinkedHashMap<>();
      for (ToolCallback callback : callbackProvider.getToolCallbacks()) {
        ToolCallback normalizedCallback = SchemaNormalizingToolCallback.from(callback);
        callbacksByName.put(normalizedCallback.getToolDefinition().name(), normalizedCallback);
      }
      for (Method method : bean.getClass().getDeclaredMethods()) {
        AgentToolPolicy annotation = method.getAnnotation(AgentToolPolicy.class);
        if (annotation == null) {
          continue;
        }
        validateSignature(method, annotation.tool());
        Tool tool = method.getAnnotation(Tool.class);
        if (tool == null || tool.description().isBlank()) {
          throw new IllegalStateException("Tool description is required for " + annotation.tool());
        }
        ToolCallback callback = callbacksByName.get(annotation.tool());
        if (callback == null) {
          throw new IllegalStateException("Tool callback was not created for " + annotation.tool());
        }
        String toolName = callback.getToolDefinition().name();
        ToolPolicy policy = beanPolicies.get(toolName);
        if (policy == null) {
          throw new IllegalStateException("Missing policy for tool " + toolName);
        }
        if (discoveredPolicies.putIfAbsent(toolName, policy) != null) {
          throw new IllegalStateException("Duplicate tool name: " + toolName);
        }
        validateSchema(toolName, callback.getToolDefinition().inputSchema());
        ToolDescriptor descriptor =
            new ToolDescriptor(
                toolName,
                callback.getToolDefinition().description(),
                policy,
                method.getParameterTypes()[0],
                callback.getToolDefinition().inputSchema(),
                bean,
                method,
                callback,
                !DISABLED_LLM_TOOLS.contains(toolName));
        discoveredDescriptors.put(toolName, descriptor);
        discoveredCallbacks.add(callback);
        if (descriptor.exposedToLlm()) {
          discoveredLlmCallbacks.add(callback);
        }
      }
    }

    this.callbacks = List.copyOf(discoveredCallbacks);
    this.llmCallbacks = List.copyOf(discoveredLlmCallbacks);
    this.policies = Map.copyOf(discoveredPolicies);
    this.descriptors = Map.copyOf(discoveredDescriptors);
  }

  public List<ToolCallback> callbacks() {
    return callbacks;
  }

  public List<ToolCallback> llmCallbacks() {
    return llmCallbacks;
  }

  public Map<String, ToolPolicy> policies() {
    return policies;
  }

  public ToolPolicy policy(String name) {
    ToolPolicy policy = policies.get(name);
    if (policy == null) {
      throw new IllegalArgumentException("Unknown tool policy: " + name);
    }
    return policy;
  }

  public ToolCallback callback(String name) {
    return descriptor(name).callback();
  }

  public ToolDescriptor descriptor(String name) {
    ToolDescriptor descriptor = descriptors.get(name);
    if (descriptor == null) {
      throw new IllegalArgumentException("Unknown tool descriptor: " + name);
    }
    return descriptor;
  }

  public Map<String, ToolDescriptor> descriptors() {
    return descriptors;
  }

  private Map<String, ToolPolicy> extractPolicies(Class<?> type) {
    Map<String, ToolPolicy> result = new LinkedHashMap<>();
    for (Method method : type.getDeclaredMethods()) {
      AgentToolPolicy annotation = method.getAnnotation(AgentToolPolicy.class);
      if (annotation == null) {
        continue;
      }
      result.put(
          annotation.tool(),
          new ToolPolicy(
              annotation.tool(),
              annotation.operation(),
              annotation.access(),
              annotation.confirmationRequired(),
              annotation.idempotencyRequired()));
    }
    return result;
  }

  private void validateSignature(Method method, String toolName) {
    if (method.getParameterCount() != 2
        || !ToolContext.class.equals(method.getParameterTypes()[1])) {
      throw new IllegalStateException("Tool method must accept (input, ToolContext): " + toolName);
    }
    if (!ToolResult.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalStateException("Tool method must return ToolResult: " + toolName);
    }
  }

  private void validateSchema(String toolName, String schema) {
    if (schema == null || schema.isBlank()) {
      throw new IllegalStateException("Missing JSON schema for tool " + toolName);
    }
    Set<String> exposedFields = new LinkedHashSet<>();
    for (String field : TRUSTED_CONTEXT_FIELDS) {
      if (schema.contains("\"" + field + "\"")) {
        exposedFields.add(field);
      }
    }
    if (!exposedFields.isEmpty()) {
      throw new IllegalStateException(
          "Tool schema exposes trusted context fields for "
              + toolName
              + ": "
              + String.join(", ", exposedFields));
    }
  }
}
