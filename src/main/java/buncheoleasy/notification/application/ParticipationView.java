package buncheoleasy.notification.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.user.domain.User;

/** 알림 변수 조립에 필요한 참여 단건 스냅샷. {@code paymentAmount} 는 멤버 금액 + 배송비(실제 입금액). */
public record ParticipationView(
    Participation participation,
    Buncheol buncheol,
    String memberName,
    User participant,
    User host,
    long paymentAmount) {}
