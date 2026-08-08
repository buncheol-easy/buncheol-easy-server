package buncheoleasy.delivery.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Delivery Tracker GraphQL 클라이언트 — 최신 추적 이벤트 조회(track)와 추적 웹훅 등록(registerTrackWebhook). 주의: (1)
 * GraphQL 은 업무 실패를 HTTP 200 + {@code errors} 배열로 주므로 본문을 검증해야 한다. (2) 웹훅은 expirationTime(권장 48h) 후
 * 소멸하며 같은 인자 재등록이 곧 연장이다(멱등). (3) 쿼터가 초당 10콜이라 모든 아웃바운드 호출은 최소 간격 스로틀을 지나고, rate limit
 * 응답은 백오프 후 1회 재시도한다 — 갱신 스케줄러·등록 이벤트 등 호출 경로가 늘어도 이 관문 하나로 보호된다.
 */
@Slf4j
@Component
public class DeliveryTrackerClient {

  private static final String TRACK_QUERY =
      """
      query Track($carrierId: ID!, $trackingNumber: String!) {
        track(carrierId: $carrierId, trackingNumber: $trackingNumber) {
          lastEvent { time status { code name } }
        }
      }""";

  private static final String REGISTER_WEBHOOK_MUTATION =
      """
      mutation RegisterTrackWebhook($input: RegisterTrackWebhookInput!) {
        registerTrackWebhook(input: $input)
      }""";

  /** "캐리어에 아직 등록되지 않은 운송장" 을 뜻하는 GraphQL 에러 코드. 조회 실패가 아니라 "추적 정보 없음" 으로 취급한다. */
  private static final String NOT_FOUND_ERROR_CODE = "NOT_FOUND";

  /** rate limit 응답의 식별 문구. 에러 코드가 인증 실패와 같은 FORBIDDEN 이라 메시지로 구분할 수밖에 없다. */
  private static final String RATE_LIMIT_MESSAGE_MARKER = "rate limit";

  /** rate limit 재시도 전 대기 — 초 단위 쿼터 창이 지나가기에 충분한 길이. */
  private static final Duration RATE_LIMIT_RETRY_BACKOFF = Duration.ofSeconds(1);

  private final RestClient restClient;
  private final DeliveryTrackerProperties properties;
  private final CallThrottle callThrottle;

  public DeliveryTrackerClient(final DeliveryTrackerProperties properties) {
    this.properties = properties;
    this.restClient =
        RestClient.builder().requestFactory(createRequestFactory(properties)).build();
    this.callThrottle = new CallThrottle(properties.minCallInterval(), System::nanoTime);
  }

  /** 크리덴셜과 콜백 검증 토큰이 모두 주입된 환경인지. 호출자가 미설정 환경에서 조회·등록을 건너뛸 수 있게 노출한다. */
  public boolean isEnabled() {
    return properties.outboundEnabled() && properties.webhookEnabled();
  }

  /** 최신 추적 이벤트 조회. 캐리어 미등록(NOT_FOUND)·이벤트 없음은 empty, 그 외 오류는 예외. */
  public Optional<TrackLastEvent> findLastEvent(final String carrierId, final String trackingNumber) {
    requireEnabled(trackingNumber);
    return withRateLimitRetry(
        "track", trackingNumber, () -> doFindLastEvent(carrierId, trackingNumber));
  }

  private Optional<TrackLastEvent> doFindLastEvent(
      final String carrierId, final String trackingNumber) {
    TrackResponse response =
        post(
            TRACK_QUERY,
            Map.of("carrierId", carrierId, "trackingNumber", trackingNumber),
            TrackResponse.class,
            "track",
            trackingNumber);
    if (hasOnlyNotFoundErrors(response.errors())) {
      log.info("Delivery Tracker 추적 정보 없음 - carrierId={} trackingNumber={}", carrierId, trackingNumber);
      return Optional.empty();
    }
    verifyNoErrors(response.errors(), "track", trackingNumber);
    if (response.data() == null
        || response.data().track() == null
        || response.data().track().lastEvent() == null) {
      return Optional.empty();
    }
    LastEvent lastEvent = response.data().track().lastEvent();
    String statusCode = lastEvent.status() == null ? null : lastEvent.status().code();
    String statusName = lastEvent.status() == null ? null : lastEvent.status().name();
    return Optional.of(
        new TrackLastEvent(statusCode, statusName, parseTime(lastEvent.time(), trackingNumber)));
  }

