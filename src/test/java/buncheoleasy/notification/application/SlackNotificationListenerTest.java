package buncheoleasy.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.application.participation.ParticipationCreatedEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackRequestedEvent;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.notification.domain.SlackChannel;
import buncheoleasy.notification.infrastructure.SlackWebhookClient;
import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlackNotificationListener 단위 테스트")
class SlackNotificationListenerTest {

  private static final String FRONTEND_BASE_URL = "https://app.buncheoleasy.com";

  private SlackNotificationListener listener;

  @Mock private NotificationAssembler assembler;
  @Mock private SlackWebhookClient slackWebhookClient;

  @BeforeEach
  void setUp() {
    listener = new SlackNotificationListener(assembler, slackWebhookClient, FRONTEND_BASE_URL);
  }

  @Nested
  @DisplayName("신규 참여 접수(onParticipationCreated)")
  class ParticipationCreated {

    @Test
    @DisplayName("참여 1건을 blocks 메시지 한 건으로 발송 - 분철·환불계좌·멤버·입금액·입금 기한(KST)·링크 포함")
    void sendsMessageForParticipation() {
      given(slackWebhookClient.isEnabled(SlackChannel.NEW_PARTICIPATION)).willReturn(true);
      // KST 로 7/6 12:30
      Instant dueAt = Instant.parse("2026-07-06T03:30:00Z");
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("엔믹스 앨범");
      given(buncheol.getId()).willReturn(7L);
      User participant = mock(User.class);
      given(participant.getNickname()).willReturn(Nickname.of("참여자닉"));
      given(participant.getName()).willReturn("김실명");
      Participation participation = mock(Participation.class);
      given(participation.getRefundAccount())
          .willReturn(RefundAccount.of("국민은행", "11012345678", "김참여"));
      given(participation.getDueAt()).willReturn(dueAt);
      given(assembler.loadByParticipation(1L))
          .willReturn(
              new ParticipationView(participation, buncheol, "설윤", participant, null, 23_000L));

      listener.onParticipationCreated(new ParticipationCreatedEvent(1L));

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Map<String, Object>>> blocksCaptor =
          ArgumentCaptor.forClass((Class) List.class);
      ArgumentCaptor<String> fallbackCaptor = ArgumentCaptor.forClass(String.class);
      then(slackWebhookClient)
          .should()
          .send(
              eq(SlackChannel.NEW_PARTICIPATION),
              fallbackCaptor.capture(),
              blocksCaptor.capture());
      assertThat(fallbackCaptor.getValue()).contains("신규 참여").contains("엔믹스 앨범");
      assertThat(blocksCaptor.getValue().toString())
          .contains("새로운 참여가 들어왔어요")
          .contains("엔믹스 앨범 (#7)")
          .contains("참여자닉(김실명)")
          .contains("국민은행 11012345678 (예금주 김참여)")
          .contains("설윤")
          .contains("23,000")
          .contains("7/6 12:30")
          .contains(FRONTEND_BASE_URL + "/products/7")
          .contains(FRONTEND_BASE_URL + "/admin");
    }

    @Test
    @DisplayName("실명이 없는 기존 회원은 닉네임만 표기한다")
    void sendsNicknameOnlyWhenNameMissing() {
      given(slackWebhookClient.isEnabled(SlackChannel.NEW_PARTICIPATION)).willReturn(true);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("엔믹스 앨범");
      given(buncheol.getId()).willReturn(7L);
      User participant = mock(User.class);
      given(participant.getNickname()).willReturn(Nickname.of("참여자닉"));
      given(participant.getName()).willReturn(null);
      Participation participation = mock(Participation.class);
      given(participation.getRefundAccount())
          .willReturn(RefundAccount.of("국민은행", "11012345678", "김참여"));
      given(participation.getDueAt()).willReturn(Instant.parse("2026-07-06T03:30:00Z"));
      given(assembler.loadByParticipation(1L))
          .willReturn(
              new ParticipationView(participation, buncheol, "설윤", participant, null, 23_000L));

      listener.onParticipationCreated(new ParticipationCreatedEvent(1L));

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Map<String, Object>>> blocksCaptor =
          ArgumentCaptor.forClass((Class) List.class);
      then(slackWebhookClient)
          .should()
          .send(eq(SlackChannel.NEW_PARTICIPATION), anyString(), blocksCaptor.capture());
      assertThat(blocksCaptor.getValue().toString())
          .contains("*참여자*\n참여자닉")
          .doesNotContain("(김실명)");
    }

