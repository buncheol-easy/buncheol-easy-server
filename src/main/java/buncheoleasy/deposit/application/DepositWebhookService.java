package buncheoleasy.deposit.application;

import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.application.participation.SystemPaymentConfirmResult;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.notification.domain.SlackChannel;
import buncheoleasy.notification.infrastructure.SlackWebhookClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 페이액션 매칭완료 웹훅을 받아 참여를 자동 확정한다. 주문번호로 참여 ID 를 그대로 쓰므로 별도 매핑 테이블이 없다.
 *
 * <p>재전송해도 결과가 같은 실패(알 수 없는 주문번호, 존재하지 않는 참여, 기한 경과)는 예외를 삼키고 정상 응답한다 — 오류를 돌려주면 페이액션이 재전송을
 * 반복하다 발송 자체를 중단하기 때문이다. 반대로 DB 장애 같은 인프라 예외는 일시적이라 그대로 전파해 재전송을 유도한다.
 *
 * <p>확정할 수 없는 건은 돈은 들어왔는데 확정할 참여가 없는 상태라 운영자에게 알린다 — 이 알림이 자동확인 실패 시의 안전망이다.
 */
@Slf4j
@Service
public class DepositWebhookService {

  /** 페이액션이 매칭에 성공했을 때 보내는 주문 상태. 그 외 상태는 확정 대상이 아니다. */
  private static final String MATCHED_STATUS = "매칭완료";

  private final ParticipationService participationService;
  private final SlackWebhookClient slackWebhookClient;
  private final String adminBaseUrl;

  public DepositWebhookService(
      final ParticipationService participationService,
      final SlackWebhookClient slackWebhookClient,
      @Value("${app.admin.base-url}") final String adminBaseUrl) {
    this.participationService = participationService;
    this.slackWebhookClient = slackWebhookClient;
    this.adminBaseUrl = adminBaseUrl;
  }

  /**
   * 매칭완료 웹훅 처리. 실패해도 예외를 던지지 않는다 — 재전송으로 해결되지 않는 상황(알 수 없는 주문번호, 기한 경과)에서 오류를 돌려주면 페이액션이 재전송을
   * 반복하다 발송을 중단해 버린다.
   */
  public void handleMatched(final String orderNumber, final String orderStatus) {
    if (!MATCHED_STATUS.equals(orderStatus)) {
      log.info("페이액션 웹훅 - 확정 대상 아닌 상태 무시 - orderNumber={} status={}", orderNumber, orderStatus);
      return;
    }

    Long participationId = parseParticipationId(orderNumber);
    if (participationId == null) {
      alert("입금이 매칭됐지만 주문번호를 참여 ID 로 해석할 수 없습니다.", orderNumber);
      return;
    }

    try {
      SystemPaymentConfirmResult result =
          participationService.confirmPaymentBySystem(participationId);
      handleResult(participationId, result);
    } catch (BusinessException e) {
      // 존재하지 않는 참여 등 — 재전송해도 결과가 같으므로 알리고 종료한다.
      log.error("페이액션 웹훅 - 참여 조회 실패 - participationId={}", participationId, e);
      alert("입금이 매칭됐지만 해당 참여를 찾을 수 없습니다.", orderNumber);
    }
  }

  private void handleResult(
      final Long participationId, final SystemPaymentConfirmResult result) {
    switch (result) {
      case CONFIRMED ->
          log.info("페이액션 웹훅 - 자동 입금확인 완료 - participationId={}", participationId);
      case ALREADY_CONFIRMED ->
          log.info("페이액션 웹훅 - 이미 확정된 참여 - participationId={}", participationId);
      case NOT_CONFIRMABLE -> {
        // 취소된 경우뿐 아니라 아직 AWAITING_PAYMENT 인데 기한만 지난 경우(만료 스케줄러 실행 전)도 여기로 들어온다.
        log.warn("페이액션 웹훅 - 확정 불가(기한 경과·취소) - participationId={}", participationId);
        alert("입금이 확인됐지만 확정할 수 없는 상태입니다(기한 경과 또는 취소). 환불 여부를 확인해 주세요.", String.valueOf(participationId));
      }
    }
  }

  private Long parseParticipationId(final String orderNumber) {
    try {
      return Long.valueOf(orderNumber);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** 운영자 개입이 필요한 입금. 신규 참여와 같은 채널로 보내 별도 채널 설정 없이도 즉시 동작한다. */
  private void alert(final String reason, final String orderNumber) {
    if (!slackWebhookClient.isEnabled(SlackChannel.NEW_PARTICIPATION)) {
      return;
    }
    try {
      slackWebhookClient.send(
          SlackChannel.NEW_PARTICIPATION,
          "⚠️ 입금 자동확인 실패 — %s (주문번호 %s) %s/payments".formatted(reason, orderNumber, adminBaseUrl));
    } catch (RuntimeException e) {
      log.error("입금 자동확인 실패 알림 발송 오류 - orderNumber={}", orderNumber, e);
    }
  }
}
