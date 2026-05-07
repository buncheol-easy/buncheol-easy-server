package buncheoleasy.user.domain.favorite;

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
@Table(name = "user_favorite_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFavoriteGroup {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "group_id", nullable = false, updatable = false)
  private Long groupId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public static UserFavoriteGroup create(final Long userId, final Long groupId) {
    return new UserFavoriteGroup(userId, groupId);
  }

  private UserFavoriteGroup(final Long userId, final Long groupId) {
    this.userId = userId;
    this.groupId = groupId;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = LocalDateTime.now();
  }
}
