package buncheoleasy.deposit.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import buncheoleasy.buncheol.application.participation.ParticipationCreatedEvent;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.domain.participation.ShippingFeeAttribution;
import buncheoleasy.deposit.infrastructure.PayActionClient;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


/**
 * 🔴 이 PR 에서 <b>돈에 가장 가까운 분기</b>를 고정한다.
 *
 * <p>입금자명 없이 페이액션에 주문을 등록하면 <b>금액만으로 오매칭</b>되어 남의 입금이 남의 참여를 확정시킬 수 있다.
 * 그래서 알림과 달리 여기서는 <b>등록 자체를 스킵</b>한다 — 자동확인만 못 하고 운영자가 슬랙 신규 참여 알림을 보고
 * 수동 확인한다(그 알림은 {@code SlackNotificationListener} 가 계좌 없이도 발송한다).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DepositOrderListener 단위 테스트")
class DepositOrderListenerTest {

  private static final Long PARTICIPATION_ID = 500L;
  private static final Long BUNDLE_ID = 77L;

  @InjectMocks private DepositOrderListener listener;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private PayActionClient payActionClient;
  @Mock private BuncheolDomainService buncheolDomainService;

  /**
   * 묶음 배송비로 귀속 판정을 실제로 만들어 스텁한다. 목으로 금액을 고정하면 「묶음 원값을 그대로 더해
   * 다슬롯에서 곱하는」 회귀를 못 잡는다 — 판정 객체가 실물이어야 그 성질이 테스트에 걸린다.
   */
  private ParticipationBundle bundleWith(
      final Participation slot, final long bundleShippingFee, final RefundAccount refundAccount) {
    setField(slot, "bundleId", BUNDLE_ID);
    setField(slot, "status", ParticipationStatus.AWAITING_PAYMENT);
    ParticipationBundle bundle = newInstance(ParticipationBundle.class);
    setField(bundle, "id", BUNDLE_ID);
    setField(bundle, "shippingFee", bundleShippingFee);
    if (refundAccount != null) {
      setField(bundle, "refundAccount", refundAccount);
    }
    given(participationBundleDomainService.shippingFeeAttributionOf(eq(bundle), any()))
        .willReturn(ShippingFeeAttribution.ofBundle(bundle, List.of(slot)));
    return bundle;
  }

  private Participation participation(final long amount, final long shippingFee) {
    given(payActionClient.isEnabled()).willReturn(true);
    Participation participation = newInstance(Participation.class);
    setField(participation, "id", PARTICIPATION_ID);
    setField(participation, "amount", amount);
    setField(participation, "shippingFee", shippingFee);
    setField(participation, "createdAt", Instant.parse("2026-05-14T12:00:00Z"));
    setField(participation, "dueAt", Instant.parse("2026-05-14T12:30:00Z"));
    given(participationDomainService.getParticipation(PARTICIPATION_ID)).willReturn(participation);
    return participation;
  }

  @Test
  void 묶음의_예금주로_주문을_등록한다() {
    Participation participation = participation(50_000L, 0L);
    ParticipationBundle bundle =
        bundleWith(participation, 3_000L, RefundAccount.of("국민", "12345678", "홍길동"));
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.of(bundle));

    listener.onParticipationCreated(new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY));

    then(payActionClient)
        .should()
        .registerOrder(eq(PARTICIPATION_ID), eq(53_000L), eq("홍길동"), any(), any());
  }

  // 🔴 빈 입금자명으로 등록하면 금액만으로 오매칭된다 — 등록하느니 안 하는 게 낫다.
  //
  // ⚠️ 이 테스트는 <b>결과</b>(주문 미등록)를 고정하지 <b>수단</b>(명시적 가드)을 고정하지 못한다. 가드를 지워도
  // 리스너가 RuntimeException 을 통째로 잡아서, 인자 평가 중 NPE 가 나고 registerOrder 는 어차피 호출되지
  // 않는다. 돈을 지키는 성질은 "등록되지 않는다" 이므로 이 단언이 그 성질을 지킨다 — 다만 가드의 값은
  // ERROR 로그 대신 의도된 warn 을 남기는 것이라는 점을 알고 있을 것.
  @Test
  void 묶음이_없으면_주문을_등록하지_않는다() {
    Participation participation = participation(50_000L, 3_000L);
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.empty());
    given(participationBundleDomainService.shippingFeeAttributionOf(isNull(), any()))
        .willReturn(ShippingFeeAttribution.empty());

    listener.onParticipationCreated(new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY));

    then(payActionClient).should(never()).registerOrder(anyLong(), anyLong(), any(), any(), any());
  }

  // 🔴 이 PR 이 막는 사고를 직접 고정한다.
  //
  // 참여 행의 배송비 쓰기를 없애면 새 행은 shipping_fee = 0 이다. 그때 「상품 0원 + 배송비만 내는」
  // 참여(0원 이벤트 분철)를 저장값으로 판정하면 <b>무료로 뒤집혀</b> 페이액션 등록이 통째로 빠지고,
  // 참여자는 배송비를 보냈는데 자동 입금확인이 영영 오지 않는다. prod LEGACY 참여의 절반이 이 모양이다.
  @Test
  void 상품이_0원이어도_묶음_배송비가_있으면_등록한다() {
    Participation participation = participation(0L, 0L);
    ParticipationBundle bundle =
        bundleWith(participation, 3_000L, RefundAccount.of("국민", "12345678", "홍길동"));
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.of(bundle));

    listener.onParticipationCreated(new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY));

    then(payActionClient)
        .should()
        .registerOrder(eq(PARTICIPATION_ID), eq(3_000L), eq("홍길동"), any(), any());
  }

  // 0원 참여는 매칭할 입금이 없다. 판정은 계좌 유무가 아니라 금액이다.
  @Test
  void _0원_참여는_묶음이_있어도_등록하지_않는다() {
    Participation participation = participation(0L, 0L);
    ParticipationBundle bundle = bundleWith(participation, 0L, null);
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.of(bundle));

    listener.onParticipationCreated(new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY));

    // 묶음을 게이트보다 먼저 읽게 됐다(0원 판정이 묶음 배송비를 봐야 하므로). 돈을 지키는 성질은
    // 「등록되지 않는다」이고 그건 위 단언이 지킨다.
    then(payActionClient).should(never()).registerOrder(anyLong(), anyLong(), any(), any(), any());
  }

  private static <T> T newInstance(final Class<T> type) {
    try {
      Constructor<T> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
