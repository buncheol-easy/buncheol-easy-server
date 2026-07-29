package buncheoleasy.deposit.presentation;

import buncheoleasy.deposit.application.DepositWebhookService;
import buncheoleasy.deposit.dto.request.PayActionWebhookRequest;
import buncheoleasy.deposit.infrastructure.PayActionProperties;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 페이액션 입금 매칭 웹훅 수신. 외부에서 인증 없이 들어오므로 SecurityConfig 의 공개 경로에 등록돼 있고, 공유 비밀키 헤더로 발신자를 검증한다.
 *
 * <p>페이액션은 HMAC 서명을 제공하지 않아 이 키 비교가 유일한 인증 수단이다. 또 공식 문서가 본문에서는 {@code X-API-Key}, 헤더 표에서는 {@code
 * x-webhook-key} 로 엇갈리게 안내하고 있어 두 헤더를 모두 받는다(실제 수신 헤더는 로그로 남긴다).
 *
 * <p>정상 처리 시 반드시 HTTP 200 과 {@code {"status":"success"}} 를 함께 돌려줘야 한다 — 페이액션은 이 본문이 없으면 실패로 간주해
 * 재전송하고, 실패가 반복되면 웹훅 발송 자체를 중단한다.
 */
@Slf4j
@RestController
@RequestMapping("/v1/deposits/payaction")
@RequiredArgsConstructor
public class DepositWebhookController {

  private static final Map<String, String> SUCCESS_BODY = Map.of("status", "success");

  private final DepositWebhookService depositWebhookService;
  private final PayActionProperties properties;

  @PostMapping("/webhook")
  public ResponseEntity<Map<String, String>> receiveWebhook(
      @RequestHeader(value = "x-webhook-key", required = false) final String webhookKey,
      @RequestHeader(value = "x-api-key", required = false) final String apiKey,
      @RequestHeader(value = "x-mall-id", required = false) final String mallId,
      @RequestHeader(value = "x-trace-id", required = false) final String traceId,
      @RequestBody final PayActionWebhookRequest request) {
    if (!properties.webhookEnabled()) {
      log.warn("페이액션 웹훅 수신 - 검증 키 미설정이라 거부 - traceId={}", traceId);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (!isAuthentic(webhookKey, apiKey, mallId)) {
      // 연동 초기 진단용. 어떤 헤더가 실제로 왔는지 알아야 문서와 실제의 차이를 좁힐 수 있다(값은 남기지 않는다).
      log.warn(
          "페이액션 웹훅 수신 - 인증 실패 - traceId={} 수신헤더=[webhookKey={} apiKey={} mallId={}]",
          traceId,
          webhookKey != null,
          apiKey != null,
          mallId != null);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    log.info(
        "페이액션 웹훅 수신 - orderNumber={} status={} processingDate={} traceId={} keyHeader={}",
        request.orderNumber(),
        request.orderStatus(),
        request.processingDate(),
        traceId,
        webhookKey != null ? "x-webhook-key" : "x-api-key");

    depositWebhookService.handleMatched(request.orderNumber(), request.orderStatus());

    return ResponseEntity.ok(SUCCESS_BODY);
  }

  /**
   * 실질 인증은 비밀키 단독이다. 상점ID 는 비밀값이 아니라 스푸핑 방어에 기여하지 않으므로, 수신됐을 때만 불일치를 거른다 — 필수로 두면 페이액션이 이 헤더를
   * 싣지 않을 때 모든 요청이 401 이 되어 자동확인이 통째로 죽는다(문서상 헤더 구성이 이미 엇갈려 있어 실제로 올지 확신할 수 없다).
   */
  private boolean isAuthentic(
      final String webhookKey, final String apiKey, final String mallId) {
    String presentedKey = webhookKey != null ? webhookKey : apiKey;
    if (!matches(presentedKey, properties.webhookKey())) {
      return false;
    }
    return mallId == null || matches(mallId, properties.mallId());
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
