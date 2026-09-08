package buncheoleasy.inbox.application.image;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.List;
import java.util.Locale;

/**
 * 공지 첨부 이미지. 🔴 <b>검증은 생성 시점(= 컨트롤러, 201 응답 전)</b>이다 — 업로더의 검사는 커밋 후
 * 비동기 리스너에서 돌아, 잘못된 확장자가 「등록 완료」 응답을 받고 이미지만 조용히 사라졌다(로그만 남음).
 * 생성자에서 막으면 관리자가 그 자리에서 400(FILE_EXTENSION_INVALID)을 본다. 업로더의 중복 검사는
 * 방어선으로 유지한다.
 */
public record ImageFile(String originalFilename, String contentType, byte[] bytes) {

  private static final List<String> ALLOWED_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp");

  public ImageFile {
    if (originalFilename == null || !originalFilename.contains(".")) {
      throw new BusinessException(ErrorCode.FILE_NAME_INVALID);
    }
    String extension =
        originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new BusinessException(ErrorCode.FILE_EXTENSION_INVALID);
    }
  }
}
