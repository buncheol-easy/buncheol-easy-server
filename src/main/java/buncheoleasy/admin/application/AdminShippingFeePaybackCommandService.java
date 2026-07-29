package buncheoleasy.admin.application;

import buncheoleasy.admin.dto.request.AdminShippingFeePaybackActionRequest;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackCompletedEvent;
import buncheoleasy.buncheol.application.payback.ShippingFeePaybackRejectedEvent;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 배송비 환급 검수 처리 (입금완료/반려). 이벤트 한정 저볼륨 작업이고 반려 사유가 건별로 달라 결제·배송의 벌크 패턴 대신 단건 처리로 둔다. 전이는
 * REQUESTED 일 때만 성공하는 CAS 라 동시 검수(더블클릭·운영자 중복 처리)에서도 한 요청만 성공하고, 참여자 알림톡 이벤트는 전이 성공 시에만 발행돼 커밋
 * 후 발송된다 — 전이가 상태 위반으로 실패하면 예외로 끊겨 발행되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminShippingFeePaybackCommandService {

  private final ParticipationDomainService participationDomainService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public void process(
      final Long participationId, final AdminShippingFeePaybackActionRequest request) {
    final Instant now = Instant.now(clock);
    switch (request.action()) {
      case COMPLETE -> {
        participationDomainService.completePayback(participationId, now);
        eventPublisher.publishEvent(new ShippingFeePaybackCompletedEvent(participationId));
      }
      case REJECT -> {
        participationDomainService.rejectPayback(participationId, request.rejectReason(), now);
        eventPublisher.publishEvent(
            new ShippingFeePaybackRejectedEvent(participationId, request.rejectReason()));
      }
    }
  }
}
