package buncheoleasy.group.domain.popularity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 외부 공개 순위에서 옮겨 온 멤버 인기 순위. 개최 화면이 멤버별 가격을 차등해 미리 채울 때만 쓴다.
 *
 * <p>행이 없는 멤버는 "순위 밖"이다. 애플리케이션 쓰기 경로가 없고 SQL 로 직접 넣는다(그룹·멤버 시드와 같다).
 */
@Entity
@Table(name = "group_member_popularity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMemberPopularity {

  @Id
  @Column(name = "member_id")
  private Long memberId;

  /** 출처 순위표 안의 순위. 1 이 가장 높다. */
  @Column(name = "popularity_rank", nullable = false)
  private int popularityRank;

  @Column(nullable = false, length = 100)
  private String source;

  public GroupMemberPopularity(final Long memberId, final int popularityRank, final String source) {
    this.memberId = memberId;
    this.popularityRank = popularityRank;
    this.source = source;
  }
}
