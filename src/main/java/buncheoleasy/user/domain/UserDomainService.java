package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDomainService {

  private final UserRepository userRepository;

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
    user.withdraw();
  }

  @Transactional
  public void updateProfile(final Long id, final String nickname, final String phoneNumber) {
    User user = getUser(id);

    if (userRepository.existsByNicknameExcludingId(nickname, id)) {
      throw new BusinessException(ErrorCode.USER_NICKNAME_DUPLICATE);
    }

    user.updateNickname(nickname);
    user.updatePhoneNumber(phoneNumber);
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

  private User createNewSocialUser(final SocialInfo socialInfo, final String email) {
    User newUser = User.create(socialInfo.provider().name(), socialInfo.providerId(), email);
    return userRepository.save(newUser);
  }
}
