package com.example.moduleai.guardrail;

import com.example.moduleai.agent.RoutingDecision;
import com.example.moduleai.config.AiAgentProperties;
import com.example.moduleai.confirmation.ToolConfirmationRegistry;
import com.example.moduleai.rollout.AgentRolloutMode;
import com.example.moduleai.rollout.AgentRolloutProperties;
import com.example.moduleai.tool.catalog.ToolCatalog;
import com.example.moduleai.tool.catalog.ToolDescriptor;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.ToolAccess;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

@Component
public class ToolAllowlistResolver implements SmartInitializingSingleton {

  private static final Logger log = LoggerFactory.getLogger(ToolAllowlistResolver.class);

  private static final Map<String, Set<String>> INTENT_TO_TOOLS =
      Map.ofEntries(
          Map.entry(
              "booking_lookup",
              Set.of(
                  "lookup_booking",
                  "get_booking_tickets",
                  "list_payment_methods",
                  "get_payment_detail")),
          Map.entry("payment_detail", Set.of("get_payment_detail", "list_payment_methods")),
          Map.entry("payment_methods", Set.of("list_payment_methods")),
          Map.entry("seat_map_lookup", Set.of("get_seat_map", "preview_seat_change")),
          Map.entry("flight_search", Set.of("search_flights", "search_flight_calendar")),
          Map.entry("flight_calendar_search", Set.of("search_flight_calendar", "search_flights")),
          Map.entry("fare_revalidate", Set.of("revalidate_fare")),
          Map.entry("manage_options", Set.of("get_manage_options")),
          Map.entry("checkin_continue", Set.of("continue_check_in", "confirm_check_in")),
          Map.entry("cancellation_preview", Set.of("preview_cancellation", "confirm_cancellation")),
          Map.entry("flight_change_quote", Set.of("quote_flight_change", "confirm_flight_change")),
          Map.entry("booking_tickets", Set.of("get_booking_tickets", "lookup_booking")),
          Map.entry(
              "seat_change_preview",
              Set.of("preview_seat_change", "get_seat_map", "confirm_seat_change")),
          Map.entry("seat_change_confirm", Set.of("confirm_seat_change")),
          Map.entry(
              "service_change_preview", Set.of("preview_service_change", "confirm_service_change")),
          Map.entry("my_trips", Set.of("list_my_trips", "get_trip_detail")),
          Map.entry("trip_detail", Set.of("get_trip_detail")),
          Map.entry("account_profile", Set.of("get_current_profile")),
          Map.entry("account_profile_update", Set.of("update_current_profile")));

  private final ToolCatalog toolCatalog;
  private final AiAgentProperties agentProperties;
  private final AgentRolloutProperties rolloutProperties;
  private final ToolConfirmationRegistry confirmationRegistry;

  public ToolAllowlistResolver(
      ToolCatalog toolCatalog,
      AiAgentProperties agentProperties,
      AgentRolloutProperties rolloutProperties,
      ToolConfirmationRegistry confirmationRegistry) {
    this.toolCatalog = toolCatalog;
    this.agentProperties = agentProperties;
    this.rolloutProperties = rolloutProperties;
    this.confirmationRegistry = confirmationRegistry;
  }

  @Override
  public void afterSingletonsInstantiated() {
    log.info(
        "Agent action rollout: actionsEnabled={}, mode={}, enabledTools={}, confirmationHandlers={}",
        agentProperties.isActionsEnabled(),
        rolloutProperties.getMode(),
        rolloutProperties.getEnabledTools(),
        toolCatalog.descriptors().keySet().stream()
            .filter(confirmationRegistry::hasCompleteHandler)
            .sorted()
            .toList());
  }

  public Set<ToolDescriptor> resolve(RoutingDecision decision, AgentToolContext context) {
    Set<String> toolNames = INTENT_TO_TOOLS.get(decision.intent());
    if (toolNames == null || toolNames.isEmpty()) {
      return Set.of();
    }

    Set<ToolDescriptor> allowlist = new LinkedHashSet<>();
    for (String toolName : toolNames) {
      ToolDescriptor descriptor = toolCatalog.descriptor(toolName);
      if (isAllowed(descriptor, context)) {
        allowlist.add(descriptor);
      }
    }
    return Set.copyOf(allowlist);
  }

  private boolean isAllowed(ToolDescriptor descriptor, AgentToolContext context) {
    if (descriptor == null) {
      return false;
    }
    if (!descriptor.exposedToLlm()) {
      return false;
    }

    // Check Rollout constraints for WRITE/FINANCIAL mutations
    boolean isMutation =
        descriptor.policy().operation() == com.example.moduleai.tool.core.ToolOperation.WRITE
            || descriptor.policy().operation()
                == com.example.moduleai.tool.core.ToolOperation.FINANCIAL;

    if (isMutation) {
      if (!agentProperties.isActionsEnabled()
          || rolloutProperties.getMode() == AgentRolloutMode.OFF) {
        return false;
      }
      if (rolloutProperties.getEnabledTools() != null
          && !rolloutProperties.getEnabledTools().contains(descriptor.name())) {
        return false;
      }
      if (rolloutProperties.getCanaryUserIds() != null
          && !rolloutProperties.getCanaryUserIds().isEmpty()) {
        if (context.userId() == null
            || !rolloutProperties.getCanaryUserIds().contains(context.userId())) {
          return false;
        }
      }
      if (!confirmationRegistry.hasCompleteHandler(descriptor.name())) {
        return false;
      }
    }

    ToolAccess access = descriptor.policy().access();
    if (access == ToolAccess.PUBLIC) {
      return true;
    }
    if (context.userId() == null) {
      return false;
    }
    if (access == ToolAccess.AUTHENTICATED) {
      return true;
    }
    // Exposure is safe for authenticated users. Ownership is resolved from the typed input and
    // verified by the booking application service immediately before invocation.
    return true;
  }
}
