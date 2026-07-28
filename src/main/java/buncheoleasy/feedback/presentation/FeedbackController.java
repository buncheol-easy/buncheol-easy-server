package buncheoleasy.feedback.presentation;

import buncheoleasy.feedback.application.FeedbackService;
import buncheoleasy.feedback.dto.request.CreateFeedbackRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

  private final FeedbackService feedbackService;

  /**
   * 의견 보내기 (비로그인 허용). SecurityConfig 가 {@code POST /v1/feedbacks} 를 permitAll 로 연다 — 로그인이 안 돼서 남기는
   * 의견이 가장 받고 싶은 종류라 로그인을 요구하지 않는다.
   *
   * <p>비로그인 호출 시 익명 principal(문자열) 은 {@code Long} 캐스팅에 실패해 {@code userId} 가 null 로 들어온다 ({@link
   * AuthenticationPrincipal#errorOnInvalidType()} 기본값 false).
   */
  @PostMapping
  public ResponseEntity<Void> createFeedback(
      @AuthenticationPrincipal final Long userId,
      @Valid @RequestBody final CreateFeedbackRequest request,
      final HttpServletRequest httpRequest) {
    feedbackService.submit(userId, resolveClientIp(httpRequest), request);
    return ResponseEntity.noContent().build();
  }

  /**
   * 도배 방지 키로 쓸 클라이언트 IP. 앱 서버는 Nginx 뒤에 있어 {@code remoteAddr} 이 항상 프록시 IP 라, {@code
   * X-Forwarded-For} 의 첫 항목(원 클라이언트)을 우선한다. 헤더는 위조 가능하지만 그 경우 제한이 느슨해질 뿐 다른 사용자를 막지는 않는다.
   */
  private String resolveClientIp(final HttpServletRequest request) {
    String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
    if (forwardedFor == null || forwardedFor.isBlank()) {
      return request.getRemoteAddr();
    }
    return forwardedFor.split(",")[0].trim();
  }
}
