package com.example.moduleai.conversation;

import com.example.moduleai.confirmation.ToolProposalView;
import com.example.moduleai.dto.RagChatResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatStructuredStateProjector {

  public ChatStructuredState update(ChatStructuredState current, RagChatResponse response) {
    ChatStructuredState base = current == null ? ChatStructuredState.empty() : current;
    ToolProposalView proposal = response == null ? null : response.proposal();
    if (proposal == null) {
      return new ChatStructuredState(
          base.itinerary(),
          base.adultCount(),
          base.childCount(),
          base.infantCount(),
          base.cabinClass(),
          base.selectedFlightCode(),
          base.selectedFareCode(),
          base.verifiedBookingId(),
          null);
    }
    Long verifiedBookingId = readLong(proposal.details(), "bookingId");
    UUID activeProposalId = proposal.id();
    return new ChatStructuredState(
        base.itinerary(),
        base.adultCount(),
        base.childCount(),
        base.infantCount(),
        base.cabinClass(),
        base.selectedFlightCode(),
        base.selectedFareCode(),
        verifiedBookingId == null ? base.verifiedBookingId() : verifiedBookingId,
        activeProposalId);
  }

  private Long readLong(Map<String, Object> map, String key) {
    if (map == null) {
      return null;
    }
    Object value = map.get(key);
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String stringValue && !stringValue.isBlank()) {
      try {
        return Long.valueOf(stringValue.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
