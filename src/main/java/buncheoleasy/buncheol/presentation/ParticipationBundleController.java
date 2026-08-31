package buncheoleasy.buncheol.presentation;

import buncheoleasy.buncheol.application.participation.ParticipationBundleService;
import buncheoleasy.buncheol.dto.response.BundleReleaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 참여 <b>묶음</b> 단위 API. 묶음은 현실의 돈 단위(이체 1회 · 배송비 1회 · 택배 1개)이므로 조작도 그 단위로 연다 — 슬롯마다
 * 누르게 하면 개최자가 같은 신청을 여러 번 처리하게 되고, 그 사이에 묶음 상태가 갈린다.
 */
@RestController
@RequestMapping("/v1/participation-bundles")
@RequiredArgsConstructor
public class ParticipationBundleController {

  private final ParticipationBundleService participationBundleService;

  /**
   * 개최자 「제외」 API — 입금 기한이 지난 묶음의 활성 슬롯을 전부 취소한다.
   *
   * <p>C2C 는 입금 기한이 지나도 자동 취소하지 않으므로(docs/70 결정 9) <b>이것이 미입금자를 빼는 유일한 출구</b>다.
   *
   * <p>열리는 조건이 하나다 — <b>입금 기한이 정해졌고 그 기한이 지났을 때.</b> 모집 중(기한 없음)에는 열리지 않는다:
   * 아직 확정도 안 된 참여자를 개최자가 임의로 자를 수 있는 도구가 되면 안 된다. 입금확인된 슬롯이 하나라도 있으면
   * 역시 거부한다(확정분은 분철 취소 + 환불 경로로만 끝난다).
   */
  @PostMapping("/{bundleId}/release")
  public ResponseEntity<BundleReleaseResponse> release(
      @AuthenticationPrincipal final Long hostId, @PathVariable final Long bundleId) {
    return ResponseEntity.ok(
        new BundleReleaseResponse(bundleId, participationBundleService.release(hostId, bundleId)));
  }
}
