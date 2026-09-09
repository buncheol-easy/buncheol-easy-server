package buncheoleasy.inbox.application.image;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import java.util.Locale;

/**
 * 공지 첨부 이미지. 🔴 <b>검증은 생성 시점(= 컨트롤러, 201 응답 전)</b>이다 — 업로더의 검사는 커밋 후
 * 비동기 리스너에서 돌아, 잘못된 확장자가 「등록 완료」 응답을 받고 이미지만 조용히 사라졌다(로그만 남음).
 * 생성자에서 막으면 관리자가 그 자리에서 400(FILE_EXTENSION_INVALID)을 본다. 허용 목록은 이 레코드가
 * 유일한 정본 — 업로더는 {@link #extension()} 을 그대로 쓰고 재검증하지 않는다(목록이 두 곳이면 발산
 * 시 이 버그가 재발한다).
 */
public record ImageFile(String originalFilename, String contentType, byte[] bytes) {

  private static final List<String> ALLOWED_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp");

  public ImageFile {
    if (originalFilename == null || !originalFilename.contains(".")) {
      throw new BusinessException(ErrorCode.FILE_NAME_INVALID);
    }
    if (!ALLOWED_EXTENSIONS.contains(extensionOf(originalFilename))) {
      throw new BusinessException(ErrorCode.FILE_EXTENSION_INVALID);
    }
  }

  /** 검증을 통과한 소문자 확장자(예: ".png"). 생성 시점에 보장된다. */
  public String extension() {
    return extensionOf(originalFilename);
  }

  private static String extensionOf(final String originalFilename) {
    return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
  }
}
