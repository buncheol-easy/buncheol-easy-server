package buncheoleasy.buncheol.dto.response;

import java.util.List;

/**
 * 로그인 유저가 이 분철에서 가진 활성 참여 요약.
 *
 * @param participatedMemberCount 이 분철 내 내가 활성 참여 중인 멤버 슬롯 수
 * @param participations 내 활성 참여 목록 (멤버 슬롯별 1건)
 * @param inheritanceApplies 지금 자리를 더 신청하면 <b>첫 신청의 묶음을 재사용하는가</b>. 참이면 배송지를 고를 수
 *     없다 — 화면은 선택 UI 를 감춘다. 거짓이면 평소대로 고른다(LEGACY · 성사 확정 뒤 추가 모집 · 활성 참여 없음).
 * @param inheritedShippingAddress 그때 상속될 배송지.
 *     <p>🔴 <b>{@code inheritanceApplies} 와 함께 읽어야 한다.</b> 이 값이 {@code null} 인 경우는 <b>둘</b>이고
 *     화면에서 정반대의 UI 다 — ① 상속 구간이 아니라서(고를 수 있다) ② <b>상속인데 배송지를 못 읽어서</b>
 *     (고정인데 값이 없다). ②에서 "평소대로 고르세요" 를 그리면 유저가 주소를 고른 뒤 쓰기 경로의 {@code
 *     requireShippingAddressIdOf} 에 막혀 <b>고르라고 해 놓고 거부당하는</b> 화면이 된다. 그래서 불리언을 따로 싣는다.
 *     <p>판정은 {@code ParticipationDomainService#findInheritanceSource} — 신청을 처리하는 쪽과 <b>같은</b>
 *     메서드다. 화면이 약속한 주소와 서버가 각인하는 주소가 갈리면 {@code updatable=false} 라 되돌릴 수 없다.
 */
public record MyParticipationSummaryResponse(
    int participatedMemberCount,
    List<MyParticipationItemResponse> participations,
    boolean inheritanceApplies,
    RequestedShippingAddressResponse inheritedShippingAddress) {}
