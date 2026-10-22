package buncheoleasy.aidraft.infrastructure;

import buncheoleasy.aidraft.domain.AiDraftCost;
import java.util.List;

/**
 * 엔진이 만든 초안 중 본서버가 쓰는 것. 홍보 트윗(promo)·진단 정보(meta 의 나머지)는 받지만 옮기지 않는다.
 *
 * @param images 사진 후보 — 엔진이 준 base64(데이터 URL 접두어 없음) 그대로
 * @param groupMatchKeywords 그룹 찾기 보조 검색어. 엔진이 준 순서 그대로
 */
public record AiWorkerDraft(
    String title,
    String description,
    String purchaseSite,
    String purchaseUrl,
    String groupName,
    List<String> groupMatchKeywords,
    Long listPrice,
    String priceCurrency,
    String releaseDate,
    List<String> images,
    int thumbnailIndex,
    AiDraftCost cost) {}
