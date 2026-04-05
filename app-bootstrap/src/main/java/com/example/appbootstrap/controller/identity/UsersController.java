package com.example.appbootstrap.controller.identity;

import com.example.appbootstrap.api.ApiSuccessMessage;
import com.example.appbootstrap.controller.identity.dto.ChangePasswordRequest;
import com.example.appbootstrap.controller.identity.dto.CurrentUserResponse;
import com.example.appbootstrap.controller.identity.dto.UpdateCurrentUserRequest;
import com.example.moduleidentity.application.AuthService;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.moduleidentity.entity.UserEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.ZoneOffset;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Identity", description = "Authentication and user endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UsersController {

  private final AuthService authService;

  public UsersController(AuthService authService) {
    this.authService = authService;
  }

  @GetMapping("/me")
  @Operation(summary = "Get current user", description = "Returns the profile of the authenticated user")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Current user returned successfully",
        content = @Content(schema = @Schema(implementation = CurrentUserResponse.class))),
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
  })
  CurrentUserResponse getCurrentUser(@AuthenticationPrincipal AppUserPrincipal principal) {
    return toCurrentUserResponse(authService.findById(principal.getUserId()));
  }

  @PatchMapping("/me")
  @ApiSuccessMessage("Profile updated successfully")
  @Operation(summary = "Update current user", description = "Partially updates profile fields of the authenticated user")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Profile updated successfully",
        content = @Content(schema = @Schema(implementation = CurrentUserResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
  })
  CurrentUserResponse updateCurrentUser(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @Valid @RequestBody UpdateCurrentUserRequest request) {
    UserEntity user =
        authService.updateCurrentUserProfile(
            principal.getUserId(),
            request.firstName(),
            request.lastName(),
            request.dob(),
            request.nationality(),
            request.identifyNumber());
    return toCurrentUserResponse(user);
  }

  @PostMapping("/me/password")
  @ApiSuccessMessage("Password changed successfully")
  @Operation(
      summary = "Change current user password",
      description = "Verifies the current password, updates it, and revokes all existing sessions")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Password changed successfully"),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(responseCode = "401", description = "Authentication required or current password incorrect", content = @Content)
  })
  Void changePassword(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(
        principal.getUserId(), request.currentPassword(), request.newPassword());
    return null;
  }

  private CurrentUserResponse toCurrentUserResponse(UserEntity user) {
    return new CurrentUserResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getPhone(),
        user.getDob() == null ? null : user.getDob().atZoneSameInstant(ZoneOffset.UTC).toLocalDate(),
        user.getNationality(),
        user.getIdentifyNumber(),
        user.getRole(),
        user.getCreatedAt());
  }
}
