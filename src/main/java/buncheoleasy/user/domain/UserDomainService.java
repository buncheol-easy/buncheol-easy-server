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
      final String ageRange,
      final boolean ageRangeWithdrawn) {
    return userRepository
        .findBySocialInfo(socialInfo)
        .map(user -> refreshAgeRange(user, ageRange, ageRangeWithdrawn))
        .orElseGet(() -> createNewSocialUser(socialInfo, email, name, phoneNumber, ageRange));
  }

  /**
   * 재로그인 시 연령대를 카카오 최신 상태로 맞춘다 — 철회 확정 신호가 오면 지체 없이 파기(PIPA), 새 값이 오면 갱신, 신호가 없으면(미동의였던 그대로·조회
   * 실패) 기존 값을 유지한다.
   */
  private User refreshAgeRange(
      final User user, final String ageRange, final boolean ageRangeWithdrawn) {
    if (ageRangeWithdrawn) {
      user.clearAgeRange();
    } else if (ageRange != null) {
      user.updateAgeRange(ageRange);
    }
    return user;
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

  /** 던지지 않는 정산 계좌 등록 여부 — 개최 자격 사전 조회용(docs/53 Q-07). 개최 시점 검사는 {@link #requireBankAccountRegistered}. */
  public boolean hasBankAccount(final Long id) {
    return getUser(id).getBankAccount() != null;
  }

  public void requireBankAccountRegistered(final Long id) {
    getUser(id).requireBankAccount();
  }

  public void requireProfileCompleted(final Long id) {
    getUser(id).requireProfileCompleted();
  }

  public boolean canHost(final Long id) {
    return getUser(id).isCanHost();
  }

  /**
   * C2C 개최 자격 판정 (docs/46 §7.1-8) — 연락처(가입 완료 = 전화번호 보유)와 성인 확인. 이메일은 전 회원 필수 수집이라 별도 검사가 없다. 연령대
   * 미보유(카카오 재동의로 해결 — USR-032)와 미성년 확정(차단 — USR-033)을 구분한다.
   *
   * <p>던지지 않는 판정이라 개최 폼 진입 전 사전 조회에도 그대로 쓴다(docs/53 Q-07). 제출 시점 게이트는 {@link
   * #requireC2cHostQualification} 이 이 결과를 예외로 바꾼다 — 두 경로의 판정이 갈리지 않게 하기 위함이다.
   */
  public C2cHostQualification evaluateC2cHostQualification(final Long id) {
    User user = getUser(id);
    if (!user.isProfileCompleted()) {
      return C2cHostQualification.PHONE_REQUIRED;
    }
    if (user.getAgeRange() == null) {
      return C2cHostQualification.AGE_UNVERIFIED;
    }
    if (!user.isVerifiedAdult()) {
      return C2cHostQualification.NOT_ADULT;
    }
    return C2cHostQualification.QUALIFIED;
  }

  /**
   * C2C 개최 자격 게이트 — 자격 미달이면 사유별 에러코드로 던진다. 매핑을 여기 switch 로 두면 사유가 추가될 때 컴파일 에러로 잡히고, 응답 사유 매핑({@code
   * HostingEligibilityResponse#from})과 대칭이 된다.
   */
  public void requireC2cHostQualification(final Long id) {
    C2cHostQualification qualification = evaluateC2cHostQualification(id);
    switch (qualification) {
      case QUALIFIED -> {}
      case PHONE_REQUIRED ->
          throw new BusinessException(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE);
      case AGE_UNVERIFIED -> throw new BusinessException(ErrorCode.USER_AGE_NOT_VERIFIED);
      case NOT_ADULT -> throw new BusinessException(ErrorCode.USER_NOT_ADULT);
    }
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
    if (ageRange != null) {
      newUser.updateAgeRange(ageRange);
    }
    return userRepository.save(newUser);
  }
}
