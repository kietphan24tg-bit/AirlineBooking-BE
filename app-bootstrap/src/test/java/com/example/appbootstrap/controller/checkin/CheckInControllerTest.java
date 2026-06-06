package com.example.appbootstrap.controller.checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.application.checkin.CheckInSuccessEmailService;
import com.example.appbootstrap.controller.checkin.dto.CheckInConfirmRequest;
import com.example.appbootstrap.controller.checkin.dto.CheckInConfirmResponse;
import com.example.appbootstrap.controller.checkin.dto.CheckInContinueRequest;
import com.example.appbootstrap.controller.checkin.dto.CheckInContinueResponse;
import com.example.modulecheckin.application.CheckInConfirmCommand;
import com.example.modulecheckin.application.CheckInConfirmService;
import com.example.modulecheckin.application.CheckInConfirmView;
import com.example.modulecheckin.application.CheckInContinueCommand;
import com.example.modulecheckin.application.CheckInContinueService;
import com.example.modulecheckin.application.CheckInContinueView;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckInControllerTest {

  @Mock private CheckInConfirmService checkInConfirmService;
  @Mock private CheckInContinueService checkInContinueService;
  @Mock private CheckInSuccessEmailService checkInSuccessEmailService;

  private CheckInController controller;
  private Validator validator;

  @BeforeEach
  void setUp() {
    controller =
        new CheckInController(
            checkInContinueService, checkInConfirmService, checkInSuccessEmailService);
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void continueCheckInDelegatesToService() {
    CheckInContinueRequest request =
        new CheckInContinueRequest(
            "ABC123",
            "NGUYEN",
            List.of(new CheckInContinueRequest.SegmentRequest(1001L, List.of(1L))));
    CheckInContinueView response =
        new CheckInContinueView(
            new CheckInContinueView.BookingView(
                10L, "ABC123", "CONFIRMED", "a", "b", 1, "VND", "NGUYEN"),
            List.of(),
            new CheckInContinueView.SummaryView(1, 1, 1, 0, 0),
            new CheckInContinueView.ActionsView(true, false, "CONFIRM_CHECKIN"));
    when(checkInContinueService.continueCheckIn(
            new CheckInContinueCommand(
                "ABC123",
                "NGUYEN",
                List.of(new CheckInContinueCommand.SegmentSelection(1001L, List.of(1L))))))
        .thenReturn(response);

    CheckInContinueResponse actual = controller.continueCheckIn(request);

    assertEquals("CONFIRM_CHECKIN", actual.actions().nextStep());
  }

  @Test
  void requestShouldFailValidationWhenRequiredFieldsMissing() {
    var violations =
        validator.validate(
            new CheckInContinueRequest(
                " ", "", List.of(new CheckInContinueRequest.SegmentRequest(null, List.of()))));

    assertEquals(4, violations.size());
  }

  @Test
  void confirmCheckInDelegatesToService() {
    CheckInConfirmRequest request =
        new CheckInConfirmRequest(
            "ABC123",
            "NGUYEN",
            List.of(new CheckInConfirmRequest.SegmentRequest(1001L, List.of(1L))));
    CheckInConfirmView response =
        new CheckInConfirmView(
            new CheckInConfirmView.BookingView(10L, "ABC123", "CHECKED_IN_COMPLETED"),
            List.of(
                new CheckInConfirmView.SegmentView(
                    1001L,
                    "VJ123",
                    List.of(
                        new CheckInConfirmView.PassengerView(
                            1L, 9001L, "ABC123-1", "Nguyen Van A", "12A", 7001L, "CHECKED_IN")))),
            List.of(
                new CheckInConfirmView.BoardingPassView(
                    7001L,
                    9001L,
                    1L,
                    1001L,
                    "BP|ABC123",
                    "12",
                    java.time.OffsetDateTime.parse("2026-05-12T10:15:30Z"),
                    1,
                    "12A")),
            new CheckInConfirmView.SummaryView(1, 1));
    when(checkInConfirmService.confirmCheckIn(
            new CheckInConfirmCommand(
                "ABC123",
                "NGUYEN",
                List.of(new CheckInConfirmCommand.SegmentSelection(1001L, List.of(1L))))))
        .thenReturn(response);

    CheckInConfirmResponse actual = controller.confirmCheckIn(request);

    assertEquals("CHECKED_IN_COMPLETED", actual.booking().status());
    assertEquals(1, actual.boardingPasses().size());
    verify(checkInSuccessEmailService).sendSuccess(response);
  }
}
