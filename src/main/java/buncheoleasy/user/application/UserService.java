package buncheoleasy.user.application;

import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.dto.request.UpdateUserProfileRequest;
import buncheoleasy.user.dto.response.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserDomainService userDomainService;
  private final RefreshTokenStore refreshTokenStore;

  @Transactional
  public void withdraw(final Long userId) {
    userDomainService.withdraw(userId);
    try {
      refreshTokenStore.delete(userId);
    } catch (Exception e) {
      log.warn("회원 탈퇴 후 리프레시 토큰 삭제 실패. userId: {}", userId, e);
    }
  }

  @Transactional
  public void updateProfile(final Long userId, final UpdateUserProfileRequest request) {
    userDomainService.updateProfile(userId, request.nickname(), request.phoneNumber());
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
        user.getPhoneNumber() != null ? user.getPhoneNumber().value() : null);
  }
}
