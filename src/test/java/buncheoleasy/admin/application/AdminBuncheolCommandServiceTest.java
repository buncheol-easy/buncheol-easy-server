package buncheoleasy.admin.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.application.BuncheolCancelReason;
import buncheoleasy.buncheol.application.BuncheolCancelledEvent;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.delivery.domain.DeliveryStatus;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBuncheolCommandService 단위 테스트 — 운영진 파기")
class AdminBuncheolCommandServiceTest {

  private static final Long BUNCHEOL_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");

  @InjectMocks private AdminBuncheolCommandService adminBuncheolCommandService;

  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private DeliveryDomainService deliveryDomainService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private Buncheol givenLocked(final BuncheolStatus status, final boolean staff) {
    Buncheol buncheol = mock(Buncheol.class);
    lenient().when(buncheol.getStatus()).thenReturn(status);
    lenient().when(buncheol.isStaff()).thenReturn(staff);
    given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID)).willReturn(buncheol);
    return buncheol;
  }

  private static Participation participation(final long id) {
    Participation participation = mock(Participation.class);
    lenient().when(participation.getId()).thenReturn(id);
    return participation;
  }

  private static Delivery delivery(final DeliveryStatus status) {
    Delivery delivery = mock(Delivery.class);
    given(delivery.getStatus()).willReturn(status);
    return delivery;
  }

  private void givenCascadeCancelled(final Long... ids) {
    List<Participation> cancelled = Arrays.stream(ids).map(id -> participation(id)).toList();
    given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
        .willReturn(cancelled);
  }

  private void givenActive(final Long... ids) {
    List<Participation> active = Arrays.stream(ids).map(id -> participation(id)).toList();
    given(participationDomainService.findActiveByBuncheolIdForUpdate(BUNCHEOL_ID))
        .willReturn(active);
  }

  private void givenDeliveries(
      final List<Long> participationIds, final DeliveryStatus... statuses) {
    List<Delivery> deliveries = Arrays.stream(statuses).map(status -> delivery(status)).toList();
    given(deliveryDomainService.findAllByParticipationIdsForUpdate(participationIds))
        .willReturn(deliveries);
  }

  // 입금 확인된 참여까지 전원 취소하고 「분철 취소 안내」를 사유 "개최자 취소" 로 분철 단위 1번 보낸다.
  @Test
  void 입금_수집_중이면_분철을_숨기고_살아_있는_참여를_전원_취소하며_취소_안내를_한_번_보낸다() {
    givenLocked(BuncheolStatus.PAYMENT_COLLECTING, false);
    given(
            buncheolDomainService.staffCancelIfStatus(
                BUNCHEOL_ID, BuncheolStatus.PAYMENT_COLLECTING, NOW))
        .willReturn(true);
    givenCascadeCancelled(602L, 601L);

    adminBuncheolCommandService.cancel(BUNCHEOL_ID);

    InOrder order =
        inOrder(
            buncheolDomainService, participationDomainService, participationBundleDomainService);
    order.verify(buncheolDomainService).getBuncheolForUpdate(BUNCHEOL_ID);
    order
        .verify(buncheolDomainService)
        .staffCancelIfStatus(BUNCHEOL_ID, BuncheolStatus.PAYMENT_COLLECTING, NOW);
    order.verify(participationDomainService).cancelActiveByBuncheolId(BUNCHEOL_ID, NOW);
    order.verify(participationBundleDomainService).closeEmptyByBuncheolId(BUNCHEOL_ID, NOW);
    then(deliveryDomainService).should().deleteByParticipationIds(List.of(601L, 602L));
    then(deliveryDomainService).should(never()).findAllByParticipationIdsForUpdate(anyList());
    then(eventPublisher)
        .should()
        .publishEvent(
            new BuncheolCancelledEvent(
                BUNCHEOL_ID, List.of(601L, 602L), BuncheolCancelReason.HOST_CANCELLED));
  }

  // 진행 확정 때 생긴 배송은 모두 운송장 전(SNAPSHOTTED)이다 — 취소 연쇄가 취소된 참여의 배송을 지운다.
  @Test
  void 진행_확정_분철도_배송이_모두_운송장_전이면_파기하고_배송을_지운다() {
    givenLocked(BuncheolStatus.CONFIRMED, false);
    givenActive(601L, 602L);
    givenDeliveries(List.of(601L, 602L), DeliveryStatus.SNAPSHOTTED, DeliveryStatus.SNAPSHOTTED);
    given(buncheolDomainService.staffCancelIfStatus(BUNCHEOL_ID, BuncheolStatus.CONFIRMED, NOW))
        .willReturn(true);
    givenCascadeCancelled(601L, 602L);

    adminBuncheolCommandService.cancel(BUNCHEOL_ID);

    // 분철 → 참여 → 배송 순서로 잠근 뒤 CAS 한다.
    InOrder order =
        inOrder(buncheolDomainService, participationDomainService, deliveryDomainService);
    order.verify(buncheolDomainService).getBuncheolForUpdate(BUNCHEOL_ID);
    order.verify(participationDomainService).findActiveByBuncheolIdForUpdate(BUNCHEOL_ID);
    order.verify(deliveryDomainService).findAllByParticipationIdsForUpdate(List.of(601L, 602L));
    order
        .verify(buncheolDomainService)
        .staffCancelIfStatus(BUNCHEOL_ID, BuncheolStatus.CONFIRMED, NOW);
    order.verify(participationDomainService).cancelActiveByBuncheolId(BUNCHEOL_ID, NOW);
    order.verify(deliveryDomainService).deleteByParticipationIds(List.of(601L, 602L));
    then(eventPublisher)
        .should()
        .publishEvent(
            new BuncheolCancelledEvent(
                BUNCHEOL_ID, List.of(601L, 602L), BuncheolCancelReason.HOST_CANCELLED));
  }

  @Test
  void 진행_확정_분철에_배송이_없어도_운송장이_없으니_파기한다() {
    givenLocked(BuncheolStatus.CONFIRMED, false);
    givenActive(601L);
    givenDeliveries(List.of(601L));
    given(buncheolDomainService.staffCancelIfStatus(BUNCHEOL_ID, BuncheolStatus.CONFIRMED, NOW))
        .willReturn(true);
    givenCascadeCancelled(601L);

    adminBuncheolCommandService.cancel(BUNCHEOL_ID);

    then(participationDomainService).should().cancelActiveByBuncheolId(BUNCHEOL_ID, NOW);
  }

  // 발송이 시작된 분철은 운영자가 따로 처리한다 — 상태 위반(BCH-050)과 다른 코드로 알리고 아무것도 바꾸지 않는다.
  @ParameterizedTest
  @EnumSource(
      value = DeliveryStatus.class,
      names = {"SHIPPING", "DELIVERED", "RECEIVED"})
  void 진행_확정_분철에_운송장이_하나라도_있으면_BCH_130_으로_거절한다(final DeliveryStatus shipped) {
    givenLocked(BuncheolStatus.CONFIRMED, false);
    givenActive(601L, 602L);
    givenDeliveries(List.of(601L, 602L), DeliveryStatus.SNAPSHOTTED, shipped);

    assertThatThrownBy(() -> adminBuncheolCommandService.cancel(BUNCHEOL_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_HAS_TRACKING);

    then(buncheolDomainService).should(never()).staffCancelIfStatus(anyLong(), any(), any());
    then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    then(deliveryDomainService).should(never()).deleteByParticipationIds(anyList());
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @EnumSource(
      value = BuncheolStatus.class,
      names = {"RECRUITING", "CANCELLED", "HOST_CANCELLED"})
  void 입금_수집_중이나_진행_확정이_아니면_상태_위반으로_거절한다(final BuncheolStatus status) {
    givenLocked(status, false);

    assertThatThrownBy(() -> adminBuncheolCommandService.cancel(BUNCHEOL_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);

    then(buncheolDomainService).should(never()).staffCancelIfStatus(anyLong(), any(), any());
    then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @ParameterizedTest
  @EnumSource(
      value = BuncheolStatus.class,
      names = {"PAYMENT_COLLECTING", "CONFIRMED"})
  void 운영진_분철은_상태와_무관하게_거절한다(final BuncheolStatus status) {
    givenLocked(status, true);

    assertThatThrownBy(() -> adminBuncheolCommandService.cancel(BUNCHEOL_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);

    then(participationDomainService).shouldHaveNoInteractions();
    then(deliveryDomainService).shouldHaveNoInteractions();
    then(buncheolDomainService).should(never()).staffCancelIfStatus(anyLong(), any(), any());
  }

  // 분철 행을 잠근 채 상태를 봤으므로 CAS 가 0 행이면 내부 불변식 위반이다 — 참여를 건드리지 않고 롤백한다.
  @Test
  void 파기_CAS_가_0행이면_참여를_취소하지_않는다() {
    givenLocked(BuncheolStatus.PAYMENT_COLLECTING, false);
    given(
            buncheolDomainService.staffCancelIfStatus(
                BUNCHEOL_ID, BuncheolStatus.PAYMENT_COLLECTING, NOW))
        .willReturn(false);

    assertThatThrownBy(() -> adminBuncheolCommandService.cancel(BUNCHEOL_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);

    then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void 없는_분철이면_404() {
    given(buncheolDomainService.getBuncheolForUpdate(BUNCHEOL_ID))
        .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

    assertThatThrownBy(() -> adminBuncheolCommandService.cancel(BUNCHEOL_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);

    then(participationDomainService).shouldHaveNoInteractions();
  }
}
