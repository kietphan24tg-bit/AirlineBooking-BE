package com.example.moduleai.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.moduleai.agent.RequiredContext;
import com.example.moduleai.agent.RoutingDecision;
import com.example.moduleai.config.AiAgentProperties;
import com.example.moduleai.confirmation.ToolConfirmationRegistry;
import com.example.moduleai.rollout.AgentRolloutMode;
import com.example.moduleai.rollout.AgentRolloutProperties;
import com.example.moduleai.tool.catalog.ToolCatalog;
import com.example.moduleai.tool.catalog.ToolDescriptor;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolAllowlistResolverTest {

  @Test
  void usesGlobalActionsFlagAndRolloutModeForMutation() {
    ToolCatalog catalog = mock(ToolCatalog.class);
    ToolConfirmationRegistry confirmationRegistry = mock(ToolConfirmationRegistry.class);
    ToolDescriptor mutation = descriptor("confirm_seat_change", ToolOperation.FINANCIAL);
    when(catalog.descriptor("confirm_seat_change")).thenReturn(mutation);
    when(confirmationRegistry.hasCompleteHandler("confirm_seat_change")).thenReturn(true);

    AiAgentProperties agentProperties = new AiAgentProperties();
    agentProperties.setActionsEnabled(true);
    AgentRolloutProperties rollout = new AgentRolloutProperties();
    rollout.setMode(AgentRolloutMode.LIVE);
    rollout.setEnabledTools(Set.of("confirm_seat_change"));

    ToolAllowlistResolver resolver =
        new ToolAllowlistResolver(catalog, agentProperties, rollout, confirmationRegistry);
    AgentToolContext context =
        new AgentToolContext(null, null, 9L, Set.of(), false, null, null, null, Map.of());

    assertThat(resolver.resolve(decision("seat_change_confirm"), context))
        .containsExactly(mutation);

    agentProperties.setActionsEnabled(false);
    assertThat(resolver.resolve(decision("seat_change_confirm"), context)).isEmpty();
  }

  @Test
  void exposesBookingVerifiedReadSchemaOnlyToAuthenticatedUser() {
    ToolCatalog catalog = mock(ToolCatalog.class);
    ToolConfirmationRegistry confirmationRegistry = mock(ToolConfirmationRegistry.class);
    ToolDescriptor descriptor =
        new ToolDescriptor(
            "get_manage_options",
            "test",
            new ToolPolicy(
                "get_manage_options",
                ToolOperation.READ,
                ToolAccess.BOOKING_VERIFIED,
                false,
                false),
            Object.class,
            "{}",
            null,
            null,
            null,
            true);
    when(catalog.descriptor("get_manage_options")).thenReturn(descriptor);
    AiAgentProperties agentProperties = new AiAgentProperties();
    AgentRolloutProperties rollout = new AgentRolloutProperties();
    ToolAllowlistResolver resolver =
        new ToolAllowlistResolver(catalog, agentProperties, rollout, confirmationRegistry);

    assertThat(resolver.resolve(decision("manage_options"), AgentToolContext.empty())).isEmpty();

    AgentToolContext authenticated =
        new AgentToolContext(null, null, 9L, Set.of(), false, null, null, null, Map.of());
    assertThat(resolver.resolve(decision("manage_options"), authenticated))
        .containsExactly(descriptor);
  }

  private RoutingDecision decision(String intent) {
    return RoutingDecision.api(intent, "test", List.of(RequiredContext.BOOKING_CODE), false, false);
  }

  private ToolDescriptor descriptor(String name, ToolOperation operation) {
    return new ToolDescriptor(
        name,
        "test",
        new ToolPolicy(name, operation, ToolAccess.AUTHENTICATED, true, true),
        Object.class,
        "{}",
        null,
        null,
        null,
        true);
  }
}
