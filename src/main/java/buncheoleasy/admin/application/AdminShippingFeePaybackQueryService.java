package buncheoleasy.admin.application;

import buncheoleasy.admin.domain.payback.AdminPaybackView;
import buncheoleasy.admin.domain.payback.AdminShippingFeePaybackQueryRepository;
import buncheoleasy.admin.dto.response.AdminShippingFeePaybackResponse;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 배송비 환급 검수 목록 조회. 신청 이력이 있는 참여만 신청 최신순 커서 페이지네이션으로 내려준다. */
@Service
@RequiredArgsConstructor
public class AdminShippingFeePaybackQueryService {

  private static final int MIN_SIZE = 1;
  private static final int MAX_SIZE = 100;

  private final AdminShippingFeePaybackQueryRepository adminShippingFeePaybackQueryRepository;

  @Transactional(readOnly = true)
  public CursorResponse<AdminShippingFeePaybackResponse> getPaybacks(
      final PaybackStatus statusFilter, final Cursor cursor, final int requestedSize) {
    final int safeSize = clampSize(requestedSize);

    final List<AdminPaybackView> fetched =
        adminShippingFeePaybackQueryRepository.findPaybacks(statusFilter, cursor, safeSize + 1);
    final boolean hasNext = fetched.size() > safeSize;
    final List<AdminPaybackView> visible = hasNext ? fetched.subList(0, safeSize) : fetched;

    if (visible.isEmpty()) {
      return CursorResponse.empty();
    }

    final List<AdminShippingFeePaybackResponse> items =
        visible.stream().map(AdminShippingFeePaybackResponse::from).toList();

    final var lastParticipation = visible.getLast().participation();
    // 정렬 축이 paybackRequestedAt 이라 커서의 Instant 슬롯에도 requestedAt 을 싣는다.
    final String nextCursor =
        hasNext
            ? new Cursor(lastParticipation.getPaybackRequestedAt(), lastParticipation.getId())
                .encode()
            : null;
    return new CursorResponse<>(items, nextCursor, hasNext);
  }

  private int clampSize(final int requested) {
    return Math.max(MIN_SIZE, Math.min(requested, MAX_SIZE));
  }
}
