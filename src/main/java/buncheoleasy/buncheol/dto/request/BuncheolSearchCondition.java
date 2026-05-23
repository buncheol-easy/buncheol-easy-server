package buncheoleasy.buncheol.dto.request;

/** 분철 목록 검색 조건 (모두 nullable, null 이면 해당 필터 미적용). */
public record BuncheolSearchCondition(Long groupId, Long memberId, String keyword) {}
