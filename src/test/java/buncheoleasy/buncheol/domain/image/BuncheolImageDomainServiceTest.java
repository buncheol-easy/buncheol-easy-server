package buncheoleasy.buncheol.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuncheolImageDomainService 단위 테스트")
class BuncheolImageDomainServiceTest {

  @InjectMocks private BuncheolImageDomainService buncheolImageDomainService;

  @Mock private BuncheolImageRepository buncheolImageRepository;

  @Captor private ArgumentCaptor<List<BuncheolImage>> imagesCaptor;

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
      buncheolImageDomainService.createBuncheolImages(buncheolId, imageUrls, 0);

      // then
      then(buncheolImageRepository).should().saveAll(anyList());
    }

    @Test
    void thumbnailPosition_위치의_이미지에만_대표사진_플래그를_남기고_순서를_유지한다() {
      // given
      Long buncheolId = 1L;
      List<String> imageUrls =
          List.of(
              "https://cdn.example.com/image1.jpg",
              "https://cdn.example.com/image2.jpg",
              "https://cdn.example.com/image3.jpg");

      // when
      buncheolImageDomainService.createBuncheolImages(buncheolId, imageUrls, 1);

      // then — 저장 순서는 요청 순서 그대로, 플래그는 지정 위치 한 장에만 남는다.
      then(buncheolImageRepository).should().saveAll(imagesCaptor.capture());
      List<BuncheolImage> saved = imagesCaptor.getValue();
      assertThat(saved)
          .extracting(BuncheolImage::getImageUrl)
          .containsExactly(
              "https://cdn.example.com/image1.jpg",
              "https://cdn.example.com/image2.jpg",
              "https://cdn.example.com/image3.jpg");
      assertThat(saved)
          .extracting(BuncheolImage::isThumbnail)
          .containsExactly(false, true, false);
    }

    @Test
    void thumbnailPosition이_null이면_어떤_이미지에도_대표사진_플래그를_남기지_않는다() {
      // given
      Long buncheolId = 1L;
      List<String> imageUrls =
          List.of("https://cdn.example.com/image1.jpg", "https://cdn.example.com/image2.jpg");

      // when
      buncheolImageDomainService.createBuncheolImages(buncheolId, imageUrls, null);

      // then
      then(buncheolImageRepository).should().saveAll(imagesCaptor.capture());
      assertThat(imagesCaptor.getValue())
          .extracting(BuncheolImage::isThumbnail)
          .containsExactly(false, false);
    }
  }

  @Nested
  @DisplayName("대표사진 인덱스 검증 테스트 (validateThumbnailIndex)")
  class ValidateThumbnailIndexTest {

    @Test
    void 인덱스가_이미지_개수_범위_안이면_예외가_발생하지_않는다() {
      // when & then — 0-base 경계값(0, count-1) 모두 허용된다.
      assertThatCode(() -> buncheolImageDomainService.validateThumbnailIndex(3, 0))
          .doesNotThrowAnyException();
      assertThatCode(() -> buncheolImageDomainService.validateThumbnailIndex(3, 2))
          .doesNotThrowAnyException();
    }

    @Test
    void 인덱스가_음수면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(() -> buncheolImageDomainService.validateThumbnailIndex(3, -1))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_THUMBNAIL_INDEX_INVALID);
    }

    @Test
    void 인덱스가_이미지_개수_이상이면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(() -> buncheolImageDomainService.validateThumbnailIndex(3, 3))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_THUMBNAIL_INDEX_INVALID);
    }
  }

  @Nested
  @DisplayName("수정 시 대표사진 지정 검증 테스트 (validateThumbnailSelection)")
  class ValidateThumbnailSelectionTest {

    @Test
    void 유지하는_기존_이미지를_대표로_지정하면_통과한다() {
      // when & then
      assertThatCode(
              () ->
                  buncheolImageDomainService.validateThumbnailSelection(
                      List.of(1L, 2L), 0, 2L, null))
          .doesNotThrowAnyException();
    }

    @Test
    void 신규_이미지_범위_안의_인덱스를_대표로_지정하면_통과한다() {
      // when & then
      assertThatCode(
              () ->
                  buncheolImageDomainService.validateThumbnailSelection(
                      List.of(1L), 2, null, 1))
          .doesNotThrowAnyException();
    }

    @Test
    void 기존_이미지와_신규_인덱스를_동시에_지정하면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(
              () ->
                  buncheolImageDomainService.validateThumbnailSelection(
                      List.of(1L, 2L), 2, 1L, 0))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_THUMBNAIL_SELECTION_DUPLICATED);
    }

    @Test
    void 유지_목록에_없는_이미지를_대표로_지정하면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(
              () ->
                  buncheolImageDomainService.validateThumbnailSelection(
                      List.of(1L, 2L), 0, 99L, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_THUMBNAIL_IMAGE_INVALID);
    }

    @Test
    void 신규_이미지_범위_밖의_인덱스를_대표로_지정하면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(
              () ->
                  buncheolImageDomainService.validateThumbnailSelection(
                      List.of(1L), 2, null, 2))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_THUMBNAIL_INDEX_INVALID);
    }
  }

  @Nested
  @DisplayName("대표사진 교체 테스트 (changeThumbnail)")
  class ChangeThumbnailTest {

    private static final Long BUNCHEOL_ID = 1L;
    private static final Long IMAGE_ID = 5L;

    @Test
    void 기존_플래그를_해제한_뒤_지정_이미지를_대표사진으로_지정한다() {
      // given
      given(buncheolImageRepository.markThumbnail(BUNCHEOL_ID, IMAGE_ID)).willReturn(true);

      // when
      buncheolImageDomainService.changeThumbnail(BUNCHEOL_ID, IMAGE_ID);

      // then — clear 가 mark 보다 먼저 수행돼야 분철당 플래그 1장 불변식이 유지된다.
      InOrder order = inOrder(buncheolImageRepository);
      then(buncheolImageRepository).should(order).clearThumbnail(BUNCHEOL_ID);
      then(buncheolImageRepository).should(order).markThumbnail(BUNCHEOL_ID, IMAGE_ID);
    }

    @Test
    void 해당_분철_소유_이미지가_아니면_예외가_발생한다() {
      // given — markThumbnail 이 false 면 지정 대상이 분철 소유 이미지가 아니다.
      given(buncheolImageRepository.markThumbnail(BUNCHEOL_ID, IMAGE_ID)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> buncheolImageDomainService.changeThumbnail(BUNCHEOL_ID, IMAGE_ID))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_THUMBNAIL_IMAGE_INVALID);
    }
  }

  @Nested
  @DisplayName("대표사진 플래그 해제 테스트 (clearThumbnail)")
  class ClearThumbnailTest {

    @Test
    void 분철의_대표사진_플래그_해제를_위임한다() {
      // when
      buncheolImageDomainService.clearThumbnail(1L);

      // then
      then(buncheolImageRepository).should().clearThumbnail(1L);
    }
  }
}
