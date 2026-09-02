package buncheoleasy.buncheol.application.payback;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.buncheol.domain.participation.PaybackTweetUrl;
import buncheoleasy.buncheol.domain.participation.ShippingFeeAttribution;
import buncheoleasy.buncheol.domain.participation.RefundAccount;
import buncheoleasy.buncheol.dto.request.ShippingFeePaybackRequest;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ShippingFeePaybackService 테스트")
class ShippingFeePaybackServiceTest {

  private final ShippingFeeAttribution fees = ShippingFeeAttribution.empty();

  private static final Long PARTICIPANT_ID = 100L;
  private static final Long PARTICIPATION_ID = 500L;
  private static final Long BUNDLE_ID = 9999L;
  private static final Instant NOW = Instant.parse("2026-07-16T09:00:00Z");
  private static final String TWEET_URL = "https://x.com/fan/status/1234567890";
  private static final ShippingFeePaybackRequest REQUEST =
      new ShippingFeePaybackRequest(TWEET_URL + "?s=20");

  @Mock private buncheoleasy.buncheol.domain.BuncheolDomainService buncheolDomainService;
  @Mock private ParticipationDomainService participationDomainService;
  @Mock private DeliveryRepository deliveryRepository;
  @Mock private ParticipationBundleDomainService participationBundleDomainService;
  @Mock private ShippingFeePaybackPolicy policy;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Mock private Participation participation;
  @Mock private Delivery delivery;

  private ShippingFeePaybackService service;

