package buncheoleasy.admin.infrastructure.payback;

import buncheoleasy.admin.domain.payback.AdminPaybackView;
import buncheoleasy.admin.domain.payback.AdminShippingFeePaybackQueryRepository;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.PaybackStatus;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaAdminShippingFeePaybackQueryRepositoryAdapter
    implements AdminShippingFeePaybackQueryRepository {

  private final JpaAdminShippingFeePaybackQueryRepository jpaRepository;

  @Override
  public List<AdminPaybackView> findPaybacks(
      final PaybackStatus statusFilter, final Cursor cursor, final int limit) {
    return jpaRepository
        .findPaybackRows(
            statusFilter,
            PaybackStatus.NONE,
            cursor.createdAt(),
            cursor.id(),
            PageRequest.of(0, limit))
        .stream()
        .map(JpaAdminShippingFeePaybackQueryRepositoryAdapter::toView)
        .toList();
  }

  // SELECT 절 순서(p, b, u, gm)와 일치해야 한다.
  private static AdminPaybackView toView(final Object[] row) {
    return new AdminPaybackView(
        (Participation) row[0], (Buncheol) row[1], (User) row[2], (GroupMember) row[3]);
  }
}
