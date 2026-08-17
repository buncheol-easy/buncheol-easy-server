package buncheoleasy.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import buncheoleasy.inbox.domain.InboxMessage;
import buncheoleasy.inbox.domain.InboxMessageRepository;
import buncheoleasy.inbox.domain.InboxMessageType;
import buncheoleasy.notification.domain.AlimtalkTemplate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationInboxRecorder 테스트")
class NotificationInboxRecorderTest {

  @Mock private InboxMessageRepository inboxMessageRepository;

  @InjectMocks private NotificationInboxRecorder recorder;

  private InboxMessage record(
      final Long recipientId, final AlimtalkTemplate template, final Map<String, String> variables) {
    recorder.record(recipientId, template, variables);

    ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
    verify(inboxMessageRepository).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void 입금확인_알림은_NOTIFICATION_타입으로_제목_참고_경로를_채워_저장한다() {
    Map<String, String> variables =
        Map.of(
            "닉네임", "참여자",
            "분철명", "아이브 앨범",
            "멤버명", "장원영",
            "입금금액", "32,000");

    InboxMessage saved = record(7L, AlimtalkTemplate.PAYMENT_CONFIRMED, variables);

    assertThat(saved.getType()).isEqualTo(InboxMessageType.NOTIFICATION);
    assertThat(saved.getRecipientId()).isEqualTo(7L);
    assertThat(saved.getTitle()).isEqualTo(AlimtalkTemplate.PAYMENT_CONFIRMED.subject());
    assertThat(saved.getReference()).isEqualTo("아이브 앨범");
    assertThat(saved.getDescription()).contains("입금이 확인").doesNotContain("#{");
    assertThat(saved.getLinkPath()).isEqualTo("/profile/bids");
    assertThat(saved.isPinned()).isFalse();
  }

  @Test
  void 진행확정_알림의_경로는_참여_내역_화면을_가리킨다() {
    Map<String, String> variables =
        Map.of("닉네임", "참여자", "분철명", "엔믹스 앨범", "멤버명", "설윤");

    InboxMessage saved = record(7L, AlimtalkTemplate.BUNCHEOL_CONFIRMED, variables);

    assertThat(saved.getLinkPath()).isEqualTo("/profile/bids");
  }

  @Test
  void 분철취소_알림의_경로는_참여_내역_화면을_가리킨다() {
    Map<String, String> variables =
        Map.of("닉네임", "참여자", "분철명", "르세라핌 앨범", "멤버명", "카즈하", "취소사유", "개최자 취소");

    InboxMessage saved = record(9L, AlimtalkTemplate.BUNCHEOL_CANCELLED, variables);

    assertThat(saved.getLinkPath()).isEqualTo("/profile/bids");
  }

  @Test
  void 운송장_알림의_경로는_참여_내역_화면을_가리킨다() {
    // 본문이 "아래 배송조회 버튼" 을 언급하는데 수신함엔 배송조회 버튼이 없으므로 참여 내역으로 대체한다.
    Map<String, String> variables =
        Map.of("닉네임", "참여자", "분철명", "아이브 앨범", "멤버명", "안유진", "운송장번호", "123456789");

    InboxMessage saved = record(4L, AlimtalkTemplate.TRACKING_CU, variables);

    assertThat(saved.getLinkPath()).isEqualTo("/profile/bids");
  }

  @Test
  void 개최자_알림의_경로는_분철ID를_치환한_분철_관리_화면을_가리킨다() {
    Map<String, String> variables =
        Map.of("닉네임", "개최자", "분철명", "세븐틴 미니 12집 분철", "신청인원", "5", "분철ID", "77");

    InboxMessage saved = record(3L, AlimtalkTemplate.C2C_BUNCHEOL_FULL, variables);

    assertThat(saved.getLinkPath()).isEqualTo("/products/77/manage");
  }

  @Test
  void 경로_변수가_누락되면_미치환_토큰이_남은_경로_대신_경로_없이_기록한다() {
    Map<String, String> variables = Map.of("닉네임", "개최자", "분철명", "세븐틴 미니 12집 분철", "신청인원", "5");

    InboxMessage saved = record(3L, AlimtalkTemplate.C2C_BUNCHEOL_FULL, variables);

    assertThat(saved.getLinkPath()).isNull();
  }

  @Test
  void 수령독촉_알림은_외부_배송조회라_경로가_없다() {
    Map<String, String> variables =
        Map.of(
            "닉네임", "참여자", "분철명", "아이브 앨범", "멤버명", "안유진", "지점명", "GS25 역삼점", "운송장번호",
            "123456789");

    InboxMessage saved = record(4L, AlimtalkTemplate.PICKUP_REMINDER_GS25, variables);

    assertThat(saved.getLinkPath()).isNull();
  }
}
