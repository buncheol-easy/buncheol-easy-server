package buncheoleasy.global.config;

import buncheoleasy.delivery.infrastructure.DeliveryTrackerClient;
import buncheoleasy.deposit.infrastructure.PayActionClient;
import buncheoleasy.notification.domain.SlackChannel;
import buncheoleasy.notification.infrastructure.SlackWebhookClient;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 기동 시 외부 연동의 켜짐/꺼짐 상태를 INFO 로 1회 남긴다.
 *
 * <p>이 프로젝트는 "설정 누락 → 에러 없이 조용히 건너뜀"을 설계로 채택했고({@code .env.example} 의 "미설정이어도 에러 없이 조용히
 * 건너뛴다 — 알림이 안 오면 여기부터 확인할 것"), 그 사실을 알려주던 유일한 창구가 각 클라이언트의 {@code log.debug("... 미설정 - 건너뜀")}
 * 이었다. 운영 로그 레벨이 INFO 가 되면서 그 창구가 닫히므로, 호출량과 무관한 기동 1회 요약으로 대체한다.
 *
 * <p>스테이징·프로덕션에서 {@code OFF} 가 보이면 정상이 아니라 <b>설정 사고</b>다. 로컬은 대부분 OFF 가 정상이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationStatusReporter {

  private final PayActionClient payActionClient;
  private final DeliveryTrackerClient deliveryTrackerClient;
  private final SlackWebhookClient slackWebhookClient;

  @EventListener(ApplicationReadyEvent.class)
  public void reportOnReady() {
    log.info(
        "외부 연동 상태: payaction={}, delivery-tracker={}, slack[{}]",
        onOff(payActionClient.isEnabled()),
        onOff(deliveryTrackerClient.isEnabled()),
        Arrays.stream(SlackChannel.values())
            .map(channel -> channel + "=" + onOff(slackWebhookClient.isEnabled(channel)))
            .collect(Collectors.joining(", ")));
  }

  private String onOff(final boolean enabled) {
    return enabled ? "ON" : "OFF";
  }
}
