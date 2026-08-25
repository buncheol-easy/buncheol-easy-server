package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.FlowType;
import java.time.Instant;
import java.util.List;

/**
 * 분철 단건 상세 조회 응답.
 *
 * @param minHeadcount 분철 진행 최소 인원
 * @param confirmedCount 현재 입금확인된 참여자 수
 * @param images 등록 순(업로드 순) 이미지 목록 — 대표사진 순서 우대 없음. 이미지가 있으면 정확히 1장이 {@code thumbnail=true}
 * @param hostedByMe 호출 유저가 이 분철의 개최자인지 여부. 비로그인 호출이면 항상 false
 * @param myParticipation 비로그인 호출 시 null
 * @param openChatUrl 개최자 소통 채널. <b>개최자 본인과 활성 참여자에게만 내려간다</b> — 그 외(비로그인·비참여자·취소한 참여자)에게는
 *     등록돼 있어도 null 이다. 이 조회가 비로그인에 열려 있어 무조건 실으면 링크가 수집되기 때문이다.
 */
public record BuncheolDetailResponse(
    Long id,
    String title,
    String groupName,
    String purchaseSite,
    Instant deadline,
    String description,
    BuncheolStatus status,
    int minHeadcount,
    int confirmedCount,
    List<BuncheolImageResponse> images,
    List<ShippingOptionResponse> shippingOptions,
    List<BuncheolMemberDetailResponse> members,
    boolean hostedByMe,
    MyParticipationSummaryResponse myParticipation,
    FlowType flowType,
    Instant paymentDueAt,
    String openChatUrl) {}
