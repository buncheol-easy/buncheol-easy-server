package buncheoleasy.user.domain;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class ShippingAddressDomainService {

    private final ShippingAddressRepository shippingAddressRepository;

    public List<ShippingAddress> getUserShippingAddresses(final Long userId) {
        return shippingAddressRepository.getUserShippingAddresses(userId);
    }
}
