package buncheoleasy.auth.presentation;

import buncheoleasy.auth.application.SocialLoginService;
import buncheoleasy.auth.dto.RefreshTokenRequest;
import buncheoleasy.auth.dto.Tokens;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final SocialLoginService socialLoginService;

    @PostMapping("/reissue")
    public ResponseEntity<Tokens> reIssueToken(@RequestBody final RefreshTokenRequest request) {
        Tokens newTokens = socialLoginService.reissueTokens(request.refreshToken());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(newTokens);
    }
}
