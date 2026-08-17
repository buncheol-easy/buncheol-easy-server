package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import java.time.Instant;
import java.util.List;

/**
 * 분철 공개 목록 조회 응답의 단일 카드.
 *
 * <p>{@code status} 는 모집중/진행확정/취소 그룹을 클라이언트가 구분(배지·섹션)하도록 노출한다. 목록은 {@code RECRUITING} / {@code
 * CONFIRMED} / {@code CANCELLED}(인원미달 자동취소) 를 보여주고, 개최자 취소({@code HOST_CANCELLED}) 는 제외한다.
 *
 * <p>{@code memberNames} 는 분철의 전체 멤버 이름, {@code availableMemberNames} 는 <b>지금 신청할 수 있는</b> 멤버 이름이다.
 * 둘 다 호스트가 등록한 슬롯 순서로 정렬된다. 신규 참여를 받지 않는 분철(취소·진행확정·마감 경과)은 슬롯이 비어 있어도
 * <b>빈 배열</b>이라, 카드가 {@code status} 로 다시 걸러낼 필요가 없다 — 상세의 {@code CLOSED} 와 같은 판정이다 (docs/56 F-2).
 *
 * <p>{@code shippingFeePaybackTarget} 은 오픈 이벤트 배송비 환급 대상 분철(전 슬롯 0원 + 이벤트 활성) 여부로, 목록 카드의
 * "배송비 돌려받는 무료 분철" 배지 판정에 쓴다.
 *
 * <p>{@code flowType} 은 카드의 상태 배지·dim 판정을 상세 화면과 같은 기준으로 통일하기 위한 필드다 (docs/51 §3-1-2 — 이전에는 카드가
 * 상태값으로 플로우를 추정했다).
 */
public record BuncheolSummaryResponse(
    Long id,
    String title,
    BuncheolStatus status,
    FlowType flowType,
    Instant deadline,
    int minHeadcount,
    boolean bookmarked,
    String groupName,
    String thumbnailUrl,
    List<String> memberNames,
    List<String> availableMemberNames,
    boolean shippingFeePaybackTarget) {}
