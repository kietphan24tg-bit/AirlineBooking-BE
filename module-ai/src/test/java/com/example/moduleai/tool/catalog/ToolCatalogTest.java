package com.example.moduleai.tool.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.moduleai.tool.AccountTools;
import com.example.moduleai.tool.AirportTools;
import com.example.moduleai.tool.AncillaryTools;
import com.example.moduleai.tool.BookingTools;
import com.example.moduleai.tool.CancellationTools;
import com.example.moduleai.tool.CheckInTools;
import com.example.moduleai.tool.FlightChangeTools;
import com.example.moduleai.tool.FlightTools;
import com.example.moduleai.tool.PaymentTools;
import com.example.moduleai.tool.SeatTools;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.support.AccountToolService;
import com.example.moduleai.tool.support.AncillaryCatalogToolService;
import com.example.moduleai.tool.support.PromotionCatalogToolService;
import com.example.modulebooking.application.BookingAncillarySelectionService;
import com.example.modulebooking.application.BookingLookupService;
import com.example.modulebooking.application.BookingManageOptionsService;
import com.example.modulebooking.application.BookingRevalidateService;
import com.example.modulebooking.application.BookingSeatAssignmentService;
import com.example.modulebooking.application.FlightSeatMapService;
import com.example.modulebooking.application.TicketIssuanceService;
import com.example.modulebooking.application.UserTripDetailService;
import com.example.modulebooking.application.UserTripHistoryService;
import com.example.modulecheckin.application.CheckInConfirmService;
import com.example.modulecheckin.application.CheckInContinueService;
import com.example.moduleflight.application.AirportPickerService;
import com.example.moduleflight.application.AirportQueryService;
import com.example.moduleflight.application.FareFamilyCatalogService;
import com.example.moduleflight.application.FlightCalendarSearchService;
import com.example.moduleflight.application.FlightSearchFacadeService;
import com.example.modulepayment.application.BookingCancellationService;
import com.example.modulepayment.application.FlightChangePaymentService;
import com.example.modulepayment.application.PaymentResultService;
import com.example.modulepayment.application.PaymentService;
import com.example.modulepayment.application.SeatChangePaymentService;
import com.example.modulepayment.application.SeatChangeSettlementService;
import com.example.modulepayment.application.ServiceChangePaymentService;
import com.example.modulepayment.application.ServiceChangeSettlementService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ToolCatalogTest {

  @Test
  void discoversAllAgentToolsWithPoliciesAndDescriptions() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(
          jakarta.validation.Validator.class,
          () -> Validation.buildDefaultValidatorFactory().getValidator());
      context.registerBean(ToolExecutionGuard.class);
      context.registerBean(ToolCatalog.class);

      context.registerBean(AccountToolService.class, () -> Mockito.mock(AccountToolService.class));
      context.registerBean(
          AirportPickerService.class, () -> Mockito.mock(AirportPickerService.class));
      context.registerBean(
          AirportQueryService.class, () -> Mockito.mock(AirportQueryService.class));
      context.registerBean(
          AncillaryCatalogToolService.class, () -> Mockito.mock(AncillaryCatalogToolService.class));
      context.registerBean(
          BookingAncillarySelectionService.class,
          () -> Mockito.mock(BookingAncillarySelectionService.class));
      context.registerBean(
          ServiceChangeSettlementService.class,
          () -> Mockito.mock(ServiceChangeSettlementService.class));
      context.registerBean(
          TicketIssuanceService.class, () -> Mockito.mock(TicketIssuanceService.class));
      context.registerBean(
          BookingLookupService.class, () -> Mockito.mock(BookingLookupService.class));
      context.registerBean(
          BookingRevalidateService.class, () -> Mockito.mock(BookingRevalidateService.class));
      context.registerBean(
          UserTripHistoryService.class, () -> Mockito.mock(UserTripHistoryService.class));
      context.registerBean(
          UserTripDetailService.class, () -> Mockito.mock(UserTripDetailService.class));
      context.registerBean(
          BookingManageOptionsService.class, () -> Mockito.mock(BookingManageOptionsService.class));
      context.registerBean(
          FlightSeatMapService.class, () -> Mockito.mock(FlightSeatMapService.class));
      context.registerBean(
          BookingSeatAssignmentService.class,
          () -> Mockito.mock(BookingSeatAssignmentService.class));
      context.registerBean(
          BookingCancellationService.class, () -> Mockito.mock(BookingCancellationService.class));
      context.registerBean(
          CheckInContinueService.class, () -> Mockito.mock(CheckInContinueService.class));
      context.registerBean(
          CheckInConfirmService.class, () -> Mockito.mock(CheckInConfirmService.class));
      context.registerBean(
          FlightSearchFacadeService.class, () -> Mockito.mock(FlightSearchFacadeService.class));
      context.registerBean(
          FlightCalendarSearchService.class, () -> Mockito.mock(FlightCalendarSearchService.class));
      context.registerBean(
          FareFamilyCatalogService.class, () -> Mockito.mock(FareFamilyCatalogService.class));
      context.registerBean(
          PromotionCatalogToolService.class, () -> Mockito.mock(PromotionCatalogToolService.class));
      context.registerBean(PaymentService.class, () -> Mockito.mock(PaymentService.class));
      context.registerBean(
          PaymentResultService.class, () -> Mockito.mock(PaymentResultService.class));
      context.registerBean(
          SeatChangePaymentService.class, () -> Mockito.mock(SeatChangePaymentService.class));
      context.registerBean(
          ServiceChangePaymentService.class, () -> Mockito.mock(ServiceChangePaymentService.class));
      context.registerBean(
          FlightChangePaymentService.class, () -> Mockito.mock(FlightChangePaymentService.class));
      context.registerBean(
          SeatChangeSettlementService.class, () -> Mockito.mock(SeatChangeSettlementService.class));

      context.registerBean(AccountTools.class);
      context.registerBean(AirportTools.class);
      context.registerBean(AncillaryTools.class);
      context.registerBean(BookingTools.class);
      context.registerBean(CancellationTools.class);
      context.registerBean(CheckInTools.class);
      context.registerBean(FlightChangeTools.class);
      context.registerBean(FlightTools.class);
      context.registerBean(PaymentTools.class);
      context.registerBean(SeatTools.class);
      context.refresh();

      ToolCatalog toolCatalog = context.getBean(ToolCatalog.class);

      assertThat(toolCatalog.callbacks()).hasSize(44);
      assertThat(toolCatalog.policies()).hasSize(44);
      assertThat(toolCatalog.policies()).containsKeys("search_flights", "confirm_cancellation");
      assertThat(toolCatalog.policies().keySet())
          .containsExactlyInAnyOrderElementsOf(
              toolCatalog.callbacks().stream().map(c -> c.getToolDefinition().name()).toList());
      assertThat(toolCatalog.callbacks())
          .allSatisfy(
              callback -> assertThat(callback.getToolDefinition().description()).isNotBlank());
      assertThat(toolCatalog.policies().get("search_flights").operation())
          .isEqualTo(ToolOperation.READ);
      assertThat(toolCatalog.policies().get("search_flights").access())
          .isEqualTo(ToolAccess.PUBLIC);
      assertThat(toolCatalog.policies().get("confirm_cancellation").operation())
          .isEqualTo(ToolOperation.FINANCIAL);
      assertThat(toolCatalog.policies().get("confirm_cancellation").confirmationRequired())
          .isTrue();
      assertThat(toolCatalog.policies().get("confirm_cancellation").idempotencyRequired()).isTrue();
      assertThat(toolCatalog.policies().get("confirm_seat_change").idempotencyRequired()).isTrue();
      assertThat(toolCatalog.policies().get("confirm_service_change").idempotencyRequired())
          .isTrue();
      assertThat(toolCatalog.descriptor("list_my_trips").exposedToLlm()).isTrue();
      assertThat(toolCatalog.descriptor("get_trip_detail").exposedToLlm()).isTrue();
      assertThat(toolCatalog.descriptor("get_manage_options").exposedToLlm()).isTrue();
      assertThat(toolCatalog.descriptor("quote_flight_change").exposedToLlm()).isFalse();
    }
  }
}
