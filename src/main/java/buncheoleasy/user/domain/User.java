package buncheoleasy.user.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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
public class User {

  private static final String NICKNAME_PREFIX = "Guest";
  private static final int RANDOM_SUFFIX_LENGTH = 10;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Embedded
  @AttributeOverride(
      name = "provider",
      column = @Column(name = "provider", nullable = false, length = 20))
  @AttributeOverride(
      name = "providerId",
      column = @Column(name = "provider_id", nullable = false, length = 100))
  private SocialInfo socialInfo;

  @Convert(converter = EmailConverter.class)
  @Column(name = "email", nullable = false, length = 320)
  private Email email;

  @Convert(converter = NicknameConverter.class)
  @Column(name = "nickname", nullable = false, length = 20)
  private Nickname nickname;

  @Convert(converter = PhoneNumberConverter.class)
  @Column(name = "phone_number", length = 15)
  private PhoneNumber phoneNumber;

  // 프로필 설정 완료 여부. 첫 phoneNumber 등록 시 true 로 전이. 소셜 가입 직후엔 false (전화번호 미입력 상태).
  @Column(name = "profile_completed", nullable = false)
  private boolean profileCompleted;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // 회원 탈퇴 soft delete 시각. NULL 이면 활성 유저. @SQLRestriction 으로 모든 조회에서 자동 제외.
  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public static User create(final String provider, final String providerId, final String email) {
    User user = new User();
    user.socialInfo = SocialInfo.of(provider, providerId);
    user.email = Email.of(email);
    user.nickname = Nickname.of(generateRandomNickname());
    user.profileCompleted = false;
    return user;
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

  public void withdraw() {
    this.deletedAt = LocalDateTime.now();
  }

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
