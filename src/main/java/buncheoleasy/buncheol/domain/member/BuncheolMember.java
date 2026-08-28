package buncheoleasy.buncheol.domain.member;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "buncheol_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuncheolMember extends TimestampedEntity {

  // 멤버 금액은 100원 단위로만 설정할 수 있다 (예외 케이스 차단).
  private static final long PRICE_UNIT = 100L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  // group_members FK.
  @Column(name = "member_id", nullable = false, updatable = false)
  private Long memberId;

  // 호스트가 설정한 멤버 1명당 고정 금액 (선착순 참여자가 입금할 굿즈 금액). 100원 단위.
  @Column(nullable = false)
  private long price;

  @Enumerated(EnumType.STRING)
  @Column(name = "access_type", nullable = false, length = 20)
  private BuncheolMemberAccessType accessType = BuncheolMemberAccessType.OPEN;

  public static BuncheolMember create(
      final Long buncheolId, final Long memberId, final long price) {
    return new BuncheolMember(buncheolId, memberId, price, BuncheolMemberAccessType.OPEN);
  }

  public static BuncheolMember create(
      final Long buncheolId,
      final Long memberId,
      final long price,
      final BuncheolMemberAccessType accessType) {
    return new BuncheolMember(buncheolId, memberId, price, accessType);
  }

  private BuncheolMember(
      final Long buncheolId,
      final Long memberId,
      final long price,
      final BuncheolMemberAccessType accessType) {
    validate(buncheolId, memberId, price, accessType);
    this.buncheolId = buncheolId;
    this.memberId = memberId;
    this.price = price;
    this.accessType = accessType;
  }

  public boolean requiresCode() {
    return accessType.requiresCode();
  }

  public boolean isFree() {
    return price == 0L;
  }

  public void updatePrice(final long newPrice) {
    validatePrice(newPrice);
    this.price = newPrice;
  }

  private void validate(
      final Long buncheolId,
      final Long memberId,
      final long price,
      final BuncheolMemberAccessType accessType) {
    validateBuncheolId(buncheolId);
    validateMemberId(memberId);
    validatePrice(price);
    validateAccessType(accessType);
  }

  private void validateAccessType(final BuncheolMemberAccessType value) {
    if (value == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  private void validateBuncheolId(final Long buncheolId) {
    if (buncheolId == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  private void validateMemberId(final Long memberId) {
    if (memberId == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  // 0원은 오픈 이벤트 무료 분철(운영진 발행) 슬롯으로 허용한다.
  private void validatePrice(final long price) {
    if (price < 0 || price % PRICE_UNIT != 0) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_PRICE_INVALID);
    }
  }
}
