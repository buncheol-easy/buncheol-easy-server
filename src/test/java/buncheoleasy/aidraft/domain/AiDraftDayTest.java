package buncheoleasy.aidraft.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AiDraftDay 단위 테스트")
class AiDraftDayTest {

  @Test
  void 한국_자정_직전과_직후는_다른_날이다() {
    // 2026-10-22 23:59:59 KST = 14:59:59 UTC, 2026-10-23 00:00:00 KST = 15:00:00 UTC
    AiDraftDay before = AiDraftDay.of(Instant.parse("2026-10-22T14:59:59Z"));
    AiDraftDay after = AiDraftDay.of(Instant.parse("2026-10-22T15:00:00Z"));

    assertThat(before.date()).isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(after.date()).isEqualTo(LocalDate.of(2026, 10, 23));
  }

  @Test
  void UTC_날짜가_아니라_한국_날짜로_센다() {
    // UTC 로는 10/22 이지만 한국은 이미 10/23 오전 8시다 — UTC 로 세면 오전 9시에 초기화된다.
    AiDraftDay day = AiDraftDay.of(Instant.parse("2026-10-22T23:00:00Z"));

    assertThat(day.date()).isEqualTo(LocalDate.of(2026, 10, 23));
  }

  @Test
  void 키_수명은_다음_한국_자정까지에_1시간을_더한다() {
    // 23:59:50 KST 에 시작한 요청이 자정 뒤에 횟수를 돌려줘도 그날 키가 남아 있어야 한다.
    AiDraftDay day = AiDraftDay.of(Instant.parse("2026-10-22T14:59:50Z"));

    assertThat(day.keyTtl()).isEqualTo(Duration.ofSeconds(10).plusHours(1));
  }

  @Test
  void 자정_정각에는_새_날이고_하루_전체에_1시간을_더한다() {
    AiDraftDay day = AiDraftDay.of(Instant.parse("2026-10-22T15:00:00Z"));

    assertThat(day.date()).isEqualTo(LocalDate.of(2026, 10, 23));
    assertThat(day.keyTtl()).isEqualTo(Duration.ofHours(25));
  }

  @Test
  void 자정_1밀리초_전에도_그날이고_수명이_양수다() {
    // 2026-10-22 23:59:59.999 KST
    AiDraftDay day = AiDraftDay.of(Instant.parse("2026-10-22T14:59:59.999Z"));

    assertThat(day.date()).isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(day.keyTtl()).isEqualTo(Duration.ofMillis(1).plusHours(1));
  }

  @Test
  void 자정_1나노초_전에도_밀리초로_바꾼_수명이_1시간_이상이다() {
    // 카운터는 수명을 toMillis() 로 보낸다 — 여유가 없으면 여기서 0ms 가 되어 키가 바로 지워진다.
    AiDraftDay day = AiDraftDay.of(Instant.parse("2026-10-22T14:59:59.999999999Z"));

    assertThat(day.date()).isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(day.keyTtl().toMillis()).isGreaterThanOrEqualTo(Duration.ofHours(1).toMillis());
  }

  @Test
  void 밀리초로_0_이하인_수명은_만들_수_없다() {
    LocalDate date = LocalDate.of(2026, 10, 22);

    assertThatThrownBy(() -> new AiDraftDay(date, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AiDraftDay(date, Duration.ofNanos(999_999)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AiDraftDay(date, Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
