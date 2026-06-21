package buncheoleasy.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AlimtalkTemplate 렌더링")
class AlimtalkTemplateTest {

  @Nested
  @DisplayName("입금 확인 요청(PARTICIPATION_REQUESTED)")
  class ParticipationRequested {

    @Test
    @DisplayName("변수를 모두 치환하고 미치환 토큰을 남기지 않는다")
    void render() {
      Map<String, String> variables =
          Map.of(
              "닉네임", "개최자닉",
              "분철명", "아이브 미니 4집",
              "참여자닉네임", "참여자닉",
              "멤버명", "장원영",
              "입금금액", "12,000",
              "입금기한", "6/10(화) 15:00");

      String rendered = AlimtalkTemplate.PARTICIPATION_REQUESTED.render(variables);

      assertThat(rendered)
          .startsWith("개최자닉님, 새로운 참여가 들어왔어요.")
          .contains("아이브 미니 4집")
          .contains("▪ 참여자 : 참여자닉")
          .contains("▪ 참여 멤버 : 장원영")
          .contains("▪ 입금 금액 : 12,000원")
          .contains("▪ 입금 기한 : 6/10(화) 15:00")
          .doesNotContain("#{");
    }
  }

  @Nested
  @DisplayName("입금 확인(PAYMENT_CONFIRMED)")
  class PaymentConfirmed {

    @Test
    @DisplayName("참여자 닉네임과 입금 금액을 포함하고 미치환 토큰을 남기지 않는다")
    void render() {
      Map<String, String> variables =
          Map.of(
              "닉네임", "참여자닉",
              "분철명", "엔믹스 앨범",
              "멤버명", "설윤",
              "입금금액", "20,000");

      String rendered = AlimtalkTemplate.PAYMENT_CONFIRMED.render(variables);

      assertThat(rendered)
          .startsWith("참여자닉님, 입금이 확인되었어요!")
          .contains("엔믹스 앨범")
          .contains("▪ 참여 멤버 : 설윤")
          .contains("▪ 입금 금액 : 20,000원")
          .doesNotContain("#{");
    }
  }

  @Nested
  @DisplayName("분철 진행 확정(BUNCHEOL_CONFIRMED)")
  class BuncheolConfirmed {

    @Test
    @DisplayName("닉네임·분철명·멤버명을 치환하고 미치환 토큰을 남기지 않는다")
    void render() {
      Map<String, String> variables =
          Map.of("닉네임", "참여자닉", "분철명", "르세라핌 앨범", "멤버명", "카즈하");

      String rendered = AlimtalkTemplate.BUNCHEOL_CONFIRMED.render(variables);

      assertThat(rendered)
          .startsWith("참여자닉님, 참여하신 분철의 진행이 확정되었어요!")
          .contains("르세라핌 앨범")
          .contains("▪ 참여 멤버 : 카즈하")
          .doesNotContain("#{");
    }
  }

  @Nested
  @DisplayName("분철 취소(BUNCHEOL_CANCELLED)")
  class BuncheolCancelled {

    @Test
    @DisplayName("닉네임·분철명·멤버명을 치환하고 미치환 토큰을 남기지 않는다")
    void render() {
      Map<String, String> variables =
          Map.of("닉네임", "참여자닉", "분철명", "아이브 앨범", "멤버명", "안유진");

      String rendered = AlimtalkTemplate.BUNCHEOL_CANCELLED.render(variables);

      assertThat(rendered)
          .startsWith("참여자닉님, 참여하신 분철이 취소되었어요.")
          .contains("아이브 앨범")
          .contains("▪ 참여 멤버 : 안유진")
          .doesNotContain("#{");
    }
  }

  @Nested
  @DisplayName("운송장 등록(TRACKING_*)")
  class Tracking {

    @Test
    @DisplayName("택배사 문구가 CU/GS25 로 고정되어 있다")
    void fixedCarrier() {
      Map<String, String> variables =
          Map.of("닉네임", "영희", "분철명", "엔믹스 앨범", "멤버명", "설윤", "운송장번호", "123456789");

      assertThat(AlimtalkTemplate.TRACKING_CU.render(variables))
          .contains("▪ 택배사 : CU 편의점 택배")
          .contains("▪ 운송장 번호 : 123456789")
          .doesNotContain("GS25")
          .doesNotContain("#{");
      assertThat(AlimtalkTemplate.TRACKING_GS25.render(variables))
          .contains("▪ 택배사 : GS25 편의점 택배")
          .contains("▪ 운송장 번호 : 123456789")
          .doesNotContain("#{");
    }
  }
}
