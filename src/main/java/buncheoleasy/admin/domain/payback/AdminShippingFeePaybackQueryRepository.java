package buncheoleasy.admin.domain.payback;

import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.global.page.Cursor;
import java.util.List;

public interface AdminShippingFeePaybackQueryRepository {

  /**
   * 배송비 환급 신청 이력이 있는 참여(paybackStatus != NONE) 목록. 신청 최신순({@code paybackRequestedAt DESC, id
   * DESC}) 커서 페이지네이션이며, 커서의 Instant 슬롯에는 createdAt 이 아니라 {@code paybackRequestedAt} 을 싣는다.
   *
   * @param statusFilter 저장 상태 필터. null 이면 신청 이력 전체.
   */
  List<AdminPaybackView> findPaybacks(PaybackStatus statusFilter, Cursor cursor, int limit);
}
