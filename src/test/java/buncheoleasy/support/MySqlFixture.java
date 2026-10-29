package buncheoleasy.support;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * MySQL 테스트용 행을 SQL 로 바로 넣는다. 시각은 DB 시계({@code UTC_TIMESTAMP()})에서 상대값으로 정해 JVM 시간대와 무관하게 만든다.
 * 넣은 행은 커밋된다(트랜잭션 밖 {@link JdbcTemplate}).
 */
public final class MySqlFixture {

  private final JdbcTemplate jdbc;
  private int sequence;

  public MySqlFixture(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 가입을 마치고(전화번호) 정산 계좌가 있는 회원. 신청·입금 확인·배송 스냅샷이 모두 통과한다. */
  public long user(final boolean staff) {
    int n = ++sequence;
    return insert(
        "INSERT INTO users (provider, provider_id, email, nickname, name, phone_number,"
            + " settlement_bank, settlement_account, settlement_holder, profile_completed, can_host)"
            + " VALUES ('KAKAO', ?, ?, ?, '테스트', '01012345678', '국민', '123-456-789', ?, 1, ?)",
        "fixture-" + n,
        "fixture-" + n + "@example.com",
        "user" + n,
        "예금주" + n,
        staff);
  }

  public long group() {
    return insert("INSERT INTO `groups` (name) VALUES (?)", "그룹" + (++sequence));
  }

  public long groupMember(final long groupId) {
    return insert(
        "INSERT INTO group_members (group_id, name) VALUES (?, ?)",
        groupId,
        "멤버" + (++sequence));
  }

  /**
   * 분철. 마감은 지금 + {@code deadlineFromNow}(음수면 지남), 입금 기한은 {@code paymentDueFromNow} 가 null 이 아니면
   * 같은 방식으로 채운다. 입금 수집 중이면 계좌 스냅샷과 {@code finalized_at} 도 채운다(입금 단계 시작 CAS 와 같은 칸).
   */
  public long buncheol(
      final long hostId,
      final long groupId,
      final String flowType,
      final String status,
      final Duration deadlineFromNow,
      final int minHeadcount,
      final Duration paymentDueFromNow) {
    boolean collecting = paymentDueFromNow != null;
    return insert(
        "INSERT INTO buncheols (host_id, group_id, title, purchase_site, deadline, min_headcount,"
            + " gs25_shipping_fee, cu_shipping_fee, status, flow_type, payment_due_at, finalized_at,"
            + " payment_bank, payment_account, payment_holder)"
            + " VALUES (?, ?, '테스트 분철', '공식 스토어', UTC_TIMESTAMP() + INTERVAL ? SECOND, ?,"
            + " 3000, 3000, ?, ?,"
            + " IF(?, UTC_TIMESTAMP() + INTERVAL ? SECOND, NULL), IF(?, UTC_TIMESTAMP(), NULL),"
            + " IF(?, '국민', NULL), IF(?, '111-222', NULL), IF(?, '개최자', NULL))",
        hostId,
        groupId,
        deadlineFromNow.toSeconds(),
        minHeadcount,
        status,
        flowType,
        collecting,
        collecting ? paymentDueFromNow.toSeconds() : 0,
        collecting,
        collecting,
        collecting,
        collecting);
  }

  /** 추가 모집 스위치를 켠다(진행 확정 회원 개최에서만 뜻이 있다). */
  public void openExtraRecruit(final long buncheolId) {
    jdbc.update("UPDATE buncheols SET extra_recruit_open = 1 WHERE id = ?", buncheolId);
  }

  /** 추가 신청으로 열린 묶음 표시를 단다. */
  public void markExtra(final long bundleId) {
    jdbc.update("UPDATE participation_bundles SET is_extra = 1 WHERE id = ?", bundleId);
  }

  public long slot(final long buncheolId, final long groupMemberId) {
    return insert(
        "INSERT INTO buncheol_members (buncheol_id, member_id, price) VALUES (?, ?, 15000)",
        buncheolId,
        groupMemberId);
  }

  public long shippingAddress(final long userId) {
    return insert(
        "INSERT INTO shipping_addresses (user_id, shipping_method, store_name, is_default)"
            + " VALUES (?, 'GS25_HALF', ?, 1)",
        userId,
        "GS25 테스트점" + (++sequence));
  }

  /** 열린 묶음. 기한은 {@code dueFromNow} 가 null 이면 비운다(모집 중 신청 묶음). */
  public long bundle(
      final long buncheolId,
      final long participantId,
      final long shippingAddressId,
      final Duration dueFromNow) {
    boolean hasDue = dueFromNow != null;
    return insert(
        "INSERT INTO participation_bundles (buncheol_id, participant_id, shipping_address_id,"
            + " shipping_fee, refund_bank, refund_account, refund_holder, due_at)"
            + " VALUES (?, ?, ?, 3000, '국민', '123-456-789', '예금주',"
            + " IF(?, UTC_TIMESTAMP() + INTERVAL ? SECOND, NULL))",
        buncheolId,
        participantId,
        shippingAddressId,
        hasDue,
        hasDue ? dueFromNow.toSeconds() : 0);
  }

  public long participation(
      final long buncheolId,
      final long slotId,
      final long participantId,
      final long bundleId,
      final String status,
      final String flowType,
      final Duration dueFromNow) {
    boolean hasDue = dueFromNow != null;
    return insert(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id, bundle_id,"
            + " amount, status, flow_type, due_at, confirmed_at)"
            + " VALUES (?, ?, ?, ?, 15000, ?, ?, IF(?, UTC_TIMESTAMP() + INTERVAL ? SECOND, NULL),"
            + " IF(? = 'CONFIRMED', UTC_TIMESTAMP(), NULL))",
        buncheolId,
        slotId,
        participantId,
        bundleId,
        status,
        flowType,
        hasDue,
        hasDue ? dueFromNow.toSeconds() : 0,
        status);
  }

  /** 이 묶음에 배송을 하나 만든다(대표 슬롯 {@code participationId}). */
  public long delivery(final long participationId, final long bundleId) {
    return insert(
        "INSERT INTO deliveries (participation_id, bundle_id, shipping_method, store_name,"
            + " receiver_nickname, receiver_phone_number)"
            + " VALUES (?, ?, 'GS25_HALF', 'GS25 테스트점', 'user', '01012345678')",
        participationId,
        bundleId);
  }

  private long insert(final String sql, final Object... args) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
          }
          return ps;
        },
        keyHolder);
    return keyHolder.getKey().longValue();
  }
}
