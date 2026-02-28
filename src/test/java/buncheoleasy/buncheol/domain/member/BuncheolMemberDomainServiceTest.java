package buncheoleasy.buncheol.domain.member;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.then;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolMemberDomainService 단위 테스트")
class BuncheolMemberDomainServiceTest {

    @InjectMocks
    private BuncheolMemberDomainService buncheolMemberDomainService;

    @Mock
    private BuncheolMemberRepository buncheolMemberRepository;

    @Nested
    @DisplayName("분철 멤버 저장 테스트")
    class CreateBuncheolMembersTest {

        @Test
        void 유효한_멤버_목록으로_저장에_성공한다() {
            // given
            Long buncheolId = 1L;
            List<BuncheolMemberParams> params = List.of(
                    new BuncheolMemberParams(null, "멤버A", 50_000L, false, null),
                    new BuncheolMemberParams(null, "멤버B", 30_000L, false, null)
            );

            // when
            buncheolMemberDomainService.createBuncheolMembers(buncheolId, params);

            // then
            then(buncheolMemberRepository).should().saveAll(anyList());
        }

        @Test
        void 멤버_목록이_null이면_예외가_발생한다() {
            // given
            Long buncheolId = 1L;

            // when & then
            assertThatThrownBy(() -> buncheolMemberDomainService.createBuncheolMembers(buncheolId, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_REQUIRED);
        }

        @Test
        void 멤버_목록이_비어있으면_예외가_발생한다() {
            // given
            Long buncheolId = 1L;

            // when & then
            assertThatThrownBy(() -> buncheolMemberDomainService.createBuncheolMembers(buncheolId, Collections.emptyList()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_REQUIRED);
        }
    }
}
