package buncheoleasy.delivery.presentation;

import buncheoleasy.delivery.application.TrackingSyncService;
import buncheoleasy.delivery.dto.request.TrackingCallbackRequest;
import buncheoleasy.delivery.infrastructure.DeliveryTrackerProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery Tracker 추적 콜백 수신. 외부에서 인증 없이 들어오므로 SecurityConfig 의 공개 경로에 등록돼 있고, 웹훅 등록 때 콜백 URL 에 심어둔
 * 공유 비밀 토큰(쿼리 파라미터)으로 발신자를 검증한다 — Delivery Tracker 는 서명·인증 헤더를 제공하지 않아 이 토큰 비교가 유일한 인증 수단이다.
 *
 * <p>1초 안에 2XX(202 권장)를 돌려주지 않으면 지수 백오프로 재전송된다. Track API 재조회가 1초를 넘길 수 있어 검증만 하고 즉시 202 를 반환하며,
 * 실제 동기화는 비동기로 처리한다 — 비동기 실패는 다음 상태 변화 콜백과 갱신 스케줄러 폴링이 따라잡는다.
 */
@Slf4j
@RestController
@RequestMapping("/v1/deliveries/webhook")
@RequiredArgsConstructor
public class DeliveryTrackerWebhookController {

  private final TrackingSyncService trackingSyncService;
  private final DeliveryTrackerProperties properties;

  @PostMapping("/callback")
  public ResponseEntity<Void> receiveCallback(
      @RequestParam(value = "token", required = false) final String token,
      @RequestBody final TrackingCallbackRequest request) {
    if (!properties.webhookEnabled()) {
      log.warn("배송 추적 콜백 수신 - 검증 토큰 미설정이라 거부 - trackingNumber={}", request.trackingNumber());
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (!matches(token, properties.webhookToken())) {
      // 토큰 값은 로그에 남기지 않는다 — 수신 여부만 남겨 설정 오류와 스푸핑을 구분할 단서로 쓴다.
      log.warn(
          "배송 추적 콜백 수신 - 인증 실패 - trackingNumber={} 토큰수신={}",
          request.trackingNumber(),
          token != null);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    log.info(
        "배송 추적 콜백 수신 - carrierId={} trackingNumber={}",
        request.carrierId(),
        request.trackingNumber());

    trackingSyncService.syncAsync(request.carrierId(), request.trackingNumber());

    return ResponseEntity.accepted().build();
  }

  /** 타이밍 공격을 피하려 상수 시간 비교한다. */
  private boolean matches(final String presented, final String expected) {
    if (presented == null || expected == null) {
      return false;
    }
    return MessageDigest.isEqual(
        presented.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
  }
}
