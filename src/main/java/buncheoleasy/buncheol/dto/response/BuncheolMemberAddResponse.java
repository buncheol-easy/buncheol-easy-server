package buncheoleasy.buncheol.dto.response;

/** 자리 다시 넣기 응답. 상세 응답의 {@code members[].buncheolMemberId} 와 같은 값이라 바로 신청 슬롯 id 로 쓸 수 있다. */
public record BuncheolMemberAddResponse(Long buncheolMemberId) {

  public static BuncheolMemberAddResponse of(final Long buncheolMemberId) {
    return new BuncheolMemberAddResponse(buncheolMemberId);
  }
}
