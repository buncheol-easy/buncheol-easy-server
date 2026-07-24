package buncheoleasy.user.domain.serviceterm;

import buncheoleasy.global.domain.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 서비스 약관 동의 내역. 카카오 간편가입 동의창 통과 시 동의 내역 조회 API 로 받아 저장한다 — 필수 약관 동의가 서버에 남지 않던 문제(18 §2)의 해소
 * 지점. (user_id, tag) 당 1행이며 재로그인 시 최신 상태로 갱신한다.
 */
@Entity
@Table(name = "user_service_terms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserServiceTerm extends TimestampedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "tag", nullable = false, length = 100)
  private String tag;

  @Column(name = "agreed", nullable = false)
  private boolean agreed;

  @Column(name = "agreed_at")
  private Instant agreedAt;

  public static UserServiceTerm of(
      final Long userId, final String tag, final boolean agreed, final Instant agreedAt) {
    UserServiceTerm term = new UserServiceTerm();
    term.userId = userId;
    term.tag = tag;
    term.agreed = agreed;
    term.agreedAt = agreedAt;
    return term;
  }

  public void update(final boolean agreed, final Instant agreedAt) {
    this.agreed = agreed;
    this.agreedAt = agreedAt;
  }
}
