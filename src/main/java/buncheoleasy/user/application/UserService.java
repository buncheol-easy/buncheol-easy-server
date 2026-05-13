package buncheoleasy.user.application;

import buncheoleasy.auth.domain.RefreshTokenStore;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.participation.ParticipationDomainService;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.BankAccount;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.domain.UserDomainService;
import buncheoleasy.user.dto.request.BankAccountRequest;
import buncheoleasy.user.dto.request.CompleteUserProfileRequest;
import buncheoleasy.user.dto.request.UpdateUserProfileRequest;
import buncheoleasy.user.dto.response.ProfileStatusResponse;
import buncheoleasy.user.dto.response.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserDomainService userDomainService;
  private final BuncheolDomainService buncheolDomainService;
  private final ParticipationDomainService participationDomainService;
  private final RefreshTokenStore refreshTokenStore;

  /** 활성 분철(개최)·활성 참여가 남아 있으면 탈퇴를 거부한다. */
  public void withdraw(final Long userId) {
    if (buncheolDomainService.hasActiveBuncheolHostedBy(userId)) {
      throw new BusinessException(ErrorCode.USER_WITHDRAW_BLOCKED_BY_ACTIVE_BUNCHEOL);
    }
    if (participationDomainService.hasActiveParticipationBy(userId)) {
      throw new BusinessException(ErrorCode.USER_WITHDRAW_BLOCKED_BY_ACTIVE_PARTICIPATION);
    }

    userDomainService.withdraw(userId);
    try {
      refreshTokenStore.delete(userId);
    } catch (Exception e) {
      log.warn("회원 탈퇴 후 리프레시 토큰 삭제 실패. userId: {}", userId, e);
    }
  }

  public void completeProfile(final Long userId, final CompleteUserProfileRequest request) {
    userDomainService.completeProfile(userId, request.nickname(), request.phoneNumber());
  }

  public void updateProfile(final Long userId, final UpdateUserProfileRequest request) {
    userDomainService.updateProfile(userId, request.nickname(), request.phoneNumber());
  }

  public ProfileStatusResponse getProfileStatus(final Long userId) {
    User user = userDomainService.getUser(userId);
    return ProfileStatusResponse.of(user.isProfileCompleted());
  }

  public void updateBankAccount(final Long userId, final BankAccountRequest request) {
    userDomainService.updateBankAccount(
        userId, request.bank(), request.account(), request.holder());
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
        toBankAccountInfo(user.getBankAccount()));
  }

  private UserProfileResponse.BankAccountInfo toBankAccountInfo(final BankAccount bankAccount) {
    if (bankAccount == null) {
      return null;
    }
    return new UserProfileResponse.BankAccountInfo(
        bankAccount.bank(), bankAccount.account(), bankAccount.holder());
  }
}
