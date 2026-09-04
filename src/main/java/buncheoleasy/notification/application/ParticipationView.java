package buncheoleasy.notification.application;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.user.domain.User;
import java.time.Instant;

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
    long paymentAmount) {

  /**
   * 알림톡에 실을 <b>입금 기한</b>. C2C 의 정본은 묶음이다 — 이체가 한 번이므로 기한도 하나다.
   *
   * <p>🔴 화면과 문자가 <b>같은 값</b>을 말해야 한다. 응답만 묶음으로 옮기고 여기를 두면, 참여자가
   * 방금 받은 문자와 화면이 서로 다른 기한을 말한다.
   *
   * <p>LEGACY 는 자리 값 그대로다 — 그쪽에서는 이 값이 표시값이 아니라 수동 입금확인·자동 취소의
   * 판정 조건이다.
   */
  public Instant paymentDueAt() {
    if (!buncheol.isC2c() || bundle == null || bundle.getDueAt() == null) {
      return participation.getDueAt();
    }

    return bundle.getDueAt();
  }
}
