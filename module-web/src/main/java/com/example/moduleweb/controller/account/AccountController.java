package com.example.moduleweb.controller.account;

import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.moduleweb.api.ApiSuccessMessage;
import com.example.moduleweb.application.account.AccountOverviewService;
import com.example.moduleweb.controller.account.dto.AccountOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@Tag(name = "Identity", description = "Authentication and user endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

  private final AccountOverviewService accountOverviewService;

  public AccountController(AccountOverviewService accountOverviewService) {
    this.accountOverviewService = accountOverviewService;
  }

  @GetMapping("/overview")
  @ApiSuccessMessage
  @Operation(
      summary = "Get account overview",
      description =
          "Returns the authenticated user's profile, trip status counts, and recent airports for"
              + " the account dashboard.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Account overview returned successfully",
        content = @Content(schema = @Schema(implementation = AccountOverviewResponse.class))),
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
  })
  AccountOverviewResponse getOverview(@AuthenticationPrincipal AppUserPrincipal principal) {
    return AccountOverviewResponse.from(accountOverviewService.getOverview(principal.getUserId()));
  }
}
