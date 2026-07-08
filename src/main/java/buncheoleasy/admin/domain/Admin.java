package buncheoleasy.admin.domain;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 계정. 서비스 유저(users)와 무관한 독립 ID/PW 계정이며, 권한 등급은 {@link AdminRole} 로 구분한다.
 *
 * <p>계정 생성은 배포 환경변수 부트스트랩({@code AdminAccountInitializer}) 또는 운영자의 직접 INSERT(BCrypt 해시)로 한다.
 * {@code password} 는 항상 인코딩된 해시만 보관한다 — 원문 인코딩은 애플리케이션 레이어({@code PasswordEncoder})의 책임.
 */
@Entity
@Table(name = "admins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends TimestampedEntity {

  private static final int LOGIN_ID_MAX_LENGTH = 50;
  private static final int PASSWORD_MAX_LENGTH = 100;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "login_id", nullable = false, updatable = false, length = 50, unique = true)
  private String loginId;

  // BCrypt 해시 (60자). 원문 비밀번호는 절대 저장하지 않는다.
  @Column(nullable = false, length = 100)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AdminRole role;

  public static Admin create(
      final String loginId, final String encodedPassword, final AdminRole role) {
    return new Admin(loginId, encodedPassword, role);
  }

  private Admin(final String loginId, final String encodedPassword, final AdminRole role) {
    validate(loginId, encodedPassword, role);
    this.loginId = loginId;
    this.password = encodedPassword;
    this.role = role;
  }

  private void validate(final String loginId, final String encodedPassword, final AdminRole role) {
    if (loginId == null
        || loginId.isBlank()
        || loginId.length() > LOGIN_ID_MAX_LENGTH
        || encodedPassword == null
        || encodedPassword.isBlank()
        || encodedPassword.length() > PASSWORD_MAX_LENGTH
        || role == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
  }
}
