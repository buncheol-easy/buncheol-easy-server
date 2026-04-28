package buncheoleasy.user.domain.shipping;

import jakarta.persistence.Column;
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
@Table(name = "shipping_addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddress {

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

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public ShippingAddress(
      final Long id,
      final Long userId,
      final ShippingMethod shippingMethod,
      final String storeName,
      final LocalDateTime createdAt,
      final LocalDateTime updatedAt) {
    this.id = id;
    this.userId = userId;
    this.shippingMethod = shippingMethod;
    this.storeName = storeName;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  private ShippingAddress(
      final Long userId, final ShippingMethod shippingMethod, final String storeName) {
    this.userId = userId;
    this.shippingMethod = shippingMethod;
    this.storeName = storeName;
  }

  public static ShippingAddress create(
      final Long userId, final String shippingMethodName, final String storeName) {
    return new ShippingAddress(userId, ShippingMethod.of(shippingMethodName), storeName);
  }

  public void update(final String shippingMethodName, final String storeName) {
    this.shippingMethod = ShippingMethod.of(shippingMethodName);
    this.storeName = storeName;
  }

  public boolean isSameAddress(final String shippingMethodName, final String storeName) {
    return this.shippingMethod.name().equals(shippingMethodName)
        && this.storeName.equals(storeName);
  }

  public boolean isOwnedBy(final Long userId) {
    return this.userId.equals(userId);
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
