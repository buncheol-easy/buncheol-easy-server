package buncheoleasy.user.domain.shipping;

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
@Table(name = "shipping_addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddress extends TimestampedEntity {

  private static final int ALIAS_MAX_LENGTH = 10;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "shipping_method", nullable = false, length = 20)
  private ShippingMethod shippingMethod;

  @Column(name = "store_name", nullable = false, length = 100)
  private String storeName;

  /**
   * 선택한 접수처의 원천 점포 코드 — {@code cvs_stores (brand, store_code)} 재조인 키. 지점명은 개명·중복이
   * 가능해 마스터 연결은 이 코드를 기준으로 한다. 코드 없이 등록된 배송지는 {@code null}.
   */
  @Column(name = "store_code", length = 20)
  private String storeCode;

  @Column(name = "alias", length = 10)
  private String alias;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  public ShippingAddress(
      final Long id,
      final Long userId,
      final ShippingMethod shippingMethod,
      final String storeName,
      final String alias,
      final boolean isDefault) {
    this.id = id;
    this.userId = userId;
    this.shippingMethod = shippingMethod;
    this.storeName = storeName;
    this.alias = alias;
    this.isDefault = isDefault;
  }

  private ShippingAddress(
      final Long userId,
      final ShippingMethod shippingMethod,
      final String storeName,
      final String storeCode,
      final String alias,
      final boolean isDefault) {
    this.userId = userId;
    this.shippingMethod = shippingMethod;
    this.storeName = storeName;
    this.storeCode = storeCode;
    this.alias = alias;
    this.isDefault = isDefault;
  }

  /** 점포 코드 없는 생성 — 접수처 검색 이전 클라이언트와의 하위 호환용. */
  public static ShippingAddress create(
      final Long userId,
      final String shippingMethodName,
      final String storeName,
      final String alias,
      final boolean isDefault) {
    return create(userId, shippingMethodName, storeName, null, alias, isDefault);
  }

  public static ShippingAddress create(
      final Long userId,
      final String shippingMethodName,
      final String storeName,
      final String storeCode,
      final String alias,
      final boolean isDefault) {
    String normalizedAlias = normalizeAlias(alias);
    return new ShippingAddress(
        userId,
        ShippingMethod.of(shippingMethodName),
        storeName,
        normalizeStoreCode(storeCode),
        normalizedAlias,
        isDefault);
  }

  /** 점포 코드 없는 수정 — 하위 호환용. 코드 유지/초기화 규칙은 6-인자 {@link #update} 를 따른다. */
  public void update(
      final String shippingMethodName,
      final String storeName,
      final String alias,
      final boolean isDefault) {
    update(shippingMethodName, storeName, null, alias, isDefault);
  }

  /**
   * 코드가 안 넘어온 요청은 지점명이 그대로일 때만 기존 코드를 유지한다(기본 배송지 토글 등 코드 필드를 모르는
   * 클라이언트 보호). 지점명이 바뀌면 낡은 코드가 다른 지점을 가리키게 되므로 초기화한다.
   */
  public void update(
      final String shippingMethodName,
      final String storeName,
      final String storeCode,
      final String alias,
      final boolean isDefault) {
    final String normalizedStoreCode = normalizeStoreCode(storeCode);
    if (normalizedStoreCode != null) {
      this.storeCode = normalizedStoreCode;
    } else if (!this.storeName.equals(storeName)) {
      this.storeCode = null;
    }
    this.shippingMethod = ShippingMethod.of(shippingMethodName);
    this.storeName = storeName;
    this.alias = normalizeAlias(alias);
    this.isDefault = isDefault;
  }

  public boolean isSameAddress(final String shippingMethodName, final String storeName) {
    return this.shippingMethod.name().equals(shippingMethodName)
        && this.storeName.equals(storeName);
  }

  public boolean isOwnedBy(final Long userId) {
    return this.userId.equals(userId);
  }

  private static String normalizeStoreCode(final String storeCode) {
    if (storeCode == null || storeCode.isBlank()) {
      return null;
    }
    return storeCode.trim();
  }

  private static String normalizeAlias(final String alias) {
    if (alias == null) {
      return null;
    }
    String trimmed = alias.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > ALIAS_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.SHIPPING_ADDRESS_ALIAS_TOO_LONG);
    }
    return trimmed;
  }
}
