package buncheoleasy.feedback.presentation;

import buncheoleasy.feedback.application.FeedbackService;
import buncheoleasy.feedback.dto.request.CreateFeedbackRequest;
import buncheoleasy.global.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

  private static final String USER_ROLE = "ROLE_USER";

  private final FeedbackService feedbackService;

  /**
   * 의견 보내기 (비로그인 허용). SecurityConfig 가 {@code POST /v1/feedbacks} 를 permitAll 로 연다 — 로그인이 안 돼서 남기는
   * 의견이 가장 받고 싶은 종류라 로그인을 요구하지 않는다.
   */
  @PostMapping
  public ResponseEntity<Void> createFeedback(
      // 맨 Authentication 파라미터는 서블릿의 getUserPrincipal() 로 해석돼 시큐리티 필터가 빠진 환경에서 null 이 된다.
      // @CurrentSecurityContext 는 SecurityContextHolder 를 직접 읽어 @AuthenticationPrincipal 과 같은 경로다.
      @CurrentSecurityContext(expression = "authentication") final Authentication authentication,
      @Valid @RequestBody final CreateFeedbackRequest request,
      final HttpServletRequest httpRequest) {
    feedbackService.submit(
        resolveUserId(authentication), ClientIpResolver.resolve(httpRequest), request);
    return ResponseEntity.noContent().build();
  }

  /**
   * 회원 ID. permitAll 경로라 익명·관리자 토큰도 도달하는데, 관리자(admins)와 회원(users)은 <b>id 공간이 겹칠 수 있다</b>({@code
   * SecurityConfig} 주석 참고). 관리자 id 를 회원 id 로 넘기면 무관한 회원의 닉네임이 슬랙에 표시되므로 {@code ROLE_USER} 인 경우에만 회원으로
   * 취급한다.
   */
  private Long resolveUserId(final Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
      return null;
    }
    boolean isUser =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(USER_ROLE::equals);
    return isUser ? userId : null;
  }
}
