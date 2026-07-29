package buncheoleasy.buncheol.application.payback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ShippingFeePaybackPolicy 테스트")
class ShippingFeePaybackPolicyTest {

  private static final Instant DELIVERED_AT = Instant.parse("2026-07-15T09:00:00Z");
  private static final Instant NOW_BEFORE_DEADLINE = DELIVERED_AT.plusSeconds(3600);
  // 신청 일수 7일 + 1시간 경과
  private static final Instant NOW_AFTER_DEADLINE =
      DELIVERED_AT.plusSeconds(7 * 24 * 3600 + 3600);

  @Mock private Participation participation;
  @Mock private Delivery delivery;

  private ShippingFeePaybackPolicy policy;

  @BeforeEach
  void setUp() {
    policy = policyWith(true);
    given(participation.getAmount()).willReturn(0L);
    given(participation.getStatus()).willReturn(ParticipationStatus.CONFIRMED);
    given(participation.getPaybackStatus()).willReturn(PaybackStatus.NONE);
    given(delivery.getStatus()).willReturn(DeliveryStatus.DELIVERED);
    given(delivery.getDeliveredAt()).willReturn(DELIVERED_AT);
    given(delivery.getReceivedAt()).willReturn(null);
  }

  private ShippingFeePaybackPolicy policyWith(final boolean enabled) {
    return new ShippingFeePaybackPolicy(new ShippingFeePaybackProperties(enabled, 7));
  }

  @Nested
  @DisplayName("파생 상태 테스트")
  class DeriveStatusTest {

    @Test
    void 이벤트_대상이고_배송_완료면_마감_전에는_ELIGIBLE_이다() {
      assertThat(policy.deriveStatus(participation, delivery, NOW_BEFORE_DEADLINE))
          .isEqualTo(PaybackStatus.ELIGIBLE);
    }

    @Test
    void 수령_완료_상태도_신청_가능하다() {
      given(delivery.getStatus()).willReturn(DeliveryStatus.RECEIVED);

      assertThat(policy.deriveStatus(participation, delivery, NOW_BEFORE_DEADLINE))
          .isEqualTo(PaybackStatus.ELIGIBLE);
    }

    @Test
    void 이벤트가_비활성이면_NONE_이다() {
      assertThat(policyWith(false).deriveStatus(participation, delivery, NOW_BEFORE_DEADLINE))
          .isEqualTo(PaybackStatus.NONE);
    }

    @Test
    void 슬롯_금액이_0원이_아니면_NONE_이다() {
      given(participation.getAmount()).willReturn(30_000L);

      assertThat(policy.deriveStatus(participation, delivery, NOW_BEFORE_DEADLINE))
          .isEqualTo(PaybackStatus.NONE);
    }

    @Test
    void 입금확인되지_않은_참여는_NONE_이다() {
      given(participation.getStatus()).willReturn(ParticipationStatus.AWAITING_PAYMENT);

      assertThat(policy.deriveStatus(participation, delivery, NOW_BEFORE_DEADLINE))
          .isEqualTo(PaybackStatus.NONE);
    }

    @Test
    void 배송_완료_전에는_NONE_이다() {
      given(delivery.getStatus()).willReturn(DeliveryStatus.SHIPPING);

      assertThat(policy.deriveStatus(participation, delivery, NOW_BEFORE_DEADLINE))
          .isEqualTo(PaybackStatus.NONE);
    }

    @Test
    void 배송_스냅샷이_없으면_NONE_이다() {
      assertThat(policy.deriveStatus(participation, null, NOW_BEFORE_DEADLINE))
          .isEqualTo(PaybackStatus.NONE);
    }

    @Test
    void 배송_완료_후_신청_일수가_지나면_EXPIRED_이다() {
      assertThat(policy.deriveStatus(participation, delivery, NOW_AFTER_DEADLINE))
          .isEqualTo(PaybackStatus.EXPIRED);
    }

