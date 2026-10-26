package buncheoleasy.aidraft.dto.request;

/**
 * AI 개최글 초안 요청. 둘 중 정확히 하나만 채운다. 검증은 {@code @Valid} 가 아니라 서비스가 기능 스위치·개최 자격 다음에 한다 — 꺼진 기능이나 자격
 * 없는 사람에게 입력 오류부터 보여 주지 않게.
 *
 * @param url 판매 페이지 주소 (http/https)
 * @param rawText 판매 페이지를 읽지 못할 때 붙여 넣는 상품 설명
 */
public record CreateAiDraftRequest(String url, String rawText) {}
