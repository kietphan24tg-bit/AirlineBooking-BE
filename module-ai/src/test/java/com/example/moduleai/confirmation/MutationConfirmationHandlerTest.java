package com.example.moduleai.confirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import com.example.moduleai.tool.AncillaryTools;
import com.example.moduleai.tool.CancellationTools;
import com.example.moduleai.tool.SeatTools;
import com.example.moduleai.tool.core.AgentToolContext;
import com.example.modulepayment.application.BookingCancellationPreviewView;
import com.example.modulepayment.application.BookingCancellationService;
import com.example.modulepayment.application.SeatChangePreviewView;
import com.example.modulepayment.application.SeatChangeSettlementService;
import com.example.modulepayment.application.ServiceChangePreviewView;
import com.example.modulepayment.application.ServiceChangeSettlementService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MutationConfirmationHandlerTest {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final AgentToolContext context =
      new AgentToolContext(null, null, 7L, java.util.Set.of(), true, null, "key", 11L, Map.of());

  @Test
  void cancellationUsesServerSnapshotTotalsAfterScaleSafeRevalidation() throws Exception {
    BookingCancellationService service = Mockito.mock(BookingCancellationService.class);
    BookingCancellationPreviewView preview =
        new BookingCancellationPreviewView(
            11L,
            "CONFIRMED",
            "SUCCESS",
            "VND",
            List.of(),
            new BookingCancellationPreviewView.SummaryView(
                money("100.00"),
                money("0.00"),
                money("100.00"),
                money("0.00"),
                money("0.00"),
                money("100.00"),
                money("0.00")));
    when(service.preview(7L, 11L, List.of(21L))).thenReturn(preview);
    CancellationConfirmationHandler handler =
        new CancellationConfirmationHandler(service, objectMapper);
    CancellationTools.ConfirmCancellationInput input =
        new CancellationTools.ConfirmCancellationInput(
            11L, List.of(21L), "requested", money("999"), money("999"));
    Map<String, Object> snapshot = persistedSnapshot(preview);

    assertThatCode(() -> handler.revalidate(input, snapshot, context)).doesNotThrowAnyException();
    CancellationTools.ConfirmCancellationInput canonical =
        handler.canonicalize(input, snapshot, context);

    assertThat(canonical.expectedNetRefund()).isEqualByComparingTo("0.00");
    assertThat(canonical.expectedRefundFee()).isEqualByComparingTo("100.00");
  }

  @Test
  void seatChangeUsesServerSnapshotTotalsAfterScaleSafeRevalidation() throws Exception {
    SeatChangeSettlementService service = Mockito.mock(SeatChangeSettlementService.class);
    SeatChangePreviewView preview =
        new SeatChangePreviewView(
            11L,
            "VND",
            "token",
            "NONE",
            List.of(),
            new SeatChangePreviewView.SummaryView(money("0.00"), money("0.00"), money("0.00")));
    SeatTools.SeatChangeInput change = new SeatTools.SeatChangeInput(21L, 31L, 41L, 42L);
    when(service.preview(
            7L, 11L, List.of(new SeatChangeSettlementService.ChangeCommand(21L, 31L, 41L, 42L))))
        .thenReturn(preview);
    SeatChangeConfirmationHandler handler =
        new SeatChangeConfirmationHandler(service, objectMapper);
    SeatTools.ConfirmSeatChangeInput input =
        new SeatTools.ConfirmSeatChangeInput(
            11L, List.of(change), money("999"), money("999"), money("999"));
    Map<String, Object> snapshot = persistedSnapshot(preview);

    assertThatCode(() -> handler.revalidate(input, snapshot, context)).doesNotThrowAnyException();
    SeatTools.ConfirmSeatChangeInput canonical = handler.canonicalize(input, snapshot, context);

    assertThat(canonical.expectedAdditionalAmount()).isEqualByComparingTo("0.00");
    assertThat(canonical.expectedRefundAmount()).isEqualByComparingTo("0.00");
    assertThat(canonical.expectedNetAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void serviceChangeUsesServerSnapshotTokenAndTotalsAfterRevalidation() throws Exception {
    ServiceChangeSettlementService service = Mockito.mock(ServiceChangeSettlementService.class);
    ServiceChangePreviewView preview =
        new ServiceChangePreviewView(
            11L,
            "VND",
            "server-token",
            "NONE",
            List.of(),
            new ServiceChangePreviewView.SummaryView(money("0.00"), money("0.00"), money("0.00")));
    AncillaryTools.PassengerSelectionsInput passenger =
        new AncillaryTools.PassengerSelectionsInput(
            21L, List.of(new AncillaryTools.SelectionInput(51L, 1)));
    when(service.preview(
            7L,
            11L,
            List.of(
                new ServiceChangeSettlementService.PassengerSelectionsCommand(
                    21L, List.of(new ServiceChangeSettlementService.SelectionCommand(51L, 1))))))
        .thenReturn(preview);
    ServiceChangeConfirmationHandler handler =
        new ServiceChangeConfirmationHandler(service, objectMapper);
    AncillaryTools.ConfirmServiceChangeInput input =
        new AncillaryTools.ConfirmServiceChangeInput(
            11L, List.of(passenger), "model-token", money("999"), money("999"), money("999"));
    Map<String, Object> snapshot = persistedSnapshot(preview);

    assertThatCode(() -> handler.revalidate(input, snapshot, context)).doesNotThrowAnyException();
    AncillaryTools.ConfirmServiceChangeInput canonical =
        handler.canonicalize(input, snapshot, context);

    assertThat(canonical.previewToken()).isEqualTo("server-token");
    assertThat(canonical.expectedAdditionalAmount()).isEqualByComparingTo("0.00");
    assertThat(canonical.expectedRefundAmount()).isEqualByComparingTo("0.00");
    assertThat(canonical.expectedNetAmount()).isEqualByComparingTo("0.00");
  }

  private Map<String, Object> persistedSnapshot(Object preview) throws Exception {
    Map<String, Object> snapshot = Map.of("preview", objectMapper.convertValue(preview, MAP_TYPE));
    return objectMapper.readValue(objectMapper.writeValueAsString(snapshot), MAP_TYPE);
  }

  private BigDecimal money(String value) {
    return new BigDecimal(value);
  }
}
