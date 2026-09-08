package buncheoleasy.inbox.application.image;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
