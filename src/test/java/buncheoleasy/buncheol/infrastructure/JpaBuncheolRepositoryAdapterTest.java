package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.global.page.Cursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
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
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
    return new BuncheolParams(groupId, "테스트 분철 제목", "분철 설명입니다.", "공식 스토어", deadline, 3000, null);
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
      Buncheol buncheol = Buncheol.create(hostId, validParams(), Instant.now());

      buncheolRepository.save(buncheol);
      em.flush();

      assertThat(buncheol.getId()).isNotNull();
      assertThat(buncheol.getId()).isPositive();
    }

    @Test
    void 저장된_분철의_초기_상태는_RECRUITING이다() {
      Buncheol buncheol = Buncheol.create(hostId, validParams(), Instant.now());

      buncheolRepository.save(buncheol);
      em.flush();

      assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
    }

    @Test
    void gs25_배송비만_설정하여_저장할_수_있다() {
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      BuncheolParams params = new BuncheolParams(groupId, "제목", null, "스토어명", deadline, 2500, null);
      Buncheol buncheol = Buncheol.create(hostId, params, Instant.now());

      buncheolRepository.save(buncheol);
      em.flush();

      assertThat(buncheol.getId()).isNotNull();
    }

    @Test
    void cu_배송비만_설정하여_저장할_수_있다() {
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      BuncheolParams params = new BuncheolParams(groupId, "제목", null, "스토어명", deadline, null, 2000);
      Buncheol buncheol = Buncheol.create(hostId, params, Instant.now());

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
      Buncheol buncheol = persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));

      Buncheol found = buncheolRepository.findById(buncheol.getId()).orElseThrow();

      assertThat(found.getId()).isEqualTo(buncheol.getId());
      assertThat(found.getHostId()).isEqualTo(hostId);
      assertThat(found.getGroupId()).isEqualTo(groupId);
      assertThat(found.getTitle()).isEqualTo("테스트 분철 제목");
      assertThat(found.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
    }

    @Test
    void 도메인_updateContent_호출_시_더티체킹으로_DB가_갱신된다() {
      Buncheol buncheol = persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));

      // managed 상태로 다시 로드 후 도메인 메서드만 호출 → flush 시 dirty UPDATE 가 발생해야 한다
      Buncheol managed = buncheolRepository.findById(buncheol.getId()).orElseThrow();
      managed.updateContent("수정 제목", "수정 설명");
      em.flush();
      em.clear();

      Buncheol found = buncheolRepository.findById(buncheol.getId()).orElseThrow();
      assertThat(found.getTitle()).isEqualTo("수정 제목");
      assertThat(found.getDescription()).isEqualTo("수정 설명");
    }

    @Test
    void managed_엔티티에서_cancel_호출_시_더티체킹으로_DB에_CANCELLED가_반영된다() {
      Buncheol buncheol = persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));

      // findById 로 managed 상태로 다시 로드 후 도메인 메서드만 호출 → flush 시 dirty UPDATE 가 발생해야 한다
      Buncheol managed = buncheolRepository.findById(buncheol.getId()).orElseThrow();
      managed.cancel();
      em.flush();
      em.clear();

      Buncheol found = buncheolRepository.findById(buncheol.getId()).orElseThrow();
      assertThat(found.getStatus()).isEqualTo(BuncheolStatus.CANCELLED);
    }
  }

  @Nested
  @DisplayName("호스트의 활성 분철 존재 여부 테스트")
  class ExistsActiveByHostIdTest {

    private void forceStatus(Long buncheolId, BuncheolStatus status) {
      jdbcTemplate.update(
          "UPDATE buncheols SET status = ? WHERE id = ?", status.name(), buncheolId);
      em.clear();
    }

    @Test
    void 호스트의_분철이_없으면_false를_반환한다() {
      assertThat(buncheolRepository.existsActiveByHostId(hostId)).isFalse();
    }

    @Test
    void 호스트의_RECRUITING_분철이_있으면_true를_반환한다() {
      persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));

      assertThat(buncheolRepository.existsActiveByHostId(hostId)).isTrue();
    }

    @Test
    void 호스트의_분철이_모두_FINISHED_또는_CANCELLED면_false를_반환한다() {
      Buncheol finished = persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));
      Buncheol cancelled = persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));
      forceStatus(finished.getId(), BuncheolStatus.FINISHED);
      forceStatus(cancelled.getId(), BuncheolStatus.CANCELLED);

      assertThat(buncheolRepository.existsActiveByHostId(hostId)).isFalse();
    }

    @Test
    void 다른_호스트의_활성_분철은_영향을_주지_않는다() {
      Long otherHostId = TestUserFixture.insertUser(jdbcTemplate, "other_host");
      persistAndDetach(Buncheol.create(otherHostId, validParams(), Instant.now()));

      assertThat(buncheolRepository.existsActiveByHostId(hostId)).isFalse();
    }
  }

  @Nested
  @DisplayName("분철 목록 검색(search) 테스트")
  class SearchTest {

    private void forceStatus(Long buncheolId, BuncheolStatus status) {
      jdbcTemplate.update(
          "UPDATE buncheols SET status = ? WHERE id = ?", status.name(), buncheolId);
      em.clear();
    }

    private void forceCreatedAt(Long buncheolId, Instant createdAt) {
      jdbcTemplate.update(
          "UPDATE buncheols SET created_at = ? WHERE id = ?",
          Timestamp.from(createdAt),
          buncheolId);
      em.clear();
    }

    private Long persistWithCreatedAt(Long hostId, BuncheolParams params, Instant createdAt) {
      Buncheol b = Buncheol.create(hostId, params, Instant.now());
      buncheolRepository.save(b);
      em.flush();
      forceCreatedAt(b.getId(), createdAt);
      return b.getId();
    }

    private BuncheolParams paramsWithTitleAndDescription(
        Long gId, String title, String description) {
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
      return new BuncheolParams(gId, title, description, "공식 스토어", deadline, 3000, null);
    }

    private void linkMember(Long buncheolId, Long memberId) {
      jdbcTemplate.update(
          "INSERT INTO buncheol_members (buncheol_id, member_id, bid_min_price)"
              + " VALUES (?, ?, ?)",
          buncheolId,
          memberId,
          10000L);
      em.clear();
    }

    @Test
    void CANCELLED_상태의_분철은_검색_결과에서_제외된다() {
      Long b1 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-15T08:00:00Z"));
      Long b2 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-14T08:00:00Z"));
      forceStatus(b2, BuncheolStatus.CANCELLED);

      List<Buncheol> result =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, null), Cursor.firstPage(), 10);

      assertThat(result).extracting(Buncheol::getId).containsExactly(b1);
    }

    @Test
    void groupId_필터가_적용된다() {
      Long otherGroupId = TestGroupFixture.insertGroup(jdbcTemplate, "다른 그룹");
      Long target =
          persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-15T08:00:00Z"));
      persistWithCreatedAt(
          hostId,
          paramsWithTitleAndDescription(otherGroupId, "다른 그룹 분철", "설명"),
          Instant.parse("2026-05-14T08:00:00Z"));

      List<Buncheol> result =
          buncheolRepository.search(
              new BuncheolSearchCondition(groupId, null, null), Cursor.firstPage(), 10);

      assertThat(result).extracting(Buncheol::getId).containsExactly(target);
    }

    @Test
    void memberId_필터는_해당_멤버가_포함된_분철만_반환한다() {
      Long memberA = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "민지");
      Long memberB = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "하니");
      Long b1 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-15T08:00:00Z"));
      Long b2 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-14T08:00:00Z"));
      linkMember(b1, memberA);
      linkMember(b2, memberB);

      List<Buncheol> result =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, memberA, null), Cursor.firstPage(), 10);

      assertThat(result).extracting(Buncheol::getId).containsExactly(b1);
    }

    @Test
    void keyword_는_title_과_description_모두_검색되고_대소문자를_구분하지_않는다() {
      Long titleMatch =
          persistWithCreatedAt(
              hostId,
              paramsWithTitleAndDescription(groupId, "NewJeans 분철 A", "설명"),
              Instant.parse("2026-05-15T08:00:00Z"));
      Long descMatch =
          persistWithCreatedAt(
              hostId,
              paramsWithTitleAndDescription(groupId, "분철 B", "newjeans 설명"),
              Instant.parse("2026-05-14T08:00:00Z"));
      persistWithCreatedAt(
          hostId,
          paramsWithTitleAndDescription(groupId, "에스파 분철", "다른 설명"),
          Instant.parse("2026-05-13T08:00:00Z"));

      List<Buncheol> result =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, "newjeans"), Cursor.firstPage(), 10);

      assertThat(result).extracting(Buncheol::getId).containsExactly(titleMatch, descMatch);
    }

    @Test
    void 정렬은_createdAt_DESC_그리고_id_DESC_tie_break() {
      Instant same = Instant.parse("2026-05-15T08:00:00Z");
      Long b1 = persistWithCreatedAt(hostId, validParams(), same);
      Long b2 = persistWithCreatedAt(hostId, validParams(), same);
      Long b3 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-14T08:00:00Z"));

      List<Buncheol> result =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, null), Cursor.firstPage(), 10);

      // 같은 시각 b1, b2 → id 큰 것이 먼저 (b2). 그 다음 b1. 마지막에 더 이른 b3.
      assertThat(result).extracting(Buncheol::getId).containsExactly(b2, b1, b3);
    }

    @Test
    void 커서를_주면_그_이전_분철만_반환되고_중복_누락이_없다() {
      Long b1 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-15T08:00:00Z"));
      Long b2 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-14T08:00:00Z"));
      Long b3 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-13T08:00:00Z"));

      // 첫 페이지: size 2 → b1, b2 노출, 다음 cursor 는 b2 의 (createdAt, id)
      List<Buncheol> firstPage =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, null), Cursor.firstPage(), 3);
      assertThat(firstPage).extracting(Buncheol::getId).containsExactly(b1, b2, b3);

      // size 2 + 1 패턴: cursor=b2 로 다음 페이지 조회 시 b3 만 남는다
      Cursor cursor = new Cursor(Instant.parse("2026-05-14T08:00:00Z"), b2);
      List<Buncheol> secondPage =
          buncheolRepository.search(new BuncheolSearchCondition(null, null, null), cursor, 3);

      assertThat(secondPage).extracting(Buncheol::getId).containsExactly(b3);
    }

    @Test
    void 동일_createdAt_에서_커서의_id_보다_작은_id_만_반환된다() {
      Instant same = Instant.parse("2026-05-15T08:00:00Z");
      Long b1 = persistWithCreatedAt(hostId, validParams(), same);
      Long b2 = persistWithCreatedAt(hostId, validParams(), same);
      // 정렬 시 큰 id 먼저: 첫 페이지 b2, 두 번째 페이지에선 b1 만 나와야.
      Cursor cursor = new Cursor(same, b2);

      List<Buncheol> result =
          buncheolRepository.search(new BuncheolSearchCondition(null, null, null), cursor, 10);

      assertThat(result).extracting(Buncheol::getId).containsExactly(b1);
    }
  }
}
