package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDomainService {

  private final UserRepository userRepository;
  private final Clock clock;

  public User getOrCreateBySocialLogin(final SocialInfo socialInfo, final String email) {
    return userRepository
        .findBySocialInfo(socialInfo)
        .orElseGet(() -> createNewSocialUser(socialInfo, email));
  }

  public boolean isValidUser(final Long id) {
    return userRepository.existsById(id);
  }

  public User getUser(final Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  @Transactional
  public void withdraw(final Long id) {
    User user = getUser(id);
    user.withdraw(Instant.now(clock));
  }

  @Transactional
  public void updateProfile(
      final Long id,
      final String nickname,
      final String phoneNumber,
      final String name,
      final Boolean marketingAgreed) {
    User user = getUser(id);

    if (userRepository.existsByNicknameExcludingId(nickname, id)) {
      throw new BusinessException(ErrorCode.USER_NICKNAME_DUPLICATE);
    }

    user.updateNickname(nickname);
    user.updatePhoneNumber(phoneNumber);
    // null 이면 기존 실명을 유지한다 (실명 필드 없는 기존 호출 호환).
    if (name != null) {
      user.updateName(name);
    }
    // null 이면 동의 상태를 건드리지 않는다 (동의 필드 없이 프로필만 수정하는 기존 호출 호환).
    if (marketingAgreed != null) {
      user.updateMarketingAgreement(marketingAgreed, Instant.now(clock));
    }
  }

  @Transactional
  public void updateBankAccount(
      final Long id, final String bank, final String account, final String holder) {
    User user = getUser(id);
    user.updateBankAccount(bank, account, holder);
  }

  public void requireBankAccountRegistered(final Long id) {
    getUser(id).requireBankAccount();
  }

  public void requireProfileCompleted(final Long id) {
    getUser(id).requireProfileCompleted();
  }

  public void requireCanHost(final Long id) {
    getUser(id).requireCanHost();
  }

  public boolean isNicknameDuplicate(final String nickname, final Long excludeId) {
    return userRepository.existsByNicknameExcludingId(nickname, excludeId);
  }

  private User createNewSocialUser(final SocialInfo socialInfo, final String email) {
    User newUser = User.create(socialInfo.provider().name(), socialInfo.providerId(), email);
    return userRepository.save(newUser);
  }
}
