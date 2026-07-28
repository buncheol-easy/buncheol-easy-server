package buncheoleasy.feedback.application;

import buncheoleasy.feedback.domain.FeedbackRateLimiter;
import buncheoleasy.feedback.dto.request.CreateFeedbackRequest;
import buncheoleasy.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 사용자 의견("의견 보내기") 접수. 답장 없는 단방향 수집이라 <b>DB 에 저장하지 않고 슬랙으로만 흘려보낸다</b> — 보관이 필요해지면 이 서비스에 저장을 붙인다.
 *
 * <p>발송은 이벤트로 넘겨 비동기 처리한다({@code SlackNotificationListener}). 슬랙이 느리거나 죽어도 사용자 응답이 지연되거나 실패하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

  private final FeedbackRateLimiter rateLimiter;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 의견 1건 접수.
   *
   * @param userId 로그인 회원 ID (비로그인이면 null)
   * @param clientIp 클라이언트 IP — 비로그인 제출의 도배 방지 키
   */
  public void submit(
      final Long userId, final String clientIp, final CreateFeedbackRequest request) {
    // 로그인 회원은 회원 ID 로 제한한다 — IP 를 공유하는 환경(학교·카페 와이파이)에서 서로를 막지 않도록.
    rateLimiter.checkAndRecord(userId == null ? "ip:" + clientIp : "user:" + userId);

    eventPublisher.publishEvent(
        new FeedbackSubmittedEvent(
            request.content(), request.screenPath(), userId, resolveNickname(userId)));
  }

  // 닉네임은 표시용 부가 정보라 회원이 조회되지 않아도 접수 자체는 성공시킨다.
  private String resolveNickname(final Long userId) {
    if (userId == null) {
      return null;
    }
    return userRepository
        .findById(userId)
        .map(user -> user.getNickname().value())
        .orElse(null);
  }
}
