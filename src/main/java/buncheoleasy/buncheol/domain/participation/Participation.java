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
 * <p>예외로 배송비 환급(payback) 전이는 도메인 메서드 + dirty-checking 으로 한다 — 참여 본체 전이와 달리 스케줄러 경합 주체가 없고(신청은
 * 참여자 본인, 검수는 운영진 한 명뿐), 유일한 실질 경합인 같은 트윗 URL 의 동시 신청은 {@code payback_tweet_url} 유니크 인덱스가 커밋
 * 시점에 차단한다. payback 전이 트랜잭션에서는 참여 status CAS 를 함께 수행하지 않으므로 더티체킹+CAS 혼용 문제도 없다.
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

  // 참여 시 선택한 배송지 (shipping_addresses.id). 배송 방법·수령 지점이 여기서 도출된다. 참여 후에는 변경할 수 없다(updatable=false).
  // 종료(취소·만료)된 참여가 참조하던 배송지를 사용자가 삭제하면 FK ON DELETE SET NULL 로 이 값이 NULL 이 된다
  // (활성 참여가 참조 중이면 앱에서 삭제를 막으므로, 활성 참여의 배송지가 NULL 이 되는 일은 없다).
  @Column(name = "shipping_address_id", updatable = false)
  private Long shippingAddressId;

  // 멤버 금액(굿즈 가격). 점유 시점 스냅샷이라 이후 멤버 금액 변경에 영향받지 않는다. 배송비는 shippingFee 로 분리 보관한다.
  @Column(nullable = false, updatable = false)
  private long amount;

  // 선택한 배송수단의 배송비. 참여 1건 = 멤버 슬롯 1개(단일 선택 정책)라 참여마다 부과된다.
  // 단, 다중 선택 시절 생성된 기존 행은 묶음 첫 슬롯에만 부과되고(>0) 나머지는 0 일 수 있다.
  // 실제 입금 총액은 amount + shippingFee 다(getTotalAmount).
  @Column(name = "shipping_fee", nullable = false, updatable = false)
  private long shippingFee;

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

  // --- 오픈 이벤트 배송비 환급(배송비 돌려받기) ---
  // 저장 값은 NONE/REQUESTED/COMPLETED/REJECTED 뿐이다. 신청 가능(ELIGIBLE)·만료(EXPIRED)는 이벤트
  // 설정(환경변수)+배송 상태로 조회 시 파생한다 (PaybackStatus javadoc 참고).

  @Enumerated(EnumType.STRING)
  @Column(name = "payback_status", nullable = false, length = 20)
  private PaybackStatus paybackStatus = PaybackStatus.NONE;

  // 신청 시 제출한 후기 트윗 URL (PaybackTweetUrl 로 정규화된 퍼머링크). 유니크 인덱스로 타 참여 중복 사용을 막는다.
  @Column(name = "payback_tweet_url")
  private String paybackTweetUrl;

  @Column(name = "payback_requested_at")
  private Instant paybackRequestedAt;

  @Column(name = "payback_completed_at")
  private Instant paybackCompletedAt;

  @Column(name = "payback_reject_reason", length = 200)
  private String paybackRejectReason;

  // 신청 시점의 배송비 스냅샷. 이후 배송비 정책이 바뀌어도 환급액은 고정된다.
  @Column(name = "payback_amount")
  private Long paybackAmount;

  public static Participation create(
      final Long buncheolId,
      final Long buncheolMemberId,
      final Long participantId,
      final Long shippingAddressId,
      final long amount,
      final long shippingFee,
      final RefundAccount refundAccount,
      final Instant dueAt) {
    return new Participation(
        buncheolId,
        buncheolMemberId,
        participantId,
        shippingAddressId,
        amount,
        shippingFee,
        refundAccount,
        dueAt);
  }

  private Participation(
      final Long buncheolId,
      final Long buncheolMemberId,
      final Long participantId,
      final Long shippingAddressId,
      final long amount,
      final long shippingFee,
      final RefundAccount refundAccount,
      final Instant dueAt) {
    validate(refundAccount, dueAt);
    this.buncheolId = buncheolId;
    this.buncheolMemberId = buncheolMemberId;
    this.participantId = participantId;
    this.shippingAddressId = shippingAddressId;
    this.amount = amount;
    this.shippingFee = shippingFee;
    this.refundAccount = refundAccount;
    this.dueAt = dueAt;
    this.status = ParticipationStatus.AWAITING_PAYMENT;
  }

  /** 실제 입금 총액 = 멤버 금액 + 배송비. */
  public long getTotalAmount() {
    return amount + shippingFee;
  }

  /**
   * 배송비 환급 신청 (NONE/REJECTED → REQUESTED). 재신청이면 이전 반려 사유를 지운다. 환급액은 신청 시점의 배송비를 스냅샷해 이후 배송비
   * 정책 변경에 영향받지 않는다. 신청 자격(이벤트 대상·배송 완료·마감 전)은 호출 측 {@code ShippingFeePaybackService} 가 검증한다.
   */
  public void requestPayback(final PaybackTweetUrl tweetUrl, final Instant now) {
    if (!paybackStatus.requestable()) {
      throw new BusinessException(ErrorCode.PAYBACK_STATE_TRANSITION_INVALID);
    }
    this.paybackStatus = PaybackStatus.REQUESTED;
    this.paybackTweetUrl = tweetUrl.value();
    this.paybackRequestedAt = now;
    this.paybackRejectReason = null;
    this.paybackAmount = shippingFee;
  }

  /** 운영진의 환급 입금 완료 처리 (REQUESTED → COMPLETED). 승인·입금완료를 한 번에 처리한다. */
  public void completePayback(final Instant now) {
    if (paybackStatus != PaybackStatus.REQUESTED) {
      throw new BusinessException(ErrorCode.PAYBACK_STATE_TRANSITION_INVALID);
    }
    this.paybackStatus = PaybackStatus.COMPLETED;
    this.paybackCompletedAt = now;
  }

  /** 운영진의 후기 반려 (REQUESTED → REJECTED). 유저는 반려 사유를 보고 재신청할 수 있다. */
  public void rejectPayback(final String rejectReason) {
    if (rejectReason == null || rejectReason.isBlank()) {
      throw new BusinessException(ErrorCode.PAYBACK_REJECT_REASON_REQUIRED);
    }
    if (paybackStatus != PaybackStatus.REQUESTED) {
      throw new BusinessException(ErrorCode.PAYBACK_STATE_TRANSITION_INVALID);
    }
    this.paybackStatus = PaybackStatus.REJECTED;
    this.paybackRejectReason = rejectReason;
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
