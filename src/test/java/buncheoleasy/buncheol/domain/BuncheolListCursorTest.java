package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BuncheolListCursor 단위 테스트")
class BuncheolListCursorTest {

  @Nested
  @DisplayName("firstPage")
  class FirstPageTest {

    @Test
    void firstPage_는_모든_필드가_null_이고_isFirstPage_가_true() {
      BuncheolListCursor cursor = BuncheolListCursor.firstPage();

      assertThat(cursor.groupRank()).isNull();
      assertThat(cursor.sortAt()).isNull();
      assertThat(cursor.id()).isNull();
      assertThat(cursor.isFirstPage()).isTrue();
      assertThat(cursor.isRecruitingGroup()).isFalse();
    }
  }

  @Nested
  @DisplayName("parse")
  class ParseTest {

    @Test
    void null_또는_blank_는_firstPage_로_파싱된다() {
      assertThat(BuncheolListCursor.parse(null).isFirstPage()).isTrue();
      assertThat(BuncheolListCursor.parse("").isFirstPage()).isTrue();
      assertThat(BuncheolListCursor.parse("   ").isFirstPage()).isTrue();
    }

    @Test
    void 모집중_커서를_파싱한다() {
      BuncheolListCursor cursor = BuncheolListCursor.parse("0_2026-05-21T00:00:00Z_42");

      assertThat(cursor.groupRank()).isEqualTo(BuncheolListCursor.RANK_RECRUITING);
      assertThat(cursor.sortAt()).isEqualTo(Instant.parse("2026-05-21T00:00:00Z"));
      assertThat(cursor.id()).isEqualTo(42L);
      assertThat(cursor.isFirstPage()).isFalse();
      assertThat(cursor.isRecruitingGroup()).isTrue();
    }

    @Test
    void 마감_커서를_파싱한다() {
      BuncheolListCursor cursor = BuncheolListCursor.parse("1_2026-06-01T12:00:00Z_7");

      assertThat(cursor.groupRank()).isEqualTo(BuncheolListCursor.RANK_CONFIRMED);
      assertThat(cursor.sortAt()).isEqualTo(Instant.parse("2026-06-01T12:00:00Z"));
      assertThat(cursor.id()).isEqualTo(7L);
      assertThat(cursor.isRecruitingGroup()).isFalse();
    }

    @Test
    void 구성요소_개수가_3_이_아니면_CURSOR_INVALID() {
      assertThatThrownBy(() -> BuncheolListCursor.parse("0_2026-05-21T00:00:00Z"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CURSOR_INVALID);
    }

    @Test
    void rank_가_정수가_아니면_CURSOR_INVALID() {
      assertThatThrownBy(() -> BuncheolListCursor.parse("x_2026-05-21T00:00:00Z_42"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CURSOR_INVALID);
    }

    @Test
    void sortAt_가_Instant_가_아니면_CURSOR_INVALID() {
      assertThatThrownBy(() -> BuncheolListCursor.parse("0_not-an-instant_42"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CURSOR_INVALID);
    }

    @Test
    void id_가_long_이_아니면_CURSOR_INVALID() {
      assertThatThrownBy(() -> BuncheolListCursor.parse("0_2026-05-21T00:00:00Z_abc"))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CURSOR_INVALID);
    }

    @Test
    void rank_가_범위_밖이면_파싱은_되지만_모집중_그룹으로_취급되지_않아_마감_그룹으로_안전하게_흡수된다() {
      // 변조된 커서(rank 0/1 외)는 파싱은 통과하되 isRecruitingGroup()=false → 어댑터가 마감 그룹으로 처리.
      // 데이터 유출·예외 없이 빈/정상 결과만 반환되는 안전한 흡수 동작을 보장한다.
      BuncheolListCursor positive = BuncheolListCursor.parse("5_2026-05-21T00:00:00Z_42");
      BuncheolListCursor negative = BuncheolListCursor.parse("-1_2026-05-21T00:00:00Z_42");

      assertThat(positive.isFirstPage()).isFalse();
      assertThat(positive.isRecruitingGroup()).isFalse();
      assertThat(negative.isRecruitingGroup()).isFalse();
    }
  }

  @Nested
  @DisplayName("from / encode 라운드트립")
  class FromEncodeTest {

    @Test
    void 모집중_분철은_rank0_와_createdAt_으로_인코딩되고_라운드트립된다() {
      Instant createdAt = Instant.parse("2026-05-21T00:00:00Z");
      Buncheol buncheol =
          buncheol(42L, BuncheolStatus.RECRUITING, createdAt, Instant.parse("2026-06-01T12:00:00Z"));

      BuncheolListCursor cursor = BuncheolListCursor.from(buncheol);

      assertThat(cursor.groupRank()).isEqualTo(BuncheolListCursor.RANK_RECRUITING);
      assertThat(cursor.sortAt()).isEqualTo(createdAt);
      assertThat(cursor.id()).isEqualTo(42L);
      assertThat(cursor.encode()).isEqualTo("0_2026-05-21T00:00:00Z_42");
      assertThat(BuncheolListCursor.parse(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void 마감_분철은_rank1_과_deadline_으로_인코딩되고_라운드트립된다() {
      Instant deadline = Instant.parse("2026-06-01T12:00:00Z");
      Buncheol buncheol =
          buncheol(7L, BuncheolStatus.CONFIRMED, Instant.parse("2026-05-01T00:00:00Z"), deadline);

      BuncheolListCursor cursor = BuncheolListCursor.from(buncheol);

      assertThat(cursor.groupRank()).isEqualTo(BuncheolListCursor.RANK_CONFIRMED);
      assertThat(cursor.sortAt()).isEqualTo(deadline);
      assertThat(cursor.id()).isEqualTo(7L);
      assertThat(cursor.encode()).isEqualTo("1_2026-06-01T12:00:00Z_7");
      assertThat(BuncheolListCursor.parse(cursor.encode())).isEqualTo(cursor);
    }
  }

  private Buncheol buncheol(Long id, BuncheolStatus status, Instant createdAt, Instant deadline) {
    Buncheol buncheol = newInstance(Buncheol.class);
    setField(buncheol, "id", id);
    setField(buncheol, "status", status);
    setField(buncheol, "createdAt", createdAt);
    setField(buncheol, "deadline", deadline);
    return buncheol;
  }

  private static <T> T newInstance(Class<T> type) {
    try {
      var constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static void setField(Object target, String fieldName, Object value) {
    try {
      Field field = findField(target.getClass(), fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
    Class<?> current = type;
    while (current != null) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }
}
