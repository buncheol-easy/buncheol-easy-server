package buncheoleasy.admin.application;

import buncheoleasy.admin.domain.payment.AdminPaymentQueryRepository;
import buncheoleasy.admin.domain.payment.AdminPaymentStatus;
import buncheoleasy.admin.domain.payment.AdminPaymentView;
import buncheoleasy.admin.domain.payment.BuncheolConfirmedCount;
import buncheoleasy.admin.dto.response.AdminPaymentRecordResponse;
import buncheoleasy.admin.dto.response.AdminPaymentSummaryResponse;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationBundle;
import buncheoleasy.buncheol.domain.participation.ParticipationBundleDomainService;
import buncheoleasy.buncheol.domain.participation.ShippingFeeAttribution;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import buncheoleasy.global.query.LikeEscaper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 결제 대시보드 조회. 전체 분철의 참여(결제)를 최신순 커서 페이지네이션으로 내려준다 — 기존 프론트가 개최 분철 목록 → 분철별 관리 API 를 반복 호출해
 * 클라이언트에서 조립하던 것을 단일 API 로 대체한다. 상단 통계는 {@link #getSummary()} 로 분리해 목록 페이지네이션과 무관하게 전체 기준을 유지한다.
 */
@Service
@RequiredArgsConstructor
public class AdminPaymentQueryService {

  private static final int MIN_SIZE = 1;
  private static final int MAX_SIZE = 100;

  private final AdminPaymentQueryRepository adminPaymentQueryRepository;
  private final ParticipationBundleDomainService participationBundleDomainService;

  @Transactional(readOnly = true)
  public CursorResponse<AdminPaymentRecordResponse> getPayments(
      final AdminPaymentStatus statusFilter,
      final String keyword,
      final Cursor cursor,
      final int requestedSize) {
    final int safeSize = clampSize(requestedSize);

    final List<AdminPaymentView> fetched =
        adminPaymentQueryRepository.findPayments(
            statusFilter, LikeEscaper.escape(trimKeyword(keyword)), cursor, safeSize + 1);
    final boolean hasNext = fetched.size() > safeSize;
    final List<AdminPaymentView> visible = hasNext ? fetched.subList(0, safeSize) : fetched;

    if (visible.isEmpty()) {
      return CursorResponse.empty();
    }

    final Map<Long, Long> confirmedCountByBuncheolId = resolveConfirmedCounts(visible);
    // 계좌의 정본은 묶음이다 (P2-c). 건별로 읽으면 페이지 크기만큼 쿼리가 늘어난다(N+1).
    final List<Participation> pageParticipations =
        visible.stream().map(AdminPaymentView::participation).toList();
    final Map<Long, ParticipationBundle> bundleById =
        participationBundleDomainService.findAllByParticipations(pageParticipations);
    // ⚠️ 이 목록은 커서 페이지네이션 + 상태/키워드 필터라 한 묶음의 슬롯이 페이지 경계로 쪼개지는 게 정상이다.
    // 페이지 조각을 그대로 판정에 넘기면 그 안에서 carrier 를 다시 뽑아 배송비가 두 번 걷힌다 —
    // 형제 슬롯을 대신 읽어 주는 진입점을 써야 한다. 묶음은 위에서 이미 읽었으므로 그대로 넘겨 재조회를 막는다.
    final ShippingFeeAttribution shippingFees =
        participationBundleDomainService.shippingFeeAttributionFor(
            pageParticipations, bundleById);
    final List<AdminPaymentRecordResponse> items =
        visible.stream()
            .map(
                view ->
                    AdminPaymentRecordResponse.of(
                        view,
                        confirmedCountByBuncheolId.getOrDefault(view.buncheol().getId(), 0L),
                        ParticipationBundleDomainService.refundAccountOf(
                            bundleById, view.participation()),
                        shippingFees.totalAmountOf(view.participation()),
                        ParticipationBundleDomainService.dueAtOf(
                            bundleById, view.participation(), view.buncheol().isC2c())))
            .toList();

    final var lastParticipation = visible.getLast().participation();
    final String nextCursor =
        hasNext
            ? new Cursor(lastParticipation.getCreatedAt(), lastParticipation.getId()).encode()
            : null;
    return new CursorResponse<>(items, nextCursor, hasNext);
  }

  @Transactional(readOnly = true)
  public AdminPaymentSummaryResponse getSummary() {
    return AdminPaymentSummaryResponse.from(adminPaymentQueryRepository.summarize());
  }

  private Map<Long, Long> resolveConfirmedCounts(final List<AdminPaymentView> visible) {
    final List<Long> buncheolIds =
        visible.stream().map(view -> view.buncheol().getId()).distinct().toList();
    return adminPaymentQueryRepository.countConfirmedByBuncheolIds(buncheolIds).stream()
        .collect(
            Collectors.toMap(BuncheolConfirmedCount::buncheolId, BuncheolConfirmedCount::count));
  }

  private int clampSize(final int requested) {
    return Math.max(MIN_SIZE, Math.min(requested, MAX_SIZE));
  }

  private static String trimKeyword(final String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    return keyword.trim();
  }
}
