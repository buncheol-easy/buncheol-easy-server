package buncheoleasy.buncheol.dto.request;

/** 찜한 분철 목록 정렬 옵션. */
public enum BookmarkSortOption {
  /** 찜 등록 시각 내림차순 (기본). */
  LATEST,
  /** 분철 마감 임박순 (분철 deadline 오름차순). */
  DEADLINE
}
