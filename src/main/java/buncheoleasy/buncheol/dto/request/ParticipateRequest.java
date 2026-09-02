package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 분철 참여 신청 요청.
 *
 * <p><b>슬롯 지정은 두 가지다.</b> {@code buncheolMemberIds} 로 <b>여러 슬롯을 한 번에</b> 신청하거나,
 * {@code buncheolMemberId} 로 하나만 신청한다. 배열이 있으면 배열이 이긴다 — 구버전 클라이언트가 단수만
 * 보내므로 둘 다 받는다.
 *
 * <p>🔴 <b>다중 슬롯은 C2C 모집중 전용이다.</b> LEGACY 는 1인 1활성슬롯이 DB 유니크로 강제돼 있어 애초에
 * 열리지 않는다({@code uq_participations_legacy_active_participant}). 성사 확정 뒤 추가 모집도 막는다 —
 * 그 구간은 슬롯마다 별개 묶음·별개 이체라 합산 응답 1건이 거짓이 되기 때문이다({@code
 * ParticipationService#participate} javadoc).
 *
 * <p>🔴 <b>하나라도 실패하면 전체 롤백</b>이다 (docs/70 §5). 3개 중 하나가 이미 팔렸으면 나머지 둘도 만들지
 * 않는다 — 부분 성공을 남기면 참여자가 "몇 개가 잡혔는지" 를 화면에서 재구성해야 한다.
 *
 * @param refundAccount <b>무시한다.</b> 환불 계좌는 서버가 마이페이지 정산 계좌에서 읽는다 — 계좌 입력 경로가 마이페이지 하나뿐이라 요청으로
 *     받으면 왕복만 늘고 화면에 보이는 계좌와 저장되는 계좌가 갈릴 수 있다. 구버전 클라이언트 호환을 위해 필드만 남겼고, 클라이언트 배포 후 제거한다.
 * @param buncheolMemberIds 한 번에 신청할 슬롯들. ⚠️ {@code @Size(max = 20)} 은 <b>중복 제거 전</b>
 *     배열 길이에 걸린다 — 상한의 의미가 "실제 신청 슬롯 수" 가 아니라 "보낸 배열 길이" 다. 같은 id 20개는
 *     통과해 슬롯 1개가 되고, 유니크 20개 + 중복 5개는 400 이다. 검증을 {@code slotIds()} 뒤로 미루면
 *     클라이언트가 "20개를 보냈는데 왜 400 이지" 를 역산해야 하므로 보낸 그대로를 기준으로 둔다.
 * @param participationCode 코드 참여 슬롯({@code CODE_ONLY})에 참여할 때 제출하는 코드. 선착순 슬롯에 보내면 거부한다.
 *     다중 슬롯 신청에는 쓸 수 없다 — 코드는 슬롯 하나에 대응한다.
 */
public record ParticipateRequest(
    Long buncheolMemberId,
    @Size(max = 20) List<@NotNull Long> buncheolMemberIds,
    @NotNull Long shippingAddressId,
    RefundAccountRequest refundAccount,
    @Size(max = 32) String participationCode) {

  public ParticipateRequest(final Long buncheolMemberId, final Long shippingAddressId) {
    this(buncheolMemberId, null, shippingAddressId, null, null);
  }

  public ParticipateRequest(
      final Long buncheolMemberId, final Long shippingAddressId, final String participationCode) {
    this(buncheolMemberId, null, shippingAddressId, null, participationCode);
  }

  /**
   * 슬롯을 하나도 안 보내면 검증 계층에서 400 으로 끊는다. 서비스까지 흘려보내면 같은 입력 오류가 다른 코드로
   * 나가고, 단수 필드에 걸려 있던 {@code @NotNull} 계약이 배열 도입으로 조용히 사라진다.
   */
  @AssertTrue(message = "참여할 멤버 슬롯을 지정해야 합니다.")
  public boolean isSlotSpecified() {
    return !slotIds().isEmpty();
  }

  /**
   * 신청할 슬롯 목록. 배열이 있으면 그것을, 없으면 단수를 하나짜리 목록으로 돌려준다.
   *
   * <p>중복은 여기서 걷어낸다 — 같은 슬롯을 두 번 보내면 두 번째 INSERT 가 정원 가드에 막혀 전체가 롤백되는데,
   * 사용자에게는 "이미 팔렸다" 로 보여 원인이 드러나지 않는다. 순서는 보존한다(첫 슬롯이 배송비를 진다).
   */
  public List<Long> slotIds() {
    if (buncheolMemberIds != null && !buncheolMemberIds.isEmpty()) {
      return buncheolMemberIds.stream().distinct().toList();
    }
    return buncheolMemberId == null ? List.of() : List.of(buncheolMemberId);
  }
}
