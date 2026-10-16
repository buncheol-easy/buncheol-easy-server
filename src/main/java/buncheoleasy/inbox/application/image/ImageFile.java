package buncheoleasy.inbox.application.image;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.util.Locale;
import java.util.Map;

/**
 * 공지 첨부 이미지. 🔴 <b>검증은 생성 시점(= 컨트롤러, 201 응답 전)</b>이다 — 업로더의 검사는 커밋 후
 * 비동기 리스너에서 돌아, 잘못된 확장자가 「등록 완료」 응답을 받고 이미지만 조용히 사라졌다(로그만 남음).
 * 생성자에서 막으면 관리자가 그 자리에서 400(FILE_EXTENSION_INVALID)을 본다. 허용 목록은 이 레코드가
 * 유일한 정본 — 업로더는 {@link #extension()} 을 그대로 쓰고 재검증하지 않는다(목록이 두 곳이면 발산
 * 시 이 버그가 재발한다).
 *
 * <p><b>저장 형식(Content-Type)도 확장자로 정하고, 클라가 보낸 값은 버린다</b>(#224). 보낸 값을 S3 에
 * 그대로 저장하면 {@code evil.png} 를 {@code text/html}·{@code image/svg+xml} 로 올려 이미지 주소를 웹
 * 페이지로 열 수 있었다. {@code image/} 접두 검사는 SVG 가 통과하고, 거절로 막으면 Content-Type 없는 업로드가
 * 새로 400 이 되므로 덮어쓴다.
 *
 * <p>분철 이미지 {@link buncheoleasy.buncheol.application.image.ImageFile} 과 허용 확장자·저장 형식이
 * 같아야 한다 — 한쪽을 바꾸면 다른 쪽도 바꾼다.
 *
 * @param contentType 넘긴 값은 쓰지 않는다. 확장자로 정한 값({@code image/jpeg}·{@code image/png}·
 *     {@code image/webp})이 들어간다
 */
public record ImageFile(String originalFilename, String contentType, byte[] bytes) {

  /** 허용 확장자 → S3 에 저장할 Content-Type. 키 집합이 곧 허용 목록이다. */
  private static final Map<String, String> MEDIA_TYPES =
      Map.of(
          ".jpg", "image/jpeg",
          ".jpeg", "image/jpeg",
          ".png", "image/png",
          ".webp", "image/webp");

  public ImageFile {
    if (originalFilename == null || !originalFilename.contains(".")) {
      throw new BusinessException(ErrorCode.FILE_NAME_INVALID);
    }
    String extension = extensionOf(originalFilename);
    if (!MEDIA_TYPES.containsKey(extension)) {
      throw new BusinessException(ErrorCode.FILE_EXTENSION_INVALID);
    }
    contentType = MEDIA_TYPES.get(extension);
  }

  /** 검증을 통과한 소문자 확장자(예: ".png"). 생성 시점에 보장된다. */
  public String extension() {
    return extensionOf(originalFilename);
  }

  private static String extensionOf(final String originalFilename) {
    return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
  }
}
