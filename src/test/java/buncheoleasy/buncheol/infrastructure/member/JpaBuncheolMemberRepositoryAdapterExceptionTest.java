package buncheoleasy.buncheol.infrastructure.member;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * H2 로는 만들 수 없는 제약 위반의 변환을 고정한다 — 조건부 DELETE 가 아는 참조(참여·참여 코드)는 FK 까지 가지 않으므로, 모르는
 * 참조가 생겼을 때의 경로는 실제 DB 로 재현할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JpaBuncheolMemberRepositoryAdapter 제약 위반 변환 테스트")
class JpaBuncheolMemberRepositoryAdapterExceptionTest {

  @InjectMocks private JpaBuncheolMemberRepositoryAdapter adapter;

  @Mock private JpaBuncheolMemberRepository jpaBuncheolMemberRepository;

  // MySQL 1451 형식. 자리를 가리키는 FK 두 개(schema.sql participations·participation_codes)가 대상이다.
  @ParameterizedTest
  @ValueSource(strings = {"fk_participations_buncheol_member", "fk_participation_codes_member"})
  void 자리_삭제가_자리_FK_로_막히면_500이_아니라_기록이_있어_뺄_수_없다로_거부한다(final String fk) {
    given(jpaBuncheolMemberRepository.deleteIfUnreferenced(7L, 10L))
        .willThrow(
            new DataIntegrityViolationException(
                "could not execute statement [Cannot delete or update a parent row: a foreign key"
                    + " constraint fails (`buncheoleasy`.`x`, CONSTRAINT `"
                    + fk
                    + "` FOREIGN KEY (`buncheol_member_id`)"
                    + " REFERENCES `buncheol_members` (`id`))]"));

    assertThatThrownBy(() -> adapter.deleteIfUnreferenced(7L, 10L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_REMOVE_HAS_HISTORY);
  }

  // DELETE 는 flushAutomatically 라 같은 트랜잭션의 다른 쓰기 실패가 여기서 터질 수 있다 — 「기록 있음」으로 덮지 않는다.
  @Test
  void 자리_FK_가_아닌_무결성_위반은_그대로_올린다() {
    DataIntegrityViolationException other =
        new DataIntegrityViolationException(
            "could not execute statement [Duplicate entry '3' for key"
                + " 'participations.uq_participations_active_member']");
    given(jpaBuncheolMemberRepository.deleteIfUnreferenced(7L, 10L)).willThrow(other);

    assertThatThrownBy(() -> adapter.deleteIfUnreferenced(7L, 10L)).isSameAs(other);
  }

  @Test
  void 자리_유니크_위반은_중복_멤버로_거부한다() {
    given(jpaBuncheolMemberRepository.saveAndFlush(any()))
        .willThrow(
            new DataIntegrityViolationException(
                "Duplicate entry '10-2' for key"
                    + " 'buncheol_members.UQ_BUNCHEOL_MEMBERS_BUNCHEOL_MEMBER'"));

    assertThatThrownBy(() -> adapter.save(BuncheolMember.create(10L, 2L, 0L)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_ALREADY_EXISTS);
  }

  // 그룹 멤버 FK 같은 다른 위반을 중복으로 오분류하면 원인이 가려진다.
  @Test
  void 다른_제약_위반은_중복으로_바꾸지_않는다() {
    DataIntegrityViolationException fkViolation =
        new DataIntegrityViolationException("fk_buncheol_members_member");
    given(jpaBuncheolMemberRepository.saveAndFlush(any())).willThrow(fkViolation);

    assertThatThrownBy(() -> adapter.save(BuncheolMember.create(10L, 2L, 0L)))
        .isSameAs(fkViolation);
  }
}
