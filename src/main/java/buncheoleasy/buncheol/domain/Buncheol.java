package buncheoleasy.buncheol.domain;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.page.Cursorable;
import buncheoleasy.user.domain.shipping.ShippingMethod;
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

@Entity
@Table(name = "buncheols")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Buncheol extends TimestampedEntity implements Cursorable {

  private static final int TITLE_MAX_LENGTH = 200;
  private static final int DESCRIPTION_MAX_LENGTH = 300;
  private static final int PURCHASE_SITE_MAX_LENGTH = 200;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "host_id", nullable = false, updatable = false)
  private Long hostId;

  // 대상 K-pop 그룹 FK.
  @Column(name = "group_id", nullable = false)
  private Long groupId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column private String description;

  @Column(name = "purchase_site", nullable = false, length = 200)
  private String purchaseSite;

  // 참여(제시) 신청 마감 시각. 이 시각 이후엔 새 참여 불가.
  @Column(nullable = false)
  private Instant deadline;

  @Embedded private ShippingFeePolicy shippingFeePolicy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private BuncheolStatus status;

  // 분철이 RECRUITING → CLOSED 로 실제 마감된 시각 (deadline 도달 또는 호스트 수동 마감).
  @Column(name = "closed_at")
  private Instant closedAt;

  public static Buncheol create(final Long hostId, final BuncheolParams params, final Instant now) {
    return new Buncheol(hostId, params, now);
  }

  private Buncheol(final Long hostId, final BuncheolParams params, final Instant now) {
    validate(hostId, params, now);
    this.hostId = hostId;
    this.groupId = params.groupId();
    this.title = params.title();
    this.description = params.description();
    this.purchaseSite = params.purchaseSite();
    this.deadline = params.deadline();
    this.shippingFeePolicy = ShippingFeePolicy.of(params.gs25ShippingFee(), params.cuShippingFee());
    this.status = BuncheolStatus.RECRUITING;
  }

  public void updateContent(final String title, final String description) {
    validateTitle(title);
    validateDescription(description);
    this.title = title;
    this.description = description;
  }

  public void validateOwner(final Long userId) {
    if (!isHost(userId)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION);
    }
  }

  public void validateRecruiting(final Instant now) {
    if (status != BuncheolStatus.RECRUITING) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }
    if (!deadline.isAfter(now)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }
  }

  public void validateShippingMethodSupported(final ShippingMethod shippingMethod) {
    boolean supported =
        switch (shippingMethod) {
          case GS25_HALF -> shippingFeePolicy.gs25ShippingFee() != null;
          case CU_HALF -> shippingFeePolicy.cuShippingFee() != null;
        };
    if (!supported) {
      throw new BusinessException(ErrorCode.PARTICIPATION_SHIPPING_METHOD_NOT_SUPPORTED);
    }
  }

  // 참여자가 선택한 배송수단의 배송비. 낙찰자 결제 금액(제시가 + 배송비) 계산에 사용한다.
  public int shippingFeeFor(final ShippingMethod shippingMethod) {
    return shippingFeePolicy.feeFor(shippingMethod);
  }

  public boolean isHost(final Long userId) {
    return hostId.equals(userId);
  }

  public void cancel() {
    if (!status.isCancellable()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
    status = BuncheolStatus.CANCELLED;
  }

  // 호스트가 deadline 도래 전 모집을 조기에 종료. RECRUITING 일 때만 허용.
  // deadline 경과 후에도 status 가 RECRUITING 인 잔류 케이스 (자동 마감 스케줄러 도입 전) 도 정리 대상이므로
  // deadline 비교는 하지 않는다.
  public void close(final Instant now) {
    if (!status.isCloseable()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }
    this.status = BuncheolStatus.CLOSED;
    this.closedAt = now;
  }

  private void validate(final Long hostId, final BuncheolParams params, final Instant now) {
    validateHostAndParams(hostId, params);
    validateGroupId(params.groupId());
    validateTitle(params.title());
    validateDescription(params.description());
    validatePurchaseSite(params.purchaseSite());
    validateDeadline(params.deadline(), now);
  }

  private void validateHostAndParams(final Long hostId, final BuncheolParams params) {
    if (hostId == null || params == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  private void validateGroupId(final Long value) {
    if (value == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  private void validateTitle(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
    if (value.length() > TITLE_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
    }
  }

  private void validateDescription(final String value) {
    if (value != null && value.length() > DESCRIPTION_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
    }
  }

  private void validatePurchaseSite(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
    if (value.length() > PURCHASE_SITE_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
    }
  }

  private void validateDeadline(final Instant deadline, final Instant now) {
    if (deadline == null || !deadline.isAfter(now)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_DEADLINE_INVALID);
    }
  }
}
