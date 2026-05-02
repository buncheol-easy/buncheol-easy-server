package buncheoleasy.user.presentation;

import buncheoleasy.user.application.UserService;
import buncheoleasy.user.dto.request.BankAccountRequest;
import buncheoleasy.user.dto.request.UpdateUserProfileRequest;
import buncheoleasy.user.dto.response.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @DeleteMapping("/me")
  public ResponseEntity<Void> withdraw(@AuthenticationPrincipal final Long userId) {
    userService.withdraw(userId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<UserProfileResponse> getUserProfile(
      @AuthenticationPrincipal final Long userId) {
    return ResponseEntity.status(HttpStatus.OK).body(userService.getUserProfile(userId));
  }

  @PutMapping("/me")
  public ResponseEntity<Void> updateProfile(
      @AuthenticationPrincipal final Long userId,
      @Valid @RequestBody final UpdateUserProfileRequest request) {
    userService.updateProfile(userId, request);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/me/bank-account")
  public ResponseEntity<Void> updateBankAccount(
      @AuthenticationPrincipal final Long userId,
      @Valid @RequestBody final BankAccountRequest request) {
    userService.updateBankAccount(userId, request);
    return ResponseEntity.noContent().build();
  }
}
