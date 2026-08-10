package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.BuncheolDetailQueryService;
import buncheoleasy.buncheol.application.BuncheolListQueryService;
import buncheoleasy.buncheol.application.BuncheolManagementQueryService;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.MyHostedBuncheolQueryService;
import buncheoleasy.buncheol.application.image.ImageFile;
import buncheoleasy.buncheol.domain.BuncheolListCursor;
import buncheoleasy.buncheol.dto.request.BuncheolModifyRequest;
import buncheoleasy.buncheol.dto.request.BuncheolSearchCondition;
import buncheoleasy.buncheol.dto.request.HoldBuncheolRequest;
import buncheoleasy.buncheol.dto.response.BuncheolConfirmResponse;
import buncheoleasy.buncheol.dto.response.BuncheolDetailResponse;
import buncheoleasy.buncheol.dto.response.BuncheolManagementResponse;
import buncheoleasy.buncheol.dto.response.BuncheolSummaryResponse;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
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
  private final BuncheolManagementQueryService buncheolManagementQueryService;

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
            BuncheolListCursor.parse(cursor),
            size));
  }

  /** 마이페이지 - 내가 개최한 분철 목록 조회 API. 최신 개최순으로 정렬한다. */
  @GetMapping("/me")
  public ResponseEntity<List<MyHostedBuncheolResponse>> getMyHostedBuncheols(
      @AuthenticationPrincipal final Long hostId) {
    return ResponseEntity.ok(myHostedBuncheolQueryService.getMyHostedBuncheols(hostId));
  }

  /** 분철 단건 상세 조회 (비로그인 허용). 멤버별 가격·참여 가능 여부, 최소 진행 인원·현재 확정 인원, 로그인 유저의 내 참여 현황을 포함한다. */
  @GetMapping("/{id}")
  public ResponseEntity<BuncheolDetailResponse> getBuncheolDetail(
      @AuthenticationPrincipal final Long userId, @PathVariable final Long id) {
    return ResponseEntity.ok(buncheolDetailQueryService.getDetail(id, userId));
  }

  /**
   * 개최자(운영자) 분철 관리 화면 조회 API. 호스트 본인만 호출 가능 (그 외 {@code BUNCHEOL_NO_PERMISSION}). 분철 기본 정보와 함께
   * 입금확인 대상(입금확인중)·확정 참여자 목록을 환불 계좌·배송 스냅샷(확정 후 생성)과 함께 노출한다. 운영자는 이 화면에서 입금확인·환불을 처리한다.
   */
  @GetMapping("/{id}/management")
  public ResponseEntity<BuncheolManagementResponse> getBuncheolManagement(
      @AuthenticationPrincipal final Long hostId, @PathVariable final Long id) {
    return ResponseEntity.ok(buncheolManagementQueryService.getManagement(id, hostId));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> holdBuncheol(
      @AuthenticationPrincipal final Long hostId,
      @Valid @RequestPart("request") final HoldBuncheolRequest request,
      @RequestPart(value = "images") final List<MultipartFile> images) {
    buncheolService.holdBuncheol(hostId, request, toImageFiles(images));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /**
   * C2C 개최자 성사 확정 API (docs/46 §4.1). 신청자 전원을 일괄 입금 기한(24h)과 함께 입금 대기로 전이하고, 입금 안내가 발송된다. 정원
   * 미달 재량 확정·마감 전 조기 확정 허용.
   */
  @PostMapping("/{id}/confirm")
  public ResponseEntity<BuncheolConfirmResponse> confirmRecruitment(
      @AuthenticationPrincipal final Long hostId, @PathVariable final Long id) {
    return ResponseEntity.ok(
        BuncheolConfirmResponse.from(buncheolService.confirmRecruitment(hostId, id)));
  }

  /**
   * C2C 입금 수집 종료(부분 확정) API (docs/46 §7.1-6). 입금 기한 경과로 미입금 슬롯이 정리된 뒤, 확정된 참여만으로 진행을 확정한다.
   * 미입금 활성 참여가 남아 있으면 실패한다(보냈어요 잔여는 확인/반려로 먼저 정리).
   */
  @PostMapping("/{id}/finalize-collected")
  public ResponseEntity<Void> finalizeCollected(
      @AuthenticationPrincipal final Long hostId, @PathVariable final Long id) {
    buncheolService.finalizeCollected(hostId, id);
    return ResponseEntity.noContent().build();
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
