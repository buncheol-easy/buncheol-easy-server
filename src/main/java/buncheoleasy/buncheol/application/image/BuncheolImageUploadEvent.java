package buncheoleasy.buncheol.application.image;

import java.util.List;

/**
 * @param thumbnailIndex 대표사진으로 지정할 {@code images} 내 인덱스(0-base). null 이면 이번 업로드분에서 대표사진을 지정하지 않는다(수정
 *     시 기존 이미지를 대표로 유지하는 경우).
 */
public record BuncheolImageUploadEvent(
    Long buncheolId, List<ImageFile> images, Integer thumbnailIndex) {}
