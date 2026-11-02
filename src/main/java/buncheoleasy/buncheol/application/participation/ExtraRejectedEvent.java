package buncheoleasy.buncheol.application.participation;

import java.util.List;

/** 개최자가 추가 신청 묶음을 거절했다. 참여자에게 묶음 1통으로 알린다. */
public record ExtraRejectedEvent(Long bundleId, List<Long> participationIds) {}
