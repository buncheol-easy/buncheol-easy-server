package buncheoleasy.buncheol.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.shipping.ShippingMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "buncheols")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Buncheol {

  private static final int TITLE_MAX_LENGTH = 200;
  private static final int DESCRIPTION_MAX_LENGTH = 300;
  private static final int STORE_NAME_MAX_LENGTH = 200;

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

  @Column(name = "store_name", nullable = false, length = 200)
  private String storeName;

  // 참여(제시) 신청 마감 시각. 이 시각 이후엔 새 참여 불가.
  @Column(nullable = false)
  private LocalDateTime deadline;

  // 호스트가 굿즈를 수령한 후 참여자에게 발송해야 하는 마감 기한(일수).
  @Column(name = "shipping_deadline_days", nullable = false)
  private int shippingDeadlineDays;

  @Embedded private ShippingFeePolicy shippingFeePolicy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private BuncheolStatus status;

  // 분철이 RECRUITING → CLOSED 로 실제 마감된 시각 (deadline 도달 또는 호스트 수동 마감).
  @Column(name = "closed_at")
  private LocalDateTime closedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public static Buncheol create(final Long hostId, final BuncheolParams params) {
    return new Buncheol(hostId, params);
  }

  private Buncheol(final Long hostId, final BuncheolParams params) {
    validate(hostId, params);
    this.hostId = hostId;
    this.groupId = params.groupId();
    this.title = params.title();
    this.description = params.description();
    this.storeName = params.storeName();
    this.deadline = params.deadline();
    this.shippingDeadlineDays = params.shippingDeadlineDays();
    this.shippingFeePolicy = ShippingFeePolicy.of(params.gs25ShippingFee(), params.cuShippingFee());
    this.status = BuncheolStatus.RECRUITING;
  }

  public void update(final BuncheolParams params) {
    validate(this.hostId, params);
    this.groupId = params.groupId();
    this.title = params.title();
    this.description = params.description();
    this.storeName = params.storeName();
    this.deadline = params.deadline();
    this.shippingDeadlineDays = params.shippingDeadlineDays();
    this.shippingFeePolicy = ShippingFeePolicy.of(params.gs25ShippingFee(), params.cuShippingFee());
  }

  public void validateOwner(final Long userId) {
    if (!isHost(userId)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION);
    }
  }

  public void validateRecruiting() {
    if (status != BuncheolStatus.RECRUITING) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NOT_RECRUITING);
    }
    if (!deadline.isAfter(LocalDateTime.now())) {
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

  public boolean isHost(final Long userId) {
    return hostId.equals(userId);
  }

  public void cancel() {
    if (!status.isCancellable()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
    status = BuncheolStatus.CANCELLED;
  }

  public void advanceStatus(final BuncheolStatus nextStatus) {
    if (!status.canAdvanceTo(nextStatus)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_STATUS_ADVANCE_NOT_ALLOWED);
    }
    this.status = nextStatus;
  }

  private void validate(final Long hostId, final BuncheolParams params) {
    validateHostAndParams(hostId, params);
    validateGroupId(params.groupId());
    validateTitle(params.title());
    validateDescription(params.description());
    validateStoreName(params.storeName());
    validateShippingDeadlineDays(params.shippingDeadlineDays());
    validateDeadline(params.deadline());
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

  private void validateStoreName(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
    if (value.length() > STORE_NAME_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
    }
  }

  private void validateShippingDeadlineDays(final int value) {
    if (value <= 0) {
      throw new BusinessException(ErrorCode.BUNCHEOL_SHIPPING_DEADLINE_DAYS_INVALID);
    }
  }

  private void validateDeadline(final LocalDateTime deadline) {
    if (deadline == null || !deadline.isAfter(LocalDateTime.now())) {
      throw new BusinessException(ErrorCode.BUNCHEOL_DEADLINE_INVALID);
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
