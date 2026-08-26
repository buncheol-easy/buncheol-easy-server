package buncheoleasy.buncheol.domain.member;

/** 멤버 슬롯 생성 파라미터. {@code accessType} 이 null 이면 선착순({@link SlotAccessType#OPEN})으로 만든다. */
public record BuncheolMemberParams(Long memberId, long price, SlotAccessType accessType) {

  public BuncheolMemberParams(final Long memberId, final long price) {
    this(memberId, price, SlotAccessType.OPEN);
  }

  public BuncheolMemberParams {
    accessType = accessType == null ? SlotAccessType.OPEN : accessType;
  }
}
