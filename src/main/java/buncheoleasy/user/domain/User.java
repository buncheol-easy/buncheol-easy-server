package buncheoleasy.user.domain;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends TimestampedEntity {

  private static final String NICKNAME_PREFIX = "Guest";
  private static final int RANDOM_SUFFIX_LENGTH = 10;
  private static final int ADULT_AGE_LOWER_BOUND = 20;
  private static final int AGE_RANGE_MAX_LENGTH = 10;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Embedded private SocialInfo socialInfo;

  @Convert(converter = EmailConverter.class)
  @Column(name = "email", nullable = false, length = 320)
  private Email email;

  @Convert(converter = NicknameConverter.class)
  @Column(name = "nickname", nullable = false, length = 20)
  private Nickname nickname;

  // 실명. 입금내역 대조·배송 연락 시 참조용. 기존 회원은 NULL 이며 마이페이지에서 수시 입력한다.
  @Column(name = "name", length = 30)
  private String name;

  @Convert(converter = PhoneNumberConverter.class)
  @Column(name = "phone_number", length = 15)
  private PhoneNumber phoneNumber;

  // 분철 개최 시 정산받을 계좌. 분철을 개최할 때만 필수이며, 마이페이지에서 별도로 등록·수정한다.
  @Embedded private BankAccount bankAccount;

  // 프로필 설정 완료 여부. 첫 phoneNumber 등록 시 true 로 전이. 소셜 가입 직후엔 false (전화번호 미입력 상태).
  @Column(name = "profile_completed", nullable = false)
  private boolean profileCompleted;

  // 분철 개최 허용 여부. 개최 오픈 전이라 운영이 지정한 계정만 true (부여는 현재 DB 직접 UPDATE).
  @Column(name = "can_host", nullable = false)
  private boolean canHost;

  // 마케팅 정보 수신 동의 일시. NULL 이면 미동의(또는 철회).
  // 광고성 정보 수신동의는 동의 일시 기록·2년 주기 재확인 의무가 있어 boolean 대신 일시로 저장한다.
  @Column(name = "marketing_agreed_at")
  private Instant marketingAgreedAt;

  // 카카오 연령대(선택 동의, 예: "20~29"). 개최자 성인 확인 참조용. NULL 이면 미동의/미수집.
  // 카카오계정 생일 기반이라 본인인증 수준은 아니다 (docs/50).
  @Column(name = "age_range", length = 10)
  private String ageRange;

  // 회원 탈퇴 soft delete 시각. NULL 이면 활성 유저. @SQLRestriction 으로 모든 조회에서 자동 제외.
  @Column(name = "deleted_at")
  private Instant deletedAt;

  public static User create(final String provider, final String providerId, final String email) {
    return new User(
        SocialInfo.of(provider, providerId),
        Email.of(email),
        Nickname.of(generateRandomNickname()));
  }

  /** 닉네임을 지정해 생성한다 (카카오싱크 전환 후 기본 조합값 닉네임 — RandomNicknameGenerator 산출값). */
  public static User create(
      final String provider, final String providerId, final String email, final String nickname) {
    return new User(SocialInfo.of(provider, providerId), Email.of(email), Nickname.of(nickname));
  }

  private User(final SocialInfo socialInfo, final Email email, final Nickname nickname) {
    this.socialInfo = socialInfo;
    this.email = email;
    this.nickname = nickname;
    this.profileCompleted = false;
    this.canHost = false;
  }

  private static String generateRandomNickname() {
    String cleanUuid = UUID.randomUUID().toString().replace("-", "");
    String uniqueSuffix = cleanUuid.substring(0, RANDOM_SUFFIX_LENGTH);
    return NICKNAME_PREFIX + uniqueSuffix;
  }

  public void updatePhoneNumber(final String newValue) {
    PhoneNumber newPhoneNumber = PhoneNumber.of(newValue);

    boolean wasNull = (this.phoneNumber == null);
    this.phoneNumber = newPhoneNumber;

    // 최초 전화번호 설정 시에만 profileCompleted를 true로 변경
    if (wasNull && !this.profileCompleted) {
      this.profileCompleted = true;
    }
  }

  public void updateNickname(final String newValue) {
    this.nickname = Nickname.of(newValue);
  }

  public void updateName(final String newValue) {
    this.name = newValue;
  }

  public void updateBankAccount(final String bank, final String account, final String holder) {
    this.bankAccount = BankAccount.of(bank, account, holder);
  }

  // null/blank 는 무시한다 — 카카오 보강 조회 실패나 미동의 재로그인이 기존 값을 지우면 안 된다.
  // 컬럼 길이(VARCHAR(10)) 초과 값도 무시 — 이 경로는 try/catch 밖이라 커밋 실패가 로그인 자체를 깨기 때문.
  public void updateAgeRange(final String newValue) {
    if (newValue != null && !newValue.isBlank() && newValue.length() <= AGE_RANGE_MAX_LENGTH) {
      this.ageRange = newValue;
    }
  }

  /** 카카오에서 연령대 동의 철회가 확정 신호로 확인된 경우 지체 없이 파기한다 (PIPA). */
  public void clearAgeRange() {
    this.ageRange = null;
  }

  /**
   * 성인 확인 여부 — 연령대 구간 하한이 20 이상일 때만 true. "15~19" 구간은 만 19세 성인을 구분할 수 없어 보수적으로 false 처리한다(만 19세
   * 개최 배제 감수 — docs/50 결정).
   */
  public boolean isVerifiedAdult() {
    if (this.ageRange == null) {
      return false;
    }
    int tildeIndex = this.ageRange.indexOf('~');
    if (tildeIndex <= 0) {
      return false;
    }
    try {
      return Integer.parseInt(this.ageRange.substring(0, tildeIndex).trim())
          >= ADULT_AGE_LOWER_BOUND;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  // 같은 상태 재요청은 no-op 으로 두어 최초 동의 일시를 보존한다.
  public void updateMarketingAgreement(final boolean agreed, final Instant now) {
    if (agreed == (this.marketingAgreedAt != null)) {
      return;
    }
    this.marketingAgreedAt = agreed ? now : null;
  }

  public void requireBankAccount() {
    if (this.bankAccount == null) {
      throw new BusinessException(ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED);
    }
  }

  public void requireProfileCompleted() {
    if (!this.profileCompleted) {
      throw new BusinessException(ErrorCode.USER_PROFILE_IS_NOT_COMPLETE);
    }
  }

  public void allowHosting() {
    this.canHost = true;
  }

  public void requireCanHost() {
    if (!this.canHost) {
      throw new BusinessException(ErrorCode.USER_CANNOT_HOST);
    }
  }

  // TODO: 탈퇴 정책 보강 필요.
  //  1) 활성 분철(RECRUITING~SETTLING)·미정산 결제가 있으면 탈퇴 거부 (애플리케이션 레이어에서 검증).
  //  2) 모든 거래가 종료된 뒤 PII(phoneNumber·bankAccount·email·ageRange) 를 NULL/해시로 익명화.
  //  PIPA 상 탈퇴 시 즉시 파기가 원칙이나, 결제 기록(전자상거래법 5년)은 분리 보관해야 한다.
  public void withdraw(final Instant now) {
    this.deletedAt = now;
  }
}
