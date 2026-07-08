package buncheoleasy.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import buncheoleasy.buncheol.application.participation.ParticipationCreatedEvent;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
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
    @DisplayName("슬롯 묶음을 메시지 한 건으로 발송 - 멤버명 나열, 총액 합산, 입금 기한 KST 표기")
    void sendsSingleMessageForBundle() {
      // KST 로 7/6 12:30
      Instant dueAt = Instant.parse("2026-07-06T03:30:00Z");
      Buncheol buncheol = mock(Buncheol.class);
      given(buncheol.getTitle()).willReturn("엔믹스 앨범");
      User participant = mock(User.class);
      given(participant.getNickname()).willReturn(Nickname.of("참여자닉"));
      Participation firstParticipation = mock(Participation.class);
      given(firstParticipation.getDueAt()).willReturn(dueAt);
      given(assembler.loadByParticipation(1L))
          .willReturn(
              new ParticipationView(
                  firstParticipation, buncheol, "설윤", participant, mock(User.class), 23_000L));
      given(assembler.loadByParticipation(2L))
          .willReturn(
              new ParticipationView(
                  mock(Participation.class), buncheol, "해원", participant, mock(User.class), 20_000L));

      listener.onParticipationCreated(new ParticipationCreatedEvent(List.of(1L, 2L)));

      ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
      then(slackWebhookClient).should().send(messageCaptor.capture());
      assertThat(messageCaptor.getValue())
          .contains("엔믹스 앨범")
          .contains("참여자닉")
          .contains("설윤, 해원")
          .contains("2슬롯")
          .contains("43,000")
          .contains("7/6 12:30");
    }
  }
}
