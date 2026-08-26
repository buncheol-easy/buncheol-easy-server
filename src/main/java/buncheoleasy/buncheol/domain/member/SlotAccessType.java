package buncheoleasy.buncheol.domain.member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 멤버 슬롯에 누가 참여할 수 있는가. 코드 수명과 무관한 지속 속성이라 {@code ParticipationCode} 와 분리한다 — 코드가 만료돼도
 * 슬롯은 배정 상태로 남아야 차순위에게 재발급할 수 있다.
 */
@Getter
@RequiredArgsConstructor
public enum SlotAccessType {
  OPEN("선착순"),
  CODE_ONLY("코드 참여");

  private final String description;

  public boolean requiresCode() {
    return this == CODE_ONLY;
  }
}
