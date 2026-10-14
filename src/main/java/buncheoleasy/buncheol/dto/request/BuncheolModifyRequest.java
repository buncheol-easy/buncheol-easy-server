package buncheoleasy.buncheol.dto.request;

import buncheoleasy.buncheol.domain.BuncheolTermsChange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * 대표사진 지정은 필수 — {@code thumbnailImageId}(유지 이미지)와 {@code thumbnailIndex}(신규 이미지) 중 정확히 하나를 보내야 한다 (둘 다
 * 생략 시 {@code BUNCHEOL_THUMBNAIL_REQUIRED}, 동시 지정 시 {@code BUNCHEOL_THUMBNAIL_SELECTION_DUPLICATED}).
 *
 * <p>거래 조건 칸({@code deadline}·{@code minHeadcount}·{@code gs25ShippingFee}·{@code cuShippingFee}·{@code
 * purchaseSite})은 선택이다. null 이면 지금 값을 유지하고, 지금 값과 다를 때만 바뀐 것으로 본다. 활성 신청이 없는 모집중 C2C 분철만 바꿀 수
 * 있다. 값 검증은 개최와 같은 규칙을 도메인이 한다 — 여기서 막으면 오류 코드가 공용 입력 오류로 뭉개진다.
 *
 * @param thumbnailImageId 유지할 기존 이미지 중 대표사진으로 지정할 이미지 id ({@code keepImageIds} 에 포함돼야 한다)
 * @param thumbnailIndex 신규 업로드 이미지(images 파트) 중 대표사진으로 쓸 인덱스(0-base)
 * @param openChatUrl 오픈채팅 링크 수정(선택 — docs/51 §3-1-2). null = 기존 값 유지(필드를 안 보내는 구 클라이언트 호환), 빈
 *     문자열·공백 = 링크 제거, 값 = 형식 검증 후 교체
 * @param gs25ShippingFee 배송비 두 칸은 한 묶음 — 둘 다 null 이면 유지, 하나라도 오면 정책 전체 교체(안 보낸 쪽 = 지원 안 함)
 */
public record BuncheolModifyRequest(
    // 제목 64자 — 개최(HoldBuncheolRequest)와 같은 상한 (docs/56 H-02).
    @NotBlank @Size(max = 64) String title,
    @Size(max = 700) String description,
    @NotNull List<Long> keepImageIds,
    Long thumbnailImageId,
    @PositiveOrZero Integer thumbnailIndex,
    @Size(max = 200) String openChatUrl,
    Instant deadline,
    Integer minHeadcount,
    Integer gs25ShippingFee,
    Integer cuShippingFee,
    String purchaseSite) {

  /** 거래 조건 칸이 없는 수정 — 조건 칸을 모르는 요청과 같다. */
  public BuncheolModifyRequest(
      final String title,
      final String description,
      final List<Long> keepImageIds,
      final Long thumbnailImageId,
      final Integer thumbnailIndex,
      final String openChatUrl) {
    this(
        title,
        description,
        keepImageIds,
        thumbnailImageId,
        thumbnailIndex,
        openChatUrl,
        null,
        null,
        null,
        null,
        null);
  }

  public BuncheolTermsChange toTermsChange() {
    return new BuncheolTermsChange(
        deadline, minHeadcount, gs25ShippingFee, cuShippingFee, purchaseSite);
  }
}
