package buncheoleasy.buncheol.dto.response;

import java.time.Instant;

/**
 * 분철 상세 화면의 멤버별 카드.
 *
 * @param price 호스트가 설정한 해당 멤버의 고정 금액 (100원 단위)
 * @param saleStatus 판매 상태 (AVAILABLE=공석 / APPLIED=C2C 무입금 신청으로 선점 / AWAITING_PAYMENT=선점 후 입금 확인 중 /
 *     SOLD=판매 완료 / CLOSED=신규 참여를 받지 않는 분철의 공석)
 * @param paymentDueAt 선점한 참여의 입금 기한. AWAITING_PAYMENT 일 때만 값이 있고, 이 시각이 지나면 슬롯이 공석으로 풀린다 —
 *     단 그때 분철이 신규 참여를 받지 않는 상태면 AVAILABLE 이 아니라 CLOSED 가 되므로 "이 시각에 다시 신청 가능"으로 안내하면 안 된다
 * @param participatedByMe 이 슬롯을 점유한 활성 참여(선점·구매)가 호출 유저의 것인지. 비로그인 호출이면 항상 false
 */
public record BuncheolMemberDetailResponse(
    Long buncheolMemberId,
    Long memberId,
    String memberName,
    String memberImage,
    long price,
    BuncheolMemberSaleStatus saleStatus,
    Instant paymentDueAt,
    boolean participatedByMe) {}
