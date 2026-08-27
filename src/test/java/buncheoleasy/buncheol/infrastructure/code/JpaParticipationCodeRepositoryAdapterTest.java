package buncheoleasy.buncheol.infrastructure.code;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.code.ParticipationCode;
import buncheoleasy.buncheol.domain.code.ParticipationCodeRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.buncheol.domain.member.BuncheolMemberRepository;
import buncheoleasy.buncheol.domain.member.BuncheolMemberAccessType;
import buncheoleasy.buncheol.infrastructure.TestGroupFixture;
import buncheoleasy.buncheol.infrastructure.TestUserFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("JpaParticipationCodeRepositoryAdapter 테스트")
class JpaParticipationCodeRepositoryAdapterTest {

  @Autowired private ParticipationCodeRepository participationCodeRepository;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private BuncheolMemberRepository buncheolMemberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long buncheolId;
  private Long slotId;
  private Long otherSlotId;
  private Instant now;

  @BeforeEach
  void setUp() {
    Long hostId = TestUserFixture.insertUser(jdbcTemplate, "host-code");
    Long groupId = TestGroupFixture.insertGroup(jdbcTemplate, "코드 그룹");
    Long groupMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "정원");
    Long groupMemberId2 = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "제이");

    now = Instant.now();
    Instant deadline = now.plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(
                groupId, "제목", null, "스토어명", deadline, 1, 2300, 2300, FlowType.LEGACY, null),
            now);
    buncheolRepository.save(buncheol);
    buncheolId = buncheol.getId();

    BuncheolMember slot =
        BuncheolMember.create(buncheolId, groupMemberId, 0L, BuncheolMemberAccessType.CODE_ONLY);
    BuncheolMember otherSlot =
        BuncheolMember.create(buncheolId, groupMemberId2, 0L, BuncheolMemberAccessType.CODE_ONLY);
    buncheolMemberRepository.saveAll(List.of(slot, otherSlot));
    em.flush();
    em.clear();
    slotId = slot.getId();
    otherSlotId = otherSlot.getId();
  }

  private ParticipationCode issue(final String code, final Long targetSlotId) {
    return participationCodeRepository.save(
        ParticipationCode.issue(
            code, buncheolId, targetSlotId, "@supporter", now.plus(Duration.ofHours(48)), now));
  }

  @Nested
  @DisplayName("슬롯별 미사용 코드 조회·일괄 폐기")
  class OutstandingTest {

    @Test
    void 미사용_미폐기_코드를_최신순으로_돌려준다() {
      issue("AAAA1111", slotId);
      issue("BBBB2222", slotId);
      em.flush();
      em.clear();

      assertThat(participationCodeRepository.findOutstandingByBuncheolMemberId(slotId))
          .extracting(ParticipationCode::getCode)
          .containsExactly("BBBB2222", "AAAA1111");
    }

    @Test
    void 사용되거나_폐기된_코드는_제외한다() {
      ParticipationCode used = issue("AAAA1111", slotId);
      ParticipationCode revoked = issue("BBBB2222", slotId);
      issue("CCCC3333", slotId);
      em.flush();

      participationCodeRepository.markUsedIfRedeemable(used.getId(), 500L, now);
      participationCodeRepository.revokeIfActive(revoked.getId(), now);
      em.flush();
      em.clear();

      assertThat(participationCodeRepository.findOutstandingByBuncheolMemberId(slotId))
          .extracting(ParticipationCode::getCode)
          .containsExactly("CCCC3333");
    }

    @Test
    void 다른_슬롯의_코드는_포함하지_않는다() {
      issue("AAAA1111", slotId);
      issue("BBBB2222", otherSlotId);
      em.flush();
      em.clear();

      assertThat(participationCodeRepository.findOutstandingByBuncheolMemberId(slotId))
          .extracting(ParticipationCode::getCode)
          .containsExactly("AAAA1111");
    }

    @Test
    void 일괄_폐기는_해당_슬롯의_미사용_코드를_모두_닫는다() {
      issue("AAAA1111", slotId);
      issue("BBBB2222", slotId);
      issue("CCCC3333", otherSlotId);
      em.flush();
      em.clear();

      assertThat(participationCodeRepository.revokeOutstandingByBuncheolMemberId(slotId, now))
          .isEqualTo(2);

      assertThat(participationCodeRepository.findOutstandingByBuncheolMemberId(slotId)).isEmpty();
      assertThat(participationCodeRepository.findOutstandingByBuncheolMemberId(otherSlotId))
          .hasSize(1);
    }

    @Test
    void 일괄_폐기는_이미_사용된_코드를_건드리지_않는다() {
      ParticipationCode used = issue("AAAA1111", slotId);
      em.flush();
      participationCodeRepository.markUsedIfRedeemable(used.getId(), 500L, now);
      em.flush();
      em.clear();

      assertThat(participationCodeRepository.revokeOutstandingByBuncheolMemberId(slotId, now))
          .isZero();
      assertThat(participationCodeRepository.findById(used.getId()).orElseThrow().getRevokedAt())
          .isNull();
    }
  }

  @Nested
  @DisplayName("소모 CAS 테스트")
  class MarkUsedTest {

    @Test
    void 미사용_미폐기_기한내면_소모에_성공한다() {
      ParticipationCode code = issue("AAAA1111", slotId);
      em.flush();
      em.clear();

      assertThat(participationCodeRepository.markUsedIfRedeemable(code.getId(), 500L, now))
          .isTrue();

      ParticipationCode reloaded = participationCodeRepository.findById(code.getId()).orElseThrow();
      assertThat(reloaded.getUsedAt()).isNotNull();
      assertThat(reloaded.getUsedParticipationId()).isEqualTo(500L);
    }

    // 같은 코드로 동시 참여가 들어와도 한쪽만 성공한다는 보장.
    @Test
    void 이미_소모된_코드는_두_번째_소모에_실패한다() {
      ParticipationCode code = issue("AAAA1111", slotId);
      em.flush();
      em.clear();

      assertThat(participationCodeRepository.markUsedIfRedeemable(code.getId(), 500L, now))
          .isTrue();
      assertThat(participationCodeRepository.markUsedIfRedeemable(code.getId(), 501L, now))
          .isFalse();
    }

    @Test
    void 기한이_지난_코드는_소모되지_않는다() {
      ParticipationCode code = issue("AAAA1111", slotId);
      em.flush();
      em.clear();

      assertThat(
              participationCodeRepository.markUsedIfRedeemable(
                  code.getId(), 500L, now.plus(Duration.ofHours(49))))
          .isFalse();
    }

    @Test
    void 폐기된_코드는_소모되지_않는다() {
      ParticipationCode code = issue("AAAA1111", slotId);
      em.flush();
      em.clear();

      assertThat(participationCodeRepository.revokeIfActive(code.getId(), now)).isTrue();
      assertThat(participationCodeRepository.markUsedIfRedeemable(code.getId(), 500L, now))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("조회 테스트")
  class FindTest {

    @Test
    void 코드_문자열로_조회한다() {
      issue("AAAA1111", slotId);
      em.flush();
      em.clear();

      assertThat(participationCodeRepository.findByCode("AAAA1111")).isPresent();
      assertThat(participationCodeRepository.findByCode("BBBB2222")).isEmpty();
    }
  }
}
