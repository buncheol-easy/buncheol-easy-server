package buncheoleasy.notification.application;

import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageRepository;
import buncheoleasy.notification.domain.AlimtalkTemplate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림톡 발송 시점에 in-app 알림(수신함 NOTIFICATION)을 1:1 로 영속화한다.
 *
 * <p>알림 제목은 템플릿 제목, 설명은 알림톡 본문과 동일하게 렌더링한 텍스트, 참고는 분철명이다. {@code linkPath} 는 알림톡 버튼이 가리키는 화면과 동일
 * 목적지의 in-app 상대 경로다(배송조회처럼 외부로만 연결되는 경우 null).
 *
 * <p>카카오 발송 성공 여부와 무관하게 in-app 알림은 남겨야 하므로 호출 측에서 발송 전에 먼저 기록한다. 비동기 리스너(AFTER_COMMIT)에서 호출되므로 이
 * 저장은 독립 트랜잭션이다.
 */
@Component
@RequiredArgsConstructor
public class NotificationInboxRecorder {

  private static final String VARIABLE_BUNCHEOL_NAME = "분철명";

  private static final String PATH_MY_PARTICIPATIONS = "/profile/bids";

  private final InboxMessageRepository inboxMessageRepository;

  @Transactional
  public void record(
      final Long recipientId, final AlimtalkTemplate template, final Map<String, String> variables) {
    final InboxMessage notification =
        InboxMessage.createNotification(
            recipientId,
            template.subject(),
            variables.get(VARIABLE_BUNCHEOL_NAME),
            template.render(variables),
            resolveLinkPath(template));
    inboxMessageRepository.save(notification);
  }

  // 알림톡 버튼 목적지(BuncheolUrls)와 동일한 화면의 in-app 상대 경로. 배송조회(TRACKING_*)는 외부 연결이라 경로 없음.
  private String resolveLinkPath(final AlimtalkTemplate template) {
    return switch (template) {
      case PAYMENT_CONFIRMED,
              PAYMENT_EXPIRED,
              BUNCHEOL_CONFIRMED,
              BUNCHEOL_CANCELLED,
              PAYBACK_COMPLETED,
              PAYBACK_REJECTED ->
          PATH_MY_PARTICIPATIONS;
      case TRACKING_CU, TRACKING_GS25 -> null;
    };
  }
}
