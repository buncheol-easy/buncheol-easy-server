package buncheoleasy.buncheol.dto.response;

import java.util.List;

/**
 * 「보냈어요」 마킹 결과. 「제외」와 같은 이유로 <b>실제로 마킹된 슬롯</b>을 돌려준다 — 화면이 본 집합과 다를 수 있어
 * (그 사이 개최자가 먼저 입금확인하는 등) 사후 대조가 필요하다.
 */
public record BundlePaymentSentResponse(Long bundleId, List<Long> markedParticipationIds) {}
