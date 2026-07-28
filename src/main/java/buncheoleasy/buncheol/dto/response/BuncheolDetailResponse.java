package buncheoleasy.buncheol.dto.response;

import buncheoleasy.buncheol.domain.BuncheolStatus;
import java.time.Instant;
import java.util.List;

/**
 * 분철 단건 상세 조회 응답.
 *
 * @param minHeadcount 분철 진행 최소 인원
 * @param confirmedCount 현재 입금확인된 참여자 수
 * @param imageUrls 등록 순(업로드 순) 이미지 URL 목록. 대표사진 순서 우대 없음
 * @param imageIds {@code imageUrls} 와 같은 순서의 이미지 id 목록(수정 시 keepImageIds 로 사용)
 * @param thumbnailImageId 대표사진 이미지 id. 플래그가 없으면 첫 번째 이미지로 폴백, 이미지가 없으면 null
 * @param hostedByMe 호출 유저가 이 분철의 개최자인지 여부. 비로그인 호출이면 항상 false
 * @param myParticipation 비로그인 호출 시 null
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
    List<String> imageUrls,
    List<Long> imageIds,
    Long thumbnailImageId,
    List<ShippingOptionResponse> shippingOptions,
    List<BuncheolMemberDetailResponse> members,
    boolean hostedByMe,
    MyParticipationSummaryResponse myParticipation) {}
