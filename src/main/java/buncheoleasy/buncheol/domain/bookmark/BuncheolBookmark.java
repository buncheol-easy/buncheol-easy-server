package buncheoleasy.buncheol.domain.bookmark;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "buncheol_bookmarks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BuncheolBookmark {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public static BuncheolBookmark create(final Long userId, final Long buncheolId) {
    return new BuncheolBookmark(userId, buncheolId);
  }

  private BuncheolBookmark(final Long userId, final Long buncheolId) {
    this.userId = userId;
    this.buncheolId = buncheolId;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = LocalDateTime.now();
  }
}
