package buncheoleasy.aidraft.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AiDraftText 단위 테스트")
class AiDraftTextTest {

  @Test
  void 상한_이하면_그대로_둔다() {
    assertThat(AiDraftText.fit("가나다", 3)).isEqualTo("가나다");
    assertThat(AiDraftText.fit(null, 3)).isNull();
  }

  @Test
  void 상한을_넘으면_자른다() {
    assertThat(AiDraftText.fit("가나다라", 3)).isEqualTo("가나다");
  }

  @Test
  void 이모지를_반으로_자르지_않는다() {
    // "가나" + 💿(서로게이트 2칸) = 4칸. 3칸에서 자르면 💿의 앞 반쪽만 남아 깨진 글자가 된다.
    String value = "가나💿";

    String fitted = AiDraftText.fit(value, 3);

    assertThat(fitted).isEqualTo("가나");
    assertThat(fitted.length()).isLessThanOrEqualTo(3);
  }

  @Test
  void 이모지가_상한에_딱_맞으면_남긴다() {
    assertThat(AiDraftText.fit("가💿나", 3)).isEqualTo("가💿");
  }

  @Test
  void 엔진은_코드포인트로_세서_이모지가_섞이면_여기서_더_길다() {
    // 엔진(파이썬)은 💿 를 1글자로 센다 — 64자 제목이 여기서는 64칸을 넘을 수 있다.
    String engineSixtyFour = "💿".repeat(64);

    String fitted = AiDraftText.fit(engineSixtyFour, 64);

    assertThat(fitted.length()).isEqualTo(64);
    assertThat(fitted.codePointCount(0, fitted.length())).isEqualTo(32);
  }
}
