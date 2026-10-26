package buncheoleasy.push.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import buncheoleasy.notification.application.InboxNotificationRecordedEvent;
import buncheoleasy.push.domain.PushDevice;
import buncheoleasy.push.domain.PushDeviceRepository;
import buncheoleasy.push.domain.PushEnvironment;
import buncheoleasy.push.domain.PushMessage;
import buncheoleasy.push.domain.PushPlatform;
import buncheoleasy.push.infrastructure.ApnsClient;
import buncheoleasy.push.infrastructure.ApnsSendResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PushNotificationListener 테스트")
class PushNotificationListenerTest {

  private static final Long RECIPIENT_ID = 7L;
  private static final InboxNotificationRecordedEvent EVENT =
      new InboxNotificationRecordedEvent(RECIPIENT_ID, "입금 확인 안내", "입금이 확인되었어요", "/profile/bids");
  private static final PushMessage MESSAGE =
      new PushMessage("입금 확인 안내", "입금이 확인되었어요", "/profile/bids");

  @InjectMocks private PushNotificationListener listener;

  @Mock private ApnsClient apnsClient;
  @Mock private PushDeviceRepository pushDeviceRepository;
  @Mock private PushDeviceService pushDeviceService;

  private static PushDevice device(final String token) {
    return PushDevice.create(RECIPIENT_ID, PushPlatform.IOS, token, PushEnvironment.PRODUCTION);
  }

  @Test
  void 발송기가_꺼져_있으면_기기를_조회하지도_보내지도_않는다() {
    given(apnsClient.isEnabled()).willReturn(false);

    listener.onInboxNotificationRecorded(EVENT);

    then(pushDeviceRepository).shouldHaveNoInteractions();
    then(apnsClient).should(never()).send(any(), any());
  }

  @Test
  void 수신자의_모든_기기로_같은_알림을_보낸다() {
    PushDevice phone = device("a".repeat(64));
    PushDevice pad = device("b".repeat(64));
    given(apnsClient.isEnabled()).willReturn(true);
    given(pushDeviceRepository.findAllByUserId(RECIPIENT_ID)).willReturn(List.of(phone, pad));
    given(apnsClient.send(any(), any())).willReturn(ApnsSendResult.SENT);

    listener.onInboxNotificationRecorded(EVENT);

    then(apnsClient).should().send(phone, MESSAGE);
    then(apnsClient).should().send(pad, MESSAGE);
    then(pushDeviceService).shouldHaveNoInteractions();
  }

  @Test
  void 애플이_무효라고_답한_토큰만_지운다() {
    PushDevice dead = device("a".repeat(64));
    PushDevice failing = device("b".repeat(64));
    given(apnsClient.isEnabled()).willReturn(true);
    given(pushDeviceRepository.findAllByUserId(RECIPIENT_ID)).willReturn(List.of(dead, failing));
    given(apnsClient.send(dead, MESSAGE)).willReturn(ApnsSendResult.TOKEN_INVALID);
    given(apnsClient.send(failing, MESSAGE)).willReturn(ApnsSendResult.FAILED);

    listener.onInboxNotificationRecorded(EVENT);

    then(pushDeviceService).should().removeInvalidToken("a".repeat(64));
    then(pushDeviceService).should(never()).removeInvalidToken("b".repeat(64));
  }

  @Test
  void 한_기기에서_예외가_나도_다음_기기로_보내고_위로_던지지_않는다() {
    PushDevice broken = device("a".repeat(64));
    PushDevice next = device("b".repeat(64));
    given(apnsClient.isEnabled()).willReturn(true);
    given(pushDeviceRepository.findAllByUserId(RECIPIENT_ID)).willReturn(List.of(broken, next));
    given(apnsClient.send(broken, MESSAGE)).willThrow(new IllegalStateException("boom"));
    given(apnsClient.send(next, MESSAGE)).willReturn(ApnsSendResult.TOKEN_INVALID);
    willThrow(new IllegalStateException("db down"))
        .given(pushDeviceService)
        .removeInvalidToken("b".repeat(64));

    assertThatCode(() -> listener.onInboxNotificationRecorded(EVENT)).doesNotThrowAnyException();

    then(apnsClient).should().send(next, MESSAGE);
  }

  @Test
  void 기기_조회가_실패해도_위로_던지지_않는다() {
    given(apnsClient.isEnabled()).willReturn(true);
    given(pushDeviceRepository.findAllByUserId(RECIPIENT_ID))
        .willThrow(new IllegalStateException("db down"));

    assertThatCode(() -> listener.onInboxNotificationRecorded(EVENT)).doesNotThrowAnyException();

    then(apnsClient).should(never()).send(any(), any());
  }
}
