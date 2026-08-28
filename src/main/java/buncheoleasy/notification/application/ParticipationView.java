package buncheoleasy.notification.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.user.domain.User;

/**
 * 알림 변수 조립에 필요한 참여 단건 스냅샷. {@code paymentAmount} 는 멤버 금액 + 배송비(실제 입금액).
 *
 * <p>{@code bundle} 은 환불 계좌·입금자명의 <b>정본</b>이다 (P2-c). 배포선 창에서 생긴 미연결 참여는 {@code null} 일 수 있으므로
 * 역참조 전에 확인할 것 — 그 행은 배포 직후 백필이 채운다.
 */
public record ParticipationView(
    Participation participation,
    ParticipationBundle bundle,
    Buncheol buncheol,
    String memberName,
    User participant,
    User host,
    long paymentAmount) {}
