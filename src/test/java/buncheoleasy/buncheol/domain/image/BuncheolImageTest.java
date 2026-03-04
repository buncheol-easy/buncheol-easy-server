package buncheoleasy.buncheol.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("BuncheolImage 도메인 테스트")
class BuncheolImageTest {

  private static final Long BUNCHEOL_ID = 1L;
  private static final String VALID_URL = "https://cdn.example.com/images/album-cover.jpg";

  @Nested
  @DisplayName("BuncheolImage 생성 테스트")
  class CreateTest {

    @Test
    void 유효한_파라미터로_이미지_생성에_성공한다() {
      // when
      BuncheolImage image = BuncheolImage.create(BUNCHEOL_ID, VALID_URL);

      // then
      assertThat(image.getBuncheolId()).isEqualTo(BUNCHEOL_ID);
      assertThat(image.getImageUrl()).isEqualTo(VALID_URL);
    }

    @Test
    void buncheolId가_null이면_예외가_발생한다() {
      // when & then
      assertThatThrownBy(() -> BuncheolImage.create(null, VALID_URL))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }
  }

  @Nested
  @DisplayName("이미지 URL 검증 테스트")
  class ValidateImageUrlTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 이미지_URL이_null이거나_빈_값이면_예외가_발생한다(String imageUrl) {
      // when & then
      assertThatThrownBy(() -> BuncheolImage.create(BUNCHEOL_ID, imageUrl))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_URL_REQUIRED);
    }

    @Test
    void 이미지_URL이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longUrl = "https://cdn.example.com/" + "a".repeat(500);

      // when & then
      assertThatThrownBy(() -> BuncheolImage.create(BUNCHEOL_ID, longUrl))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_IMAGE_URL_LENGTH_INVALID);
    }

    @Test
    void 이미지_URL이_최대_길이_이하면_유효하다() {
      // given
      String maxLengthUrl = "https://cdn.example.com/" + "a".repeat(476);

      // when & then
      assertThatCode(() -> BuncheolImage.create(BUNCHEOL_ID, maxLengthUrl))
          .doesNotThrowAnyException();
    }
  }
}
