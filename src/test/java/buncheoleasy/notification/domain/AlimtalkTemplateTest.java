package buncheoleasy.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AlimtalkTemplate 렌더링")
class AlimtalkTemplateTest {

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
          .startsWith("참여자닉님, 참여하신 분철의 입금이 확인되었어요!")
          .contains("엔믹스 앨범")
          .contains("▶ 참여 멤버: 설윤")
          .contains("▶ 입금 금액: 20,000원")
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
          .contains("▶ 참여 멤버: 카즈하")
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
          Map.of(
              "닉네임", "참여자닉", "분철명", "아이브 앨범", "멤버명", "안유진", "취소사유", "최소 진행 인원 미달");

      String rendered = AlimtalkTemplate.BUNCHEOL_CANCELLED.render(variables);

      assertThat(rendered)
          .startsWith("참여자닉님, 참여하신 분철이 아래 사유로 취소되었어요.")
          .contains("취소 사유: 최소 진행 인원 미달")
          .contains("아이브 앨범")
          .contains("▶ 참여 멤버: 안유진")
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
          .contains("▶ 택배사: CU 편의점 택배")
          .contains("▶ 운송장 번호: 123456789")
          .doesNotContain("GS25")
          .doesNotContain("#{");
      assertThat(AlimtalkTemplate.TRACKING_GS25.render(variables))
          .contains("▶ 택배사: GS25 편의점 택배")
          .contains("▶ 운송장 번호: 123456789")
          .doesNotContain("#{");
    }
  }

  @Nested
  @DisplayName("배송비 환급 완료(PAYBACK_COMPLETED)")
  class PaybackCompleted {

    @Test
    @DisplayName("닉네임·분철명·멤버명·환급 금액을 치환하고 미치환 토큰을 남기지 않는다")
    void render() {
      Map<String, String> variables =
          Map.of("닉네임", "참여자닉", "분철명", "엔믹스 앨범", "멤버명", "설윤", "환급금액", "3,500");

      String rendered = AlimtalkTemplate.PAYBACK_COMPLETED.render(variables);

      assertThat(rendered)
          .startsWith("참여자닉님, 무료 분철 이벤트에 남겨주신 소중한 후기 감사해요!")
          .contains("엔믹스 앨범")
          .contains("▶ 참여 멤버: 설윤")
          .contains("▶ 환급 금액: 3,500원")
          .doesNotContain("#{");
    }
  }

  @Nested
  @DisplayName("배송비 환급 반려(PAYBACK_REJECTED)")
  class PaybackRejected {

    @Test
    @DisplayName("반려 사유와 환급 예정 금액을 치환하고 미치환 토큰을 남기지 않는다")
    void render() {
      Map<String, String> variables =
          Map.of(
              "닉네임", "참여자닉", "분철명", "아이브 앨범", "멤버명", "안유진", "반려사유", "비공개 계정이라 확인 불가", "환급금액",
              "3,500");

      String rendered = AlimtalkTemplate.PAYBACK_REJECTED.render(variables);

      assertThat(rendered)
          .startsWith("참여자닉님, 참여하신 무료 분철 이벤트의 배송비 환급 신청이 반려되었어요.")
          .contains("반려 사유: 비공개 계정이라 확인 불가")
          .contains("아이브 앨범")
          .contains("▶ 참여 멤버: 안유진")
          .contains("▶ 환급 예정 금액: 3,500원")
          .doesNotContain("#{");
    }
  }

  @Nested
  @DisplayName("버튼 구성")
  class Buttons {

    // 발신 채널이 광고추가형(AD)이라 등록본의 1번 버튼이 항상 채널 추가다. 새 템플릿이 이를 빠뜨리면 버튼 불일치로 전량 미발송되므로 정적으로 고정한다.
    @Test
    @DisplayName("모든 템플릿의 1번 버튼은 채널 추가(AC)다")
    void firstButtonIsChannelAdd() {
      for (final AlimtalkTemplate template : AlimtalkTemplate.values()) {
        assertThat(template.buttons()).as("%s 의 버튼", template).isNotEmpty();
        assertThat(template.buttons().get(0).type())
            .as("%s 의 1번 버튼 타입", template)
            .isEqualTo(AlimtalkButtonType.AC);
      }
    }
  }
}
