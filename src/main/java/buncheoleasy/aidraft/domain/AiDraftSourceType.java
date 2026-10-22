package buncheoleasy.aidraft.domain;

/** 초안 재료의 종류. */
public enum AiDraftSourceType {
  /** 판매 페이지 주소 — 엔진이 페이지를 받아 읽는다. */
  URL,
  /** 개최자가 붙여 넣은 상품 설명 — 엔진이 읽지 못하는 판매처(네이버·올리브영 등)의 우회로. */
  TEXT
}
