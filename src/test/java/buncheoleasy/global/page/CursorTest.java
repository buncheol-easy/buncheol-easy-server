package buncheoleasy.global.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Cursor 단위 테스트")
class CursorTest {

  @Nested
  @DisplayName("parse 동작")
  class ParseTest {

    @Test
    void null_이면_첫_페이지를_반환한다() {
      Cursor cursor = Cursor.parse(null);
      assertThat(cursor.isFirstPage()).isTrue();
      assertThat(cursor.createdAt()).isNull();
      assertThat(cursor.id()).isNull();
    }

    @Test
    void 공백_문자열이면_첫_페이지를_반환한다() {
      assertThat(Cursor.parse("   ").isFirstPage()).isTrue();
      assertThat(Cursor.parse("").isFirstPage()).isTrue();
    }

    @Test
    void 정상_형식이면_Instant_와_id_로_분해된다() {
      Cursor cursor = Cursor.parse("2026-05-21T00:00:00Z_42");
      assertThat(cursor.isFirstPage()).isFalse();
      assertThat(cursor.createdAt()).isEqualTo(Instant.parse("2026-05-21T00:00:00Z"));
      assertThat(cursor.id()).isEqualTo(42L);
    }

    @Test
    void 구분자가_없거나_많으면_CURSOR_INVALID_예외() {
      assertCursorInvalid("2026-05-21T00:00:00Z");
      assertCursorInvalid("2026-05-21T00:00:00Z_42_extra");
    }

    @Test
    void Instant_파싱_실패시_CURSOR_INVALID_예외() {
      assertCursorInvalid("not-a-date_42");
    }

    @Test
    void id_파싱_실패시_CURSOR_INVALID_예외() {
      assertCursorInvalid("2026-05-21T00:00:00Z_abc");
    }

    private void assertCursorInvalid(final String raw) {
      assertThatThrownBy(() -> Cursor.parse(raw))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CURSOR_INVALID);
    }
  }

  @Nested
  @DisplayName("encode 동작")
  class EncodeTest {

    @Test
    void 인코딩_라운드트립_정상() {
      Cursor original = new Cursor(Instant.parse("2026-05-21T00:00:00Z"), 42L);
      String encoded = original.encode();
      assertThat(encoded).isEqualTo("2026-05-21T00:00:00Z_42");
      assertThat(Cursor.parse(encoded)).isEqualTo(original);
    }
  }

  @Nested
  @DisplayName("Cursorable 로부터 생성")
  class FromTest {

    @Test
    void Cursorable_의_createdAt_과_id_로_커서를_만든다() {
      Instant createdAt = Instant.parse("2026-05-21T00:00:00Z");
      Cursorable item =
          new Cursorable() {
            @Override
            public Instant getCreatedAt() {
              return createdAt;
            }

            @Override
            public Long getId() {
              return 99L;
            }
          };

      Cursor cursor = Cursor.from(item);

      assertThat(cursor.createdAt()).isEqualTo(createdAt);
      assertThat(cursor.id()).isEqualTo(99L);
    }
  }
}
