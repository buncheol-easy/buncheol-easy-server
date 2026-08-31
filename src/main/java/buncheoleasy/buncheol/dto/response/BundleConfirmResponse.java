package buncheoleasy.buncheol.dto.response;

import java.util.List;

/** 묶음 입금확인 결과. all-or-nothing 이라 요청한 집합과 항상 같지만, 화면이 대조할 수 있게 그대로 돌려준다. */
public record BundleConfirmResponse(Long bundleId, List<Long> confirmedParticipationIds) {}
