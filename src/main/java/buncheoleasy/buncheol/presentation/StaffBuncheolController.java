package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.dto.request.HoldStaffBuncheolRequest;
import buncheoleasy.buncheol.dto.response.HoldBuncheolResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 운영진(can_host) 전용 분철 API. 회원 토큰으로 부르고, 운영진이 아니면 USR-031 이다. */
@RestController
@RequestMapping("/v1/staff/buncheols")
@RequiredArgsConstructor
public class StaffBuncheolController {

  private final BuncheolService buncheolService;

  /** 운영진 분철 개최. 파트 구성은 회원 개최({@code POST /v1/buncheols})와 같고, 요청에 예약 오픈 시각을 실을 수 있다. */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<HoldBuncheolResponse> holdStaffBuncheol(
      @AuthenticationPrincipal final Long hostId,
      @Valid @RequestPart("request") final HoldStaffBuncheolRequest request,
      @RequestPart(value = "images") final List<MultipartFile> images) {
    Long buncheolId =
        buncheolService.holdStaffBuncheol(hostId, request, MultipartImageFiles.from(images));
    return ResponseEntity.status(HttpStatus.CREATED).body(HoldBuncheolResponse.of(buncheolId));
  }
}