  /** 추적 웹훅 등록·연장 (같은 인자 재호출 = 만료 연장, 멱등). */
  public void registerWebhook(
      final String carrierId, final String trackingNumber, final Instant expirationTime) {
    requireEnabled(trackingNumber);
    withRateLimitRetry(
        "registerTrackWebhook",
        trackingNumber,
        () -> {
          doRegisterWebhook(carrierId, trackingNumber, expirationTime);
          return null;
        });
  }

  private void doRegisterWebhook(
      final String carrierId, final String trackingNumber, final Instant expirationTime) {
    Map<String, Object> input =
        Map.of(
            "carrierId", carrierId,
            "trackingNumber", trackingNumber,
            "callbackUrl", properties.tokenizedCallbackUrl(),
            "expirationTime", expirationTime.toString());
    WebhookResponse response =
        post(
            REGISTER_WEBHOOK_MUTATION,
            Map.of("input", input),
            WebhookResponse.class,
            "registerTrackWebhook",
            trackingNumber);
    verifyNoErrors(response.errors(), "registerTrackWebhook", trackingNumber);
    if (response.data() == null || !Boolean.TRUE.equals(response.data().registerTrackWebhook())) {
      throw new DeliveryTrackerException(
          "Delivery Tracker 웹훅 등록 거부 - trackingNumber=" + trackingNumber);
    }
  }

  /** fail-closed 안전망 — 토큰 없이 웹훅을 등록하면 인증 불가능한 콜백이 열리므로 조용히 진행하지 않는다. */
  private void requireEnabled(final String trackingNumber) {
    if (!isEnabled()) {
      throw new DeliveryTrackerException(
          "Delivery Tracker 미설정(크리덴셜 또는 웹훅 토큰 누락) - trackingNumber=" + trackingNumber);
    }
  }

  /** rate limit 는 순간 호출이 겹치면 스로틀에도 불구하고 날 수 있어, 쿼터 창이 지나가길 기다렸다가 1회만 재시도한다. */
  private <T> T withRateLimitRetry(
      final String operation, final String trackingNumber, final Supplier<T> call) {
    try {
      return call.get();
    } catch (DeliveryTrackerRateLimitException e) {
      log.warn(
          "Delivery Tracker rate limit - operation={} trackingNumber={} ({}ms 후 1회 재시도)",
          operation,
          trackingNumber,
          RATE_LIMIT_RETRY_BACKOFF.toMillis());
      sleep(RATE_LIMIT_RETRY_BACKOFF);
      return call.get();
    }
  }

  /** 다음 호출 슬롯까지 대기 — 모든 아웃바운드 호출이 지나는 단일 관문이라 호출 경로가 몇 개든 합산 초당 콜 수가 억제된다. */
  private void awaitCallSlot() {
    long waitNanos = callThrottle.reserveWaitNanos();
    if (waitNanos > 0) {
      sleep(Duration.ofNanos(waitNanos));
    }
  }

