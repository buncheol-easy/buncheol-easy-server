package buncheoleasy.global.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LikeEscaper 테스트")
class LikeEscaperTest {

  @Test
  void null_이면_null_을_돌려준다() {
    assertThat(LikeEscaper.escape(null)).isNull();
  }

  @Test
  void 와일드카드와_이스케이프_문자를_리터럴로_이스케이프한다() {
    assertThat(LikeEscaper.escape("50%")).isEqualTo("50\\%");
    assertThat(LikeEscaper.escape("a_b")).isEqualTo("a\\_b");
    assertThat(LikeEscaper.escape("a\\b")).isEqualTo("a\\\\b");
  }

  @Test
  void 이스케이프_문자를_먼저_처리해_이중_이스케이프를_막는다() {
    assertThat(LikeEscaper.escape("\\%")).isEqualTo("\\\\\\%");
  }

  @Test
  void 일반_문자열은_그대로_돌려준다() {
    assertThat(LikeEscaper.escape("아이브 포카")).isEqualTo("아이브 포카");
  }
}
