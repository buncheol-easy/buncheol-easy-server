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
      final Long recipientId,
      final AlimtalkTemplate template,
      final Map<String, String> variables,
      final Long buncheolId) {
    recorder.record(recipientId, template, variables, buncheolId);

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

    InboxMessage saved = record(7L, AlimtalkTemplate.PAYMENT_CONFIRMED, variables, 5L);

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

    InboxMessage saved = record(7L, AlimtalkTemplate.BUNCHEOL_CONFIRMED, variables, 5L);

    assertThat(saved.getLinkPath()).isEqualTo("/profile/bids");
  }

  @Test
  void 참여요청_알림의_경로는_분철_관리_화면을_가리킨다() {
    Map<String, String> variables =
        Map.of(
            "닉네임", "개최자",
            "분철명", "엔믹스 앨범",
            "참여자닉네임", "참여자",
            "멤버명", "설윤",
            "입금금액", "20,000",
            "입금기한", "3/11(화) 12:00",
            "분철아이디", "5");

    InboxMessage saved = record(3L, AlimtalkTemplate.PARTICIPATION_REQUESTED, variables, 5L);

    assertThat(saved.getLinkPath()).isEqualTo("/products/5/manage");
  }

  @Test
  void 분철취소_알림의_경로는_홈을_가리킨다() {
    Map<String, String> variables =
        Map.of("닉네임", "참여자", "분철명", "르세라핌 앨범", "멤버명", "카즈하");

    InboxMessage saved = record(9L, AlimtalkTemplate.BUNCHEOL_CANCELLED, variables, 5L);

    assertThat(saved.getLinkPath()).isEqualTo("/");
  }

  @Test
  void 운송장_알림은_외부_배송조회라_경로가_없다() {
    Map<String, String> variables =
        Map.of("닉네임", "참여자", "분철명", "아이브 앨범", "멤버명", "안유진", "운송장번호", "123456789");

    InboxMessage saved = record(4L, AlimtalkTemplate.TRACKING_CU, variables, 5L);

    assertThat(saved.getLinkPath()).isNull();
  }
}
