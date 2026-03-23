package buncheoleasy.buncheol.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolParams;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.user.domain.User;
import buncheoleasy.user.infrastructure.UserMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@MybatisTest
@ActiveProfiles("test")
@DisplayName("BuncheolMapper 테스트")
class BuncheolMapperTest {

  @Autowired private BuncheolMapper buncheolMapper;

  @Autowired private UserMapper userMapper;

  private Long hostId;

  @BeforeEach
  void setUp() {
    User host = User.create("KAKAO", "host123", "host@example.com");
    userMapper.insert(host);
    hostId = host.getId();
  }

  private BuncheolParams validParams(String groupName) {
    return new BuncheolParams(
        null,
        groupName,
        "테스트 분철 제목",
        "분철 설명입니다.",
        "공식 앨범",
        "공식 스토어",
        50_000L,
        LocalDateTime.now().plusDays(7),
        7,
        3000,
        null,
        "국민은행",
        "123-456-789012",
        "홍길동");
  }

  @Nested
  @DisplayName("분철 저장 테스트")
  class InsertTest {

    @Test
    void 분철을_저장할_수_있다() {
      // given
      Buncheol buncheol = Buncheol.create(hostId, validParams("테스트 그룹"));

      // when
      buncheolMapper.insert(buncheol);

      // then
      assertThat(buncheol.getId()).isNotNull();
      assertThat(buncheol.getId()).isPositive();
    }

    @Test
    void 저장된_분철의_초기_상태는_RECRUITING이다() {
      // given
      Buncheol buncheol = Buncheol.create(hostId, validParams("테스트 그룹"));

      // when
      buncheolMapper.insert(buncheol);

      // then
      assertThat(buncheol.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
    }

    @Test
    void gs25_배송비만_설정하여_저장할_수_있다() {
      // given
      BuncheolParams params =
          new BuncheolParams(
              null,
              "그룹명",
              "제목",
              null,
              "앨범명",
              "스토어명",
              30_000L,
              LocalDateTime.now().plusDays(7),
              5,
              2500,
              null,
              "우리은행",
              "1002-123-456789",
              "홍길동");
      Buncheol buncheol = Buncheol.create(hostId, params);

      // when & then
      buncheolMapper.insert(buncheol);
      assertThat(buncheol.getId()).isNotNull();
    }

    @Test
    void cu_배송비만_설정하여_저장할_수_있다() {
      // given
      BuncheolParams params =
          new BuncheolParams(
              null,
              "그룹명",
              "제목",
              null,
              "앨범명",
              "스토어명",
              30_000L,
              LocalDateTime.now().plusDays(7),
              5,
              null,
              2000,
              "하나은행",
              "1002-123-456789",
              "홍길동");
      Buncheol buncheol = Buncheol.create(hostId, params);

      // when & then
      buncheolMapper.insert(buncheol);
      assertThat(buncheol.getId()).isNotNull();
    }
  }

  @Nested
  @DisplayName("분철 조회/수정 테스트")
  class FindAndUpdateTest {

    @Test
    void ID로_분철을_조회할_수_있다() {
      // given
      Buncheol buncheol = Buncheol.create(hostId, validParams("조회 그룹"));
      buncheolMapper.insert(buncheol);

      // when
      Buncheol found = buncheolMapper.findById(buncheol.getId()).orElseThrow();

      // then
      assertThat(found.getId()).isEqualTo(buncheol.getId());
      assertThat(found.getHostId()).isEqualTo(hostId);
      assertThat(found.getGroupName()).isEqualTo("조회 그룹");
      assertThat(found.getTitle()).isEqualTo("테스트 분철 제목");
      assertThat(found.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
    }

    @Test
    void 분철_기본정보를_수정할_수_있다() {
      // given
      Buncheol buncheol = Buncheol.create(hostId, validParams("원본 그룹"));
      buncheolMapper.insert(buncheol);

      BuncheolParams updatedParams =
          new BuncheolParams(
              null,
              "수정 그룹",
              "수정 제목",
              "수정 설명",
              "수정 굿즈",
              "수정 스토어",
              77_000L,
              LocalDateTime.now().plusDays(3),
              3,
              null,
              1800,
              "신한은행",
              "999-888-777",
              "수정예금주");

      // when
      buncheolMapper.update(buncheol.getId(), updatedParams);
      Buncheol found = buncheolMapper.findById(buncheol.getId()).orElseThrow();

      // then
      assertThat(found.getGroupName()).isEqualTo("수정 그룹");
      assertThat(found.getTitle()).isEqualTo("수정 제목");
      assertThat(found.getDescription()).isEqualTo("수정 설명");
      assertThat(found.getGoodsName()).isEqualTo("수정 굿즈");
      assertThat(found.getStoreName()).isEqualTo("수정 스토어");
      assertThat(found.getOriginalPrice()).isEqualTo(77_000L);
      assertThat(found.getShippingDeadlineDays()).isEqualTo(3);
      assertThat(found.getShippingFeePolicy().gs25ShippingFee()).isNull();
      assertThat(found.getShippingFeePolicy().cuShippingFee()).isEqualTo(1800);
      assertThat(found.getSettlementInfo().bank()).isEqualTo("신한은행");
      assertThat(found.getSettlementInfo().account()).isEqualTo("999-888-777");
      assertThat(found.getSettlementInfo().holder()).isEqualTo("수정예금주");
    }

    @Test
    void 분철_상태를_수정할_수_있다() {
      // given
      Buncheol buncheol = Buncheol.create(hostId, validParams("상태 그룹"));
      buncheolMapper.insert(buncheol);
      BuncheolStatus expectedStatus = buncheol.getStatus();
      buncheol.cancel();

      // when
      int updated = buncheolMapper.updateStatus(buncheol, expectedStatus);
      Buncheol found = buncheolMapper.findById(buncheol.getId()).orElseThrow();

      // then
      assertThat(updated).isEqualTo(1);
      assertThat(found.getStatus()).isEqualTo(BuncheolStatus.CANCELLED);
    }

    @Test
    void 예상_상태가_다르면_업데이트되지_않는다() {
      // given
      Buncheol buncheol = Buncheol.create(hostId, validParams("낙관적 잠금"));
      buncheolMapper.insert(buncheol);
      buncheol.cancel();

      // when — expectedStatus를 CLOSED로 전달 (실제는 RECRUITING)
      int updated = buncheolMapper.updateStatus(buncheol, BuncheolStatus.CLOSED);
      Buncheol found = buncheolMapper.findById(buncheol.getId()).orElseThrow();

      // then
      assertThat(updated).isEqualTo(0);
      assertThat(found.getStatus()).isEqualTo(BuncheolStatus.RECRUITING);
    }
  }
}
