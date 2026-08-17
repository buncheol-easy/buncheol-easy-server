package buncheoleasy.group.domain.member;

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

@Entity
@Table(name = "group_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMember extends TimestampedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "group_id", nullable = false)
  private Long groupId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 500)
  private String image;

  /**
   * {@code name} 의 검색용 정규화본. DB 생성 컬럼이라 애플리케이션은 읽기만 한다. {@link
   * buncheoleasy.group.domain.Group#getSearchName()} 참고.
   */
  @Column(name = "search_name", insertable = false, updatable = false, length = 100)
  private String searchName;

  // searchName 은 DB 가 계산하므로 생성자에서 받지 않는다.
  public GroupMember(final Long id, final Long groupId, final String name, final String image) {
    this.id = id;
    this.groupId = groupId;
    this.name = name;
    this.image = image;
  }
}
