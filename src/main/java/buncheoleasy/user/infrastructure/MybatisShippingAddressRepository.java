package buncheoleasy.user.infrastructure;

import buncheoleasy.user.domain.ShippingAddress;
import buncheoleasy.user.domain.ShippingAddressRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class MybatisShippingAddressRepository implements ShippingAddressRepository {

    private final ShippingAddressMapper shippingAddressMapper;

    @Override
    public List<ShippingAddress> getUserShippingAddresses(Long userId) {
        return shippingAddressMapper.findAllByUser(userId);
    }
}
