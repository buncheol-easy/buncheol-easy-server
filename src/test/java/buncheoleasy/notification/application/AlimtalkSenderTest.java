package buncheoleasy.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import buncheoleasy.notification.domain.AlimtalkTemplate;
import buncheoleasy.notification.infrastructure.AligoAlimtalkClient;
import buncheoleasy.notification.infrastructure.AligoProperties;
import buncheoleasy.notification.infrastructure.AlimtalkSendRequest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlimtalkSenderTest {

  private final AligoAlimtalkClient client = mock(AligoAlimtalkClient.class);

  private AlimtalkSender senderWith(final Map<AlimtalkTemplate, String> codes) {
    AligoProperties properties =
        new AligoProperties(
            "https://kakaoapi.aligo.in",
            "key",
            "uid",
            "sk",
            "01000000000",
            false,
            true,
            Duration.ofSeconds(3),
            Duration.ofSeconds(5),
            codes);
    return new AlimtalkSender(properties, client);
  }

  @Test
  @DisplayName("템플릿 코드가 있으면 렌더링된 본문·버튼으로 발송한다")
  void sendsRenderedMessage() {
    AlimtalkSender sender = senderWith(Map.of(AlimtalkTemplate.PAYMENT_CONFIRMED, "TPL_001"));
    Map<String, String> variables =
        Map.of(
            "닉네임", "철수",
            "분철명", "아이브 앨범",
            "멤버명", "장원영",
            "입금금액", "12,000");

    sender.send(AlimtalkTemplate.PAYMENT_CONFIRMED, "01011112222", variables);

    ArgumentCaptor<AlimtalkSendRequest> captor = ArgumentCaptor.forClass(AlimtalkSendRequest.class);
    verify(client).send(captor.capture());
    AlimtalkSendRequest request = captor.getValue();
    assertThat(request.tplCode()).isEqualTo("TPL_001");
    assertThat(request.receiverPhone()).isEqualTo("01011112222");
    assertThat(request.subject()).isEqualTo("입금 확인 안내");
    assertThat(request.message()).startsWith("철수님, 입금이 확인되었어요!").doesNotContain("#{");
    assertThat(request.button().mobileUrl()).isEqualTo("https://buncheoleasy.com/profile/bids");
  }

  @Test
  @DisplayName("버튼 URL 의 #{분철아이디} 변수도 치환해 발송한다")
  void rendersButtonUrlVariables() {
    AlimtalkSender sender = senderWith(Map.of(AlimtalkTemplate.PARTICIPATION_REQUESTED, "TPL_002"));
    Map<String, String> variables =
        Map.of(
            "닉네임", "개최자",
            "분철명", "엔믹스 앨범",
            "참여자닉네임", "참여자",
            "멤버명", "설윤",
            "입금금액", "20,000",
            "입금기한", "6/10(화) 15:00",
            "분철아이디", "7");

    sender.send(AlimtalkTemplate.PARTICIPATION_REQUESTED, "01011112222", variables);

    ArgumentCaptor<AlimtalkSendRequest> captor = ArgumentCaptor.forClass(AlimtalkSendRequest.class);
    verify(client).send(captor.capture());
    AlimtalkSendRequest request = captor.getValue();
    assertThat(request.subject()).isEqualTo("입금 확인 요청 안내");
    assertThat(request.message()).startsWith("개최자님, 새로운 참여가 들어왔어요.").doesNotContain("#{");
    assertThat(request.button().mobileUrl())
        .isEqualTo("https://buncheoleasy.com/products/7/manage");
  }

  @Test
  @DisplayName("템플릿 코드가 미설정이면 발송하지 않는다")
  void skipsWhenCodeMissing() {
    AlimtalkSender sender = senderWith(Map.of());

    sender.send(AlimtalkTemplate.PAYMENT_CONFIRMED, "01011112222", Map.of());

    verify(client, never()).send(any());
  }

  @Test
  @DisplayName("변수 누락으로 미치환 토큰이 남으면 발송하지 않는다")
  void skipsWhenUnresolvedToken() {
    AlimtalkSender sender = senderWith(Map.of(AlimtalkTemplate.PAYMENT_CONFIRMED, "TPL_001"));

    // 분철명·멤버명 등 변수를 누락 → 본문에 #{...} 가 남는다.
    sender.send(AlimtalkTemplate.PAYMENT_CONFIRMED, "01011112222", Map.of("닉네임", "철수"));

    verify(client, never()).send(any());
  }
}
