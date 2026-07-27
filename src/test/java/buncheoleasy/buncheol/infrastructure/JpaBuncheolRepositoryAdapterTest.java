package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolListCursor;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
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
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    return new BuncheolParams(groupId, "테스트 분철 제목", "분철 설명입니다.", "공식 스토어", deadline, 1, 3000, null);
  }

  private Buncheol persistAndDetach(Buncheol buncheol) {
    buncheolRepository.save(buncheol);
    em.flush();
    em.clear();
    return buncheol;
  }

  // 공통 픽스처 헬퍼 — 여러 nested 테스트에서 status·createdAt 을 강제 주입한다.
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

  private void forceDeadline(Long buncheolId, Instant deadline) {
    jdbcTemplate.update(
        "UPDATE buncheols SET deadline = ? WHERE id = ?", Timestamp.from(deadline), buncheolId);
    em.clear();
  }

  // 분철 params 가 테스트와 무관할 때만 사용. params 가 케이스의 검증 대상이면 (예: keyword/groupId 필터)
  // 반드시 아래 3-인자 오버로드를 직접 호출해 default validParams() 가 새지 않도록 한다.
  private Long persistWithCreatedAt(Long hostId, Instant createdAt) {
    return persistWithCreatedAt(hostId, validParams(), createdAt);
  }

  private Long persistWithCreatedAt(Long hostId, BuncheolParams params, Instant createdAt) {
    Buncheol b = Buncheol.create(hostId, params, Instant.now());
    buncheolRepository.save(b);
    em.flush();
    forceCreatedAt(b.getId(), createdAt);
    return b.getId();
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
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
      BuncheolParams params = new BuncheolParams(groupId, "제목", null, "스토어명", deadline, 1, 2500, null);
      Buncheol buncheol = Buncheol.create(hostId, params, Instant.now());

      buncheolRepository.save(buncheol);
      em.flush();

      assertThat(buncheol.getId()).isNotNull();
    }

    @Test
    void cu_배송비만_설정하여_저장할_수_있다() {
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
      BuncheolParams params = new BuncheolParams(groupId, "제목", null, "스토어명", deadline, 1, null, 2000);
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
    void finalizeIfStatus_으로_RECRUITING_분철을_CANCELLED_로_전이한다() {
      Buncheol buncheol = persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));

      int affected =
          buncheolRepository.finalizeIfStatus(
              buncheol.getId(), BuncheolStatus.RECRUITING, BuncheolStatus.CANCELLED, Instant.now());
      em.clear();

      assertThat(affected).isEqualTo(1);
      Buncheol found = buncheolRepository.findById(buncheol.getId()).orElseThrow();
      assertThat(found.getStatus()).isEqualTo(BuncheolStatus.CANCELLED);
      assertThat(found.getFinalizedAt()).isNotNull();
    }

    @Test
    void finalizeIfStatus_으로_인원미달_CANCELLED_분철을_HOST_CANCELLED_로_전이한다() {
      Buncheol buncheol = persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));
      forceStatus(buncheol.getId(), BuncheolStatus.CANCELLED);

      int affected =
          buncheolRepository.finalizeIfStatus(
              buncheol.getId(),
              BuncheolStatus.CANCELLED,
              BuncheolStatus.HOST_CANCELLED,
              Instant.now());
      em.clear();

      assertThat(affected).isEqualTo(1);
      Buncheol found = buncheolRepository.findById(buncheol.getId()).orElseThrow();
      assertThat(found.getStatus()).isEqualTo(BuncheolStatus.HOST_CANCELLED);
      assertThat(found.getFinalizedAt()).isNotNull();
    }

    @Test
    void finalizeIfStatus_은_기대_상태와_다르면_전이하지_않는다() {
      Buncheol buncheol = persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));
      forceStatus(buncheol.getId(), BuncheolStatus.CONFIRMED);

      int affectedFromRecruiting =
          buncheolRepository.finalizeIfStatus(
              buncheol.getId(), BuncheolStatus.RECRUITING, BuncheolStatus.CANCELLED, Instant.now());
      int affectedFromCancelled =
          buncheolRepository.finalizeIfStatus(
              buncheol.getId(),
              BuncheolStatus.CANCELLED,
              BuncheolStatus.HOST_CANCELLED,
              Instant.now());

      assertThat(affectedFromRecruiting).isZero();
      assertThat(affectedFromCancelled).isZero();
    }
  }

  @Nested
  @DisplayName("호스트의 분철 목록 조회 테스트")
  class FindVisibleByHostIdOrderByCreatedAtDescTest {

    @Test
    void 호스트의_HOST_CANCELLED_분철만_제외되고_인원미달_CANCELLED_는_포함된다() {
      Long active = persistWithCreatedAt(hostId, Instant.parse("2026-05-15T08:00:00Z"));
      Long cancelled = persistWithCreatedAt(hostId, Instant.parse("2026-05-14T08:00:00Z"));
      Long hostCancelled = persistWithCreatedAt(hostId, Instant.parse("2026-05-13T08:00:00Z"));
      forceStatus(cancelled, BuncheolStatus.CANCELLED);
      forceStatus(hostCancelled, BuncheolStatus.HOST_CANCELLED);

      List<Buncheol> result = buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(hostId);

      // createdAt DESC: active(05-15) → cancelled(05-14). HOST_CANCELLED 만 제외.
      assertThat(result).extracting(Buncheol::getId).containsExactly(active, cancelled);
    }

    @Test
    void CONFIRMED_분철은_포함되어_반환된다() {
      Long recruiting = persistWithCreatedAt(hostId, Instant.parse("2026-05-15T08:00:00Z"));
      Long confirmed = persistWithCreatedAt(hostId, Instant.parse("2026-05-10T08:00:00Z"));
      forceStatus(confirmed, BuncheolStatus.CONFIRMED);

      List<Buncheol> result = buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(hostId);

      assertThat(result).extracting(Buncheol::getId).containsExactly(recruiting, confirmed);
    }

    @Test
    void 다른_호스트의_분철은_반환되지_않는다() {
      Long otherHostId = TestUserFixture.insertUser(jdbcTemplate, "other_host");
      Long mine = persistWithCreatedAt(hostId, Instant.parse("2026-05-15T08:00:00Z"));
      persistWithCreatedAt(otherHostId, Instant.parse("2026-05-14T08:00:00Z"));

      List<Buncheol> result = buncheolRepository.findVisibleByHostIdOrderByCreatedAtDesc(hostId);

      assertThat(result).extracting(Buncheol::getId).containsExactly(mine);
    }
  }

  @Nested
  @DisplayName("호스트의 활성 분철 존재 여부 테스트")
  class ExistsActiveByHostIdTest {

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
    void 호스트의_분철이_모두_CANCELLED면_false를_반환한다() {
      Buncheol cancelledA = persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));
      Buncheol cancelledB = persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));
      forceStatus(cancelledA.getId(), BuncheolStatus.CANCELLED);
      forceStatus(cancelledB.getId(), BuncheolStatus.CANCELLED);

      assertThat(buncheolRepository.existsActiveByHostId(hostId)).isFalse();
    }

    @Test
    void 호스트의_CONFIRMED_분철이_있으면_true를_반환한다() {
      Buncheol confirmed = persistAndDetach(Buncheol.create(hostId, validParams(), Instant.now()));
      forceStatus(confirmed.getId(), BuncheolStatus.CONFIRMED);

      assertThat(buncheolRepository.existsActiveByHostId(hostId)).isTrue();
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

    private BuncheolParams paramsWithTitleAndDescription(
        Long gId, String title, String description) {
      Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
      return new BuncheolParams(gId, title, description, "공식 스토어", deadline, 1, 3000, null);
    }

    private void linkMember(Long buncheolId, Long memberId) {
      jdbcTemplate.update(
          "INSERT INTO buncheol_members (buncheol_id, member_id, price)" + " VALUES (?, ?, ?)",
          buncheolId,
          memberId,
          10000L);
      em.clear();
    }

    @Test
    void HOST_CANCELLED_는_검색에서_제외되고_인원미달_CANCELLED_는_맨_뒤에_포함된다() {
      Long recruiting =
          persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-15T08:00:00Z"));
      Long cancelled =
          persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-14T08:00:00Z"));
      Long hostCancelled =
          persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-13T08:00:00Z"));
      forceStatus(cancelled, BuncheolStatus.CANCELLED);
      forceStatus(hostCancelled, BuncheolStatus.HOST_CANCELLED);

      List<Buncheol> result =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 10);

      // 모집중(rank0) → 인원미달취소(rank2) 순. HOST_CANCELLED 는 어느 그룹에도 없어 제외.
      assertThat(result).extracting(Buncheol::getId).containsExactly(recruiting, cancelled);
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
              new BuncheolSearchCondition(groupId, null, null), BuncheolListCursor.firstPage(), 10);

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
              new BuncheolSearchCondition(null, memberA, null), BuncheolListCursor.firstPage(), 10);

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
              new BuncheolSearchCondition(null, null, "newjeans"), BuncheolListCursor.firstPage(), 10);

      assertThat(result).extracting(Buncheol::getId).containsExactly(titleMatch, descMatch);
    }

    @Test
    void 모집중_그룹은_createdAt_DESC_그리고_id_DESC_tie_break_로_정렬된다() {
      Instant same = Instant.parse("2026-05-15T08:00:00Z");
      Long b1 = persistWithCreatedAt(hostId, validParams(), same);
      Long b2 = persistWithCreatedAt(hostId, validParams(), same);
      Long b3 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-14T08:00:00Z"));

      List<Buncheol> result =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 10);

      // 같은 시각 b1, b2 → id 큰 것이 먼저 (b2). 그 다음 b1. 마지막에 더 이른 b3.
      assertThat(result).extracting(Buncheol::getId).containsExactly(b2, b1, b3);
    }

    @Test
    void 모집중_커서를_주면_그_이전_분철만_반환되고_중복_누락이_없다() {
      Long b1 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-15T08:00:00Z"));
      Long b2 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-14T08:00:00Z"));
      Long b3 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-13T08:00:00Z"));

      List<Buncheol> firstPage =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 3);
      assertThat(firstPage).extracting(Buncheol::getId).containsExactly(b1, b2, b3);

      // 모집중 그룹(rank 0) 커서 = b2 의 (createdAt, id) → 다음 페이지는 b3 만
      BuncheolListCursor cursor =
          new BuncheolListCursor(
              BuncheolListCursor.RANK_RECRUITING, Instant.parse("2026-05-14T08:00:00Z"), b2);
      List<Buncheol> secondPage =
          buncheolRepository.search(new BuncheolSearchCondition(null, null, null), cursor, 3);

      assertThat(secondPage).extracting(Buncheol::getId).containsExactly(b3);
    }

    @Test
    void 모집중_동일_createdAt_에서_커서의_id_보다_작은_id_만_반환된다() {
      Instant same = Instant.parse("2026-05-15T08:00:00Z");
      Long b1 = persistWithCreatedAt(hostId, validParams(), same);
      Long b2 = persistWithCreatedAt(hostId, validParams(), same);
      // 정렬 시 큰 id 먼저: 첫 페이지 b2, 두 번째 페이지에선 b1 만 나와야.
      BuncheolListCursor cursor =
          new BuncheolListCursor(BuncheolListCursor.RANK_RECRUITING, same, b2);

      List<Buncheol> result =
          buncheolRepository.search(new BuncheolSearchCondition(null, null, null), cursor, 10);

      assertThat(result).extracting(Buncheol::getId).containsExactly(b1);
    }

    @Test
    void 모집중을_먼저_그_뒤에_마감을_이어_보여준다() {
      // 모집중 2건 (createdAt DESC: rec1 → rec2), 마감 2건 (deadline DESC: conf1 → conf2)
      Long rec1 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-15T08:00:00Z"));
      Long rec2 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-14T08:00:00Z"));
      Long conf1 = persistConfirmed(Instant.parse("2026-05-10T00:00:00Z"));
      Long conf2 = persistConfirmed(Instant.parse("2026-05-01T00:00:00Z"));

      List<Buncheol> result =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 10);

      // 모집중(최신순) → 마감(마감일 내림차순)
      assertThat(result).extracting(Buncheol::getId).containsExactly(rec1, rec2, conf1, conf2);
    }

    @Test
    void 모집중_진행확정_인원미달취소_순으로_세_그룹을_이어_보여준다() {
      Long rec = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-15T08:00:00Z"));
      Long conf1 = persistConfirmed(Instant.parse("2026-05-10T00:00:00Z"));
      Long conf2 = persistConfirmed(Instant.parse("2026-05-01T00:00:00Z"));
      Long canc1 = persistCancelled(Instant.parse("2026-05-09T00:00:00Z"));
      Long canc2 = persistCancelled(Instant.parse("2026-05-02T00:00:00Z"));

      List<Buncheol> result =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 10);

      // 모집중 → 진행확정(deadline DESC) → 인원미달취소(deadline DESC)
      assertThat(result)
          .extracting(Buncheol::getId)
          .containsExactly(rec, conf1, conf2, canc1, canc2);
    }

    @Test
    void 마감_그룹은_deadline_DESC_그리고_id_DESC_tie_break_로_정렬된다() {
      Instant sameDeadline = Instant.parse("2026-05-10T00:00:00Z");
      Long c1 = persistConfirmed(sameDeadline);
      Long c2 = persistConfirmed(sameDeadline);
      Long c3 = persistConfirmed(Instant.parse("2026-05-09T00:00:00Z"));

      List<Buncheol> result =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 10);

      // 같은 deadline c1, c2 → id 큰 것이 먼저 (c2). 그 다음 c1. 마지막에 더 이른 c3.
      assertThat(result).extracting(Buncheol::getId).containsExactly(c2, c1, c3);
    }

    @Test
    void 마감_그룹_커서를_주면_그_이전_마감_분철만_반환된다() {
      Long c1 = persistConfirmed(Instant.parse("2026-05-10T00:00:00Z"));
      Long c2 = persistConfirmed(Instant.parse("2026-05-08T00:00:00Z"));
      Long c3 = persistConfirmed(Instant.parse("2026-05-06T00:00:00Z"));

      // 마감 그룹(rank 1) 커서 = c1 의 (deadline, id) → c2, c3 만
      BuncheolListCursor cursor =
          new BuncheolListCursor(
              BuncheolListCursor.RANK_CONFIRMED, Instant.parse("2026-05-10T00:00:00Z"), c1);
      List<Buncheol> result =
          buncheolRepository.search(new BuncheolSearchCondition(null, null, null), cursor, 10);

      assertThat(result).extracting(Buncheol::getId).containsExactly(c2, c3);
    }

    @Test
    void 그룹_경계를_걸친_페이지는_모집중을_채우고_남은_자리를_마감_첫구간으로_잇는다() {
      Long rec1 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-15T08:00:00Z"));
      Long rec2 = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-14T08:00:00Z"));
      Long conf1 = persistConfirmed(Instant.parse("2026-05-10T00:00:00Z"));
      Long conf2 = persistConfirmed(Instant.parse("2026-05-01T00:00:00Z"));

      // limit 3: 모집중 2건을 다 채우고 모집중 소진 → 마감 첫구간(conf1) 1건을 이어 채운다
      List<Buncheol> page =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 3);

      assertThat(page).extracting(Buncheol::getId).containsExactly(rec1, rec2, conf1);

      // 다음 페이지: 마지막으로 본 모집중(rec2) 커서 → 모집중 소진 후 마감 첫구간부터 다시 → conf1, conf2
      BuncheolListCursor afterRec2 =
          new BuncheolListCursor(
              BuncheolListCursor.RANK_RECRUITING, Instant.parse("2026-05-14T08:00:00Z"), rec2);
      List<Buncheol> next =
          buncheolRepository.search(new BuncheolSearchCondition(null, null, null), afterRec2, 3);

      assertThat(next).extracting(Buncheol::getId).containsExactly(conf1, conf2);
    }

    @Test
    void 마감만_존재하면_첫_페이지부터_마감_그룹을_반환한다() {
      Long c1 = persistConfirmed(Instant.parse("2026-05-10T00:00:00Z"));
      Long c2 = persistConfirmed(Instant.parse("2026-05-05T00:00:00Z"));

      List<Buncheol> result =
          buncheolRepository.search(
              new BuncheolSearchCondition(null, null, null), BuncheolListCursor.firstPage(), 10);

      assertThat(result).extracting(Buncheol::getId).containsExactly(c1, c2);
    }

    // 모집중으로 저장한 뒤 status=CONFIRMED, deadline 을 강제 주입한다 (Buncheol.create 는 과거 deadline 을 거부하므로).
    private Long persistConfirmed(Instant deadline) {
      Long id = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-01T00:00:00Z"));
      forceStatus(id, BuncheolStatus.CONFIRMED);
      forceDeadline(id, deadline);
      return id;
    }

    // 인원 미달 자동취소(CANCELLED) 분철. 공개 목록 맨 뒤 그룹(rank2, deadline DESC) 검증용.
    private Long persistCancelled(Instant deadline) {
      Long id = persistWithCreatedAt(hostId, validParams(), Instant.parse("2026-05-01T00:00:00Z"));
      forceStatus(id, BuncheolStatus.CANCELLED);
      forceDeadline(id, deadline);
      return id;
    }
  }
}
