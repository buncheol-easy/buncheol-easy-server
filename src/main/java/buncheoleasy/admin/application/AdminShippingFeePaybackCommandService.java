package buncheoleasy.admin.application;

import buncheoleasy.admin.dto.request.AdminShippingFeePaybackActionRequest;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 배송비 환급 검수 처리 (입금완료/반려). 이벤트 한정 저볼륨 작업이고 반려 사유가 건별로 달라 결제·배송의 벌크 패턴 대신 단건 처리로 둔다. 전이는
 * 엔티티 도메인 메서드 + dirty-checking 이라 {@code @Transactional} 이 필수다.
 */
@Service
@RequiredArgsConstructor
public class AdminShippingFeePaybackCommandService {

  private final ParticipationDomainService participationDomainService;
  private final Clock clock;

  @Transactional
  public void process(
      final Long participationId, final AdminShippingFeePaybackActionRequest request) {
    switch (request.action()) {
      case COMPLETE ->
          participationDomainService.completePayback(participationId, Instant.now(clock));
      case REJECT ->
          participationDomainService.rejectPayback(participationId, request.rejectReason());
    }
  }
}
