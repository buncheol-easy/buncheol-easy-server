package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.BuncheolDetailQueryService;
import buncheoleasy.buncheol.application.BuncheolListQueryService;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.ImageFile;
import buncheoleasy.buncheol.application.MyHostedBuncheolQueryService;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.global.page.Cursor;
import buncheoleasy.global.page.CursorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/buncheols")
@RequiredArgsConstructor
@Validated
public class BuncheolController {

  private final BuncheolService buncheolService;
  private final MyHostedBuncheolQueryService myHostedBuncheolQueryService;
  private final BuncheolListQueryService buncheolListQueryService;
  private final BuncheolDetailQueryService buncheolDetailQueryService;

  /**
   * 공개 분철 목록 조회 (비로그인 허용). 그룹/멤버/키워드 필터 + 커서 기반 무한스크롤.
   *
   * <p>비로그인 호출 시 익명 principal(문자열) 은 {@code Long} 캐스팅에 실패해 {@code userId} 가 null 로 들어온다 ({@link
   * AuthenticationPrincipal#errorOnInvalidType()} 기본값 false).
   */
  @GetMapping
  public ResponseEntity<CursorResponse<BuncheolSummaryResponse>> searchBuncheols(
      @AuthenticationPrincipal final Long userId,
      @RequestParam(required = false) final Long groupId,
      @RequestParam(required = false) final Long memberId,
      @RequestParam(required = false) @Size(max = 100) final String keyword,
      @RequestParam(required = false) final String cursor,
      @RequestParam(defaultValue = "20") final int size) {
    return ResponseEntity.ok(
        buncheolListQueryService.search(
            userId,
            new BuncheolSearchCondition(groupId, memberId, keyword),
            Cursor.parse(cursor),
            size));
  }

  /** 마이페이지 - 내가 개최한 분철 목록 조회 API. 최신 개최순으로 정렬한다. */
  @GetMapping("/me")
  public ResponseEntity<List<MyHostedBuncheolResponse>> getMyHostedBuncheols(
      @AuthenticationPrincipal final Long hostId) {
    return ResponseEntity.ok(myHostedBuncheolQueryService.getMyHostedBuncheols(hostId));
  }

  /** 분철 단건 상세 조회 (비로그인 허용). 멤버별 실시간 top 3 입찰·활성 참여자 수와 로그인 유저의 내 입찰 현황(rank 포함)을 포함한다. */
  @GetMapping("/{id}")
  public ResponseEntity<BuncheolDetailResponse> getBuncheolDetail(
      @AuthenticationPrincipal final Long userId, @PathVariable final Long id) {
    return ResponseEntity.ok(buncheolDetailQueryService.getDetail(id, userId));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> holdBuncheol(
      @AuthenticationPrincipal final Long hostId,
      @Valid @RequestPart("request") final HoldBuncheolRequest request,
      @RequestPart(value = "images", required = false) final List<MultipartFile> images) {
    buncheolService.holdBuncheol(hostId, request, toImageFiles(images));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> modifyBuncheol(
      @AuthenticationPrincipal final Long hostId,
      @PathVariable final Long id,
      @Valid @RequestPart("request") final BuncheolModifyRequest request,
      @RequestPart(value = "images", required = false) final List<MultipartFile> images) {
    buncheolService.modifyBuncheol(hostId, id, request, toImageFiles(images));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> cancelBuncheol(
      @AuthenticationPrincipal final Long hostId, @PathVariable final Long id) {
    buncheolService.cancelBuncheol(hostId, id);
    return ResponseEntity.noContent().build();
  }

  private List<ImageFile> toImageFiles(final List<MultipartFile> files) {
    if (files == null) {
      return List.of();
    }
    return files.stream().map(this::toImageFile).toList();
  }

  private ImageFile toImageFile(final MultipartFile file) {
    try {
      return new ImageFile(file.getOriginalFilename(), file.getContentType(), file.getBytes());
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.FILE_READ_FAILED);
    }
  }
}
