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
  private static final long SECONDS_PER_HOUR = 3600;

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

  // 참여 신청 마감 시각. 이 시각 이후엔 새 참여가 불가하고, 마감 판정(진행확정/취소)이 이뤄진다.
  @Column(nullable = false)
  private Instant deadline;

  // 분철 진행 최소 인원. 마감 시점에 입금확인된(CONFIRMED) 참여자가 이 수 이상이면 진행확정, 미만이면 취소된다.
  @Column(name = "min_headcount", nullable = false)
  private int minHeadcount;

  @Embedded private ShippingFeePolicy shippingFeePolicy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private BuncheolStatus status;

  // 분철이 RECRUITING → CONFIRMED/CANCELLED 로 마감 판정된 시각 (호스트 취소 또는 마감 스케줄러).
  @Column(name = "finalized_at")
  private Instant finalizedAt;

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
    this.minHeadcount = params.minHeadcount();
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

  // 참여자가 선택한 배송수단의 배송비. 참여자가 입금할 총액(멤버 금액 + 배송비) 계산에 사용한다.
  public long shippingFeeFor(final ShippingMethod shippingMethod) {
    return shippingFeePolicy.feeFor(shippingMethod);
  }

  public boolean isHost(final Long userId) {
    return hostId.equals(userId);
  }

  private void validate(final Long hostId, final BuncheolParams params, final Instant now) {
    validateHostAndParams(hostId, params);
    validateGroupId(params.groupId());
    validateTitle(params.title());
    validateDescription(params.description());
    validatePurchaseSite(params.purchaseSite());
    validateDeadline(params.deadline(), now);
    validateMinHeadcount(params.minHeadcount());
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
    // 마감은 정각(매시 0분 0초)만 허용 — 매시 정각 cron 으로 정밀 마감하기 위함. KST(+9, 정시 오프셋)라 UTC 시각경계와
    // KST 시각경계가 일치하므로, epochSecond 가 3600 으로 나눠떨어지고 나노초가 0인지로 검증한다.
    if (deadline.getEpochSecond() % SECONDS_PER_HOUR != 0 || deadline.getNano() != 0) {
      throw new BusinessException(ErrorCode.BUNCHEOL_DEADLINE_NOT_ON_THE_HOUR);
    }
  }

  private void validateMinHeadcount(final int minHeadcount) {
    if (minHeadcount < 1) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MIN_HEADCOUNT_INVALID);
    }
  }
}