    @Test
    @DisplayName("웹훅 미설정 환경이면 조립 조회 없이 발송을 건너뛴다")
    void skipsAssemblyWhenDisabled() {
      given(slackWebhookClient.isEnabled(SlackChannel.NEW_PARTICIPATION)).willReturn(false);

      listener.onParticipationCreated(new ParticipationCreatedEvent(1L));

      then(assembler).should(never()).loadByParticipation(any());
      then(slackWebhookClient).should(never()).send(any(), anyString(), anyList());
    }
  }

  @Nested
  @DisplayName("배송비 환급 신청 접수(onShippingFeePaybackRequested)")
  class ShippingFeePaybackRequested {

    @Test
    @DisplayName("신청자·분철·환급액·환불계좌·트윗 링크·어드민 링크를 blocks 로 발송한다")
    void sendsBlocksForPaybackRequest() {
      given(slackWebhookClient.isEnabled(SlackChannel.SHIPPING_FEE_PAYBACK)).willReturn(true);
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("엔믹스 앨범");
      given(buncheol.getId()).willReturn(7L);
      User participant = mock(User.class);
      given(participant.getNickname()).willReturn(Nickname.of("참여자닉"));
      given(participant.getName()).willReturn("김실명");
      Participation participation = mock(Participation.class);
      given(participation.getRefundAccount())
          .willReturn(RefundAccount.of("국민은행", "11012345678", "김참여"));
      given(participation.getPaybackAmount()).willReturn(3_000L);
      given(participation.getPaybackTweetUrl())
          .willReturn("https://x.com/fan/status/1234567890");
      given(assembler.loadByParticipation(1L))
          .willReturn(
              new ParticipationView(participation, buncheol, "설윤", participant, null, 23_000L));

      listener.onShippingFeePaybackRequested(new ShippingFeePaybackRequestedEvent(1L));

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Map<String, Object>>> blocksCaptor =
          ArgumentCaptor.forClass((Class) List.class);
      ArgumentCaptor<String> fallbackCaptor = ArgumentCaptor.forClass(String.class);
      then(slackWebhookClient)
          .should()
          .send(
              eq(SlackChannel.SHIPPING_FEE_PAYBACK),
              fallbackCaptor.capture(),
              blocksCaptor.capture());
      assertThat(fallbackCaptor.getValue()).contains("배송비 돌려받기 신청").contains("엔믹스 앨범");
      assertThat(blocksCaptor.getValue().toString())
          .contains("참여자닉(김실명)")
          .contains("엔믹스 앨범 (#7)")
          .contains("3,000원")
          .contains("국민은행 11012345678 (예금주 김참여)")
          .contains("https://x.com/fan/status/1234567890")
          .contains(FRONTEND_BASE_URL + "/admin");
    }

    @Test
    @DisplayName("웹훅 미설정 환경이면 조립 조회 없이 발송을 건너뛴다")
    void skipsAssemblyWhenDisabled() {
      given(slackWebhookClient.isEnabled(SlackChannel.SHIPPING_FEE_PAYBACK)).willReturn(false);

      listener.onShippingFeePaybackRequested(new ShippingFeePaybackRequestedEvent(1L));

      then(assembler).should(never()).loadByParticipation(any());
      then(slackWebhookClient).should(never()).send(any(), anyString(), anyList());
    }
  }
}
