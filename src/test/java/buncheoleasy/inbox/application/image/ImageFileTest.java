package buncheoleasy.inbox.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("공지 ImageFile 생성 검증")
class ImageFileTest {

  // 🔴 검증이 생성 시점(201 전)에 도는 것 자체가 이 테스트의 요지다 — 업로더 단계로 미루면
  // 잘못된 파일이 「등록 완료」를 받고 이미지만 조용히 사라진다.
  @Test
  void 허용되지_않은_확장자는_생성_시점에_거부된다() {
    assertThatThrownBy(() -> new ImageFile("banner.gif", "image/gif", new byte[] {1}))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FILE_EXTENSION_INVALID);
  }

  @Test
  void 확장자가_없는_파일명은_생성_시점에_거부된다() {
    assertThatThrownBy(() -> new ImageFile("banner", "image/png", new byte[] {1}))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FILE_NAME_INVALID);
  }

  @Test
  void 허용_확장자는_대소문자_무관하게_통과한다() {
    assertThatCode(() -> new ImageFile("banner.PNG", "image/png", new byte[] {1}))
        .doesNotThrowAnyException();
  }

  // #224 — 보낸 Content-Type 을 그대로 저장하면 png 이름의 HTML·SVG 가 이미지 주소에서 웹 페이지로 열린다.
  // 거절이 아니라 덮어쓰기라 헤더가 없거나 엉뚱해도 생성은 성공해야 한다(새 400 없음).
  @ParameterizedTest
  @CsvSource(
      value = {
        "evil.png, text/html, image/png",
        "evil.png, image/svg+xml, image/png",
        "a.jpg, NULL, image/jpeg",
        "a.JPEG, application/octet-stream, image/jpeg",
        "a.webp, image/png, image/webp",
        "buncheol-1.jpg, image/jpeg, image/jpeg"
      },
      nullValues = "NULL")
  void 저장_형식은_보낸_Content_Type_과_무관하게_확장자로_정한다(
      final String filename, final String sentContentType, final String expectedContentType) {
    ImageFile imageFile = new ImageFile(filename, sentContentType, new byte[] {1});

    assertThat(imageFile.contentType()).isEqualTo(expectedContentType);
  }
}
