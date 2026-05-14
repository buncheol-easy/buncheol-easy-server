package buncheoleasy.user.domain.favorite;

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
@Table(name = "user_favorite_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFavoriteGroup extends CreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "group_id", nullable = false, updatable = false)
  private Long groupId;

  public static UserFavoriteGroup create(final Long userId, final Long groupId) {
    return new UserFavoriteGroup(userId, groupId);
  }

  private UserFavoriteGroup(final Long userId, final Long groupId) {
    this.userId = userId;
    this.groupId = groupId;
  }
}
