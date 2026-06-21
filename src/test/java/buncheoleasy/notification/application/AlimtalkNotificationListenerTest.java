package buncheoleasy.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import buncheoleasy.buncheol.application.BuncheolCancelledEvent;
import buncheoleasy.buncheol.application.BuncheolConfirmedEvent;
import buncheoleasy.buncheol.application.participation.ParticipationRequestedEvent;
import buncheoleasy.buncheol.application.participation.PaymentConfirmedEvent;
import buncheoleasy.buncheol.application.participation.PaymentExpiredEvent;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.delivery.application.TrackingRegisteredEvent;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.notification.domain.AlimtalkTemplate;
import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.PhoneNumber;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlimtalkNotificationListener 단위 테스트")
class AlimtalkNotificationListenerTest {

  @InjectMocks private AlimtalkNotificationListener listener;

  @Mock private NotificationAssembler assembler;
  @Mock private AlimtalkSender sender;
  @Mock private NotificationInboxRecorder inboxRecorder;

  private static final Long PARTICIPATION_ID = 50L;
  private static final Long DELIVERY_ID = 10L;
  private static final String HOST_PHONE = "01033334444";
  private static final String PARTICIPANT_PHONE = "01011112222";

  @Nested
  @DisplayName("참여 요청(onParticipationRequested)")
  class ParticipationRequested {

    @Test
    @DisplayName("개최자에게 PARTICIPATION_REQUESTED 발송 + 변수 매핑 + 수신함 기록")
    void sendsToHost() {
      Participation participation = mock(Participation.class);
      given(participation.getDueAt()).willReturn(Instant.parse("2026-03-11T03:00:00Z"));
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("아이브 앨범");
      given(buncheol.getId()).willReturn(7L);
      User host = mockUser("개최자닉", HOST_PHONE);
      given(host.getId()).willReturn(3L);
      User participant = mockUserNickOnly("참여자닉");
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(participation, buncheol, "장원영", participant, host, 32_000L));

