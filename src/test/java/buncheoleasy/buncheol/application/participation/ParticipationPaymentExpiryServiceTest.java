package buncheoleasy.buncheol.application.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipationPaymentExpiryService 단위 테스트")
class ParticipationPaymentExpiryServiceTest {

  private static final Long PARTICIPATION_ID = 500L;
  private static final Instant NOW = Instant.parse("2026-05-14T12:30:00Z");

  @InjectMocks private ParticipationPaymentExpiryService participationPaymentExpiryService;

  @Mock private ParticipationDomainService participationDomainService;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;

  @Mock private ApplicationEventPublisher eventPublisher;

  private static final Long BUNDLE_ID = 900L;

  @Nested
  @DisplayName("입금 만료 대상 조회 테스트")
  class FindOverdueTargetsTest {

    @Test
    void 입금_기한이_지난_참여를_BATCH_SIZE_상한으로_조회한다() {
      Participation participation = mock(Participation.class);
      given(participationDomainService.findOverduePaymentTargets(NOW, 200))
          .willReturn(List.of(participation));

      List<Participation> result = participationPaymentExpiryService.findOverdueTargets(NOW);

      assertThat(result).containsExactly(participation);
      then(participationDomainService).should().findOverduePaymentTargets(NOW, 200);
    }
  }

  @Nested
  @DisplayName("단일 참여 입금 만료 처리 테스트")
  class ExpireTest {

    private Participation target() {
      Participation participation = mock(Participation.class);
      given(participation.getId()).willReturn(PARTICIPATION_ID);
      return participation;
    }

    @Test
    void 만료_처리를_도메인_서비스에_위임하고_성공하면_자동취소_이벤트를_발행하며_true를_반환한다() {
      Participation participation = target();
      given(participation.getBundleId()).willReturn(BUNDLE_ID);
      given(participationDomainService.expirePayment(PARTICIPATION_ID, NOW))
          .willReturn(true);

      boolean result = participationPaymentExpiryService.expire(participation, NOW);

      assertThat(result).isTrue();
      then(participationDomainService).should().expirePayment(PARTICIPATION_ID, NOW);
      then(eventPublisher).should().publishEvent(new PaymentExpiredEvent(PARTICIPATION_ID));
    }

    // 만료로 마지막 슬롯이 빠지면 묶음도 끝난다. 안 닫으면 「죽었는데 활성」 묶음이 남고 재참여가 그 시체를 재사용한다.
    @Test
    void 만료에_성공하면_묶음_종료를_판정한다() {
      Participation participation = target();
      given(participation.getBundleId()).willReturn(BUNDLE_ID);
      given(participationDomainService.expirePayment(PARTICIPATION_ID, NOW)).willReturn(true);

      participationPaymentExpiryService.expire(participation, NOW);

      then(participationBundleDomainService).should().closeIfEmpty(BUNDLE_ID, NOW);
    }

    @Test
    void 이미_확인_취소된_참여는_CAS에_막혀_false를_반환하고_이벤트를_발행하지_않는다_멱등() {
      Participation participation = target();
      given(participationDomainService.expirePayment(PARTICIPATION_ID, NOW))
          .willReturn(false);

      boolean result = participationPaymentExpiryService.expire(participation, NOW);

      assertThat(result).isFalse();
      then(eventPublisher).should(never()).publishEvent(any());
      // CAS 에 막혔으면 이 슬롯은 여전히 살아 있다 — 묶음을 건드리면 안 된다.
      then(participationBundleDomainService).should(never()).closeIfEmpty(any(), any());
    }
  }
}
