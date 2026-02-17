package buncheoleasy.auth.presentation;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.auth.dto.request.RefreshTokenRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SocialLoginService socialLoginService;

    @PostMapping("/reissue-token")
    public ResponseEntity<TokenPair> reissueToken(@RequestBody final RefreshTokenRequest request) {
        TokenPair token = socialLoginService.reissueTokens(request.refreshToken());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(token);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal final Long userId) {
        socialLoginService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}
