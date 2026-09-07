package buncheoleasy.buncheol.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 취소 주체 축을 사유 <b>전건</b>에 대해 고정한다.
 *
 * <p>이 분류가 환불 대상 판정에 쓰인다 — 잘못 분류되면 돈을 안 낸 사람 계좌가 개최자에게 노출되거나,
 * 반대로 돌려받아야 할 사람이 목록에서 사라진다.
 */
@DisplayName("ParticipationCancelReason 단위 테스트")
class ParticipationCancelReasonTest {

  @Test
  void 참여자가_스스로_뺀_것은_자발_취소_하나뿐이다() {
    assertThat(ParticipationCancelReason.USER_CANCELLED.isCancelledByParticipant()).isTrue();
    assertThat(ParticipationCancelReason.USER_CANCELLED.isCancelledByHostOrSystem()).isFalse();
  }

  // 개최자가 한 것은 HOST_RELEASED 하나뿐이고, 나머지 둘은 시스템(만료 스케줄러·자동 취소)이다.
  // 셋 다 "참여자가 스스로 뺀 것이 아니다" 라는 점에서 같은 편이다.
  @Test
  void 나머지_셋은_개최자_또는_시스템이_한_취소다() {
    for (ParticipationCancelReason reason :
        new ParticipationCancelReason[] {
          ParticipationCancelReason.HOST_RELEASED,
          ParticipationCancelReason.PAYMENT_TIMEOUT,
          ParticipationCancelReason.BUNCHEOL_CANCELLED
        }) {
      assertThat(reason.isCancelledByHostOrSystem()).as(reason.name()).isTrue();
      assertThat(reason.isCancelledByParticipant()).as(reason.name()).isFalse();
    }
  }

  // 🔴 사유가 늘면 이 테스트가 먼저 깨져야 한다. 분류를 빠뜨린 채 배포되면
  // 새 사유가 조용히 「개최자·시스템」으로 잡혀 환불 목록에 계좌가 뜬다.
  @Test
  void 사유는_네_개이고_두_축이_전건을_덮는다() {
    ParticipationCancelReason[] all = ParticipationCancelReason.values();

    assertThat(all).hasSize(4);
    for (ParticipationCancelReason reason : all) {
      assertThat(reason.isCancelledByParticipant() ^ reason.isCancelledByHostOrSystem())
          .as("%s 는 두 축 중 정확히 하나여야 한다", reason.name())
          .isTrue();
    }
  }
}
