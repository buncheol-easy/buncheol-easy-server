package buncheoleasy.user.infrastructure;

import buncheoleasy.user.domain.ShippingAddress;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShippingAddressMapper {

    void insert(ShippingAddress shippingAddress);

    void update(ShippingAddress shippingAddress);

    void delete(Long id);

    Optional<ShippingAddress> findById(Long id);

    List<ShippingAddress> findAllByUser(Long userId);

    int countByUserId(Long userId);

    boolean existsByUserIdAndShippingMethodAndStoreName(
            @Param("userId") Long userId,
            @Param("shippingMethod") String shippingMethod,
            @Param("storeName") String storeName
    );
}
