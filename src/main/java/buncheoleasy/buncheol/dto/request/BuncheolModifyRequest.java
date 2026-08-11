package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 대표사진 지정은 필수 — {@code thumbnailImageId}(유지 이미지)와 {@code thumbnailIndex}(신규 이미지) 중 정확히 하나를 보내야 한다 (둘 다
 * 생략 시 {@code BUNCHEOL_THUMBNAIL_REQUIRED}, 동시 지정 시 {@code BUNCHEOL_THUMBNAIL_SELECTION_DUPLICATED}).
 *
 * @param thumbnailImageId 유지할 기존 이미지 중 대표사진으로 지정할 이미지 id ({@code keepImageIds} 에 포함돼야 한다)
 * @param thumbnailIndex 신규 업로드 이미지(images 파트) 중 대표사진으로 쓸 인덱스(0-base)
 * @param openChatUrl 오픈채팅 링크 수정(선택 — docs/51 §3-1-2). null = 기존 값 유지(필드를 안 보내는 구 클라이언트 호환), 빈
 *     문자열 = 링크 제거, 값 = 형식 검증 후 교체
 */
public record BuncheolModifyRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 700) String description,
    @NotNull List<Long> keepImageIds,
    Long thumbnailImageId,
    @PositiveOrZero Integer thumbnailIndex,
    @Size(max = 200) String openChatUrl) {}
