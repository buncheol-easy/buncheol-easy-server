package buncheoleasy.group.domain.alias;

import buncheoleasy.global.domain.TimestampedEntity;
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
 * 그룹의 대체 표기. 공식 그룹명 하나로는 잡히지 않는 검색어를 그룹에 이어준다 (예: {@code IVE} ← "아이브", {@code 프로미스나인} ←
 * "fromis_9", {@code (여자)아이들} ← "idle").
 */
@Entity
@Table(name = "group_aliases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupAlias extends TimestampedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long groupId;

  @Column(nullable = false, length = 100)
  private String alias;

  /**
   * {@code alias} 의 검색용 정규화본. DB 생성 컬럼이라 애플리케이션은 읽기만 한다. 정규화 규칙은 {@code schema.sql} 과 {@link
   * buncheoleasy.global.query.SearchText} 참고.
   */
  @Column(name = "search_alias", insertable = false, updatable = false, length = 100)
  private String searchAlias;

  // searchAlias 는 DB 가 계산하므로 생성자에서 받지 않는다 (Group 과 같은 이유).
  public GroupAlias(final Long id, final Long groupId, final String alias) {
    this.id = id;
    this.groupId = groupId;
    this.alias = alias;
  }
}
