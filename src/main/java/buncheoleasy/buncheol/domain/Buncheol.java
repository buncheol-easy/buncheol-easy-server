package buncheoleasy.buncheol.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class Buncheol {

  private static final int GROUP_NAME_MAX_LENGTH = 100;
  private static final int TITLE_MAX_LENGTH = 200;
  private static final int DESCRIPTION_MAX_LENGTH = 300;
  private static final int GOODS_NAME_MAX_LENGTH = 200;
  private static final int STORE_NAME_MAX_LENGTH = 200;

  private Long id;
  private final Long hostId;
  private final Long groupId;
  private final String groupName;
  private String title;
  private String description;
  private final String goodsName;
  private final String storeName;
  private final long originalPrice;
  private LocalDateTime deadline;
  private final int shippingDeadlineDays;
  private final ShippingFeePolicy shippingFeePolicy;
  private final SettlementInfo settlementInfo;
  private BuncheolStatus status;
  private LocalDateTime closedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Buncheol create(final Long hostId, final BuncheolParams params) {
    return new Buncheol(hostId, params);
  }

  private Buncheol(final Long hostId, final BuncheolParams params) {
    validate(hostId, params);
    this.hostId = hostId;
    this.groupId = params.groupId();
    this.groupName = params.groupName();
    this.title = params.title();
    this.description = params.description();
    this.goodsName = params.goodsName();
    this.storeName = params.storeName();
    this.originalPrice = params.originalPrice();
    this.deadline = params.deadline();
    this.shippingDeadlineDays = params.shippingDeadlineDays();
    this.shippingFeePolicy = ShippingFeePolicy.of(params.gs25ShippingFee(), params.cuShippingFee());
    this.settlementInfo =
        SettlementInfo.of(
            params.settlementBank(), params.settlementAccount(), params.settlementHolder());
    this.status = BuncheolStatus.RECRUITING;
  }

  // MyBatis 조회 전용 생성자
  private Buncheol(
      final Long id,
      final Long hostId,
      final Long groupId,
      final String groupName,
      final String title,
      final String description,
      final String goodsName,
      final String storeName,
      final long originalPrice,
      final LocalDateTime deadline,
      final int shippingDeadlineDays,
      final Integer gs25ShippingFee,
      final Integer cuShippingFee,
      final String settlementBank,
      final String settlementAccount,
      final String settlementHolder,
      final BuncheolStatus status,
      final LocalDateTime closedAt,
      final LocalDateTime createdAt,
      final LocalDateTime updatedAt) {
    this.id = id;
    this.hostId = hostId;
    this.groupId = groupId;
    this.groupName = groupName;
    this.title = title;
    this.description = description;
    this.goodsName = goodsName;
    this.storeName = storeName;
    this.originalPrice = originalPrice;
    this.deadline = deadline;
    this.shippingDeadlineDays = shippingDeadlineDays;
    this.shippingFeePolicy = ShippingFeePolicy.of(gs25ShippingFee, cuShippingFee);
    this.settlementInfo = SettlementInfo.of(settlementBank, settlementAccount, settlementHolder);
    this.status = status;
    this.closedAt = closedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public void validateOwner(final Long userId) {
    if (!hostId.equals(userId)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_NO_PERMISSION);
    }
  }

  public void cancel() {
    if (!status.isCancellable()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
    }
    status = BuncheolStatus.CANCELLED;
  }

  private void validate(final Long hostId, final BuncheolParams params) {
    validateHostAndParams(hostId, params);
    validateGroupName(params.groupName());
    validateTitle(params.title());
    validateDescription(params.description());
    validateGoodsName(params.goodsName());
    validateStoreName(params.storeName());
    validateOriginalPrice(params.originalPrice());
    validateShippingDeadlineDays(params.shippingDeadlineDays());
    validateDeadline(params.deadline());
  }

  private void validateHostAndParams(final Long hostId, final BuncheolParams params) {
    if (hostId == null || params == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  private void validateGroupName(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
    if (value.length() > GROUP_NAME_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.BUNCHEOL_TEXT_LENGTH_INVALID);
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

  private void validateGoodsName(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
    if (value.length() > GOODS_NAME_MAX_LENGTH) {
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

  private void validateOriginalPrice(final long value) {
    if (value <= 0) {
      throw new BusinessException(ErrorCode.BUNCHEOL_PRICE_INVALID);
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
}
