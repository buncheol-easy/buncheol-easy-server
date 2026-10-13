package buncheoleasy.buncheol.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("분철 ImageFile 생성 검증")
class ImageFileTest {

  // 🔴 검증이 생성 시점(201·204 전)에 도는 것 자체가 이 테스트의 요지다 — 업로더 단계로 미루면
  // 잘못된 파일이 성공 응답을 받고 사진만 조용히 사라진다.
  @Test
  void 허용되지_않은_확장자는_생성_시점에_거부된다() {
    assertThatThrownBy(() -> new ImageFile("s.gif", "image/gif", new byte[] {1}))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FILE_EXTENSION_INVALID);
  }

  @Test
  void 확장자가_없는_파일명은_생성_시점에_거부된다() {
    assertThatThrownBy(() -> new ImageFile("album-cover", "image/jpeg", new byte[] {1}))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FILE_NAME_INVALID);
  }

  @Test
  void 파일명이_없으면_생성_시점에_거부된다() {
    assertThatThrownBy(() -> new ImageFile(null, "image/jpeg", new byte[] {1}))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FILE_NAME_INVALID);
  }

  // 앱은 항상 buncheol-N.jpg 로 보낸다(UploadProductForm) — 이 이름이 막히면 개최 화면이 통째로 깨진다.
  @ParameterizedTest
  @CsvSource({
    "buncheol-1.jpg, .jpg",
    "album.JPG, .jpg",
    "album.jpeg, .jpeg",
    "album.PNG, .png",
    "album.webp, .webp",
    "my.album.Webp, .webp"
  })
  void 허용_확장자는_대소문자_무관하게_통과하고_소문자_확장자를_돌려준다(
      final String filename, final String expectedExtension) {
    ImageFile imageFile = new ImageFile(filename, "image/jpeg", new byte[] {1});

    assertThat(imageFile.extension()).isEqualTo(expectedExtension);
  }
}
