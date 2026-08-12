package buncheoleasy.feedback.presentation;

import buncheoleasy.feedback.application.FeedbackService;
import buncheoleasy.feedback.dto.request.CreateFeedbackRequest;
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

  /** Nginx 가 {@code $remote_addr} 로 <b>덮어쓰는</b> 헤더 — 클라이언트가 보낸 값은 무조건 대체되므로 위조할 수 없다. */
  private static final String REAL_IP_HEADER = "X-Real-IP";

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

  /** Redis 키 길이 상한. IPv6 최대 표기(45자)면 충분하며, 조작된 긴 헤더로 키가 비대해지는 것을 막는다. */
  private static final int MAX_CLIENT_IP_LENGTH = 45;

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
    feedbackService.submit(resolveUserId(authentication), resolveClientIp(httpRequest), request);
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

  /**
   * 도배 방지 키로 쓸 클라이언트 IP.
   *
   * <p>{@code X-Forwarded-For} 의 <b>첫</b> 항목은 클라이언트가 보낸 값이다 — Nginx 의 {@code
   * $proxy_add_x_forwarded_for} 는 뒤에 실제 peer 를 덧붙일 뿐 앞을 덮지 않으므로, 첫 항목을 쓰면 헤더 조작으로 키를 매 요청 바꿔 제한을
   * 통째로 우회할 수 있다. 그래서 Nginx 가 덮어쓰는 {@code X-Real-IP} 를 우선하고, 없으면 XFF 의 <b>마지막</b>(가장 가까운 프록시가 본
   * peer) 항목을 쓴다.
   *
   * <p>⚠️ 브라우저 트래픽은 프론트(Next.js) 프록시를 거쳐 오므로 여기서 해석되는 IP 가 프록시 IP 로 수렴할 수 있다. 그 경우 비로그인 제출이 한 키를
   * 공유하게 되는데, 전역 상한({@code RedisFeedbackRateLimiter})이 총량을 따로 묶으므로 도배 방어 자체는 유지된다.
   */
  private String resolveClientIp(final HttpServletRequest request) {
    String realIp = request.getHeader(REAL_IP_HEADER);
    if (realIp != null && !realIp.isBlank()) {
      return truncate(realIp.trim());
    }

    String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
    if (forwardedFor == null || forwardedFor.isBlank()) {
      return truncate(request.getRemoteAddr());
    }
    String[] hops = forwardedFor.split(",");
    return truncate(hops[hops.length - 1].trim());
  }

  private String truncate(final String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return "unknown";
    }
    return clientIp.length() <= MAX_CLIENT_IP_LENGTH
        ? clientIp
        : clientIp.substring(0, MAX_CLIENT_IP_LENGTH);
  }
}