  @BeforeEach
  void setUp() {
    service =
        new ShippingFeePaybackService(
            buncheolDomainService,
            participationDomainService,
            participationBundleDomainService,
            deliveryRepository,
            policy,
            eventPublisher,
            Clock.fixed(NOW, ZoneOffset.UTC));
    given(participationDomainService.getParticipation(PARTICIPATION_ID)).willReturn(participation);
    // 환급 판정에 분철 flowType 이 필요해졌다 — 기본 픽스처는 운영진(LEGACY) 분철.
    buncheoleasy.buncheol.domain.Buncheol buncheol =
        org.mockito.Mockito.mock(buncheoleasy.buncheol.domain.Buncheol.class);
    given(buncheol.getFlowType()).willReturn(FlowType.LEGACY);
    given(participation.getBuncheolId()).willReturn(1L);
    given(buncheolDomainService.getBuncheol(1L)).willReturn(buncheol);
    // 환급 입금 계좌의 정본은 묶음이다 (P2-c).
    given(participationBundleDomainService.shippingFeeAttributionOf(any())).willReturn(fees);
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.of(mock(ParticipationBundle.class)));
    // 배송 조회 키는 묶음이다 (택배 1개 = 묶음 1개).
    given(participation.getBundleId()).willReturn(BUNDLE_ID);
    given(deliveryRepository.findByBundleId(BUNDLE_ID)).willReturn(Optional.of(delivery));
    given(policy.deriveStatus(participation, FlowType.LEGACY, delivery, NOW, fees))
        .willReturn(PaybackStatus.ELIGIBLE);
    given(
            participationDomainService.isPaybackTweetUrlUsedByOther(
                TWEET_URL, PARTICIPATION_ID))
        .willReturn(false);
  }

  @Test
  void 신청에_성공하면_정규화된_URL_로_전이하고_슬랙_이벤트를_발행한다() {
    service.request(PARTICIPANT_ID, PARTICIPATION_ID, REQUEST);

    then(participation).should().validateOwnedBy(PARTICIPANT_ID);
    then(participationDomainService)
        .should()
        .requestPayback(eq(participation), eq(PaybackTweetUrl.parse(TWEET_URL)), eq(NOW), any());
    then(eventPublisher)
        .should()
        .publishEvent(new ShippingFeePaybackRequestedEvent(PARTICIPATION_ID));
  }

  @Test
  void 반려_상태에서도_재신청할_수_있다() {
    given(policy.deriveStatus(participation, FlowType.LEGACY, delivery, NOW, fees))
        .willReturn(PaybackStatus.REJECTED);

    service.request(PARTICIPANT_ID, PARTICIPATION_ID, REQUEST);

    then(participationDomainService)
        .should()
        .requestPayback(eq(participation), any(PaybackTweetUrl.class), eq(NOW), any());
  }

  @Test
  void 트윗_URL_형식이_잘못되면_예외가_발생하고_전이하지_않는다() {
    assertThatThrownBy(
            () ->
                service.request(
                    PARTICIPANT_ID,
                    PARTICIPATION_ID,
                    new ShippingFeePaybackRequest("https://instagram.com/p/abc")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PAYBACK_TWEET_URL_INVALID);

    then(participationDomainService).should(never()).requestPayback(any(), any(), any(), any());
  }

  @Test
  void 환급_대상이_아니면_PAYBACK_NOT_ELIGIBLE_예외가_발생한다() {
    given(policy.deriveStatus(participation, FlowType.LEGACY, delivery, NOW, fees))
        .willReturn(PaybackStatus.NONE);

    assertThatThrownBy(() -> service.request(PARTICIPANT_ID, PARTICIPATION_ID, REQUEST))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PAYBACK_NOT_ELIGIBLE);
  }

  @Test
  void 신청_마감이_지나면_PAYBACK_NOT_ELIGIBLE_예외가_발생한다() {
    given(policy.deriveStatus(participation, FlowType.LEGACY, delivery, NOW, fees))
        .willReturn(PaybackStatus.EXPIRED);

    assertThatThrownBy(() -> service.request(PARTICIPANT_ID, PARTICIPATION_ID, REQUEST))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PAYBACK_NOT_ELIGIBLE);
  }

  @Test
  void 확인중_상태에서는_트윗_링크_수정으로_재제출할_수_있다() {
    given(policy.deriveStatus(participation, FlowType.LEGACY, delivery, NOW, fees))
        .willReturn(PaybackStatus.REQUESTED);

    service.request(PARTICIPANT_ID, PARTICIPATION_ID, REQUEST);

    then(participationDomainService)
        .should()
        .requestPayback(eq(participation), any(PaybackTweetUrl.class), eq(NOW), any());
    then(eventPublisher)
        .should()
        .publishEvent(new ShippingFeePaybackRequestedEvent(PARTICIPATION_ID));
  }

  @Test
  void 입금_완료된_건이면_상태_충돌_예외가_발생한다() {
    given(policy.deriveStatus(participation, FlowType.LEGACY, delivery, NOW, fees))
        .willReturn(PaybackStatus.COMPLETED);

    assertThatThrownBy(() -> service.request(PARTICIPANT_ID, PARTICIPATION_ID, REQUEST))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PAYBACK_STATE_TRANSITION_INVALID);
  }

  @Test
  void 다른_참여가_이미_사용한_트윗_URL_이면_중복_예외가_발생한다() {
    given(participationDomainService.isPaybackTweetUrlUsedByOther(TWEET_URL, PARTICIPATION_ID))
        .willReturn(true);

    assertThatThrownBy(() -> service.request(PARTICIPANT_ID, PARTICIPATION_ID, REQUEST))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PAYBACK_TWEET_URL_DUPLICATE);

    then(participationDomainService).should(never()).requestPayback(any(), any(), any(), any());
    then(eventPublisher).should(never()).publishEvent(any());
  }

  @Test
  void 묶음이_없으면_예외가_발생한다() {
    // 배포선 창에서 생긴 미연결 참여 — 돈 보낼 곳이 없으므로 접수를 막는다.
    given(participationBundleDomainService.findByParticipation(participation))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.request(PARTICIPANT_ID, PARTICIPATION_ID, REQUEST))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.PAYBACK_REFUND_ACCOUNT_MISSING);
  }
}
