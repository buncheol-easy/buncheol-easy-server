package buncheoleasy.group.domain;

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
@Table(name = "`groups`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group extends TimestampedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 500)
  private String image;

  /**
   * {@code name} 의 검색용 정규화본. DB 생성 컬럼이라 애플리케이션은 읽기만 한다 ({@code insertable=false,
   * updatable=false}). 정규화 규칙은 {@code schema.sql} 과 {@link buncheoleasy.global.query.SearchText} 참고.
   */
  @Column(name = "search_name", insertable = false, updatable = false, length = 100)
  private String searchName;

  // searchName 은 DB 가 계산하므로 생성자에서 받지 않는다 (@AllArgsConstructor 를 쓰면 호출 측이 직접 넘기게 돼 실제 저장값과 어긋난다).
  public Group(final Long id, final String name, final String image) {
    this.id = id;
    this.name = name;
    this.image = image;
  }
}
