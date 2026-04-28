package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

  /** 호출처가 @Transactional 인 상태에서 managed 엔티티에 도메인 메서드를 호출 → 트랜잭션 커밋 시 dirty UPDATE 자동 발행. */
  public void withdraw(final Long id) {
    User user = getUser(id);
    user.withdraw();
  }

  /** 호출처가 @Transactional 인 상태에서 managed 엔티티에 도메인 메서드를 호출 → 트랜잭션 커밋 시 dirty UPDATE 자동 발행. */
  public void updateProfile(final Long id, final String nickname, final String phoneNumber) {
    User user = getUser(id);

    if (userRepository.existsByNicknameExcludingId(nickname, id)) {
      throw new BusinessException(ErrorCode.USER_NICKNAME_DUPLICATE);
    }

    user.updateNickname(nickname);
    user.updatePhoneNumber(phoneNumber);
  }

  private User createNewSocialUser(final SocialInfo socialInfo, final String email) {
    User newUser = User.create(socialInfo.provider().name(), socialInfo.providerId(), email);
    return userRepository.save(newUser);
  }
}
