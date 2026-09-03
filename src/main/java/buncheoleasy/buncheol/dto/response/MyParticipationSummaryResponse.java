package buncheoleasy.buncheol.dto.response;

import java.util.List;

/**
 * 로그인 유저가 이 분철에서 가진 활성 참여 요약.
 *
 * @param participatedMemberCount 이 분철 내 내가 활성 참여 중인 멤버 슬롯 수
 * @param participations 내 활성 참여 목록 (멤버 슬롯별 1건)
 * @param inheritedShippingAddress 지금 이 분철에 <b>자리를 더 신청하면 상속될</b> 배송지. 모집중 재참여는 첫 신청의
 *     묶음을 재사용하므로 배송지를 고를 수 없다 — 화면은 이 값을 그대로 보여 주고 선택 UI 를 감춘다. 상속 구간이
 *     아니거나(성사 확정 뒤 추가 모집) 활성 참여가 없으면 {@code null} 이고, 그때는 화면이 평소대로 배송지를 고른다.
 *     <p>🔴 판정은 {@code ParticipationDomainService#findInheritanceSource} — 신청을 처리하는 쪽과 <b>같은</b>
 *     메서드다. 화면이 약속한 주소와 서버가 각인하는 주소가 갈리면 {@code updatable=false} 라 되돌릴 수 없다.
 */
public record MyParticipationSummaryResponse(
    int participatedMemberCount,
    List<MyParticipationItemResponse> participations,
    RequestedShippingAddressResponse inheritedShippingAddress) {}
