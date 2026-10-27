package buncheoleasy.push.domain;

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
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱 푸시를 받을 기기 하나. 기기(토큰)마다 한 행이고, 같은 기기로 다른 계정에 로그인하면 주인이 바뀐다 — 기기는 마지막에 로그인한 계정의 알림만 받는다.
 *
 * <p>행은 토큰 기준 upsert({@link PushDeviceRepository#upsert})로만 쓴다. 이 엔티티의 {@link #create} 는 그 입력을 검증·정규화하는 값으로 쓰인다.
 */
@Entity
@Table(name = "push_devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice extends TimestampedEntity {

  // APNs 토큰은 지금 32바이트(hex 64자)지만 애플은 길이를 고정하지 않는다고 밝혀, 표 열 길이(200)까지 열어 둔다.
  private static final Pattern TOKEN_FORMAT = Pattern.compile("[0-9a-f]{64,200}");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private PushPlatform platform;

  @Column(nullable = false, length = 200, updatable = false)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private PushEnvironment environment;

  public static PushDevice create(
      final Long userId,
      final PushPlatform platform,
      final String token,
      final PushEnvironment environment) {
    return new PushDevice(userId, platform, normalizeToken(token), environment);
  }

  /**
   * 토큰은 소문자로 모은다. 운영 MySQL 유니크 인덱스(utf8mb4_unicode_ci)는 대소문자를 같은 값으로 보지만 H2·자바 비교는 다르게 봐서, 정규화하지 않으면
   * 대소문자만 다른 토큰의 삭제·조회가 환경마다 다르게 맞는다.
   */
  public static String normalizeToken(final String token) {
    return token == null ? null : token.strip().toLowerCase(Locale.ROOT);
  }

  private PushDevice(
      final Long userId,
      final PushPlatform platform,
      final String token,
      final PushEnvironment environment) {
    if (userId == null || platform == null || environment == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
    if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
    this.userId = userId;
    this.platform = platform;
    this.token = token;
    this.environment = environment;
  }
}
