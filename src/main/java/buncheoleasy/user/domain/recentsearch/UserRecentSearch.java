package buncheoleasy.user.domain.recentsearch;

import buncheoleasy.global.domain.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자별 최근 검색 이력 1건.
 *
 * <p>프론트가 그룹·멤버 name → id 변환을 책임지므로 서버는 검색 요청의 (keyword|groupId|memberId) 중 무엇이 와도 최종적으로 사용자가 친
 * 텍스트 1개로 normalize 해 저장한다. 따라서 엔티티에는 type 디스크리미네이터나 ref id 컬럼을 두지 않는다.
 */
@Entity
@Table(name = "user_recent_searches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRecentSearch extends CreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "keyword", nullable = false, updatable = false, length = 100)
  private String keyword;

  public static UserRecentSearch create(final Long userId, final String keyword) {
    return new UserRecentSearch(userId, keyword);
  }

  private UserRecentSearch(final Long userId, final String keyword) {
    this.userId = userId;
    this.keyword = keyword;
  }
}
