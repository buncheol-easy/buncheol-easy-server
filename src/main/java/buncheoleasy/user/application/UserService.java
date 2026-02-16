package buncheoleasy.user.application;

import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.ShippingAddressDomainService;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.dto.response.ShippingAddressResponse;
import buncheoleasy.user.dto.response.UserProfileResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserDomainService userDomainService;
    private final RefreshTokenStore refreshTokenStore;
    private final ShippingAddressDomainService shippingAddressDomainService;

    public void withdraw(final Long userId) {
        userDomainService.withdraw(userId);
        refreshTokenStore.delete(userId);
    }

    public void updateProfile(final Long userId, final String nickname, final String phoneNumber) {
        userDomainService.updateProfile(userId, nickname, phoneNumber);
    }

    public UserProfileResponse getUserProfile(final Long userId) {
        User user = userDomainService.getUser(userId);

        if (!user.isProfileCompleted()) {
            throw new BusinessException(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE);
        }

        return UserProfileResponse.of(
                user.getSocialInfo().provider().name(),
                user.getEmail().value(),
                user.getNickname().value(),
                user.getPhoneNumber() != null ? user.getPhoneNumber().value() : null,
                getUserShippingAddressResponses(userId)
        );
    }

    private List<ShippingAddressResponse> getUserShippingAddressResponses(final Long userId) {
        return shippingAddressDomainService.getUserShippingAddresses(
                        userId)
                .stream()
                .map(sa -> ShippingAddressResponse.of(sa.getShippingMethod().getDescription(), sa.getStoreName()))
                .toList();
    }
}
