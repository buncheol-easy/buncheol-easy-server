package buncheoleasy.notification.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.user.domain.User;
import java.time.Instant;
import java.util.List;

/**
 * 한 참여 요청(슬롯 묶음)의 알림 변수 조립용 스냅샷. 분철·참여자·환불계좌·입금 기한은 묶음 내에서 동일하고, {@code totalAmount} 는 슬롯별
 * 입금액(멤버 금액 + 배송비, 배송비는 첫 슬롯에만)의 합 = 실제 입금 예정액.
 */
public record ParticipationBundleView(
    Buncheol buncheol,
    User participant,
    List<String> memberNames,
    long totalAmount,
    Instant dueAt,
    RefundAccount refundAccount) {}
