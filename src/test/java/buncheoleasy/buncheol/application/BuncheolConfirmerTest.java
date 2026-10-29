package buncheoleasy.buncheol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.application.participation.ParticipationBundleService;
import buncheoleasy.buncheol.application.participation.PaymentNotConfirmedEvent;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.buncheol.dto.request.BuncheolConfirmRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolConfirmer 단위 테스트 — 회원 개최 진행 확정")
class BuncheolConfirmerTest {

  private static final Long BUNCHEOL_ID = 10L;
  private static final Instant NOW = Instant.parse("2026-11-05T03:00:00Z");

  @InjectMocks private BuncheolConfirmer buncheolConfirmer;

  @Mock private BuncheolDomainService buncheolDomainService;
  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private DeliverySnapshotCreator deliverySnapshotCreator;
  @Mock private BuncheolConfirmedFinalizer buncheolConfirmedFinalizer;
  @Mock private ApplicationEventPublisher eventPublisher;

  private static Participation participation(final long id, final ParticipationStatus status) {
    Participation participation = mock(Participation.class);
    lenient().when(participation.getId()).thenReturn(id);
    lenient().when(participation.getStatus()).thenReturn(status);
    return participation;
  }

  @Test
  void 모두_입금_확인됐으면_확인된_참여마다_배송을_만들고_빠짐_이벤트는_내지_않는다() {
    Participation first = participation(601L, ParticipationStatus.CONFIRMED);
    Participation second = participation(602L, ParticipationStatus.CONFIRMED);
    given(buncheolDomainService.confirmIfCollecting(BUNCHEOL_ID, NOW)).willReturn(true);
    given(participationDomainService.cancelUnconfirmedByBuncheolId(BUNCHEOL_ID, NOW)).willReturn(0);

    buncheolConfirmer.confirm(BUNCHEOL_ID, List.of(first, second), NOW);

    // 묶음에 배송이 이미 있으면 DeliverySnapshotCreator 가 건너뛴다 — 묶음당 1개.
    then(deliverySnapshotCreator).should().create(first);
    then(deliverySnapshotCreator).should().create(second);
    then(participationBundleDomainService).should().closeEmptyByBuncheolId(BUNCHEOL_ID, NOW);
    then(buncheolConfirmedFinalizer).should().finalizeConfirmed(BUNCHEOL_ID);
    then(eventPublisher).should(never()).publishEvent(any(PaymentNotConfirmedEvent.class));
  }

  @Test
  void 미확인_참여를_빼고_확인된_참여에만_배송을_만들며_빠짐_이벤트를_한_번_발행한다() {
    Participation confirmed = participation(601L, ParticipationStatus.CONFIRMED);
    given(buncheolDomainService.confirmIfCollecting(BUNCHEOL_ID, NOW)).willReturn(true);
    given(participationDomainService.cancelUnconfirmedByBuncheolId(BUNCHEOL_ID, NOW)).willReturn(2);

    buncheolConfirmer.confirm(
        BUNCHEOL_ID,
        List.of(
            confirmed,
            participation(603L, ParticipationStatus.PAYMENT_SENT),
            participation(602L, ParticipationStatus.AWAITING_PAYMENT)),
        NOW);

    InOrder order =
        inOrder(
            buncheolDomainService,
            participationDomainService,
            participationBundleDomainService,
            deliverySnapshotCreator,
            buncheolConfirmedFinalizer,
            eventPublisher);
    order.verify(buncheolDomainService).confirmIfCollecting(BUNCHEOL_ID, NOW);
    order.verify(participationDomainService).cancelUnconfirmedByBuncheolId(BUNCHEOL_ID, NOW);
    order.verify(participationBundleDomainService).closeEmptyByBuncheolId(BUNCHEOL_ID, NOW);
    order.verify(deliverySnapshotCreator).create(confirmed);
    order.verify(buncheolConfirmedFinalizer).finalizeConfirmed(BUNCHEOL_ID);
    order
        .verify(eventPublisher)
        .publishEvent(new PaymentNotConfirmedEvent(BUNCHEOL_ID, List.of(602L, 603L)));
    then(deliverySnapshotCreator).shouldHaveNoMoreInteractions();
  }

  // 분철 행을 잠근 채 상태를 봤으므로 CAS 가 0 행이면 내부 불변식 위반이다 — 참여를 빼지 않고 롤백한다.
  @Test
  void 진행_확정_CAS_가_0행이면_참여를_빼지_않는다() {
    given(buncheolDomainService.confirmIfCollecting(BUNCHEOL_ID, NOW)).willReturn(false);

    assertThatThrownBy(
            () ->
                buncheolConfirmer.confirm(
                    BUNCHEOL_ID, List.of(participation(601L, ParticipationStatus.CONFIRMED)), NOW))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_CONFIRM_NOT_ALLOWED);

    then(participationDomainService)
        .should(never())
        .cancelUnconfirmedByBuncheolId(anyLong(), any());
    then(deliverySnapshotCreator).shouldHaveNoInteractions();
    then(buncheolConfirmedFinalizer).shouldHaveNoInteractions();
  }

  @Test
  void 빠진_참여_수가_센_수와_다르면_롤백한다() {
    given(buncheolDomainService.confirmIfCollecting(BUNCHEOL_ID, NOW)).willReturn(true);
    given(participationDomainService.cancelUnconfirmedByBuncheolId(BUNCHEOL_ID, NOW)).willReturn(0);

    assertThatThrownBy(
            () ->
                buncheolConfirmer.confirm(
                    BUNCHEOL_ID,
                    List.of(
                        participation(601L, ParticipationStatus.CONFIRMED),
                        participation(602L, ParticipationStatus.AWAITING_PAYMENT)),
                    NOW))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);

    then(deliverySnapshotCreator).shouldHaveNoInteractions();
    then(buncheolConfirmedFinalizer).shouldHaveNoInteractions();
  }

  // 호출자 계약: 진행확정 알림 대상을 잠금 뒤 일반 조회로 읽으므로, REPEATABLE READ 면 잠금 앞 일반 조회의 스냅샷이 남아
  // 기다리는 사이 커밋된 입금 확인을 빠뜨린다. Confirmer 를 부르는 public 메서드를 새로 만들면 여기 한 줄 더한다.
  static Stream<Method> confirmerCallers() throws NoSuchMethodException {
    return Stream.of(
        BuncheolService.class.getMethod(
            "confirmBuncheol", Long.class, Long.class, BuncheolConfirmRequest.class),
        ParticipationBundleService.class.getMethod(
            "confirmPayment", Long.class, Long.class, List.class));
  }

  @ParameterizedTest
  @MethodSource("confirmerCallers")
  void Confirmer_호출자는_READ_COMMITTED_다(final Method caller) {
    Transactional transactional = caller.getAnnotation(Transactional.class);

    assertThat(transactional).as("%s 에 @Transactional", caller.getName()).isNotNull();
    assertThat(transactional.isolation())
        .as("%s 의 격리 수준", caller.getName())
        .isEqualTo(Isolation.READ_COMMITTED);
  }
}
