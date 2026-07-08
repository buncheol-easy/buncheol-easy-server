package buncheoleasy.admin.infrastructure.payment;

import buncheoleasy.admin.domain.payment.AdminPaymentQueryRepository;
import buncheoleasy.admin.domain.payment.AdminPaymentStatus;
import buncheoleasy.admin.domain.payment.AdminPaymentSummary;
import buncheoleasy.admin.domain.payment.AdminPaymentView;
import buncheoleasy.admin.domain.payment.BuncheolConfirmedCount;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.participation.Participation;
import buncheoleasy.buncheol.domain.participation.ParticipationStatus;
import buncheoleasy.delivery.domain.Delivery;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.group.domain.Group;
import buncheoleasy.group.domain.member.GroupMember;
import buncheoleasy.user.domain.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaAdminPaymentQueryRepositoryAdapter implements AdminPaymentQueryRepository {

  private final JpaAdminPaymentQueryRepository jpaAdminPaymentQueryRepository;

  @Override
  public List<AdminPaymentView> findPayments(
      final AdminPaymentStatus statusFilter,
      final String escapedKeyword,
      final Cursor cursor,
      final int limit) {
    return jpaAdminPaymentQueryRepository
        .findPaymentRows(
            statusFilter == null ? null : statusFilter.name(),
            escapedKeyword,
            cursor.createdAt(),
            cursor.id(),
            ParticipationStatus.AWAITING_PAYMENT,
            ParticipationStatus.CONFIRMED,
            PageRequest.of(0, limit))
        .stream()
        .map(JpaAdminPaymentQueryRepositoryAdapter::toView)
        .toList();
  }

  @Override
  public List<BuncheolConfirmedCount> countConfirmedByBuncheolIds(final List<Long> buncheolIds) {
    if (buncheolIds.isEmpty()) {
      return List.of();
    }
    return jpaAdminPaymentQueryRepository.countConfirmedByBuncheolIds(
        buncheolIds, ParticipationStatus.CONFIRMED);
  }

  @Override
  public AdminPaymentSummary summarize() {
    final Object[] row =
        jpaAdminPaymentQueryRepository
            .summarize(
                ParticipationStatus.AWAITING_PAYMENT,
                ParticipationStatus.CONFIRMED,
                ParticipationStatus.CANCELLED)
            .getFirst();
    return new AdminPaymentSummary(
        toLong(row[0]), toLong(row[1]), toLong(row[2]), toLong(row[3]), toLong(row[4]),
        toLong(row[5]));
  }

  // SELECT 절 순서(p, b, g, u, gm, d)와 일치해야 한다.
  private static AdminPaymentView toView(final Object[] row) {
    return new AdminPaymentView(
        (Participation) row[0],
        (Buncheol) row[1],
        (Group) row[2],
        (User) row[3],
        (GroupMember) row[4],
        (Delivery) row[5]);
  }

  private static long toLong(final Object value) {
    return ((Number) value).longValue();
  }
}
