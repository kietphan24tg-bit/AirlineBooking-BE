package com.example.appbootstrap.controller.checkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.controller.checkin.dto.CheckInContinueRequest;
import com.example.appbootstrap.controller.checkin.dto.CheckInContinueResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import com.example.modulecheckin.application.CheckInContinueCommand;
import com.example.modulecheckin.application.CheckInContinueService;
import com.example.modulecheckin.application.CheckInContinueView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckInControllerTest {

  @Mock private CheckInContinueService checkInContinueService;

  private CheckInController controller;
  private Validator validator;

  @BeforeEach
  void setUp() {
    controller = new CheckInController(checkInContinueService);
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void continueCheckInDelegatesToService() {
    CheckInContinueRequest request =
        new CheckInContinueRequest(
            "ABC123", "NGUYEN", List.of(new CheckInContinueRequest.SegmentRequest(1001L, List.of(1L))));
    CheckInContinueView response =
        new CheckInContinueView(
            new CheckInContinueView.BookingView(10L, "ABC123", "CONFIRMED", "a", "b", 1, "VND", "NGUYEN"),
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
                " ",
                "",
                List.of(new CheckInContinueRequest.SegmentRequest(null, List.of()))));

    assertEquals(4, violations.size());
  }
}
