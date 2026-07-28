package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param thumbnailImageId 유지할 기존 이미지 중 대표사진으로 지정할 이미지 id ({@code keepImageIds} 에 포함돼야 한다)
 * @param thumbnailIndex 신규 업로드 이미지(images 파트) 중 대표사진으로 쓸 인덱스(0-base). {@code thumbnailImageId} 와 동시 지정
 *     불가. 둘 다 생략하면 기존 대표사진을 유지한다(대표사진이 삭제되면 가장 먼저 등록된 이미지로 폴백).
 */
public record BuncheolModifyRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 700) String description,
    @NotNull List<Long> keepImageIds,
    Long thumbnailImageId,
    @PositiveOrZero Integer thumbnailIndex) {}
