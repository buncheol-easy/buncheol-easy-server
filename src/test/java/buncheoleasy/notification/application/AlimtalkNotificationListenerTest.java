package buncheoleasy.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import buncheoleasy.buncheol.application.BuncheolCancelReason;
import buncheoleasy.buncheol.application.BuncheolCancelledEvent;
import buncheoleasy.buncheol.application.BuncheolCollectingStartedEvent;
import buncheoleasy.buncheol.application.BuncheolConfirmedEvent;
import buncheoleasy.buncheol.application.BuncheolFullEvent;
import buncheoleasy.buncheol.application.participation.PaymentConfirmedEvent;
import buncheoleasy.buncheol.application.participation.PaymentExpiredEvent;
import buncheoleasy.buncheol.application.participation.PaymentSentEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackCompletedEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackRejectedEvent;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.delivery.application.PickupReminderDueEvent;
import buncheoleasy.delivery.application.TrackingRegisteredEvent;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.notification.domain.AlimtalkTemplate;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.PhoneNumber;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import java.time.Instant;
import java.util.List;
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
  private static final Long OTHER_PARTICIPATION_ID = 51L;
  private static final Long BUNCHEOL_ID = 7L;
  private static final Long DELIVERY_ID = 10L;
  private static final String PARTICIPANT_PHONE = "01011112222";
  private static final String OTHER_PARTICIPANT_PHONE = "01022223333";
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

    @Test
    @DisplayName("C2C 는 다음 관문이 최소 인원이 아니라 전원 입금이라 C2C_PAYMENT_CONFIRMED 로 발송")
    void c2cUsesOwnTemplate() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("세븐틴 미니 12집 분철");
      given(buncheol.isC2c()).willReturn(true);
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "호시", participant, mock(User.class), 25_000L));

      listener.onPaymentConfirmed(new PaymentConfirmedEvent(PARTICIPATION_ID));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.C2C_PAYMENT_CONFIRMED, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("멤버명", "호시")
          .containsEntry("입금금액", "25,000");
      verify(inboxRecorder)
          .record(eq(11L), eq(AlimtalkTemplate.C2C_PAYMENT_CONFIRMED), any());
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
    @DisplayName("LEGACY 참여자에게 BUNCHEOL_CONFIRMED 발송 + 변수 매핑 + 수신함 기록")
    void sendsToParticipant() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("아이브 앨범");
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "장원영", participant, mock(User.class), 32_000L));

      listener.onBuncheolConfirmed(
          new BuncheolConfirmedEvent(BUNCHEOL_ID, List.of(PARTICIPATION_ID)));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.BUNCHEOL_CONFIRMED, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("닉네임", "참여자닉")
          .containsEntry("분철명", "아이브 앨범")
          .containsEntry("멤버명", "장원영");
      verify(inboxRecorder)
          .record(eq(11L), eq(AlimtalkTemplate.BUNCHEOL_CONFIRMED), any());
    }

    @Test
    @DisplayName("C2C 는 전원 입금확인이 전이 조건이라 C2C_BUNCHEOL_CONFIRMED 로 발송")
    void c2cUsesOwnTemplate() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("세븐틴 미니 12집 분철");
      given(buncheol.isC2c()).willReturn(true);
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "호시", participant, mock(User.class), 25_000L));

      listener.onBuncheolConfirmed(
          new BuncheolConfirmedEvent(BUNCHEOL_ID, List.of(PARTICIPATION_ID)));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.C2C_BUNCHEOL_CONFIRMED, PARTICIPANT_PHONE);
      assertThat(variables).containsEntry("멤버명", "호시");
      verify(inboxRecorder)
          .record(eq(11L), eq(AlimtalkTemplate.C2C_BUNCHEOL_CONFIRMED), any());
    }

    @Test
    @DisplayName("C2C 다슬롯 참여자에게는 슬롯 수만큼이 아니라 멤버명을 합산해 1건만 발송")
    void mergesMultipleSlotsOfSameParticipant() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("세븐틴 미니 12집 분철");
      given(buncheol.isC2c()).willReturn(true);
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "호시", participant, mock(User.class), 25_000L));
      given(assembler.loadByParticipation(OTHER_PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "우지", participant, mock(User.class), 25_000L));

      listener.onBuncheolConfirmed(
          new BuncheolConfirmedEvent(
              BUNCHEOL_ID, List.of(PARTICIPATION_ID, OTHER_PARTICIPATION_ID)));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.C2C_BUNCHEOL_CONFIRMED, PARTICIPANT_PHONE);
      assertThat(variables).containsEntry("멤버명", "호시 외 1");
      verify(inboxRecorder)
          .record(eq(11L), eq(AlimtalkTemplate.C2C_BUNCHEOL_CONFIRMED), any());
    }

    @Test
    @DisplayName("서로 다른 참여자는 합산하지 않고 각각 1건씩 발송")
    void sendsPerParticipant() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("아이브 앨범");
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      User otherParticipant = mockUser("다른참여자닉", OTHER_PARTICIPANT_PHONE);
      given(otherParticipant.getId()).willReturn(12L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class),
                  buncheol,
                  "장원영",
                  participant,
                  mock(User.class),
                  32_000L));
      given(assembler.loadByParticipation(OTHER_PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class),
                  buncheol,
                  "안유진",
                  otherParticipant,
                  mock(User.class),
                  32_000L));

      listener.onBuncheolConfirmed(
          new BuncheolConfirmedEvent(
              BUNCHEOL_ID, List.of(PARTICIPATION_ID, OTHER_PARTICIPATION_ID)));

      assertThat(captureSend(AlimtalkTemplate.BUNCHEOL_CONFIRMED, PARTICIPANT_PHONE))
          .containsEntry("멤버명", "장원영");
      assertThat(captureSend(AlimtalkTemplate.BUNCHEOL_CONFIRMED, OTHER_PARTICIPANT_PHONE))
          .containsEntry("멤버명", "안유진");
    }

    // 알리고 통신 오류·실패 응답은 RuntimeException 으로 올라온다. 이벤트를 분철 단위로 합친 뒤로는 격리하지 않으면
    // 앞사람 한 명의 발송 실패가 뒷사람 전원의 알림톡과 수신함 기록까지 없앤다.
    @Test
    @DisplayName("한 수신자의 발송이 실패해도 나머지 수신자에게는 발송한다")
    void keepsSendingAfterOneRecipientFails() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("아이브 앨범");
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      User otherParticipant = mockUser("다른참여자닉", OTHER_PARTICIPANT_PHONE);
      given(otherParticipant.getId()).willReturn(12L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class),
                  buncheol,
                  "장원영",
                  participant,
                  mock(User.class),
                  32_000L));
      given(assembler.loadByParticipation(OTHER_PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class),
                  buncheol,
                  "안유진",
                  otherParticipant,
                  mock(User.class),
                  32_000L));
      willThrow(new IllegalStateException("알림톡 발송 실패"))
          .given(sender)
          .send(any(), eq(PARTICIPANT_PHONE), any());

      listener.onBuncheolConfirmed(
          new BuncheolConfirmedEvent(
              BUNCHEOL_ID, List.of(PARTICIPATION_ID, OTHER_PARTICIPATION_ID)));

      verify(sender).send(eq(AlimtalkTemplate.BUNCHEOL_CONFIRMED), eq(OTHER_PARTICIPANT_PHONE), any());
    }

    // 뷰 조립은 참여·분철·멤버슬롯·유저 조회가 모두 예외 경로라, 일괄 조회로 묶으면 한 건 결손이 분철 전원의 알림을 없앤다.
    @Test
    @DisplayName("한 참여의 조립이 실패해도 나머지 참여자에게는 발송한다")
    void skipsOnlyTheFailedParticipation() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("아이브 앨범");
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willThrow(new IllegalStateException("멤버 슬롯 결손"));
      given(assembler.loadByParticipation(OTHER_PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class),
                  buncheol,
                  "장원영",
                  participant,
                  mock(User.class),
                  32_000L));

      listener.onBuncheolConfirmed(
          new BuncheolConfirmedEvent(
              BUNCHEOL_ID, List.of(PARTICIPATION_ID, OTHER_PARTICIPATION_ID)));

      assertThat(captureSend(AlimtalkTemplate.BUNCHEOL_CONFIRMED, PARTICIPANT_PHONE))
          .containsEntry("멤버명", "장원영");
    }
  }

  @Nested
  @DisplayName("C2C 성사 확정 입금 안내(onBuncheolCollectingStarted)")
  class BuncheolCollectingStarted {

    @Test
    @DisplayName("다슬롯 참여자에게는 멤버명·금액을 합산해 1건만 발송")
    void mergesMultipleSlotsOfSameParticipant() {
      Buncheol buncheol = c2cCollectingBuncheol();
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      ParticipationView first = collectingView(buncheol, "호시", participant, 25_000L);
      ParticipationView second = collectingView(buncheol, "우지", participant, 30_000L);
      given(assembler.loadByParticipation(PARTICIPATION_ID)).willReturn(first);
      given(assembler.loadByParticipation(OTHER_PARTICIPATION_ID)).willReturn(second);

      listener.onBuncheolCollectingStarted(
          new BuncheolCollectingStartedEvent(
              BUNCHEOL_ID, List.of(PARTICIPATION_ID, OTHER_PARTICIPATION_ID)));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.C2C_BUNCHEOL_FINALIZED, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("멤버명", "호시 외 1")
          .containsEntry("입금금액", "55,000")
          .containsEntry("은행명", "국민은행");
      verify(inboxRecorder)
          .record(eq(11L), eq(AlimtalkTemplate.C2C_BUNCHEOL_FINALIZED), any());
    }

    @Test
    @DisplayName("커밋 후 취소·확정된 참여는 입금 안내 대상에서 제외한다")
    void skipsParticipationsLeftAwaitingState() {
      Participation cancelled = mock(Participation.class);
      given(cancelled.getStatus()).willReturn(ParticipationStatus.CANCELLED);
      ParticipationView view =
          new ParticipationView(
              cancelled,
              mock(Buncheol.class),
              "호시",
              mock(User.class),
              mock(User.class),
              25_000L);
      given(assembler.loadByParticipation(PARTICIPATION_ID)).willReturn(view);

      listener.onBuncheolCollectingStarted(
          new BuncheolCollectingStartedEvent(BUNCHEOL_ID, List.of(PARTICIPATION_ID)));

      verify(sender, never()).send(any(), any(), any());
      // 발송뿐 아니라 수신함에도 남으면 안 된다 — 기록이 발송보다 먼저라 필터를 우회하면 이쪽만 새어 나간다.
      verify(inboxRecorder, never()).record(any(), any(), any());
    }

    private Buncheol c2cCollectingBuncheol() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("세븐틴 미니 12집 분철");
      given(buncheol.getPaymentAccount())
          .willReturn(BankAccount.of("국민은행", "12345678", "홍길동"));
      given(buncheol.getPaymentDueAt()).willReturn(Instant.parse("2026-08-02T00:00:00Z"));
      return buncheol;
    }

    private ParticipationView collectingView(
        final Buncheol buncheol, final String memberName, final User participant, final long amount) {
      Participation participation = mock(Participation.class);
      given(participation.getStatus()).willReturn(ParticipationStatus.AWAITING_PAYMENT);
      return new ParticipationView(
          participation, buncheol, memberName, participant, mock(User.class), amount);
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

    // 성사 확정 후 개최자 취소는 입금 전 참여자에게도 온다. 입금 이력으로 갈랐다면 "성사되지 않아 취소" 라는 거짓 안내가 나갔을 자리다.
    @Test
    @DisplayName("C2C 개최자 취소는 입금 이력이 없어도 사유를 담은 C2C_BUNCHEOL_CANCELLED 로 발송")
    void c2cHostCancelUsesOwnTemplate() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("세븐틴 미니 12집 분철");
      given(buncheol.isC2c()).willReturn(true);
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "호시", participant, mock(User.class), 25_000L));

      listener.onBuncheolCancelled(
          new BuncheolCancelledEvent(PARTICIPATION_ID, BuncheolCancelReason.HOST_CANCELLED));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.C2C_BUNCHEOL_CANCELLED, PARTICIPANT_PHONE);
      assertThat(variables)
          .containsEntry("멤버명", "호시")
          .containsEntry("취소사유", "개최자 취소");
      verify(inboxRecorder)
          .record(eq(11L), eq(AlimtalkTemplate.C2C_BUNCHEOL_CANCELLED), any());
    }

    @Test
    @DisplayName("C2C 미성사는 환불 대상 대금이 없어 사유 없는 C2C_BUNCHEOL_NOT_FINALIZED 로 발송")
    void notFinalizedUsesNotFinalizedTemplate() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("세븐틴 미니 12집 분철");
      given(buncheol.isC2c()).willReturn(true);
      User participant = mockUser("참여자닉", PARTICIPANT_PHONE);
      given(participant.getId()).willReturn(11L);
      given(assembler.loadByParticipation(PARTICIPATION_ID))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "호시", participant, mock(User.class), 25_000L));

      listener.onBuncheolCancelled(
          new BuncheolCancelledEvent(PARTICIPATION_ID, BuncheolCancelReason.NOT_FINALIZED));

      Map<String, String> variables =
          captureSend(AlimtalkTemplate.C2C_BUNCHEOL_NOT_FINALIZED, PARTICIPANT_PHONE);
      assertThat(variables).containsEntry("멤버명", "호시").doesNotContainKey("취소사유");
      verify(inboxRecorder)
          .record(eq(11L), eq(AlimtalkTemplate.C2C_BUNCHEOL_NOT_FINALIZED), any());
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

    @Test
    @DisplayName("같은 분철의 정원 충족 이벤트가 반복돼도 1회만 발송·기록한다")
    void sendsOncePerBuncheol() {
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getId()).willReturn(77L);
      given(buncheol.getTitle()).willReturn("세븐틴 미니 12집 분철");
      User host = mockUser("개최자닉", HOST_PHONE);
      given(host.getId()).willReturn(3L);
      given(assembler.loadBuncheolHost(77L)).willReturn(new BuncheolHostView(buncheol, host));

      listener.onBuncheolFull(new BuncheolFullEvent(77L, 5));
      listener.onBuncheolFull(new BuncheolFullEvent(77L, 5));

      verify(inboxRecorder).record(eq(3L), eq(AlimtalkTemplate.C2C_BUNCHEOL_FULL), any());
      verify(sender).send(eq(AlimtalkTemplate.C2C_BUNCHEOL_FULL), eq(HOST_PHONE), any());
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
