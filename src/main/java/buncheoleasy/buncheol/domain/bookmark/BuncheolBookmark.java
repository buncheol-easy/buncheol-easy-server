package buncheoleasy.buncheol.domain.bookmark;

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

@Entity
@Table(name = "buncheol_bookmarks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuncheolBookmark extends CreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  public static BuncheolBookmark create(final Long userId, final Long buncheolId) {
    return new BuncheolBookmark(userId, buncheolId);
  }

  private BuncheolBookmark(final Long userId, final Long buncheolId) {
    this.userId = userId;
    this.buncheolId = buncheolId;
  }
}
