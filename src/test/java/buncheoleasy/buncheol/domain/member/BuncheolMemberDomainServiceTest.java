package buncheoleasy.buncheol.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolMemberDomainService 단위 테스트")
class BuncheolMemberDomainServiceTest {

  @InjectMocks private BuncheolMemberDomainService buncheolMemberDomainService;

  @Mock private BuncheolMemberRepository buncheolMemberRepository;

  @Nested
  @DisplayName("분철 멤버 저장 테스트")
  class CreateBuncheolMembersTest {

    @Test
    void 유효한_멤버_목록으로_저장에_성공한다() {
      // given
      Long buncheolId = 1L;
      List<BuncheolMemberParams> params =
          List.of(new BuncheolMemberParams(1L, 10_000L), new BuncheolMemberParams(2L, 5_000L));

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
      assertThatThrownBy(
              () ->
                  buncheolMemberDomainService.createBuncheolMembers(
                      buncheolId, Collections.emptyList()))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_REQUIRED);
    }

    @Test
    void 무료_슬롯과_유료_슬롯을_섞어도_저장에_성공한다() {
      // given
      Long buncheolId = 1L;
      List<BuncheolMemberParams> params =
          List.of(new BuncheolMemberParams(1L, 0L), new BuncheolMemberParams(2L, 5_000L));

      // when
      buncheolMemberDomainService.createBuncheolMembers(buncheolId, params);

      // then
      then(buncheolMemberRepository).should().saveAll(anyList());
    }

    @Test
    void 전_슬롯이_무료면_저장에_성공한다() {
      // given
      Long buncheolId = 1L;
      List<BuncheolMemberParams> params =
          List.of(new BuncheolMemberParams(1L, 0L), new BuncheolMemberParams(2L, 0L));

      // when
      buncheolMemberDomainService.createBuncheolMembers(buncheolId, params);

      // then
      then(buncheolMemberRepository).should().saveAll(anyList());
    }

    @Test
    void 단일_무료_슬롯이면_저장에_성공한다() {
      // given
      Long buncheolId = 1L;
      List<BuncheolMemberParams> params = List.of(new BuncheolMemberParams(1L, 0L));

      // when
      buncheolMemberDomainService.createBuncheolMembers(buncheolId, params);

      // then
      then(buncheolMemberRepository).should().saveAll(anyList());
    }

    @Test
    void 전_슬롯이_유료면_저장에_성공한다() {
      // given
      Long buncheolId = 1L;
      List<BuncheolMemberParams> params =
          List.of(new BuncheolMemberParams(1L, 10_000L), new BuncheolMemberParams(2L, 5_000L));

      // when
      buncheolMemberDomainService.createBuncheolMembers(buncheolId, params);

      // then
      then(buncheolMemberRepository).should().saveAll(anyList());
    }
  }

  @Nested
  @DisplayName("자리 다시 넣기 테스트")
  class AddOpenSlotTest {

    @Test
    void 선착순_자리로_저장한다() {
      given(buncheolMemberRepository.save(any()))
          .willAnswer(invocation -> invocation.getArgument(0));

      BuncheolMember saved = buncheolMemberDomainService.addOpenSlot(1L, 2L, 12_300L);

      assertThat(saved.getAccessType()).isEqualTo(BuncheolMemberAccessType.OPEN);
      assertThat(saved.getPrice()).isEqualTo(12_300L);
    }

    // 개최와 같은 가격 규칙 — 100원 단위가 아니면 저장하기 전에 막힌다.
    @Test
    void 가격이_100원_단위가_아니면_저장하지_않고_거부한다() {
      assertThatThrownBy(() -> buncheolMemberDomainService.addOpenSlot(1L, 2L, 12_350L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_PRICE_INVALID);

      then(buncheolMemberRepository).should(never()).save(any());
    }
  }

  @Nested
  @DisplayName("자리 닫기·다시 열기·잠금 조회 테스트")
  class CloseAndReopenTest {

    @Test
    void 닫기는_기존_접근_정책_CAS_로_CLOSED_를_요청한다() {
      given(
              buncheolMemberRepository.changeAccessTypeIfUnoccupied(
                  7L, 1L, BuncheolMemberAccessType.CLOSED))
          .willReturn(true);

      assertThat(buncheolMemberDomainService.closeIfUnoccupied(7L, 1L)).isTrue();
    }

    @Test
    void 다시_열기는_가격을_먼저_검사하고_CAS_를_부른다() {
      given(buncheolMemberRepository.reopenIfClosed(7L, 1L, 15_000L)).willReturn(true);

      assertThat(buncheolMemberDomainService.reopenClosedSlot(7L, 1L, 15_000L)).isTrue();
    }

    // UPDATE 는 엔티티 검증을 거치지 않는다 — 개최와 같은 가격 규칙을 여기서 지켜야 한다.
    @Test
    void 다시_열기_가격이_100원_단위가_아니면_쓰지_않고_거부한다() {
      assertThatThrownBy(() -> buncheolMemberDomainService.reopenClosedSlot(7L, 1L, 15_050L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_PRICE_INVALID);

      then(buncheolMemberRepository).should(never()).reopenIfClosed(any(), any(), anyLong());
    }

    @Test
    void 가격_바꾸기는_가격을_먼저_검사하고_조건부_UPDATE_를_부른다() {
      given(buncheolMemberRepository.changePriceIfUnoccupied(7L, 1L, 15_000L)).willReturn(true);

      assertThat(buncheolMemberDomainService.changePriceIfUnoccupied(7L, 1L, 15_000L)).isTrue();
    }

    // UPDATE 는 엔티티 검증을 거치지 않는다 — 개최와 같은 가격 규칙을 여기서 지켜야 한다.
    @ParameterizedTest
    @ValueSource(longs = {15_050L, -100L})
    void 가격_바꾸기_가격이_규칙에_어긋나면_쓰지_않고_거부한다(final long price) {
      assertThatThrownBy(() -> buncheolMemberDomainService.changePriceIfUnoccupied(7L, 1L, price))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_MEMBER_PRICE_INVALID);

      then(buncheolMemberRepository)
          .should(never())
          .changePriceIfUnoccupied(any(), any(), anyLong());
    }

    @Test
    void 잠금_조회로_자리가_없으면_없는_멤버로_거부한다() {
      given(buncheolMemberRepository.findByIdAndBuncheolIdForShare(7L, 1L))
          .willReturn(Optional.empty());

      assertThatThrownBy(() -> buncheolMemberDomainService.getBuncheolMemberForShare(7L, 1L))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.PARTICIPATION_MEMBER_NOT_FOUND);
    }
  }
}