  private void sleep(final Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DeliveryTrackerException("Delivery Tracker 호출 대기 중 인터럽트", e);
    }
  }

  private <T> T post(
      final String query,
      final Map<String, Object> variables,
      final Class<T> responseType,
      final String operation,
      final String trackingNumber) {
    awaitCallSlot();
    final T response;
    try {
      response =
          restClient
              .post()
              .uri(properties.apiUrl())
              .contentType(MediaType.APPLICATION_JSON)
              .header(
                  HttpHeaders.AUTHORIZATION,
                  "TRACKQL-API-KEY " + properties.clientId() + ":" + properties.clientSecret())
              .body(new GraphQlRequest(query, variables))
              .retrieve()
              .body(responseType);
    } catch (RestClientException e) {
      log.error(
          "Delivery Tracker 호출 통신 오류 - operation={} trackingNumber={}",
          operation,
          trackingNumber,
          e);
      throw new DeliveryTrackerException("Delivery Tracker 호출 통신 오류: " + operation, e);
    }
    if (response == null) {
      throw new DeliveryTrackerException("Delivery Tracker 응답 없음: " + operation);
    }
    return response;
  }

  /** GraphQL 업무 실패는 HTTP 200 본문으로 오므로 errors 배열을 반드시 확인한다. */
  private void verifyNoErrors(
      final List<GraphQlError> errors, final String operation, final String trackingNumber) {
    if (errors == null || errors.isEmpty()) {
      return;
    }
    if (hasRateLimitError(errors)) {
      // 재시도로 흡수될 수 있는 일시 실패라 WARN 만 남긴다 — 최종 실패는 호출자가 ERROR 로 기록한다.
      log.warn(
          "Delivery Tracker rate limit 응답 - operation={} trackingNumber={} errors={}",
          operation,
          trackingNumber,
          errors);
      throw new DeliveryTrackerRateLimitException(
          "Delivery Tracker rate limit: " + operation + " - " + errors.getFirst().message());
    }
    log.error(
        "Delivery Tracker 호출 실패 - operation={} trackingNumber={} errors={}",
        operation,
        trackingNumber,
        errors);
    throw new DeliveryTrackerException(
        "Delivery Tracker 호출 실패: " + operation + " - " + errors.getFirst().message());
  }

  private boolean hasOnlyNotFoundErrors(final List<GraphQlError> errors) {
    if (errors == null || errors.isEmpty()) {
      return false;
    }
    return errors.stream().allMatch(error -> NOT_FOUND_ERROR_CODE.equals(error.code()));
  }

  private boolean hasRateLimitError(final List<GraphQlError> errors) {
    return errors.stream()
        .anyMatch(
            error ->
                error.message() != null
                    && error.message().toLowerCase().contains(RATE_LIMIT_MESSAGE_MARKER));
  }

  /** 이벤트 시각(ISO-8601 오프셋 포함) 파싱. 형식이 예상과 다르면 null 을 돌려주고 호출자가 현재 시각으로 대체한다. */
  private Instant parseTime(final String time, final String trackingNumber) {
    if (time == null || time.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(time).toInstant();
    } catch (DateTimeParseException e) {
      log.warn("Delivery Tracker 이벤트 시각 파싱 실패 - trackingNumber={} time={}", trackingNumber, time);
      return null;
    }
  }

  private SimpleClientHttpRequestFactory createRequestFactory(
      final DeliveryTrackerProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
    factory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    return factory;
  }

  /**
   * 호출 간 최소 간격을 직렬로 예약하는 스로틀. 슬롯 예약(동기화)과 대기(호출 스레드)를 분리해 락을 잡은 채 잠들지 않으며, 시간원은 단조
   * 시계(nanoTime)를 주입받아 테스트를 허용한다.
   */
  static final class CallThrottle {

    private final long minIntervalNanos;
    private final LongSupplier nanoTime;
    private long nextAllowedNanos;

    CallThrottle(final Duration minInterval, final LongSupplier nanoTime) {
      this.minIntervalNanos = minInterval.toNanos();
      this.nanoTime = nanoTime;
      this.nextAllowedNanos = nanoTime.getAsLong();
    }

    /** 이번 호출이 대기해야 할 시간(ns)을 돌려주고 다음 호출 슬롯을 예약한다. 0 이하면 즉시 호출 가능. */
    synchronized long reserveWaitNanos() {
      long now = nanoTime.getAsLong();
      long slot = Math.max(now, nextAllowedNanos);
      nextAllowedNanos = slot + minIntervalNanos;
      return slot - now;
    }
  }

  private record GraphQlRequest(String query, Map<String, Object> variables) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TrackResponse(TrackData data, List<GraphQlError> errors) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TrackData(TrackInfo track) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TrackInfo(LastEvent lastEvent) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record LastEvent(String time, EventStatus status) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EventStatus(String code, String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record WebhookResponse(WebhookData data, List<GraphQlError> errors) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record WebhookData(Boolean registerTrackWebhook) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GraphQlError(String message, Map<String, Object> extensions) {

    /** 에러 분류 코드 (예: NOT_FOUND). extensions.code 위치는 Delivery Tracker 응답 관례를 따른다. */
    String code() {
      Object code = extensions == null ? null : extensions.get("code");
      return code == null ? null : code.toString();
    }
  }
}
