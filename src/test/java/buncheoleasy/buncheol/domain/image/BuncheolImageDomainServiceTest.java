package buncheoleasy.buncheol.domain.image;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.then;

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
    void 이미지_개수가_5개_이하면_예외가_발생하지_않는다() {
      // when & then
      assertThatCode(() -> buncheolImageDomainService.validateImageCount(5))
          .doesNotThrowAnyException();
    }

    @Test
    void 이미지_개수가_0이면_예외가_발생하지_않는다() {
      // when & then
      assertThatCode(() -> buncheolImageDomainService.validateImageCount(0))
          .doesNotThrowAnyException();
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
