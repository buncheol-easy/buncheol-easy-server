package buncheoleasy.deposit.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import buncheoleasy.buncheol.application.BuncheolCancelReason;
import buncheoleasy.buncheol.application.BuncheolCancelledEvent;
import buncheoleasy.buncheol.application.participation.ParticipationCreatedEvent;
import buncheoleasy.buncheol.application.participation.PaymentExpiredEvent;
import buncheoleasy.buncheol.domain.Buncheol;
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
import buncheoleasy.deposit.infrastructure.PayActionSendException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;


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
  private static final Long BUNCHEOL_ID = 1L;
  private static final Long BUNDLE_ID = 77L;

  @InjectMocks private DepositOrderListener listener;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private PayActionClient payActionClient;
  @Mock private BuncheolDomainService buncheolDomainService;

  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void attachLogAppender() {
    logAppender = new ListAppender<>();
    logAppender.start();
    listenerLogger().addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    listenerLogger().detachAppender(logAppender);
  }

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
    setField(participation, "status", ParticipationStatus.AWAITING_PAYMENT);
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
    // 🔴 진단 파라미터를 고정한다. any() 로 두면 호출부가 <b>엉뚱한 참여</b>(형제 슬롯 등)를 넘겨도
    // 통과하고, 경고가 틀린 participationId 를 가리켜 이 파라미터를 넣은 목적이 무너진다.
    given(participationBundleDomainService.shippingFeeAttributionOf(isNull(), eq(PARTICIPATION_ID)))
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

  // 리스너가 밀린 사이 운영자가 확정한 참여다. 주문을 걸면 매칭 없이 남아 같은 금액의 중복 입금을 알림 없이 삼킨다.
  @Test
  void 첫_조회에서_이미_확정됐으면_주문을_등록하지_않는다() {
    Participation participation = participation(50_000L, 0L);
    setField(participation, "status", ParticipationStatus.CONFIRMED);
    setField(participation, "confirmedAt", Instant.parse("2026-05-14T12:01:00Z"));

    listener.onParticipationCreated(new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY));

    then(payActionClient).should(never()).registerOrder(anyLong(), anyLong(), any(), any(), any());
    then(payActionClient).should(never()).cancelOrder(anyLong());
    then(participationBundleDomainService).should(never()).findByParticipation(any());
  }

  @Test
  void 첫_조회에서_이미_취소됐으면_주문을_등록하지_않는다() {
    Participation participation = participation(50_000L, 0L);
    setField(participation, "status", ParticipationStatus.CANCELLED);

    listener.onParticipationCreated(new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY));

    then(payActionClient).should(never()).registerOrder(anyLong(), anyLong(), any(), any(), any());
    then(payActionClient).should(never()).cancelOrder(anyLong());
    then(participationBundleDomainService).should(never()).findByParticipation(any());
  }

  // 등록·취소 리스너는 서로 다른 @Async 스레드라, 취소가 먼저 도달하면 뒤이어 등록된 주문이 살아남는다.
  @Test
  void 등록_후_재조회에서_이미_취소됐으면_주문을_취소한다() {
    Participation participation = participation(50_000L, 0L);
    ParticipationBundle bundle =
        bundleWith(participation, 3_000L, RefundAccount.of("국민", "12345678", "홍길동"));
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.of(bundle));
    Participation cancelled = newInstance(Participation.class);
    setField(cancelled, "id", PARTICIPATION_ID);
    setField(cancelled, "status", ParticipationStatus.CANCELLED);
    given(participationDomainService.getParticipation(PARTICIPATION_ID))
        .willReturn(participation, cancelled);

    listener.onParticipationCreated(new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY));

    then(payActionClient).should().registerOrder(eq(PARTICIPATION_ID), anyLong(), any(), any(), any());
    then(payActionClient).should().cancelOrder(PARTICIPATION_ID);
  }

  @Test
  void 등록_후에도_입금대기면_주문을_취소하지_않는다() {
    Participation participation = participation(50_000L, 0L);
    ParticipationBundle bundle =
        bundleWith(participation, 3_000L, RefundAccount.of("국민", "12345678", "홍길동"));
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.of(bundle));

    listener.onParticipationCreated(new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY));

    then(payActionClient).should(never()).cancelOrder(anyLong());
  }

  // 재조회는 취소 경합만 잡는다. 입금확인 이력이 있는 참여의 주문은 onBuncheolCancelled 와 같은 정책·판정
  // 키(confirmedAt)로 건드리지 않는다.
  @Test
  void 등록_후_재조회에서_확정됐으면_주문을_취소하지_않는다() {
    Participation participation = participation(50_000L, 0L);
    ParticipationBundle bundle =
        bundleWith(participation, 3_000L, RefundAccount.of("국민", "12345678", "홍길동"));
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.of(bundle));
    Participation confirmed = newInstance(Participation.class);
    setField(confirmed, "id", PARTICIPATION_ID);
    setField(confirmed, "status", ParticipationStatus.CONFIRMED);
    setField(confirmed, "confirmedAt", Instant.parse("2026-05-14T12:05:00Z"));
    given(participationDomainService.getParticipation(PARTICIPATION_ID))
        .willReturn(participation, confirmed);

    listener.onParticipationCreated(new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY));

    then(payActionClient).should().registerOrder(eq(PARTICIPATION_ID), anyLong(), any(), any(), any());
    then(payActionClient).should(never()).cancelOrder(anyLong());
  }

  @Test
  void 등록_후_재조회에서_입금확인_뒤_분철_취소됐으면_주문을_취소하지_않는다() {
    Participation participation = participation(50_000L, 0L);
    ParticipationBundle bundle =
        bundleWith(participation, 3_000L, RefundAccount.of("국민", "12345678", "홍길동"));
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.of(bundle));
    Participation cancelledAfterConfirm = newInstance(Participation.class);
    setField(cancelledAfterConfirm, "id", PARTICIPATION_ID);
    setField(cancelledAfterConfirm, "status", ParticipationStatus.CANCELLED);
    setField(cancelledAfterConfirm, "confirmedAt", Instant.parse("2026-05-14T12:05:00Z"));
    given(participationDomainService.getParticipation(PARTICIPATION_ID))
        .willReturn(participation, cancelledAfterConfirm);

    listener.onParticipationCreated(new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY));

    then(payActionClient).should().registerOrder(eq(PARTICIPATION_ID), anyLong(), any(), any(), any());
    then(payActionClient).should(never()).cancelOrder(anyLong());
  }

  // 재조회 실패는 취소 신호가 아니라 주문을 둔다. 등록은 이미 됐으므로 「등록 실패」로 남기면 운영자가 수동 확인
  // 대상으로 오해한다.
  @Test
  void 등록_후_재조회가_실패하면_취소하지_않고_재확인_실패로_남긴다() {
    Participation participation = participation(50_000L, 0L);
    ParticipationBundle bundle =
        bundleWith(participation, 3_000L, RefundAccount.of("국민", "12345678", "홍길동"));
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.of(bundle));
    given(participationDomainService.getParticipation(PARTICIPATION_ID))
        .willReturn(participation)
        .willThrow(new DataAccessResourceFailureException("DB 일시 장애"));

    assertThatCode(
            () ->
                listener.onParticipationCreated(
                    new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY)))
        .doesNotThrowAnyException();

    then(payActionClient).should().registerOrder(eq(PARTICIPATION_ID), anyLong(), any(), any(), any());
    then(payActionClient).should(never()).cancelOrder(anyLong());
    assertThat(logMessages())
        .contains("페이액션 주문 등록 후 참여 상태 재확인 실패 - participationId=" + PARTICIPATION_ID)
        .noneMatch(message -> message.startsWith("페이액션 주문 등록 실패"));
  }

  @Test
  void 등록이_실패하면_재조회하지_않고_등록_실패로만_남긴다() {
    Participation participation = participation(50_000L, 0L);
    ParticipationBundle bundle =
        bundleWith(participation, 3_000L, RefundAccount.of("국민", "12345678", "홍길동"));
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.of(bundle));
    willThrow(new PayActionSendException("페이액션 호출 거부"))
        .given(payActionClient)
        .registerOrder(eq(PARTICIPATION_ID), anyLong(), any(), any(), any());

    assertThatCode(
            () ->
                listener.onParticipationCreated(
                    new ParticipationCreatedEvent(PARTICIPATION_ID, FlowType.LEGACY)))
        .doesNotThrowAnyException();

    then(participationDomainService).should(times(1)).getParticipation(PARTICIPATION_ID);
    then(payActionClient).should(never()).cancelOrder(anyLong());
    assertThat(logMessages())
        .contains("페이액션 주문 등록 실패 - participationId=" + PARTICIPATION_ID)
        .noneMatch(message -> message.startsWith("페이액션 주문 등록 후 참여 상태 재확인 실패"));
  }

  @Test
  void 입금_만료되면_주문을_취소한다() {
    cancelTarget(false, null);

    listener.onPaymentExpired(new PaymentExpiredEvent(PARTICIPATION_ID));

    then(payActionClient).should().cancelOrder(PARTICIPATION_ID);
  }

  @Test
  void C2C_참여가_만료되면_주문을_취소하지_않는다() {
    cancelTarget(true, null);

    listener.onPaymentExpired(new PaymentExpiredEvent(PARTICIPATION_ID));

    then(payActionClient).should(never()).cancelOrder(anyLong());
  }

  @Test
  void 분철_취소로_입금대기_참여가_취소되면_주문을_취소한다() {
    cancelTarget(false, null);

    listener.onBuncheolCancelled(
        new BuncheolCancelledEvent(
            BUNCHEOL_ID, List.of(PARTICIPATION_ID), BuncheolCancelReason.HOST_CANCELLED));

    then(payActionClient).should().cancelOrder(PARTICIPATION_ID);
  }

  // 입금확인된 주문은 이미 매칭이 끝났다. 결제 취소 성격의 API 로 건드리면 오프라인 환불과 기록이 어긋날 수 있다.
  @Test
  void 분철_취소로_입금확인됐던_참여가_취소되면_주문을_취소하지_않는다() {
    cancelTarget(false, Instant.parse("2026-05-14T12:10:00Z"));

    listener.onBuncheolCancelled(
        new BuncheolCancelledEvent(
            BUNCHEOL_ID, List.of(PARTICIPATION_ID), BuncheolCancelReason.HOST_CANCELLED));

    then(payActionClient).should(never()).cancelOrder(anyLong());
  }

  @Test
  void 주문_취소_실패는_삼킨다() {
    cancelTarget(false, null);
    willThrow(new PayActionSendException("페이액션 호출 거부"))
        .given(payActionClient)
        .cancelOrder(PARTICIPATION_ID);

    assertThatCode(() -> listener.onPaymentExpired(new PaymentExpiredEvent(PARTICIPATION_ID)))
        .doesNotThrowAnyException();
  }

  // 분철 취소 이벤트는 분철 단위다. 입금확인 이력이 있는 참여만 빼고 나머지는 참여마다 주문을 취소한다.
  @Test
  void 분철_취소로_여러_참여가_취소되면_입금확인_이력이_없는_참여의_주문만_취소한다() {
    Long confirmedId = 501L;
    Long otherUnconfirmedId = 502L;
    cancelTarget(PARTICIPATION_ID, null);
    cancelTarget(confirmedId, Instant.parse("2026-05-14T12:10:00Z"));
    cancelTarget(otherUnconfirmedId, null);
    buncheolOfFlow(false);
    given(payActionClient.isEnabled()).willReturn(true);

    listener.onBuncheolCancelled(
        new BuncheolCancelledEvent(
            BUNCHEOL_ID,
            List.of(PARTICIPATION_ID, confirmedId, otherUnconfirmedId),
            BuncheolCancelReason.MIN_HEADCOUNT_NOT_MET));

    then(payActionClient).should().cancelOrder(PARTICIPATION_ID);
    then(payActionClient).should().cancelOrder(otherUnconfirmedId);
    then(payActionClient).should(never()).cancelOrder(confirmedId);
  }

  // 참여마다 따로 돌던 비동기 태스크가 한 루프로 합쳐졌다. 앞 건의 실패가 뒤 건의 주문을 살려 두면 안 된다.
  @Test
  void 분철_취소에서_앞_참여의_주문_취소가_실패해도_다음_참여의_주문은_취소한다() {
    Long nextId = 501L;
    cancelTarget(PARTICIPATION_ID, null);
    cancelTarget(nextId, null);
    buncheolOfFlow(false);
    given(payActionClient.isEnabled()).willReturn(true);
    willThrow(new PayActionSendException("페이액션 호출 거부"))
        .given(payActionClient)
        .cancelOrder(PARTICIPATION_ID);

    assertThatCode(
            () ->
                listener.onBuncheolCancelled(
                    new BuncheolCancelledEvent(
                        BUNCHEOL_ID,
                        List.of(PARTICIPATION_ID, nextId),
                        BuncheolCancelReason.HOST_CANCELLED)))
        .doesNotThrowAnyException();

    then(payActionClient).should().cancelOrder(nextId);
    assertThat(logMessages())
        .contains(
            "페이액션 주문 취소 실패 - participationId=" + PARTICIPATION_ID + " cause=페이액션 호출 거부")
        .noneMatch(message -> message.contains("participationId=" + nextId));
  }

  @Test
  void C2C_분철이_취소되면_어느_참여의_주문도_취소하지_않는다() {
    Long otherId = 501L;
    cancelTarget(PARTICIPATION_ID, null);
    cancelTarget(otherId, null);
    buncheolOfFlow(true);

    listener.onBuncheolCancelled(
        new BuncheolCancelledEvent(
            BUNCHEOL_ID, List.of(PARTICIPATION_ID, otherId), BuncheolCancelReason.HOST_CANCELLED));

    then(payActionClient).should(never()).cancelOrder(anyLong());
  }

  // 참여 조회가 실패한 건은 LEGACY·미확정으로 간주해 취소를 시도한다(주문이 없으면 no-op). C2C 판정을 분철로
  // 한 번만 하도록 루프 밖으로 빼면 이 건까지 건너뛰게 된다 — 그 회귀를 고정한다.
  @Test
  void 분철_취소에서_참여_조회가_실패한_건은_취소를_시도하고_나머지_판정은_그대로_한다() {
    Long unreadableId = 501L;
    cancelTarget(PARTICIPATION_ID, null);
    given(participationDomainService.getParticipation(unreadableId))
        .willThrow(new DataAccessResourceFailureException("DB 일시 장애"));
    buncheolOfFlow(true);
    given(payActionClient.isEnabled()).willReturn(true);

    listener.onBuncheolCancelled(
        new BuncheolCancelledEvent(
            BUNCHEOL_ID,
            List.of(PARTICIPATION_ID, unreadableId),
            BuncheolCancelReason.HOST_CANCELLED));

    then(payActionClient).should().cancelOrder(unreadableId);
    then(payActionClient).should(never()).cancelOrder(PARTICIPATION_ID);
  }

  private void cancelTarget(final boolean c2c, final Instant confirmedAt) {
    cancelTarget(PARTICIPATION_ID, confirmedAt);
    if (confirmedAt == null) {
      buncheolOfFlow(c2c);
      if (!c2c) {
        given(payActionClient.isEnabled()).willReturn(true);
      }
    }
  }

  // 분철 취소 cascade 로 CANCELLED 가 된 참여. 분철 스텁은 따로 건다 — 여러 참여가 한 분철을 공유하므로
  // 참여마다 걸면 덮어쓴 스텁이 안 쓰인 채 남아 strict stubs 에 걸린다.
  private void cancelTarget(final Long participationId, final Instant confirmedAt) {
    Participation participation = newInstance(Participation.class);
    setField(participation, "id", participationId);
    setField(participation, "buncheolId", BUNCHEOL_ID);
    setField(participation, "status", ParticipationStatus.CANCELLED);
    setField(participation, "confirmedAt", confirmedAt);
    given(participationDomainService.getParticipation(participationId)).willReturn(participation);
  }

  private void buncheolOfFlow(final boolean c2c) {
    Buncheol buncheol = mock(Buncheol.class);
    given(buncheol.isC2c()).willReturn(c2c);
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(buncheol);
  }

  private static Logger listenerLogger() {
    return (Logger) LoggerFactory.getLogger(DepositOrderListener.class);
  }

  private List<String> logMessages() {
    return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
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
