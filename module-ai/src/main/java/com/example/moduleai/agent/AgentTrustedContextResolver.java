package com.example.moduleai.agent;

import com.example.common.exception.AppException;
import com.example.moduleai.tool.catalog.ToolDescriptor;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.modulebooking.application.BookingAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AgentTrustedContextResolver {

  private final BookingAccessService bookingAccessService;
  private final ObjectMapper objectMapper;

  public AgentTrustedContextResolver(
      BookingAccessService bookingAccessService, ObjectMapper objectMapper) {
    this.bookingAccessService = bookingAccessService;
    this.objectMapper = objectMapper;
  }

  public AgentToolContext resolve(
      ToolDescriptor descriptor, Object typedInput, AgentToolContext context) {
    if (descriptor.policy().access() != ToolAccess.BOOKING_VERIFIED) {
      return context;
    }
    if (context.userId() == null) {
      throw new AppException("AUTHENTICATION_REQUIRED", "Authentication is required", 401);
    }

    JsonNode bookingIdNode = objectMapper.valueToTree(typedInput).get("bookingId");
    if (bookingIdNode == null || !bookingIdNode.canConvertToLong()) {
      throw new AppException(
          "BOOKING_CONTEXT_REQUIRED", "A bookingId is required for this tool", 400);
    }
    Long verifiedBookingId =
        bookingAccessService.requireOwnedBooking(context.userId(), bookingIdNode.longValue());
    return context.withVerifiedBookingId(verifiedBookingId);
  }
}
