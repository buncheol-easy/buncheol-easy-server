package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
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
@DisplayName("JpaBuncheolRepositoryAdapter 테스트")
class JpaBuncheolRepositoryAdapterTest {

  @Autowired private BuncheolRepository buncheolRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private Long hostId;
  private Long groupId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "host123");
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "테스트 그룹 마스터");
  }

  private BuncheolParams validParams() {
    return new BuncheolParams(
        groupId,
        "테스트 분철 제목",
        "분철 설명입니다.",
        "공식 스토어",
        LocalDateTime.now().plusDays(7),
        7,
        3000,
        null);
  }

  private Buncheol persistAndDetach(Buncheol buncheol) {
    buncheolRepository.save(buncheol);
    em.flush();
    em.clear();
    return buncheol;
  }

  @Nested
  @DisplayName("분철 저장 테스트")
  class SaveTest {

    @Test
    void 분철을_저장하면_ID가_할당된다() {
      Buncheol buncheol = Buncheol.create(hostId, validParams());

      buncheolRepository.save(buncheol);
      em.flush();

      assertThat(buncheol.getId()).isNotNull();
      assertThat(buncheol.getId()).isPositive();
    }

    @Test
    void 저장된_분철의_초기_상태는_RECRUITING이다() {
      Buncheol buncheol = Buncheol.create(hostId, validParams());

      buncheolRepository.save(buncheol);
      em.flush();

      assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
    }

    @Test
    void gs25_배송비만_설정하여_저장할_수_있다() {
      BuncheolParams params =
          new BuncheolParams(
              groupId, "제목", null, "스토어명", LocalDateTime.now().plusDays(7), 5, 2500, null);
      Buncheol buncheol = Buncheol.create(hostId, params);

      buncheolRepository.save(buncheol);
      em.flush();

      assertThat(buncheol.getId()).isNotNull();
    }

    @Test
    void cu_배송비만_설정하여_저장할_수_있다() {
      BuncheolParams params =
          new BuncheolParams(
              groupId, "제목", null, "스토어명", LocalDateTime.now().plusDays(7), 5, null, 2000);
      Buncheol buncheol = Buncheol.create(hostId, params);

      buncheolRepository.save(buncheol);
      em.flush();

      assertThat(buncheol.getId()).isNotNull();
    }
  }

  @Nested
  @DisplayName("분철 조회/수정 테스트")
  class FindAndUpdateTest {

    @Test
    void ID로_분철을_조회할_수_있다() {
      Buncheol buncheol = persistAndDetach(Buncheol.create(hostId, validParams()));

      Buncheol found = buncheolRepository.findById(buncheol.getId()).orElseThrow();

      assertThat(found.getId()).isEqualTo(buncheol.getId());
      assertThat(found.getHostId()).isEqualTo(hostId);
      assertThat(found.getGroupId()).isEqualTo(groupId);
      assertThat(found.getTitle()).isEqualTo("테스트 분철 제목");
      assertThat(found.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
    }

    @Test
    void 도메인_update_호출_시_더티체킹으로_DB가_갱신된다() {
      Buncheol buncheol = persistAndDetach(Buncheol.create(hostId, validParams()));

      BuncheolParams updatedParams =
          new BuncheolParams(
              groupId, "수정 제목", "수정 설명", "수정 스토어", LocalDateTime.now().plusDays(3), 3, null, 1800);

      // managed 상태로 다시 로드 후 도메인 메서드만 호출 → flush 시 dirty UPDATE 가 발생해야 한다
      Buncheol managed = buncheolRepository.findById(buncheol.getId()).orElseThrow();
      managed.update(updatedParams);
      em.flush();
      em.clear();

      Buncheol found = buncheolRepository.findById(buncheol.getId()).orElseThrow();
      assertThat(found.getTitle()).isEqualTo("수정 제목");
      assertThat(found.getDescription()).isEqualTo("수정 설명");
      assertThat(found.getStoreName()).isEqualTo("수정 스토어");
      assertThat(found.getShippingDeadlineDays()).isEqualTo(3);
      assertThat(found.getShippingFeePolicy().gs25ShippingFee()).isNull();
      assertThat(found.getShippingFeePolicy().cuShippingFee()).isEqualTo(1800);
    }

    @Test
    void 분철_상태를_수정할_수_있다() {
      Buncheol buncheol = persistAndDetach(Buncheol.create(hostId, validParams()));
      BuncheolStatus expectedStatus = buncheol.getStatus();
      buncheol.cancel();

      boolean updated = buncheolRepository.updateStatus(buncheol, expectedStatus);

      Buncheol found = buncheolRepository.findById(buncheol.getId()).orElseThrow();
      assertThat(updated).isTrue();
      assertThat(found.getStatus()).isEqualTo(BuncheolStatus.CANCELLED);
    }

    @Test
    void 예상_상태가_다르면_업데이트되지_않는다() {
      Buncheol buncheol = persistAndDetach(Buncheol.create(hostId, validParams()));
      buncheol.cancel();

      // expectedStatus를 CLOSED로 전달 (실제는 RECRUITING)
      boolean updated = buncheolRepository.updateStatus(buncheol, BuncheolStatus.CLOSED);

      Buncheol found = buncheolRepository.findById(buncheol.getId()).orElseThrow();
      assertThat(updated).isFalse();
      assertThat(found.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
    }
  }
}
