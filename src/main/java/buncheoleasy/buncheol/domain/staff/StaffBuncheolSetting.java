package buncheoleasy.buncheol.domain.staff;

import buncheoleasy.global.domain.TimestampedEntity;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 운영진 분철에만 있는 설정. 행이 없는 분철은 등록하자마자 열린다.
 *
 * <p>id 를 분철 id 로 직접 넣는다. {@link Persistable} 로 새 행임을 알려 저장이 merge(SELECT 후 INSERT) 대신 바로 INSERT
 * 로 가게 한다 — 같은 분철에 두 번 저장하면 조용히 덮어쓰지 않고 PK 위반으로 실패한다.
 */
@Entity
@Table(name = "staff_buncheol_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffBuncheolSetting extends TimestampedEntity implements Persistable<Long> {

  @Id
  @Column(name = "buncheol_id", nullable = false, updatable = false)
  private Long buncheolId;

  @Column(name = "opens_at", nullable = false, updatable = false)
  private Instant opensAt;

  @Transient
  @Getter(AccessLevel.NONE)
  private boolean newRow = true;

  public static StaffBuncheolSetting create(
      final Long buncheolId, final Instant opensAt, final Instant deadline, final Instant now) {
    Instant validOpensAt = validateOpensAt(opensAt, deadline, now);
    StaffBuncheolSetting setting = new StaffBuncheolSetting();
    setting.buncheolId = buncheolId;
    setting.opensAt = validOpensAt;
    return setting;
  }

  /**
   * 오픈 시각을 초 단위로 내린 값을 돌려준다. 칼럼이 DATETIME(초)이고 참여 INSERT 가 {@code UTC_TIMESTAMP()}(초)와 비교하므로, 앱
   * 판정({@link #isBeforeOpen})도 같은 초 값을 봐야 정각 판정이 갈리지 않는다.
   */
  public static Instant validateOpensAt(
      final Instant opensAt, final Instant deadline, final Instant now) {
    if (opensAt == null || deadline == null || now == null) {
      throw new BusinessException(ErrorCode.BUNCHEOL_OPENS_AT_INVALID);
    }
    Instant truncated = opensAt.truncatedTo(ChronoUnit.SECONDS);
    if (!truncated.isAfter(now) || !truncated.isBefore(deadline)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_OPENS_AT_INVALID);
    }
    return truncated;
  }

  /** 정각이면 열린 것으로 본다 — 참여 INSERT 의 {@code opens_at > UTC_TIMESTAMP()} 와 같은 경계다. */
  public boolean isBeforeOpen(final Instant now) {
    return now.isBefore(opensAt);
  }

  @Override
  public Long getId() {
    return buncheolId;
  }

  @Override
  public boolean isNew() {
    return newRow;
  }

  @PostPersist
  @PostLoad
  void markStored() {
    this.newRow = false;
  }
}
