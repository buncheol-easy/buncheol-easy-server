package buncheoleasy.buncheol.domain.member;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "buncheol_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuncheolMember {

  private static final int MEMBER_NAME_MAX_LENGTH = 100;
  private static final int MEMBER_IMAGE_MAX_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  // group_members FK.
  @Column(name = "member_id", nullable = false, updatable = false)
  private Long memberId;

  // 화면 표시용 멤버명 스냅샷. 마스터 명칭이 변경되어도 분철 시점 이름이 유지된다.
  @Column(name = "member_name", nullable = false, length = 100, updatable = false)
  private String memberName;

  // 화면 표시용 멤버 이미지 스냅샷.
  @Column(name = "member_image", length = 500, updatable = false)
  private String memberImage;

  // 호스트가 설정한 제시 최소 금액. 모든 참여는 BID 이며 이 금액 이상으로 제시해야 한다. 상한은 없다.
  @Column(name = "bid_min_price", nullable = false)
  private long bidMinPrice;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public static BuncheolMember create(
      final Long buncheolId,
      final Long memberId,
      final String memberName,
      final String memberImage,
      final long bidMinPrice) {
    return new BuncheolMember(buncheolId, memberId, memberName, memberImage, bidMinPrice);
  }

  private BuncheolMember(
      final Long buncheolId,
      final Long memberId,
      final String memberName,
      final String memberImage,
      final long bidMinPrice) {
    validate(buncheolId, memberId, memberName, memberImage, bidMinPrice);
    this.buncheolId = buncheolId;
    this.memberId = memberId;
    this.memberName = memberName;
    this.memberImage = memberImage;
    this.bidMinPrice = bidMinPrice;
  }

  public void updateBidMinPrice(final long newBidMinPrice) {
    validateBidMinPrice(newBidMinPrice);
    this.bidMinPrice = newBidMinPrice;
  }

  public void validateBidAmount(final long bidAmount) {
    if (bidAmount < bidMinPrice) {
      throw new BusinessException(ErrorCode.PARTICIPATION_BID_AMOUNT_INVALID);
    }
  }

  private void validate(
      final Long buncheolId,
      final Long memberId,
      final String memberName,
      final String memberImage,
      final long bidMinPrice) {
    validateBuncheolId(buncheolId);
    validateMemberId(memberId);
    validateMemberName(memberName);
    validateMemberImage(memberImage);
    validateBidMinPrice(bidMinPrice);
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

  private void validateMemberName(final String memberName) {
    if (memberName == null || memberName.isBlank()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
    if (memberName.length() > MEMBER_NAME_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_NAME_LENGTH_INVALID);
    }
  }

  private void validateMemberImage(final String memberImage) {
    if (memberImage != null && memberImage.length() > MEMBER_IMAGE_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_IMAGE_URL_LENGTH_INVALID);
    }
  }

  private void validateBidMinPrice(final long bidMinPrice) {
    if (bidMinPrice <= 0) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MEMBER_BID_MIN_PRICE_INVALID);
    }
  }

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