      listener.onParticipationRequested(new ParticipationRequestedEvent(PARTICIPATION_ID));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.PARTICIPATION_REQUESTED, HOST_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "개최자닉")
          .containsEntry("분철명", "아이브 앨범")
          .containsEntry("참여자닉네임", "참여자닉")
          .containsEntry("멤버명", "장원영")
          .containsEntry("입금금액", "32,000")
          .containsEntry("분철아이디", "7")
          // 03:00Z → KST(+9) 변환 검증
          .containsEntry("입금기한", "3/11(수) 12:00");
      // 참여 요청은 개최자(host)에게 분철 id 와 함께 in-app 알림이 남는다.
      verify(inboxRecorder)
          .record(eq(3L), eq(AlimtalkTemplate.PARTICIPATION_REQUESTED), any(), eq(7L));
    }
  }

  @Nested
  @DisplayName("입금 확인(onPaymentConfirmed)")
  class PaymentConfirmed {

    @Test
    @DisplayName("참여자에게 PAYMENT_CONFIRMED 발송 + 변수 매핑 + 수신함 기록")
    void sendsToParticipant() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("엔믹스 앨범");
      given(buncheol.getId()).willReturn(7L);
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "설윤", participant, mock(User.class), 20_000L));

      listener.onPaymentConfirmed(new PaymentConfirmedEvent(PARTICIPATION_ID));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.PAYMENT_CONFIRMED, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "참여자닉")
          .containsEntry("분철명", "엔믹스 앨범")
          .containsEntry("멤버명", "설윤")
          .containsEntry("입금금액", "20,000");
      verify(inboxRecorder)
          .record(eq(11L), eq(AlimtalkTemplate.PAYMENT_CONFIRMED), any(), eq(7L));
    }
  }

  @Nested
  @DisplayName("입금 만료 자동취소(onPaymentExpired)")
  class PaymentExpired {

    @Test
    @DisplayName("참여자에게 PAYMENT_EXPIRED 발송 + 변수 매핑 + 수신함 기록")
    void sendsToParticipant() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("아이브 앨범");
      given(buncheol.getId()).willReturn(7L);
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "장원영", participant, mock(User.class), 32_000L));

      listener.onPaymentExpired(new PaymentExpiredEvent(PARTICIPATION_ID));

      Map<String, String> variables = captureSend(AlimtalkTemplate.PAYMENT_EXPIRED, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "참여자닉")
          .containsEntry("분철명", "아이브 앨범")
          .containsEntry("멤버명", "장원영");
      verify(inboxRecorder).record(eq(11L), eq(AlimtalkTemplate.PAYMENT_EXPIRED), any(), eq(7L));
    }
  }

  @Nested
  @DisplayName("분철 진행 확정(onBuncheolConfirmed)")
  class BuncheolConfirmed {

    @Test
    @DisplayName("참여자에게 BUNCHEOL_CONFIRMED 발송 + 변수 매핑 + 수신함 기록")
    void sendsToParticipant() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("아이브 앨범");
      given(buncheol.getId()).willReturn(7L);
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "장원영", participant, mock(User.class), 32_000L));

      listener.onBuncheolConfirmed(new BuncheolConfirmedEvent(PARTICIPATION_ID));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.BUNCHEOL_CONFIRMED, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "참여자닉")
          .containsEntry("분철명", "아이브 앨범")
          .containsEntry("멤버명", "장원영");
      verify(inboxRecorder)
          .record(eq(11L), eq(AlimtalkTemplate.BUNCHEOL_CONFIRMED), any(), eq(7L));
    }
  }

  @Nested
  @DisplayName("분철 취소(onBuncheolCancelled)")
  class BuncheolCancelled {

    @Test
    @DisplayName("참여자에게 BUNCHEOL_CANCELLED 발송 + 변수 매핑 + 수신함 기록")
    void sendsToParticipant() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("르세라핌 앨범");
      given(buncheol.getId()).willReturn(7L);
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "카즈하", participant, mock(User.class), 0L));

      listener.onBuncheolCancelled(new BuncheolCancelledEvent(PARTICIPATION_ID));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.BUNCHEOL_CANCELLED, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "참여자닉")
          .containsEntry("분철명", "르세라핌 앨범")
          .containsEntry("멤버명", "카즈하");
      verify(inboxRecorder)
          .record(eq(11L), eq(AlimtalkTemplate.BUNCHEOL_CANCELLED), any(), eq(7L));
    }
  }

  @Nested
  @DisplayName("운송장 등록(onTrackingRegistered)")
  class TrackingRegistered {

    @Test
    @DisplayName("CU 택배는 TRACKING_CU 로 발송")
    void cu() {
      assertTrackingSends(ShippingMethod.CU_HALF, AlimtalkTemplate.TRACKING_CU);
    }

    @Test
    @DisplayName("GS25 택배는 TRACKING_GS25 로 발송")
    void gs25() {
      assertTrackingSends(ShippingMethod.GS25_HALF, AlimtalkTemplate.TRACKING_GS25);
    }

    private void assertTrackingSends(
        final ShippingMethod method, final AlimtalkTemplate expectedTemplate) {
      Delivery delivery = mock(Delivery.class);
      given(delivery.getParticipationId()).willReturn(PARTICIPATION_ID);
      given(delivery.getShippingMethod()).willReturn(method);
      given(delivery.getTrackingNumber()).willReturn("CJ123456789");
      given(assembler.loadDelivery(DELIVERY_ID)).willReturn(delivery);

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("르세라핌 앨범");
      given(buncheol.getId()).willReturn(7L);
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "카즈하", participant, mock(User.class), 0L));

      listener.onTrackingRegistered(new TrackingRegisteredEvent(DELIVERY_ID));

      Map<String, String> variables = captureSend(expectedTemplate, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "참여자닉")
          .containsEntry("분철명", "르세라핌 앨범")
          .containsEntry("멤버명", "카즈하")
          .containsEntry("운송장번호", "CJ123456789");
      verify(inboxRecorder).record(eq(11L), eq(expectedTemplate), any(), eq(7L));
    }
  }

  private User mockUser(final String nickname, final String phone) {
    User user = mock(User.class);
    given(user.getNickname()).willReturn(Nickname.of(nickname));
    given(user.getPhoneNumber()).willReturn(PhoneNumber.of(phone));
    return user;
  }

  private User mockUserNickOnly(final String nickname) {
    User user = mock(User.class);
    given(user.getNickname()).willReturn(Nickname.of(nickname));
    return user;
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> captureSend(final AlimtalkTemplate template, final String phone) {
    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
    verify(sender).send(eq(template), eq(phone), captor.capture());
    return captor.getValue();
  }
}
