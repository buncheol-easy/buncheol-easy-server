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
 * Delivery Tracker 추적 콜백 수신 (공개 경로). 콜백엔 서명이 없어, 웹훅 등록 때 콜백 URL 에 심어둔 공유 비밀 토큰(쿼리)이 유일한 발신자 검증
 * 수단이다. 1초 내 2XX 를 못 주면 재전송되므로 검증만 하고 즉시 202 — 동기화는 비동기, 실패는 다음 콜백·갱신 스케줄러 폴링이 따라잡는다.
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
      // 토큰 값은 로그에 남기지 않고 수신 여부만 남긴다.
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
