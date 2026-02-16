package buncheoleasy.user.domain;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class ShippingAddress {

    private Long id;
    private final Long userId;
    private final ShippingMethod shippingMethod;
    private String storeName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ShippingAddress(final Long id, final Long userId, final ShippingMethod shippingMethod,
                           final String storeName, final LocalDateTime createdAt, final LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.shippingMethod = shippingMethod;
        this.storeName = storeName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public ShippingAddress(final Long userId, final ShippingMethod shippingMethod, final String storeName) {
        this.userId = userId;
        this.shippingMethod = shippingMethod;
        this.storeName = storeName;
    }

    public static ShippingAddress create(final Long userId, final String shippingMethodName, final String storeName) {
        return new ShippingAddress(userId, ShippingMethod.of(shippingMethodName), storeName);
    }
}
