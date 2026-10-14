package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolRepository;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.buncheol.domain.ShippingFeePolicy;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분철 수정이 제목(더티체킹)과 거래 조건(조건부 UPDATE)을 <b>같은 행에 함께</b> 쓸 때, 한쪽이 다른 쪽을 되돌리지 않는지 고정한다.
 *
 * <p>조건 UPDATE 는 영속성 컨텍스트를 먼저 flush 하고 비운다. 순서가 뒤집히거나 비우기가 빠지면, 커밋 때 엔티티 flush 가 조건 칸을
 * 로드 시점 값으로 되쓰거나 제목 변경이 조용히 사라진다. {@link BuncheolOpenChatUrlLostUpdateTest} 와 같은 방식으로 실제 서비스
 * 경로를 태운다.
 *
 * <p>⚠️ 동시성 자체는 재현하지 않는다(클래스 트랜잭션 하나·커넥션 하나). 경합 안전성은 조건부 UPDATE 의 NOT EXISTS 와 분철 행 잠금이
 * 맡고, 그 조건은 {@code JpaBuncheolRepositoryAdapterTest} 가 고정한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("분철 수정 — 제목과 거래 조건을 함께 쓰는 통합 테스트")
class BuncheolTermsChangeIntegrationTest {

  @Autowired private BuncheolService buncheolService;
  @Autowired private BuncheolRepository buncheolRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager em;

  private final Instant deadline =
      Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  private final Instant newDeadline = deadline.plus(3, ChronoUnit.DAYS);

  private Long hostId;
  private Long groupId;
  private Long buncheolId;
  private Long imageId;

  @BeforeEach
  void setUp() {
    hostId = TestUserFixture.insertUser(jdbcTemplate, "host_terms");
    groupId = TestGroupFixture.insertGroup(jdbcTemplate, "조건수정그룹");
    Buncheol buncheol =
        Buncheol.create(
            hostId,
            new BuncheolParams(
                groupId, "원래 제목", null, "공식 스토어", deadline, 1, 3000, null, FlowType.C2C, null),
            Instant.now());
    buncheolRepository.save(buncheol);
    em.flush();
    em.clear();
    buncheolId = buncheol.getId();
    jdbcTemplate.update(
        "INSERT INTO buncheol_images (buncheol_id, image_url, is_thumbnail) VALUES (?, ?, TRUE)",
        buncheolId,
        "https://cdn.example.com/a.jpg");
    imageId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM buncheol_images WHERE buncheol_id = ?", Long.class, buncheolId);
  }

  private BuncheolModifyRequest request(
      final String title,
      final Instant requestedDeadline,
      final Integer gs25ShippingFee,
      final Integer cuShippingFee,
      final String purchaseSite) {
    return new BuncheolModifyRequest(
        title,
        null,
        List.of(imageId),
        imageId,
        null,
        null,
        requestedDeadline,
        null,
        gs25ShippingFee,
        cuShippingFee,
        purchaseSite);
  }

  private Buncheol reload() {
    em.flush();
    em.clear();
    return em.find(Buncheol.class, buncheolId);
  }

  private void insertApplied() {
    Long participantId = TestUserFixture.insertUser(jdbcTemplate, "applicant_terms");
    Long groupMemberId = TestGroupFixture.insertGroupMember(jdbcTemplate, groupId, "조건멤버");
    jdbcTemplate.update(
        "INSERT INTO buncheol_members (buncheol_id, member_id, price) VALUES (?, ?, ?)",
        buncheolId,
        groupMemberId,
        10_000L);
    Long slotId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM buncheol_members WHERE buncheol_id = ?", Long.class, buncheolId);
    jdbcTemplate.update(
        "INSERT INTO participations (buncheol_id, buncheol_member_id, participant_id, amount,"
            + " shipping_fee, status, flow_type) VALUES (?, ?, ?, 10000, 0, 'APPLIED', 'C2C')",
        buncheolId,
        slotId,
        participantId);
  }

  @Test
  void 제목과_조건을_함께_바꾸면_둘_다_남는다() {
    buncheolService.modifyBuncheol(
        hostId, buncheolId, request("새 제목", newDeadline, null, 2000, "새 구매처"), List.of());

    Buncheol reloaded = reload();
    assertThat(reloaded.getTitle()).isEqualTo("새 제목");
    assertThat(reloaded.getDeadline()).isEqualTo(newDeadline);
    assertThat(reloaded.getShippingFeePolicy()).isEqualTo(ShippingFeePolicy.of(null, 2000));
    assertThat(reloaded.getPurchaseSite()).isEqualTo("새 구매처");
  }

  @Test
  void 신청자가_있으면_조건은_409이고_제목도_바뀌지_않는다() {
    insertApplied();

    assertThatThrownBy(
            () ->
                buncheolService.modifyBuncheol(
                    hostId, buncheolId, request("새 제목", newDeadline, null, null, null), List.of()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_TERMS_EDIT_HAS_APPLICANT);

    Buncheol reloaded = reload();
    assertThat(reloaded.getTitle()).isEqualTo("원래 제목");
    assertThat(reloaded.getDeadline()).isEqualTo(deadline);
  }

  // 수정 화면이 지금 조건을 그대로 실어 보내도 신청자가 있는 분철의 제목 수정은 지금처럼 된다.
  @Test
  void 신청자가_있어도_조건을_지금_값으로_보내면_제목은_바뀐다() {
    insertApplied();

    buncheolService.modifyBuncheol(
        hostId, buncheolId, request("새 제목", deadline, 3000, null, "공식 스토어"), List.of());

    Buncheol reloaded = reload();
    assertThat(reloaded.getTitle()).isEqualTo("새 제목");
    assertThat(reloaded.getDeadline()).isEqualTo(deadline);
    assertThat(reloaded.getShippingFeePolicy()).isEqualTo(ShippingFeePolicy.of(3000, null));
  }
}
