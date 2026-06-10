package buncheoleasy.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlimtalkTemplateTest {

  @Test
  @DisplayName("낙찰 템플릿은 변수를 모두 치환하고 미치환 토큰을 남기지 않는다")
  void renderParticipationWon() {
    Map<String, String> variables =
        Map.of(
            "닉네임", "철수",
            "분철명", "아이브 미니 4집",
            "멤버명", "장원영",
            "입금금액", "12,000",
            "입금기한", "6/10(화) 15:00");

    String rendered = AlimtalkTemplate.PARTICIPATION_WON.render(variables);

    assertThat(rendered)
        .startsWith("철수님, 낙찰을 축하해요! 🎉")
        .contains("아이브 미니 4집")
        .contains("▪ 낙찰 멤버 : 장원영")
        .contains("▪ 입금 금액 : 12,000원")
        .contains("▪ 입금 기한 : 6/10(화) 15:00")
        .endsWith("기한이 지나면 차순위 참여자에게 넘어가요.")
        .doesNotContain("#{");
  }

  @Test
  @DisplayName("운송장 템플릿은 택배사 문구가 CU/GS25 로 고정되어 있다")
  void trackingTemplatesHaveFixedCarrier() {
    Map<String, String> variables =
        Map.of("닉네임", "영희", "분철명", "엔믹스 앨범", "멤버명", "설윤", "운송장번호", "123456789");

    assertThat(AlimtalkTemplate.TRACKING_CU.render(variables))
        .contains("▪ 택배사 : CU 편의점 택배")
        .doesNotContain("GS25")
        .doesNotContain("#{");
    assertThat(AlimtalkTemplate.TRACKING_GS25.render(variables))
        .contains("▪ 택배사 : GS25 편의점 택배")
        .doesNotContain("#{");
  }

  @Test
  @DisplayName("입금 확인 요청 템플릿은 참여자 닉네임과 신고 시각을 포함한다")
  void renderPaymentReported() {
    Map<String, String> variables =
        Map.of(
            "닉네임", "개최자닉",
            "분철명", "르세라핌 앨범",
            "참여자닉네임", "참여자닉",
            "멤버명", "카즈하",
            "입금금액", "20,000",
            "신고시각", "6/10(화) 12:00");

    String rendered = AlimtalkTemplate.PAYMENT_REPORTED.render(variables);

    assertThat(rendered)
        .contains("▪ 참여자 : 참여자닉")
        .contains("▪ 요청 시각 : 6/10(화) 12:00")
        .doesNotContain("#{");
  }
}
