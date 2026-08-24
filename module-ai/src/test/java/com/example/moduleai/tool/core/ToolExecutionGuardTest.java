package com.example.moduleai.tool.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.catalog.ToolCatalog;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolExecutionGuardTest {

  private ToolExecutionGuard guard;

  @BeforeEach
  void setUp() {
    guard = new ToolExecutionGuard(Validation.buildDefaultValidatorFactory().getValidator());
  }

  @Test
  void rejectsInvalidInputBeforeExecution() {
    ToolResult<String> result =
        guard.execute(
            new ToolPolicy("test_tool", ToolOperation.READ, ToolAccess.PUBLIC, false, false),
            new SampleInput(""),
            AgentToolContext.empty(),
            () -> "ok");

    assertThat(result.status()).isEqualTo(ToolResultStatus.FAILED);
    assertThat(result.error())
        .isEqualTo(ToolError.validation("VALIDATION_FAILED", "value must not be blank"));
  }

  @Test
  void rejectsAuthenticatedToolWithoutUser() {
    ToolResult<String> result =
        guard.execute(
            new ToolPolicy("auth_tool", ToolOperation.READ, ToolAccess.AUTHENTICATED, false, false),
            new SampleInput("ok"),
            AgentToolContext.empty(),
            () -> "ok");

    assertThat(result.status()).isEqualTo(ToolResultStatus.FAILED);
    assertThat(result.error())
        .isEqualTo(ToolError.unauthorized("Authentication is required for this tool"));
  }

  @Test
  void rejectsBookingVerifiedToolWithoutVerifiedBooking() {
    ToolResult<String> result =
        guard.execute(
            new ToolPolicy(
                "booking_tool", ToolOperation.READ, ToolAccess.BOOKING_VERIFIED, false, false),
            new SampleInput("ok"),
            new AgentToolContext(
                null, 10L, java.util.Set.of(), false, null, null, null, java.util.Map.of()),
            () -> "ok");

    assertThat(result.status()).isEqualTo(ToolResultStatus.FAILED);
    assertThat(result.error())
        .isEqualTo(
            ToolError.forbidden(
                "BOOKING_VERIFICATION_REQUIRED", "A trusted booking verification is required"));
  }

  @Test
  void returnsConfirmationRequiredWhenMutationLacksTrustedConfirmation() {
    ToolResult<String> result =
        guard.execute(
            new ToolPolicy("write_tool", ToolOperation.WRITE, ToolAccess.PUBLIC, true, false),
            new SampleInput("ok"),
            AgentToolContext.empty(),
            () -> "ok");

    assertThat(result.status()).isEqualTo(ToolResultStatus.CONFIRMATION_REQUIRED);
    assertThat(result.error())
        .isEqualTo(
            ToolError.forbidden(
                "CONFIRMATION_REQUIRED", "A trusted confirmation is required before mutation"));
  }

  @Test
  void rejectsMissingTrustedIdempotencyKey() {
    ToolResult<String> result =
        guard.execute(
            new ToolPolicy(
                "financial_tool", ToolOperation.FINANCIAL, ToolAccess.PUBLIC, true, true),
            new SampleInput("ok"),
            new AgentToolContext(
                null, null, java.util.Set.of(), true, null, null, null, java.util.Map.of()),
            () -> "ok");

    assertThat(result.status()).isEqualTo(ToolResultStatus.FAILED);
    assertThat(result.error())
        .isEqualTo(
            ToolError.validation(
                "IDEMPOTENCY_KEY_REQUIRED", "A trusted idempotency key is required"));
  }

  @Test
  void mapsAppExceptionToStructuredToolError() {
    ToolResult<String> result =
        guard.execute(
            new ToolPolicy("domain_tool", ToolOperation.READ, ToolAccess.PUBLIC, false, false),
            new SampleInput("ok"),
            AgentToolContext.empty(),
            () -> {
              throw new AppException("RESOURCE_CONFLICT", "Conflict happened", 409);
            });

    assertThat(result.status()).isEqualTo(ToolResultStatus.FAILED);
    assertThat(result.error())
        .isEqualTo(ToolError.conflict("RESOURCE_CONFLICT", "Conflict happened"));
  }

  @Test
  void executesSuccessfullyWhenPreflightPasses() {
    ToolResult<String> result =
        guard.execute(
            new ToolPolicy(
                "ok_tool", ToolOperation.FINANCIAL, ToolAccess.AUTHENTICATED, true, true),
            new SampleInput("ok"),
            new AgentToolContext(
                null,
                99L,
                java.util.Set.of("USER"),
                true,
                "token",
                "idem-1",
                12L,
                java.util.Map.of()),
            () -> "done");

    assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
    assertThat(result.data()).isEqualTo("done");
    assertThat(result.error()).isNull();
  }

  @Test
  void usesCatalogPolicyInsteadOfLegacyPolicyArguments() {
    ToolCatalog catalog = mock(ToolCatalog.class);
    when(catalog.policy("mismatch_tool"))
        .thenReturn(
            new ToolPolicy(
                "mismatch_tool", ToolOperation.WRITE, ToolAccess.AUTHENTICATED, true, false));
    ToolExecutionGuard catalogBackedGuard =
        new ToolExecutionGuard(
            Validation.buildDefaultValidatorFactory().getValidator(),
            catalog,
            (policy, input, context) -> null);

    ToolResult<String> result =
        catalogBackedGuard.execute(
            new ToolPolicy("mismatch_tool", ToolOperation.READ, ToolAccess.PUBLIC, false, false),
            new SampleInput("ok"),
            AgentToolContext.empty(),
            () -> "must-not-run");

    assertThat(result.status()).isEqualTo(ToolResultStatus.FAILED);
    assertThat(result.error())
        .isEqualTo(ToolError.unauthorized("Authentication is required for this tool"));
  }

  private record SampleInput(@NotBlank String value) {}
}
