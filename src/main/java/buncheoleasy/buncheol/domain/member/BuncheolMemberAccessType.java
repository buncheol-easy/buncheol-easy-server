package buncheoleasy.buncheol.domain.member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BuncheolMemberAccessType {
  OPEN("선착순"),
  CODE_ONLY("코드 참여"),
  // C2C 개최자가 「빼기」로 닫은 자리. 취소 기록 같은 참여 행이 FK 로 붙어 있어 행을 지울 수 없을 때만 쓴다 — 신청을 받지 않는다.
  // ⚠️ 이 값을 모르는 옛 서버는 @Enumerated(STRING) 로 읽다가 500 이 난다(롤백 시 주의 — PR 배포 체크리스트).
  CLOSED("마감");

  private final String description;

  public boolean requiresCode() {
    return this == CODE_ONLY;
  }

  public boolean isClosed() {
    return this == CLOSED;
  }
}
