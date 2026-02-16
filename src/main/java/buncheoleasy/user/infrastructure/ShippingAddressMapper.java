package buncheoleasy.user.infrastructure;

import buncheoleasy.user.domain.ShippingAddress;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShippingAddressMapper {

    List<ShippingAddress> findAllByUser(Long userId);
}
