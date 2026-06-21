package buncheoleasy.buncheol.application.bookmark;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.domain.BuncheolDomainService;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmark;
import buncheoleasy.buncheol.domain.bookmark.BuncheolBookmarkRepository;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolBookmarkService 단위 테스트")
class BuncheolBookmarkServiceTest {

  private static final Long USER_ID = 1L;
  private static final Long BUNCHEOL_ID = 10L;

  @InjectMocks private BuncheolBookmarkService buncheolBookmarkService;

  @Mock private BuncheolBookmarkRepository buncheolBookmarkRepository;
  @Mock private BuncheolDomainService buncheolDomainService;

  @Nested
  @DisplayName("찜 등록 테스트")
  class AddBookmarkTest {

    @Test
    void 처음_찜하는_분철이면_저장한다() {
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(mock(Buncheol.class));
      given(buncheolBookmarkRepository.existsByUserIdAndBuncheolId(USER_ID, BUNCHEOL_ID))
          .willReturn(false);

      buncheolBookmarkService.addBookmark(USER_ID, BUNCHEOL_ID);

      then(buncheolBookmarkRepository).should().save(any(BuncheolBookmark.class));
    }

    @Test
    void 존재하지_않는_분철이면_예외가_발생한다() {
      willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND))
          .given(buncheolDomainService)
          .getBuncheol(BUNCHEOL_ID);

      assertThatThrownBy(() -> buncheolBookmarkService.addBookmark(USER_ID, BUNCHEOL_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_NOT_FOUND);

      then(buncheolBookmarkRepository).should(never()).save(any());
    }

    @Test
    void 이미_찜한_분철이면_409_예외가_발생한다() {
      given(buncheolDomainService.getBuncheol(BUNCHEOL_ID)).willReturn(mock(Buncheol.class));
      given(buncheolBookmarkRepository.existsByUserIdAndBuncheolId(USER_ID, BUNCHEOL_ID))
          .willReturn(true);

      assertThatThrownBy(() -> buncheolBookmarkService.addBookmark(USER_ID, BUNCHEOL_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_BOOKMARK_ALREADY_EXISTS);

      then(buncheolBookmarkRepository).should(never()).save(any());
    }
  }

  @Nested
  @DisplayName("찜 해제 테스트")
  class RemoveBookmarkTest {

    @Test
    void 본인_찜이_존재하면_삭제한다() {
      given(buncheolBookmarkRepository.deleteByUserIdAndBuncheolId(USER_ID, BUNCHEOL_ID))
          .willReturn(1);

      assertThatCode(() -> buncheolBookmarkService.removeBookmark(USER_ID, BUNCHEOL_ID))
          .doesNotThrowAnyException();
    }

    @Test
    void 찜이_없으면_404_예외가_발생한다() {
      given(buncheolBookmarkRepository.deleteByUserIdAndBuncheolId(USER_ID, BUNCHEOL_ID))
          .willReturn(0);

      assertThatThrownBy(() -> buncheolBookmarkService.removeBookmark(USER_ID, BUNCHEOL_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_BOOKMARK_NOT_FOUND);
    }
  }
}
