package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.serviceterm.ServiceTermAgreement;
import buncheoleasy.user.domain.serviceterm.UserServiceTerm;
import buncheoleasy.user.domain.serviceterm.UserServiceTermRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDomainService {

  private final UserRepository userRepository;
  private final UserServiceTermRepository userServiceTermRepository;
  private final RandomNicknameGenerator nicknameGenerator;
  private final Clock clock;

  /**
   * 소셜 로그인 회원 조회/생성. name·phoneNumber·ageRange 는 카카오싱크 동의창에서 받은 값(없으면 null). 기존 회원은 카카오 값으로 덮어쓰지
   * 않는다 — 마이페이지에서 수정한 값을 보호한다. 단 연령대는 예외: 마이페이지 수정 대상이 아니고 시간이 지나면 구간이 바뀌므로 재로그인 때마다 카카오 최신값으로
   * 갱신한다(기존 가입자의 추가 동의 수집 경로 — docs/50).
   */
  @Transactional
  public User getOrCreateBySocialLogin(
      final SocialInfo socialInfo,
      final String email,
      final String name,
      final String phoneNumber,
      final String ageRange) {
    return userRepository
        .findBySocialInfo(socialInfo)
        .map(
            user -> {
              user.updateAgeRange(ageRange);
              return user;
            })
        .orElseGet(() -> createNewSocialUser(socialInfo, email, name, phoneNumber, ageRange));
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

  /**
   * 간편가입 약관 동의 내역을 (userId, tag) 단위로 upsert 하고, 마케팅 태그면 수신 동의 상태도 함께 갱신한다. 재로그인 시 최신 동의 상태로
   * 덮어쓴다.
   */
  @Transactional
  public void updateServiceTermAgreements(
      final Long userId,
      final List<ServiceTermAgreement> agreements,
      final String marketingTermTag) {
    User user = getUser(userId);

    for (ServiceTermAgreement agreement : agreements) {
      userServiceTermRepository
          .findByUserIdAndTag(userId, agreement.tag())
          .ifPresentOrElse(
              term -> term.update(agreement.agreed(), agreement.agreedAt()),
              () ->
                  userServiceTermRepository.save(
                      UserServiceTerm.of(
                          userId, agreement.tag(), agreement.agreed(), agreement.agreedAt())));

      if (agreement.tag().equals(marketingTermTag)) {
        // 광고성 수신 동의 증적은 카카오 동의창의 실제 동의 시각을 남긴다 (2년 주기 재확인 기산점).
        Instant marketingAgreedAt =
            agreement.agreedAt() != null ? agreement.agreedAt() : Instant.now(clock);
        user.updateMarketingAgreement(agreement.agreed(), marketingAgreedAt);
      }
    }
  }

  private User createNewSocialUser(
      final SocialInfo socialInfo,
      final String email,
      final String name,
      final String phoneNumber,
      final String ageRange) {
    User newUser =
        User.create(
            socialInfo.provider().name(),
            socialInfo.providerId(),
            email,
            nicknameGenerator.generate());
    if (name != null) {
      newUser.updateName(name);
    }
    // 동의창에서 전화번호까지 받은 경우 profileCompleted 로 전이되어 추가정보 화면 없이 가입이 완결된다.
    if (phoneNumber != null) {
      newUser.updatePhoneNumber(phoneNumber);
    }
    newUser.updateAgeRange(ageRange);
    return userRepository.save(newUser);
  }
}
