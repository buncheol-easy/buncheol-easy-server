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
      final String alias,
      final boolean isDefault) {
    this.userId = userId;
    this.shippingMethod = shippingMethod;
    this.storeName = storeName;
    this.alias = alias;
    this.isDefault = isDefault;
  }

  public static ShippingAddress create(
      final Long userId,
      final String shippingMethodName,
      final String storeName,
      final String alias,
      final boolean isDefault) {
    String normalizedAlias = normalizeAlias(alias);
    return new ShippingAddress(
        userId, ShippingMethod.of(shippingMethodName), storeName, normalizedAlias, isDefault);
  }

  public void update(
      final String shippingMethodName,
      final String storeName,
      final String alias,
      final boolean isDefault) {
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
