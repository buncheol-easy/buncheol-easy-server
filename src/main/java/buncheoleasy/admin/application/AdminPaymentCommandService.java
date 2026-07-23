package buncheoleasy.admin.application;

import buncheoleasy.admin.dto.response.AdminBulkResultResponse;
import buncheoleasy.buncheol.application.participation.ParticipationService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 관리자 입금확인 벌크 처리. 참여 건별로 독립 트랜잭션({@link ParticipationService#confirmPaymentByAdmin})으로 확인하고, 이미
 * 확정/취소된 건 등 비즈니스 실패는 사유와 함께 모아 돌려준다 — 묶음 입금 확인 중 한 건의 충돌이 나머지 확인을 막지 않도록 한다(기존 프론트가 건별 병렬 호출로
 * 얻던 내성과 동일).
 *
 * <p>앞 건들은 이미 커밋된 뒤라 중간에 500 으로 끊기면 프론트가 성공 범위를 알 수 없으므로, 예상 밖 예외도 실패 항목으로 수집해 응답 계약을 지킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPaymentCommandService {

  private final ParticipationService participationService;

  public AdminBulkResultResponse confirmPayments(final List<Long> participationIds) {
    final List<Long> succeededIds = new ArrayList<>();
    final List<AdminBulkResultResponse.Failure> failures = new ArrayList<>();

    // 같은 id 가 중복 요청되면 둘째 건이 무의미한 STATE_TRANSITION_INVALID 실패로 수집되므로 순서를 유지한 채 제거한다.
    for (final Long participationId : new LinkedHashSet<>(participationIds)) {
      try {
        participationService.confirmPaymentByAdmin(participationId);
        succeededIds.add(participationId);
      } catch (final BusinessException exception) {
        failures.add(
            new AdminBulkResultResponse.Failure(
                participationId,
                exception.getErrorCode().getCode(),
                exception.getErrorCode().getMessage()));
      } catch (final RuntimeException exception) {
        log.error("입금확인 벌크 처리 중 예상 밖 실패. participationId={}", participationId, exception);
        failures.add(
            new AdminBulkResultResponse.Failure(
                participationId,
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
      }
    }
    return new AdminBulkResultResponse(succeededIds, failures);
  }
}
