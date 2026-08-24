package com.example.moduleai.tool.core;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.catalog.ToolCatalog;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ToolExecutionGuard {

  private final Validator validator;
  private final ToolCatalog toolCatalog;
  private final ToolConfirmationVerifier confirmationVerifier;

  public ToolExecutionGuard(Validator validator) {
    this(validator, null, (policy, input, context) -> null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public ToolExecutionGuard(
      Validator validator,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
          ToolCatalog toolCatalog,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
          ToolConfirmationVerifier confirmationVerifier) {
    this.validator = validator;
    this.toolCatalog = toolCatalog;
    this.confirmationVerifier = confirmationVerifier;
  }

  public <I> ToolError validateForProposal(String toolName, I input, AgentToolContext context) {
    if (toolCatalog == null) {
      throw new IllegalStateException("Tool catalog is required for proposal validation");
    }
    return validatePreflight(toolCatalog.policy(toolName), input, context, false);
  }

  public <I, O> ToolResult<O> execute(
      String toolName, I input, AgentToolContext context, Supplier<O> action) {
    if (toolCatalog == null) {
      throw new IllegalStateException("Tool catalog is required for named tool execution");
    }
    ToolPolicy policy = toolCatalog.policy(toolName);
    ToolError preflightError = validatePreflight(policy, input, context, true);
    if (preflightError != null) {
      if ("CONFIRMATION_REQUIRED".equals(preflightError.code())) {
        return ToolResult.confirmationRequired(policy.tool(), preflightError.message());
      }
      return ToolResult.failure(policy.tool(), preflightError);
    }

    try {
      return ToolResult.success(policy.tool(), action.get());
    } catch (AppException exception) {
      return ToolResult.failure(policy.tool(), mapAppException(exception));
    } catch (RuntimeException exception) {
      return ToolResult.failure(
          policy.tool(),
          ToolError.unavailable("TOOL_UNAVAILABLE", "Tool execution is temporarily unavailable"));
    }
  }

  public <I, O> ToolResult<O> execute(
      ToolPolicy policy, I input, AgentToolContext context, Supplier<O> action) {
    ToolPolicy runtimePolicy = runtimePolicy(policy);
    ToolError preflightError = validatePreflight(runtimePolicy, input, context);
    if (preflightError != null) {
      if ("CONFIRMATION_REQUIRED".equals(preflightError.code())) {
        return ToolResult.confirmationRequired(runtimePolicy.tool(), preflightError.message());
      }
      return ToolResult.failure(runtimePolicy.tool(), preflightError);
    }

    try {
      return ToolResult.success(runtimePolicy.tool(), action.get());
    } catch (AppException exception) {
      return ToolResult.failure(runtimePolicy.tool(), mapAppException(exception));
    } catch (RuntimeException exception) {
      return ToolResult.failure(
          runtimePolicy.tool(),
          ToolError.unavailable("TOOL_UNAVAILABLE", "Tool execution is temporarily unavailable"));
    }
  }

  private ToolPolicy runtimePolicy(ToolPolicy legacyPolicy) {
    return toolCatalog == null ? legacyPolicy : toolCatalog.policy(legacyPolicy.tool());
  }

  private <I> ToolError validatePreflight(ToolPolicy policy, I input, AgentToolContext context) {
    return validatePreflight(policy, input, context, true);
  }

  private <I> ToolError validatePreflight(
      ToolPolicy policy, I input, AgentToolContext context, boolean enforceConfirmation) {
    Set<ConstraintViolation<I>> violations = validator.validate(input);
    if (!violations.isEmpty()) {
      ConstraintViolation<I> violation =
          violations.stream()
              .min(Comparator.comparing(v -> v.getPropertyPath().toString()))
              .orElseThrow();
      String path = violation.getPropertyPath().toString();
      String message =
          path.isBlank() ? violation.getMessage() : path + " " + violation.getMessage();
      return ToolError.validation("VALIDATION_FAILED", message);
    }

    if (policy.access() == ToolAccess.AUTHENTICATED && context.userId() == null) {
      return ToolError.unauthorized("Authentication is required for this tool");
    }

    if (policy.access() == ToolAccess.BOOKING_VERIFIED) {
      if (context.userId() == null) {
        return ToolError.unauthorized("Authentication is required for this tool");
      }
      if (context.verifiedBookingId() == null) {
        return ToolError.forbidden(
            "BOOKING_VERIFICATION_REQUIRED", "A trusted booking verification is required");
      }
    }

    if (policy.confirmationRequired() && enforceConfirmation && !context.confirmed()) {
      return ToolError.forbidden(
          "CONFIRMATION_REQUIRED", "A trusted confirmation is required before mutation");
    }

    if (policy.confirmationRequired() && enforceConfirmation) {
      ToolError verificationError = confirmationVerifier.verify(policy, input, context);
      if (verificationError != null) {
        return verificationError;
      }
    }

    if (policy.idempotencyRequired()
        && (context.idempotencyKey() == null || context.idempotencyKey().isBlank())) {
      return ToolError.validation(
          "IDEMPOTENCY_KEY_REQUIRED", "A trusted idempotency key is required");
    }

    return null;
  }

  private ToolError mapAppException(AppException exception) {
    int status = exception.getStatusCode();
    if (status == 400) {
      return ToolError.validation(exception.getCode(), exception.getMessage());
    }
    if (status == 401) {
      return ToolError.unauthorized(exception.getMessage());
    }
    if (status == 403) {
      return ToolError.forbidden(exception.getCode(), exception.getMessage());
    }
    if (status == 404) {
      return ToolError.notFound(exception.getCode(), exception.getMessage());
    }
    if (status == 409 || status == 410) {
      return ToolError.conflict(exception.getCode(), exception.getMessage());
    }
    return ToolError.unavailable(exception.getCode(), exception.getMessage());
  }
}
