package buncheoleasy.buncheol.application.payback;

import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.buncheol.domain.participation.PaybackTweetUrl;
import buncheoleasy.buncheol.dto.request.ShippingFeePaybackRequest;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.delivery.domain.DeliveryRepository;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 오픈 이벤트 배송비 환급(배송비 돌려받기) 신청 애플리케이션 서비스. */
@Service
@RequiredArgsConstructor
public class ShippingFeePaybackService {

  private final ParticipationDomainService participationDomainService;
  private final DeliveryRepository deliveryRepository;
  private final ShippingFeePaybackPolicy policy;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 후기 트윗 URL 제출로 환급을 신청한다. 파생 상태 ELIGIBLE(신규)·REJECTED(재신청)·REQUESTED(검수 전 링크 수정)일 때
   * 허용하고, 입금이 끝난 COMPLETED 는 상태 충돌(409), 그 외(비대상·배송 전·마감 후)는 대상 아님(409)으로 응답한다. 성공 커밋 후
   * 운영자 슬랙 알림 이벤트를 발행한다 — 링크 수정도 재발송해 운영자가 최신 링크를 보게 한다.
   */
  @Transactional
  public void request(
      final Long participantId,
      final Long participationId,
      final ShippingFeePaybackRequest request) {
    final Instant now = Instant.now(clock);

    Participation participation = participationDomainService.getParticipation(participationId);
    participation.validateOwnedBy(participantId);

    PaybackTweetUrl tweetUrl = PaybackTweetUrl.parse(request.tweetUrl());

    // 환불계좌는 참여 시 필수 입력이라 사실상 항상 존재한다. 환급 입금 계좌가 없는 신청이 접수되는 사고를 막는 방어 가드.
    if (participation.getRefundAccount() == null) {
      throw new BusinessException(ErrorCode.PAYBACK_REFUND_ACCOUNT_MISSING);
    }

    Delivery delivery = deliveryRepository.findByParticipationId(participationId).orElse(null);
    PaybackStatus derived = policy.deriveStatus(participation, delivery, now);
    if (derived != PaybackStatus.ELIGIBLE
        && derived != PaybackStatus.REJECTED
        && derived != PaybackStatus.REQUESTED) {
      // REQUESTED 재제출은 검수 전 트윗 링크 수정으로 허용한다. NONE(비대상·배송 전)·EXPIRED(마감)는 대상 아님,
      // 입금이 끝난 COMPLETED 만 상태 충돌로 구분한다.
      throw new BusinessException(
          derived == PaybackStatus.COMPLETED
              ? ErrorCode.PAYBACK_STATE_TRANSITION_INVALID
              : ErrorCode.PAYBACK_NOT_ELIGIBLE);
    }

    if (participationDomainService.isPaybackTweetUrlUsedByOther(
        tweetUrl.value(), participationId)) {
      throw new BusinessException(ErrorCode.PAYBACK_TWEET_URL_DUPLICATE);
    }

    participationDomainService.requestPayback(participation, tweetUrl, now);
    eventPublisher.publishEvent(new ShippingFeePaybackRequestedEvent(participationId));
  }
}
