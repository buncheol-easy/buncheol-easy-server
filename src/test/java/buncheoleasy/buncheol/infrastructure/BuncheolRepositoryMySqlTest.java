package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.support.MySqlContainerTestSupport;
import buncheoleasy.support.MySqlFixture;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 분철 저장소 쿼리를 실제 MySQL 문법·서브쿼리 규칙(error 1093)에서 돌린다. */
@DisplayName("분철 저장소 쿼리 (MySQL)")
class BuncheolRepositoryMySqlTest extends MySqlContainerTestSupport {

  private static final Duration HOUR = Duration.ofHours(1);

  @Autowired private BuncheolRepository buncheolRepository;

  private MySqlFixture fixture;
  private long hostId;
  private long groupId;

  @BeforeEach
  void setUp() {
    fixture = new MySqlFixture(jdbcTemplate);
    hostId = fixture.user(false);
    groupId = fixture.group();
  }

  private long buncheol(final String flowType, final String status, final Duration deadline) {
    return fixture.buncheol(hostId, groupId, flowType, status, deadline, 1, null);
  }

  @Nested
  @DisplayName("마감 폴링 findRecruitingIdsPastDeadline")
  class DeadlinePolling {

    // 운영진 분철 마감 판정이 이 쿼리를 함께 쓴다 — 회원 개최 분기를 바꿔도 운영진 마감 분철은 그대로 뽑혀야 한다.
    @Test
    void 마감이_지난_모집_중_분철을_운영진과_회원_개최_모두_마감_순으로_뽑는다() {
      long staffExpired = buncheol("LEGACY", "RECRUITING", HOUR.multipliedBy(-2));
      long memberExpired = buncheol("C2C", "RECRUITING", HOUR.negated());
      buncheol("LEGACY", "RECRUITING", HOUR);
      buncheol("C2C", "RECRUITING", HOUR);

      assertThat(buncheolRepository.findRecruitingIdsPastDeadline(Instant.now(), 100))
          .containsExactly(staffExpired, memberExpired);
    }

    // 옛 규칙은 회원 개최를 마감 + 48시간까지 기다렸다. 이제 마감 즉시 입금 단계 시작이나 미달 취소로 간다.
    @Test
    void 회원_개최는_마감이_막_지나도_뽑는다() {
      long justExpired = buncheol("C2C", "RECRUITING", Duration.ofMinutes(-1));

      assertThat(buncheolRepository.findRecruitingIdsPastDeadline(Instant.now(), 100))
          .containsExactly(justExpired);
    }

    @Test
    void 모집_중이_아닌_분철은_마감이_지나도_뽑지_않는다() {
      buncheol("C2C", "PAYMENT_COLLECTING", HOUR.negated());
      buncheol("LEGACY", "CONFIRMED", HOUR.negated());
      buncheol("C2C", "CANCELLED", HOUR.negated());

      assertThat(buncheolRepository.findRecruitingIdsPastDeadline(Instant.now(), 100)).isEmpty();
    }

    @Test
    void 배치_상한까지만_뽑는다() {
      long first = buncheol("C2C", "RECRUITING", HOUR.multipliedBy(-3));
      long second = buncheol("LEGACY", "RECRUITING", HOUR.multipliedBy(-2));
      buncheol("C2C", "RECRUITING", HOUR.negated());

      assertThat(buncheolRepository.findRecruitingIdsPastDeadline(Instant.now(), 2))
          .containsExactly(first, second);
    }
  }
}