    @Test
    void 배송_완료_시각이_없으면_마감을_적용하지_않는다() {
      given(delivery.getDeliveredAt()).willReturn(null);
      given(delivery.getReceivedAt()).willReturn(null);

      assertThat(policy.deriveStatus(participation, delivery, NOW_AFTER_DEADLINE))
          .isEqualTo(PaybackStatus.ELIGIBLE);
    }

    @Test
    void 저장된_신청_상태가_있으면_이벤트_설정과_무관하게_그대로_반환한다() {
      given(participation.getPaybackStatus()).willReturn(PaybackStatus.COMPLETED);

      assertThat(policyWith(false).deriveStatus(participation, delivery, NOW_AFTER_DEADLINE))
          .isEqualTo(PaybackStatus.COMPLETED);
    }

    @Test
    void 반려_상태는_마감_전이면_그대로_반려로_반환한다() {
      given(participation.getPaybackStatus()).willReturn(PaybackStatus.REJECTED);

      assertThat(policy.deriveStatus(participation, delivery, NOW_BEFORE_DEADLINE))
          .isEqualTo(PaybackStatus.REJECTED);
    }

    @Test
    void 반려_상태라도_마감이_지나면_EXPIRED_로_재신청을_닫는다() {
      given(participation.getPaybackStatus()).willReturn(PaybackStatus.REJECTED);

      assertThat(policy.deriveStatus(participation, delivery, NOW_AFTER_DEADLINE))
          .isEqualTo(PaybackStatus.EXPIRED);
    }
  }

  @Nested
  @DisplayName("신청 마감 시각 테스트")
  class SubmitDeadlineTest {

    @Test
    void 배송_완료_시각_더하기_신청_일수를_반환한다() {
      assertThat(policy.submitDeadline(participation, delivery))
          .isEqualTo(DELIVERED_AT.plus(Duration.ofDays(7)));
    }

    @Test
    void 배송_완료_시각이_없으면_수령_시각_기준으로_계산한다() {
      Instant receivedAt = Instant.parse("2026-07-16T09:00:00Z");
      given(delivery.getDeliveredAt()).willReturn(null);
      given(delivery.getReceivedAt()).willReturn(receivedAt);

      assertThat(policy.submitDeadline(participation, delivery))
          .isEqualTo(receivedAt.plus(Duration.ofDays(7)));
    }

    @Test
    void 기준_시각이_없으면_null_이다() {
      given(delivery.getDeliveredAt()).willReturn(null);
      given(delivery.getReceivedAt()).willReturn(null);

      assertThat(policy.submitDeadline(participation, delivery)).isNull();
    }

    @Test
    void 배송_스냅샷이_없으면_null_이다() {
      assertThat(policy.submitDeadline(participation, null)).isNull();
    }

    @Test
    void 배송_미완료_상태면_완료_시각이_남아있어도_null_이다() {
      // 상태 정정 등으로 SHIPPING 인데 delivered_at 이 잔존하는 조합 — deriveStatus 의
      // 배송 완료 게이트와 어긋나지 않아야 한다.
      given(delivery.getStatus()).willReturn(DeliveryStatus.SHIPPING);

      assertThat(policy.submitDeadline(participation, delivery)).isNull();
    }

    @Test
    void 이벤트_비대상_참여는_배송이_완료됐어도_null_이다() {
      given(participation.getAmount()).willReturn(30_000L);

      assertThat(policy.submitDeadline(participation, delivery)).isNull();
    }
  }

  @Nested
  @DisplayName("분철 단위 대상 판정 테스트")
  class EventTargetBuncheolTest {

    @Test
    void 전_슬롯_0원이면_대상이다() {
      assertThat(policy.isEventTargetBuncheol(true)).isTrue();
    }

    @Test
    void 유료_슬롯이_있으면_대상이_아니다() {
      assertThat(policy.isEventTargetBuncheol(false)).isFalse();
    }

    @Test
    void 이벤트가_비활성이면_대상이_아니다() {
      assertThat(policyWith(false).isEventTargetBuncheol(true)).isFalse();
    }
  }
}
