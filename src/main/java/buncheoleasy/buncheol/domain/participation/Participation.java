package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 분철 멤버 슬롯에 대한 참여. 한 멤버 슬롯에는 활성({@link ParticipationStatus#active()}) 참여가 최대 1개만 존재한다 (선착순) —
 * participations 의 generated column + unique index 로 DB 가 보장한다.
 *
 * <p>상태 전이(입금확인 / 만료 / 취소 / 마감 컷오프)는 모두 어댑터의 {@code @Modifying} CAS 로 수행한다. 입금 만료 스케줄러와 호스트
 * 입금확인이 동시에 경합하므로, 엔티티를 in-memory 로 변경한 뒤 dirty-checking 으로 커밋하지 않고 status 를 WHERE 조건으로 둔
 * compare-and-swap 으로만 전이해 lost update 를 막는다. 따라서 이 엔티티는 생성·조회용 데이터 홀더로 둔다.
 *
 * <p>참여 INSERT 는 분철이 모집중인지 원자적으로 확인해야 하므로 JPA save 가 아니라 {@code
 * JpaParticipationRepositoryAdapter} 의 conditional INSERT 로 수행하고, generated PK 를 리플렉션으로 {@code id}
 * 필드에 주입한다. 필드명·타입 변경 시 어댑터의 정적 {@code ID_FIELD} 초기화도 함께 갱신할 것.
 */
@Entity
@Table(name = "participations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participation extends TimestampedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  // 참여한 멤버 슬롯 (buncheol_members.id).
  @Column(name = "buncheol_member_id", nullable = false, updatable = false)
  private Long buncheolMemberId;

  // 참여한 유저 (users.id).
  @Column(name = "participant_id", nullable = false, updatable = false)
  private Long participantId;

  // 참여 시 선택한 배송지 (shipping_addresses.id). 배송 방법·수령 지점이 여기서 도출된다.
  @Column(name = "shipping_address_id", nullable = false, updatable = false)
  private Long shippingAddressId;

  // 참여자가 입금해야 할 총액 (멤버 금액 + 선택한 배송수단 배송비). 점유 시점 스냅샷이라 이후 멤버 금액 변경에 영향받지 않는다.
  @Column(nullable = false, updatable = false)
  private long amount;

  // 분철이 진행되지 않을 때(취소) 환불받을 참여자 본인 계좌. 참여와 동시에 입력받는다.
  @Embedded private RefundAccount refundAccount;

  // 입금 만료 시각 = min(점유 시각 + 30분, 분철 deadline). 이 시각까지 호스트의 입금확인이 없으면 자동 취소된다.
  @Column(name = "due_at", nullable = false, updatable = false)
  private Instant dueAt;

  // 개최자가 입금을 수동 확인한 시각. CONFIRMED 진입 시 세팅.
  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  // 참여가 취소된 시각. CANCELLED 진입 시 cancelReason 과 함께 세팅.
  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "cancel_reason", length = 30)
  private ParticipationCancelReason cancelReason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ParticipationStatus status;

  public static Participation create(
      final Long buncheolId,
      final Long buncheolMemberId,
      final Long participantId,
      final Long shippingAddressId,
      final long amount,
      final RefundAccount refundAccount,
      final Instant dueAt) {
    return new Participation(
        buncheolId, buncheolMemberId, participantId, shippingAddressId, amount, refundAccount, dueAt);
  }

  private Participation(
      final Long buncheolId,
      final Long buncheolMemberId,
      final Long participantId,
      final Long shippingAddressId,
      final long amount,
      final RefundAccount refundAccount,
      final Instant dueAt) {
    validate(refundAccount, dueAt);
    this.buncheolId = buncheolId;
    this.buncheolMemberId = buncheolMemberId;
    this.participantId = participantId;
    this.shippingAddressId = shippingAddressId;
    this.amount = amount;
    this.refundAccount = refundAccount;
    this.dueAt = dueAt;
    this.status = ParticipationStatus.AWAITING_PAYMENT;
  }

  public void validateOwnedBy(final Long participantId) {
    if (!this.participantId.equals(participantId)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_NO_PERMISSION);
    }
  }

  private void validate(final RefundAccount refundAccount, final Instant dueAt) {
    if (refundAccount == null || dueAt == null) {
      throw new BusinessException(ErrorCode.PARTICIPATION_REQUIRED_FIELD_MISSING);
    }
  }
}
