package buncheoleasy.aidraft.dto.response;

import java.util.List;

/**
 * 개최 폼에 미리 채울 초안. 마감·멤버·가격은 없다(개최자가 정한다). 비용·홍보 트윗·엔진 진단 정보는 내보내지 않는다.
 *
 * @param title 제목 (64자 이하로 맞춤)
 * @param description 본문 (700자 이하로 맞춤)
 * @param purchaseSite 구매처 이름 (20자 이하). 엔진이 모르는 판매처면 null
 * @param purchaseUrl 판매 페이지 주소. 붙여 넣은 글이면 null
 * @param group 확정한 그룹. 확정하지 못했으면 null — 화면은 {@code groupKeywords} 로 그룹 검색 칸을 채운다
 * @param groupKeywords 그룹 찾기에 쓴 검색어(시도 순서)
 * @param listPrice 정가(원). 원화로 확인된 값만, 없으면 null
 * @param priceCurrency 정가 통화. 정가가 없으면 null
 * @param releaseDate 발매일(yyyy-MM-dd). 모르면 null
 * @param images 사진 후보 — base64(데이터 URL 접두어 없음), 0~2장
 * @param thumbnailIndex 대표사진 순번
 * @param remainingToday 오늘 남은 AI 작성 횟수
 */
public record AiDraftResponse(
    String title,
    String description,
    String purchaseSite,
    String purchaseUrl,
    Group group,
    List<String> groupKeywords,
    Long listPrice,
    String priceCurrency,
    String releaseDate,
    List<String> images,
    int thumbnailIndex,
    int remainingToday) {

  public record Group(Long id, String name) {}
}
