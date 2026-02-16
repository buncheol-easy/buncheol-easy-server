package buncheoleasy.user.domain;

import java.util.List;

public interface ShippingAddressRepository {

    List<ShippingAddress> getUserShippingAddresses(Long userId);
}
