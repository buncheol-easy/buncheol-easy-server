package buncheoleasy.inbox.presentation;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.inbox.application.NoticeCommandService;
import buncheoleasy.inbox.application.image.ImageFile;
import buncheoleasy.inbox.dto.request.CreateNoticeRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

  private final NoticeCommandService noticeCommandService;

  /**
   * 공지 작성(multipart/form-data). 관리자(ROLE_ADMIN) 전용 — SecurityConfig 가 {@code POST /v1/notices} 를
   * {@code hasRole("ADMIN")} 으로 강제한다.
   *
   * <p>{@code request}(JSON) 외에 본문 이미지({@code image}, 최대 1장)와 홈 배너 이미지({@code bannerImage})를 선택적으로
   * 첨부할 수 있다. 두 이미지 모두 커밋 후 비동기로 S3 에 업로드된다.
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> createNotice(
      @Valid @RequestPart("request") final CreateNoticeRequest request,
      @RequestPart(value = "image", required = false) final MultipartFile image,
      @RequestPart(value = "bannerImage", required = false) final MultipartFile bannerImage) {
    final Long noticeId =
        noticeCommandService.createNotice(request, toImageFile(image), toImageFile(bannerImage));
    // 생성된 공지는 수신함 상세(GET /v1/inbox/{id})로 조회한다.
    return ResponseEntity.created(URI.create("/v1/inbox/" + noticeId)).build();
  }

  /** 공지 상단 고정 등록. 관리자(ROLE_ADMIN) 전용 — SecurityConfig 가 강제한다. */
  @PutMapping("/{noticeId}/pin")
  public ResponseEntity<Void> pinNotice(@PathVariable final Long noticeId) {
    noticeCommandService.pinNotice(noticeId);
    return ResponseEntity.noContent().build();
  }

  /** 공지 상단 고정 해제. 관리자(ROLE_ADMIN) 전용 — SecurityConfig 가 강제한다. */
  @DeleteMapping("/{noticeId}/pin")
  public ResponseEntity<Void> unpinNotice(@PathVariable final Long noticeId) {
    noticeCommandService.unpinNotice(noticeId);
    return ResponseEntity.noContent().build();
  }

  // 파트 누락(null)이나 빈 파일이면 첨부 없음(null)으로 본다. 읽기 실패만 FILE_READ_FAILED 로 변환한다.
  private ImageFile toImageFile(final MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return null;
    }
    try {
      return new ImageFile(file.getOriginalFilename(), file.getContentType(), file.getBytes());
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.FILE_READ_FAILED);
    }
  }
}
