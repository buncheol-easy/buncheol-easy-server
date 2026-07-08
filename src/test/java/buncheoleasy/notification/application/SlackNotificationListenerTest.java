package buncheoleasy.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.application.participation.ParticipationCreatedEvent;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.notification.domain.SlackChannel;
import buncheoleasy.notification.infrastructure.SlackWebhookClient;
import buncheoleasy.user.domain.Nickname;
import buncheoleasy.user.domain.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlackNotificationListener 단위 테스트")
class SlackNotificationListenerTest {

  @InjectMocks private SlackNotificationListener listener;

  @Mock private NotificationAssembler assembler;
  @Mock private SlackWebhookClient slackWebhookClient;

  @Nested
  @DisplayName("신규 참여 접수(onParticipationCreated)")
  class ParticipationCreated {

    @Test
    @DisplayName("슬롯 묶음을 메시지 한 건으로 발송 - 분철 ID·환불계좌·멤버명·총액·입금 기한(KST) 포함")
    void sendsSingleMessageForBundle() {
      given(slackWebhookClient.isEnabled(SlackChannel.OPERATION)).willReturn(true);
      // KST 로 7/6 12:30
      Instant dueAt = Instant.parse("2026-07-06T03:30:00Z");
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("엔믹스 앨범");
      given(buncheol.getId()).willReturn(7L);
      User participant = mock(User.class);
      given(participant.getNickname()).willReturn(Nickname.of("참여자닉"));
      given(assembler.loadByParticipations(List.of(1L, 2L)))
          .willReturn(
              new ParticipationBundleView(
                  buncheol,
                  participant,
                  List.of("설윤", "해원"),
                  43_000L,
                  dueAt,
                  RefundAccount.of("국민은행", "11012345678", "김참여")));

      listener.onParticipationCreated(new ParticipationCreatedEvent(List.of(1L, 2L)));

      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      then(slackWebhookClient).should().send(eq(SlackChannel.OPERATION), messageCaptor.capture());
      assertThat(messageCaptor.getValue())
          .contains("엔믹스 앨범 (분철 #7)")
          .contains("참여자닉")
          .contains("국민은행 11012345678 (예금주 김참여)")
          .contains("설윤, 해원")
          .contains("2슬롯")
          .contains("43,000")
          .contains("7/6 12:30");
    }

    @Test
    @DisplayName("웹훅 미설정 환경이면 조립 조회 없이 발송을 건너뛴다")
    void skipsAssemblyWhenDisabled() {
      given(slackWebhookClient.isEnabled(SlackChannel.OPERATION)).willReturn(false);

      listener.onParticipationCreated(new ParticipationCreatedEvent(List.of(1L)));

      then(assembler).should(never()).loadByParticipations(any());
      then(slackWebhookClient).should(never()).send(any(), any());
    }
  }
}
