package com.example.moduleai.agent;

import java.util.List;

public record RoutingDecision(
    RouteCategory category,
    String intent,
    String api,
    List<RequiredContext> requiredContext,
    boolean requiresConfirmation,
    boolean missingRequiredContext) {

  public static RoutingDecision rag(String intent) {
    return new RoutingDecision(
        RouteCategory.STATIC_POLICY_RAG, intent, null, List.of(), false, false);
  }

  public static RoutingDecision fallback(String intent) {
    return new RoutingDecision(
        RouteCategory.UNSAFE_OR_UNCONFIRMED_FALLBACK, intent, null, List.of(), false, false);
  }

  public static RoutingDecision api(
      String intent,
      String api,
      List<RequiredContext> requiredContext,
      boolean requiresConfirmation,
      boolean missingRequiredContext) {
    return new RoutingDecision(
        RouteCategory.DYNAMIC_API_REQUIRED,
        intent,
        api,
        List.copyOf(requiredContext),
        requiresConfirmation,
        missingRequiredContext);
  }
}
