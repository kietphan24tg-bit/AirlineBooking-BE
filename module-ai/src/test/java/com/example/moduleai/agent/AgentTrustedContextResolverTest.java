package com.example.moduleai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.catalog.ToolDescriptor;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolPolicy;
import com.example.modulebooking.application.BookingAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AgentTrustedContextResolverTest {

  private final BookingAccessService bookingAccessService =
      Mockito.mock(BookingAccessService.class);
  private final AgentTrustedContextResolver resolver =
      new AgentTrustedContextResolver(bookingAccessService, new ObjectMapper());

  @Test
  void verifiesOwnedBookingBeforeAddingTrustedContext() {
    ToolDescriptor descriptor = descriptor(ToolAccess.BOOKING_VERIFIED);
    AgentToolContext context =
        new AgentToolContext(
            null, null, 7L, Set.of("ROLE_USER"), false, null, null, null, Map.of());
    when(bookingAccessService.requireOwnedBooking(7L, 42L)).thenReturn(42L);

    AgentToolContext resolved = resolver.resolve(descriptor, new BookingInput(42L), context);

    assertThat(resolved.verifiedBookingId()).isEqualTo(42L);
    assertThat(resolved.rawContext()).containsEntry("verifiedBookingId", 42L);
    verify(bookingAccessService).requireOwnedBooking(7L, 42L);
  }

  @Test
  void rejectsBookingToolWithoutTypedBookingId() {
    ToolDescriptor descriptor = descriptor(ToolAccess.BOOKING_VERIFIED);
    AgentToolContext context =
        new AgentToolContext(null, null, 7L, Set.of(), false, null, null, null, Map.of());

    assertThatThrownBy(() -> resolver.resolve(descriptor, new MissingBookingInput("x"), context))
        .isInstanceOf(AppException.class)
        .satisfies(
            exception ->
                assertThat(((AppException) exception).getCode())
                    .isEqualTo("BOOKING_CONTEXT_REQUIRED"));
  }

  private ToolDescriptor descriptor(ToolAccess access) {
    return new ToolDescriptor(
        "booking_tool",
        "test",
        new ToolPolicy("booking_tool", ToolOperation.READ, access, false, false),
        BookingInput.class,
        "{}",
        null,
        null,
        null,
        true);
  }

  private record BookingInput(Long bookingId) {}

  private record MissingBookingInput(String value) {}
}
