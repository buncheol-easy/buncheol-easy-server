package buncheoleasy.global.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;

@MappedSuperclass
@Getter
public abstract class TimestampedEntity extends CreatedAtEntity {

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  protected void onCreate() {
    super.onCreate();
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
