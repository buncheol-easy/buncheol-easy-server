package buncheoleasy.deposit.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.buncheol.application.participation.SystemPaymentConfirmResult;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.notification.domain.SlackChannel;
import buncheoleasy.notification.infrastructure.SlackWebhookClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepositWebhookService 단위 테스트")
class DepositWebhookServiceTest {

  private static final Long PARTICIPATION_ID = 500L;
  private static final String ORDER_NUMBER = "500";
  private static final String MATCHED = "매칭완료";

  @Mock private ParticipationService participationService;
  @Mock private SlackWebhookClient slackWebhookClient;

  private DepositWebhookService depositWebhookService;

  @BeforeEach
  void setUp() {
    depositWebhookService =
        new DepositWebhookService(
            participationService, slackWebhookClient, "https://admin.example.com");
  }

  @Test
  void 매칭완료_웹훅이면_자동_입금확인을_수행하고_알리지_않는다() {
    given(participationService.confirmPaymentBySystem(PARTICIPATION_ID))
        .willReturn(SystemPaymentConfirmResult.CONFIRMED);

    depositWebhookService.handleMatched(ORDER_NUMBER, MATCHED);

    then(participationService).should().confirmPaymentBySystem(PARTICIPATION_ID);
    then(slackWebhookClient).should(never()).send(any(), anyString());
  }

  @Test
  void 이미_확정된_참여면_알리지_않는다() {
    // 웹훅 재전송·운영자 수동확인 선행. 정상 상황이라 운영 채널을 시끄럽게 하지 않는다.
    given(participationService.confirmPaymentBySystem(PARTICIPATION_ID))
        .willReturn(SystemPaymentConfirmResult.ALREADY_CONFIRMED);

    depositWebhookService.handleMatched(ORDER_NUMBER, MATCHED);

    then(slackWebhookClient).should(never()).send(any(), anyString());
  }

  @Test
  void 기한이_지나_확정할_수_없으면_운영자에게_알린다() {
    // 자동확인 실패의 안전망. 돈은 들어왔는데 참여가 없으므로 환불 판단이 필요하다.
    given(participationService.confirmPaymentBySystem(PARTICIPATION_ID))
        .willReturn(SystemPaymentConfirmResult.NOT_CONFIRMABLE);
    given(slackWebhookClient.isEnabled(SlackChannel.NEW_PARTICIPATION)).willReturn(true);

    depositWebhookService.handleMatched(ORDER_NUMBER, MATCHED);

    then(slackWebhookClient).should().send(eqChannel(), anyString());
  }

  @Test
  void 매칭완료가_아닌_상태는_확정을_시도하지_않는다() {
    depositWebhookService.handleMatched(ORDER_NUMBER, "매칭대기");

    then(participationService).should(never()).confirmPaymentBySystem(anyLong());
    then(slackWebhookClient).should(never()).send(any(), anyString());
  }

  @Test
  void 주문번호를_참여_ID_로_해석할_수_없으면_확정을_시도하지_않고_알린다() {
    given(slackWebhookClient.isEnabled(SlackChannel.NEW_PARTICIPATION)).willReturn(true);

    depositWebhookService.handleMatched("not-a-number", MATCHED);

    then(participationService).should(never()).confirmPaymentBySystem(anyLong());
    then(slackWebhookClient).should().send(eqChannel(), anyString());
  }

  @Test
  void 참여_조회에_실패하면_예외를_삼키고_알린다() {
    // 예외를 밖으로 던지면 컨트롤러가 오류를 응답하고 페이액션이 재전송을 반복한다.
    given(participationService.confirmPaymentBySystem(PARTICIPATION_ID))
        .willThrow(new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));
    given(slackWebhookClient.isEnabled(SlackChannel.NEW_PARTICIPATION)).willReturn(true);

    depositWebhookService.handleMatched(ORDER_NUMBER, MATCHED);

    then(slackWebhookClient).should().send(eqChannel(), anyString());
  }

  private static SlackChannel eqChannel() {
    return org.mockito.ArgumentMatchers.eq(SlackChannel.NEW_PARTICIPATION);
  }
}
