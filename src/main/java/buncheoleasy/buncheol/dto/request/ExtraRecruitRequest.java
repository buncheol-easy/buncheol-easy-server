package buncheoleasy.buncheol.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 추가 모집 스위치 요청.
 *
 * @param open true 면 켜고 false 면 끈다. 필수 — 빠진 요청을 끄기로 받지 않는다
 */
public record ExtraRecruitRequest(@NotNull Boolean open) {}
