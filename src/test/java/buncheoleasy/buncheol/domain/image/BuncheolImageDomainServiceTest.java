package buncheoleasy.buncheol.domain.image;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolImageDomainService 단위 테스트")
class BuncheolImageDomainServiceTest {

  @InjectMocks private BuncheolImageDomainService buncheolImageDomainService;

  @Mock private BuncheolImageRepository buncheolImageRepository;

  @Nested
  @DisplayName("이미지 개수 검증 테스트")
  class ValidateImageCountTest {

    @Test
    void 이미지_개수가_1개_이상_5개_이하면_예외가_발생하지_않는다() {
      // when & then
      assertThatCode(() -> buncheolImageDomainService.validateImageCount(1))
          .doesNotThrowAnyException();
      assertThatCode(() -> buncheolImageDomainService.validateImageCount(5))
          .doesNotThrowAnyException();
    }

    @Test
    void 이미지_개수가_0이면_최소_1장_예외가_발생한다() {
      // when & then
      assertThatThrownBy(() -> buncheolImageDomainService.validateImageCount(0))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_REQUIRED);
    }

    @Test
    void 이미지_개수가_6개_이상이면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(() -> buncheolImageDomainService.validateImageCount(6))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED);
    }
  }

  @Nested
  @DisplayName("수정 이미지 검증 테스트 (validateModifyImageCount)")
  class ValidateModifyImageCountTest {

    private static final Long BUNCHEOL_ID = 1L;

    private void givenExistingImageIds(final Long... ids) {
      List<BuncheolImage> images =
          List.of(ids).stream()
              .map(
                  id -> {
                    BuncheolImage image = mock(BuncheolImage.class);
                    given(image.getId()).willReturn(id);
                    return image;
                  })
              .toList();
      given(buncheolImageRepository.findAllByBuncheolIdOrderByIdAsc(BUNCHEOL_ID)).willReturn(images);
    }

    @Test
    void 유지_이미지와_신규_이미지_합이_1장_이상_5장_이하면_통과한다() {
      givenExistingImageIds(1L, 2L, 3L);

      // 유지 2 + 신규 0 = 2
      assertThatCode(
              () ->
                  buncheolImageDomainService.validateModifyImageCount(
                      BUNCHEOL_ID, List.of(1L, 2L), 0))
          .doesNotThrowAnyException();
    }

    @Test
    void 유지도_신규도_없으면_최소_1장_예외가_발생한다() {
      givenExistingImageIds(1L, 2L, 3L);

      assertThatThrownBy(
              () -> buncheolImageDomainService.validateModifyImageCount(BUNCHEOL_ID, List.of(), 0))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_REQUIRED);
    }

    @Test
    void 유지_이미지에_해당_분철의_이미지가_아닌_id가_있으면_예외가_발생한다() {
      givenExistingImageIds(1L, 2L, 3L);

      // 99L 은 이 분철의 이미지가 아니다 → 개수만 보면 1장이지만 거부해야 한다.
      assertThatThrownBy(
              () ->
                  buncheolImageDomainService.validateModifyImageCount(
                      BUNCHEOL_ID, List.of(99L), 0))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_KEEP_IMAGE_INVALID);
    }

    @Test
    void 유지_id가_중복돼도_실제_유지_장수로_환산해_검증한다() {
      givenExistingImageIds(1L, 2L, 3L);

      // [1,1] 은 실제로 1장 → 신규 0 → 총 1장이라 통과(중복이 최소 1장 우회에 쓰이지 않음)
      assertThatCode(
              () ->
                  buncheolImageDomainService.validateModifyImageCount(
                      BUNCHEOL_ID, List.of(1L, 1L), 0))
          .doesNotThrowAnyException();
    }

    @Test
    void 유지와_신규_합이_5장_초과면_예외가_발생한다() {
      givenExistingImageIds(1L, 2L, 3L);

      // 유지 3 + 신규 3 = 6
      assertThatThrownBy(
              () ->
                  buncheolImageDomainService.validateModifyImageCount(
                      BUNCHEOL_ID, List.of(1L, 2L, 3L), 3))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_LIMIT_EXCEEDED);
    }
  }

  @Nested
  @DisplayName("이미지 저장 테스트")
  class CreateBuncheolImagesTest {

    @Test
    void 이미지_URL_목록으로_이미지를_저장한다() {
      // given
      Long buncheolId = 1L;
      List<String> imageUrls =
          List.of("https://cdn.example.com/image1.jpg", "https://cdn.example.com/image2.jpg");

      // when
      buncheolImageDomainService.createBuncheolImages(buncheolId, imageUrls);

      // then
      then(buncheolImageRepository).should().saveAll(anyList());
    }
  }
}
