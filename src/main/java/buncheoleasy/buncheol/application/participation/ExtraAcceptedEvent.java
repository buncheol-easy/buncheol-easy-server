package buncheoleasy.buncheol.application.participation;

import java.util.List;

/** 개최자가 추가 신청 묶음을 수락했다. 참여자에게 계좌·금액·기한을 묶음 1통으로 알린다. */
public record ExtraAcceptedEvent(Long bundleId, List<Long> participationIds) {}
