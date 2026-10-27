package buncheoleasy.admin.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.application.BuncheolCancelReason;
import buncheoleasy.buncheol.application.BuncheolCancelledEvent;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.delivery.domain.DeliveryDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

  // 입금 확인된 참여까지 전원 취소하고 「분철 취소 안내」를 사유 "개최자 취소" 로 분철 단위 1번 보낸다.
  @Test
  void 입금_수집_중이면_분철을_숨기고_살아_있는_참여를_전원_취소하며_취소_안내를_한_번_보낸다() {
    given(buncheolDomainService.staffCancelIfCollecting(BUNCHEOL_ID, NOW)).willReturn(true);
    Participation confirmed = mock(Participation.class);
    given(confirmed.getId()).willReturn(602L);
    Participation awaiting = mock(Participation.class);
    given(awaiting.getId()).willReturn(601L);
    given(participationDomainService.findCascadeCancelledByBuncheolId(BUNCHEOL_ID))
        .willReturn(List.of(confirmed, awaiting));

    adminBuncheolCommandService.cancel(BUNCHEOL_ID);

    InOrder order = inOrder(buncheolDomainService, participationDomainService, participationBundleDomainService);
    order.verify(buncheolDomainService).staffCancelIfCollecting(BUNCHEOL_ID, NOW);
    order.verify(participationDomainService).cancelActiveByBuncheolId(BUNCHEOL_ID, NOW);
    order.verify(participationBundleDomainService).closeEmptyByBuncheolId(BUNCHEOL_ID, NOW);
    then(deliveryDomainService).should().deleteByParticipationIds(List.of(601L, 602L));
    then(eventPublisher)
        .should()
        .publishEvent(
            new BuncheolCancelledEvent(
                BUNCHEOL_ID, List.of(601L, 602L), BuncheolCancelReason.HOST_CANCELLED));
  }

  // 모집중·진행확정·이미 취소·운영진 분철 — CAS 가 0 행이면 상태 위반이고 아무것도 바꾸지 않는다.
  @Test
  void 입금_수집_중인_회원_개최가_아니면_상태_위반으로_거절한다() {
    given(buncheolDomainService.staffCancelIfCollecting(BUNCHEOL_ID, NOW)).willReturn(false);

    assertThatThrownBy(() -> adminBuncheolCommandService.cancel(BUNCHEOL_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);

    then(participationDomainService).should(never()).cancelActiveByBuncheolId(anyLong(), any());
    then(deliveryDomainService).should(never()).deleteByParticipationIds(anyList());
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void 없는_분철이면_404() {
    given(buncheolDomainService.staffCancelIfCollecting(BUNCHEOL_ID, NOW)).willReturn(false);
    given(buncheolDomainService.getBuncheol(BUNCHEOL_ID))
        .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

    assertThatThrownBy(() -> adminBuncheolCommandService.cancel(BUNCHEOL_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);
  }
}
