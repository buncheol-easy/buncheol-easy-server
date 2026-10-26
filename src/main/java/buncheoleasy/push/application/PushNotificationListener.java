package buncheoleasy.push.application;

import buncheoleasy.notification.application.InboxNotificationRecordedEvent;
import buncheoleasy.push.domain.PushDevice;
import buncheoleasy.push.domain.PushDeviceRepository;
import buncheoleasy.push.domain.PushMessage;
import buncheoleasy.push.infrastructure.ApnsClient;
import buncheoleasy.push.infrastructure.ApnsSendResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 수신함에 알림이 저장되면(커밋 후) 수신자의 모든 기기로 같은 알림을 앱 푸시로 보낸다. 저장이 롤백되면 이벤트가 오지 않아 푸시도 나가지 않는다.
 *
 * <p>실행기는 알림톡 전용 단일 스레드({@code alimtalkExecutor})가 아니라 기본 실행기다 — 애플 서버가 느리거나 막혀도 알림톡 발송 줄이 밀리지 않게 하려는 것이다.
 * 대가로 같은 사람에게 거의 동시에 나간 두 푸시는 도착 순서가 바뀔 수 있다(알림톡 사이에는 알리고 호출 시간만큼 간격이 있어 드물다). 실패는 로그만 남기고 위로 던지지
 * 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotificationListener {

  private final ApnsClient apnsClient;
  private final PushDeviceRepository pushDeviceRepository;
  private final PushDeviceService pushDeviceService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInboxNotificationRecorded(final InboxNotificationRecordedEvent event) {
    if (!apnsClient.isEnabled()) {
      return;
    }
    try {
      List<PushDevice> devices = pushDeviceRepository.findAllByUserId(event.recipientId());
      PushMessage message = new PushMessage(event.title(), event.description(), event.linkPath());
      devices.forEach(device -> sendTo(device, message));
    } catch (RuntimeException e) {
      log.warn("앱 푸시 기기 조회 실패 - recipientId={}", event.recipientId(), e);
    }
  }

  private void sendTo(final PushDevice device, final PushMessage message) {
    try {
      if (apnsClient.send(device, message) == ApnsSendResult.TOKEN_INVALID) {
        pushDeviceService.removeInvalidToken(device.getToken());
      }
    } catch (RuntimeException e) {
      log.warn("앱 푸시 기기 처리 실패 - deviceId={}", device.getId(), e);
    }
  }
}
