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

  @Test
  void 자리_삭제가_FK_로_막히면_500이_아니라_기록이_있어_뺄_수_없다로_거부한다() {
    given(jpaBuncheolMemberRepository.deleteIfUnreferenced(7L, 10L))
        .willThrow(new DataIntegrityViolationException("fk_some_new_reference"));

    assertThatThrownBy(() -> adapter.deleteIfUnreferenced(7L, 10L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_REMOVE_HAS_HISTORY);
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
