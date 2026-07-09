package buncheoleasy.admin.domain.payment;

import buncheoleasy.global.page.Cursor;
import java.util.List;

public interface AdminPaymentQueryRepository {

  /**
   * 전체 분철의 결제(참여) 목록을 최신 참여순(createdAt DESC, id DESC)으로 조회한다.
   *
   * @param statusFilter 파생 상태 필터. null 이면 전체
   * @param escapedKeyword {@link buncheoleasy.global.query.LikeEscaper} 로 이스케이프된 검색어 (분철 제목·그룹명·멤버명·참여자
   *     닉네임 부분 일치). null 이면 전체
   * @param cursor 커서 (첫 페이지면 {@link Cursor#isFirstPage()})
   * @param limit hasNext 판별을 위해 호출 측이 size + 1 을 넘긴다
   */
  List<AdminPaymentView> findPayments(
      AdminPaymentStatus statusFilter, String escapedKeyword, Cursor cursor, int limit);

  /** 여러 분철의 입금확인(CONFIRMED) 참여 수 집계. */
  List<BuncheolConfirmedCount> countConfirmedByBuncheolIds(List<Long> buncheolIds);

  /** 전체 결제 통계 (파생 상태별 건수 + 확인 대기 금액 합계). */
  AdminPaymentSummary summarize();
}
