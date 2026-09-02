package buncheoleasy.buncheol.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ParticipateRequest — 슬롯 지정 해석")
class ParticipateRequestTest {

  private static ParticipateRequest of(final Long single, final List<Long> multi) {
    return new ParticipateRequest(single, multi, 1L, null, null);
  }

  @Test
  @DisplayName("배열이 있으면 배열이 이긴다")
  void arrayWinsOverSingle() {
    assertThat(of(99L, List.of(1L, 2L)).slotIds()).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("배열이 없으면 단수를 쓴다 — 구버전 클라이언트 호환")
  void fallsBackToSingle() {
    assertThat(of(99L, null).slotIds()).containsExactly(99L);
    assertThat(of(99L, List.of()).slotIds()).containsExactly(99L);
  }

  // 🔴 같은 슬롯을 두 번 보내면 두 번째 INSERT 가 정원 가드에 막혀 전체가 롤백되는데, 사용자에게는
  // "이미 팔렸다" 로 보여 원인이 드러나지 않는다. 요청 단계에서 걷어낸다.
  @Test
  @DisplayName("중복 슬롯은 걷어내되 순서는 보존한다")
  void deduplicatesKeepingOrder() {
    assertThat(of(null, List.of(3L, 1L, 3L, 2L)).slotIds()).containsExactly(3L, 1L, 2L);
  }

  @Test
  @DisplayName("둘 다 없으면 빈 목록이고 검증에서 걸린다")
  void rejectsWhenNothingSpecified() {
    ParticipateRequest empty = of(null, null);
    assertThat(empty.slotIds()).isEmpty();
    assertThat(empty.isSlotSpecified()).isFalse();
  }

  @Test
  @DisplayName("하나라도 지정하면 검증을 통과한다")
  void acceptsWhenSpecified() {
    assertThat(of(1L, null).isSlotSpecified()).isTrue();
    assertThat(of(null, List.of(1L)).isSlotSpecified()).isTrue();
  }
}
