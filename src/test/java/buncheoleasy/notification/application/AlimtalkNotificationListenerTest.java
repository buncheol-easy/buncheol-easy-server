package buncheoleasy.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import buncheoleasy.buncheol.application.BuncheolCancelReason;
import buncheoleasy.buncheol.application.BuncheolCancelledEvent;
import buncheoleasy.buncheol.application.BuncheolConfirmedEvent;
import buncheoleasy.buncheol.application.BuncheolFullEvent;
import buncheoleasy.buncheol.application.participation.PaymentConfirmedEvent;
import buncheoleasy.buncheol.application.participation.PaymentExpiredEvent;
import buncheoleasy.buncheol.application.participation.PaymentSentEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackCompletedEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackRejectedEvent;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.delivery.application.PickupReminderDueEvent;
import buncheoleasy.delivery.application.TrackingRegisteredEvent;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.notification.domain.AlimtalkTemplate;
import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.PhoneNumber;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.shipping.ShippingMethod;
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
  private static final String PARTICIPANT_PHONE = "01011112222";
  private static final String HOST_PHONE = "01033334444";

  @Nested
  @DisplayName("입금 확인(onPaymentConfirmed)")
  class PaymentConfirmed {

    @Test
    @DisplayName("참여자에게 PAYMENT_CONFIRMED 발송 + 변수 매핑 + 수신함 기록")
    void sendsToParticipant() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("엔믹스 앨범");
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
          .record(eq(11L), eq(AlimtalkTemplate.PAYMENT_CONFIRMED), any());
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
      verify(inboxRecorder).record(eq(11L), eq(AlimtalkTemplate.PAYMENT_EXPIRED), any());
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
          .record(eq(11L), eq(AlimtalkTemplate.BUNCHEOL_CONFIRMED), any());
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
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "카즈하", participant, mock(User.class), 0L));

      listener.onBuncheolCancelled(
          new BuncheolCancelledEvent(PARTICIPATION_ID, BuncheolCancelReason.MIN_HEADCOUNT_NOT_MET));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.BUNCHEOL_CANCELLED, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "참여자닉")
          .containsEntry("분철명", "르세라핌 앨범")
          .containsEntry("멤버명", "카즈하")
          .containsEntry("취소사유", "최소 진행 인원 미달");
      verify(inboxRecorder)
          .record(eq(11L), eq(AlimtalkTemplate.BUNCHEOL_CANCELLED), any());
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
      verify(inboxRecorder).record(eq(11L), eq(expectedTemplate), any());
    }
  }

  @Nested
  @DisplayName("미수령 독촉(onPickupReminderDue)")
  class PickupReminderDue {

    @Test
    @DisplayName("CU 택배는 PICKUP_REMINDER_CU 로 발송")
    void cu() {
      assertReminderSends(ShippingMethod.CU_HALF, AlimtalkTemplate.PICKUP_REMINDER_CU);
    }

    @Test
    @DisplayName("GS25 택배는 PICKUP_REMINDER_GS25 로 발송")
    void gs25() {
      assertReminderSends(ShippingMethod.GS25_HALF, AlimtalkTemplate.PICKUP_REMINDER_GS25);
    }

    private void assertReminderSends(
        final ShippingMethod method, final AlimtalkTemplate expectedTemplate) {
      Delivery delivery = mock(Delivery.class);
      given(delivery.getParticipationId()).willReturn(PARTICIPATION_ID);
      given(delivery.getShippingMethod()).willReturn(method);
      given(delivery.getStoreName()).willReturn("잠실점");
      given(delivery.getTrackingNumber()).willReturn("CJ123456789");
      given(assembler.loadDelivery(DELIVERY_ID)).willReturn(delivery);

      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("르세라핌 앨범");
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "카즈하", participant, mock(User.class), 0L));

      listener.onPickupReminderDue(new PickupReminderDueEvent(DELIVERY_ID));

      Map<String, String> variables = captureSend(expectedTemplate, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "참여자닉")
          .containsEntry("분철명", "르세라핌 앨범")
          .containsEntry("멤버명", "카즈하")
          .containsEntry("지점명", "잠실점")
          .containsEntry("운송장번호", "CJ123456789");
      verify(inboxRecorder).record(eq(11L), eq(expectedTemplate), any());
    }
  }

  @Nested
  @DisplayName("배송비 환급 완료(onShippingFeePaybackCompleted)")
  class ShippingFeePaybackCompleted {

    @Test
    @DisplayName("참여자에게 PAYBACK_COMPLETED 발송 + 변수 매핑 + 수신함 기록")
    void sendsToParticipant() {
      Participation participation = mock(Participation.class);
      given(participation.getPaybackAmount()).willReturn(3_500L);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("엔믹스 앨범");
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  participation, buncheol, "설윤", participant, mock(User.class), 20_000L));

      listener.onShippingFeePaybackCompleted(new ShippingFeePaybackCompletedEvent(PARTICIPATION_ID));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.PAYBACK_COMPLETED, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "참여자닉")
          .containsEntry("분철명", "엔믹스 앨범")
          .containsEntry("멤버명", "설윤")
          .containsEntry("환급금액", "3,500");
      verify(inboxRecorder).record(eq(11L), eq(AlimtalkTemplate.PAYBACK_COMPLETED), any());
    }
  }

  @Nested
  @DisplayName("배송비 환급 반려(onShippingFeePaybackRejected)")
  class ShippingFeePaybackRejected {

    // 반려 사유는 재신청 레이스로 엔티티에서 지워질 수 있어 재조회가 아니라 이벤트 스냅샷 값을 써야 한다.
    @Test
    @DisplayName("참여자에게 PAYBACK_REJECTED 발송 + 이벤트에 스냅샷된 반려 사유로 변수 매핑 + 수신함 기록")
    void sendsToParticipant() {
      Participation participation = mock(Participation.class);
      given(participation.getPaybackAmount()).willReturn(3_500L);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("아이브 앨범");
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  participation, buncheol, "안유진", participant, mock(User.class), 20_000L));

      listener.onShippingFeePaybackRejected(
          new ShippingFeePaybackRejectedEvent(PARTICIPATION_ID, "비공개 계정이라 확인 불가"));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.PAYBACK_REJECTED, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "참여자닉")
          .containsEntry("분철명", "아이브 앨범")
          .containsEntry("멤버명", "안유진")
          .containsEntry("반려사유", "비공개 계정이라 확인 불가")
          .containsEntry("환급금액", "3,500");
      verify(inboxRecorder).record(eq(11L), eq(AlimtalkTemplate.PAYBACK_REJECTED), any());
    }
  }

  @Nested
  @DisplayName("C2C 정원 충족(onBuncheolFull)")
  class BuncheolFull {

    @Test
    @DisplayName("개최자에게 C2C_BUNCHEOL_FULL 발송 + 변수 매핑(버튼 경로용 분철ID 포함) + 수신함 기록")
    void sendsToHost() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(77L);
      given(buncheol.getTitle()).willReturn("세븐틴 미니 12집 분철");
      User host = mockUser("개최자닉", HOST_PHONE);
      given(host.getId()).willReturn(3L);
      given(assembler.loadBuncheolHost(77L)).willReturn(new BuncheolHostView(buncheol, host));

      listener.onBuncheolFull(new BuncheolFullEvent(77L, 5));

      Map<String, String> variables = captureSend(AlimtalkTemplate.C2C_BUNCHEOL_FULL, HOST_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "개최자닉")
          .containsEntry("분철명", "세븐틴 미니 12집 분철")
          .containsEntry("신청인원", "5")
          .containsEntry("분철ID", "77");
      verify(inboxRecorder).record(eq(3L), eq(AlimtalkTemplate.C2C_BUNCHEOL_FULL), any());
    }

    @Test
    @DisplayName("개최자 전화번호가 미등록이면 수신함 기록만 남기고 발송은 건너뛴다")
    void skipsSendWhenHostPhoneMissing() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(77L);
      given(buncheol.getTitle()).willReturn("세븐틴 미니 12집 분철");
      User host = mock(User.class);
      given(host.getNickname()).willReturn(Nickname.of("개최자닉"));
      given(host.getId()).willReturn(3L);
      given(host.getPhoneNumber()).willReturn(null);
      given(assembler.loadBuncheolHost(77L)).willReturn(new BuncheolHostView(buncheol, host));

      listener.onBuncheolFull(new BuncheolFullEvent(77L, 5));

      verify(inboxRecorder).record(eq(3L), eq(AlimtalkTemplate.C2C_BUNCHEOL_FULL), any());
      verify(sender, never()).send(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("C2C 보냈어요(onPaymentSent)")
  class PaymentSent {

    @Test
    @DisplayName("개최자에게 C2C_PAYMENT_SENT 발송 + 통장 대조용 입금자명 포함 변수 매핑 + 수신함 기록")
    void sendsToHost() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(77L);
      given(buncheol.getTitle()).willReturn("세븐틴 미니 12집 분철");
      Participation participation = mock(Participation.class);
      given(participation.getRefundAccount())
          .willReturn(RefundAccount.of("국민", "12345678", "홍길동"));
      User participant = mock(User.class);
      given(participant.getNickname()).willReturn(Nickname.of("참여자닉"));
      User host = mockUser("개최자닉", HOST_PHONE);
      given(host.getId()).willReturn(3L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(new ParticipationView(participation, buncheol, "호시", participant, host, 25_000L));

      listener.onPaymentSent(new PaymentSentEvent(PARTICIPATION_ID));

      Map<String, String> variables = captureSend(AlimtalkTemplate.C2C_PAYMENT_SENT, HOST_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "개최자닉")
          .containsEntry("분철명", "세븐틴 미니 12집 분철")
          .containsEntry("멤버명", "호시")
          .containsEntry("참여자닉네임", "참여자닉")
          .containsEntry("입금자명", "홍길동")
          .containsEntry("입금금액", "25,000")
          .containsEntry("분철ID", "77");
      verify(inboxRecorder).record(eq(3L), eq(AlimtalkTemplate.C2C_PAYMENT_SENT), any());
    }
  }

  private User mockUser(final String nickname, final String phone) {
    User user = mock(User.class);
    given(user.getNickname()).willReturn(Nickname.of(nickname));
    given(user.getPhoneNumber()).willReturn(PhoneNumber.of(phone));
    return user;
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> captureSend(final AlimtalkTemplate template, final String phone) {
    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
    verify(sender).send(eq(template), eq(phone), captor.capture());
    return captor.getValue();
  }
}
