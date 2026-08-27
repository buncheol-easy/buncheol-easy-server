package buncheoleasy.buncheol.domain.member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BuncheolMemberAccessType {
  OPEN("선착순"),
  CODE_ONLY("코드 참여");

  private final String description;

  public boolean requiresCode() {
    return this == CODE_ONLY;
  }
}
