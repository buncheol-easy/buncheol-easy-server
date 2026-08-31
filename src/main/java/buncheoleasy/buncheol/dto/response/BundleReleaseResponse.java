package buncheoleasy.buncheol.dto.response;

import java.util.List;

/**
 * 「제외」 결과. <b>실제로 취소된 슬롯 id 를 돌려주는 것</b>이 계약이다 — 개최자가 화면에서 본 슬롯 집합과 실제로 빠진 집합이
 * 다를 수 있어(그 사이 참여자가 자발 취소하는 등) 화면이 사후 대조할 수 있어야 한다.
 */
public record BundleReleaseResponse(Long bundleId, List<Long> releasedParticipationIds) {}
