package buncheoleasy.buncheol.domain.code;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 특정 슬롯에만 유효한 1회용 참여 코드. 서포터즈 배정이 첫 용례지만 구조는 범용이다 — {@code buncheolMemberId} 가 null 이면 분철 단위
 * 코드(초대 코드 등)로 확장할 수 있다.
 *
 * <p>수명 상태(사용·폐기·만료)는 세 시각 컬럼의 조합으로 파생한다. 만료는 아무도 UPDATE 하지 않는 시각 비교라 저장 상태로 두면
 * 어긋난다.
 *
 * <p>"슬롯당 유효 코드 1개" 는 DB 유니크가 아니라 {@code ParticipationCodeDomainService#issue} 가 지킨다. 쓰기 주체가 관리자
 * 발급 API 뿐이고, 가드가 뚫려도 실제 이중 점유는 {@code uq_participations_active_member} 가 막는다.
 */
@Entity
@Table(name = "participation_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipationCode extends TimestampedEntity {

  private static final int ISSUED_TO_MAX_LENGTH = 50;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 16, updatable = false)
  private String code;

  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  @Column(name = "buncheol_member_id", updatable = false)
  private Long buncheolMemberId;

  // 코드를 보낸 계정(X_handle·N_blogid 등) — 운영 메모이며 인증에는 쓰지 않는다.
  @Column(name = "issued_to", length = 50, updatable = false)
  private String issuedTo;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "used_participation_id")
  private Long usedParticipationId;

  public static ParticipationCode issue(
      final String code,
      final Long buncheolId,
      final Long buncheolMemberId,
      final String issuedTo,
      final Instant expiresAt,
      final Instant now) {
    return new ParticipationCode(code, buncheolId, buncheolMemberId, issuedTo, expiresAt, now);
  }

  private ParticipationCode(
      final String code,
      final Long buncheolId,
      final Long buncheolMemberId,
      final String issuedTo,
      final Instant expiresAt,
      final Instant now) {
    validate(code, buncheolId, issuedTo, expiresAt, now);
    this.code = code;
    this.buncheolId = buncheolId;
    this.buncheolMemberId = buncheolMemberId;
    this.issuedTo = issuedTo;
    this.expiresAt = expiresAt;
  }

  /** 대상 불일치를 먼저 본다 — 만료로 응답하면 애초에 자기 것이 아닌 코드로 재발급을 요청하게 된다. */
  public CodeRedeemability redeemability(
      final Long buncheolId, final Long buncheolMemberId, final Instant now) {
    if (!this.buncheolId.equals(buncheolId)) {
      return CodeRedeemability.SLOT_MISMATCH;
    }
    if (this.buncheolMemberId != null && !this.buncheolMemberId.equals(buncheolMemberId)) {
      return CodeRedeemability.SLOT_MISMATCH;
    }
    if (revokedAt != null) {
      return CodeRedeemability.REVOKED;
    }
    if (usedAt != null) {
      return CodeRedeemability.ALREADY_USED;
    }
    if (!expiresAt.isAfter(now)) {
      return CodeRedeemability.EXPIRED;
    }
    return CodeRedeemability.REDEEMABLE;
  }

  /** 미사용·미폐기·기한 내. 슬롯에 새 코드를 발급해도 되는지의 기준이다. */
  public boolean isUsable(final Instant now) {
    return revokedAt == null && usedAt == null && expiresAt.isAfter(now);
  }

  /** 미사용·미폐기 (만료 포함) — 어드민의 "발급했는데 안 쓴" 코드. */
  public boolean isOutstanding() {
    return revokedAt == null && usedAt == null;
  }

  private void validate(
      final String code,
      final Long buncheolId,
      final String issuedTo,
      final Instant expiresAt,
      final Instant now) {
    if (code == null || code.isBlank() || buncheolId == null) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_REQUIRED_FIELD_MISSING);
    }
    if (issuedTo != null && issuedTo.length() > ISSUED_TO_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_REQUIRED_FIELD_MISSING);
    }
    if (expiresAt == null || !expiresAt.isAfter(now)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_EXPIRY_INVALID);
    }
  }
}
