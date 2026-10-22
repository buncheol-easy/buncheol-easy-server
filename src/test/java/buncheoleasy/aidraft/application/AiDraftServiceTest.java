package buncheoleasy.aidraft.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import buncheoleasy.aidraft.application.AiDraftGroupMatcher.GroupMatch;
import buncheoleasy.aidraft.domain.AiDraftCost;
import buncheoleasy.aidraft.domain.AiDraftDay;
import buncheoleasy.aidraft.domain.AiDraftSource;
import buncheoleasy.aidraft.domain.AiDraftUsage;
import buncheoleasy.aidraft.dto.request.CreateAiDraftRequest;
import buncheoleasy.aidraft.dto.response.AiDraftQuotaResponse;
import buncheoleasy.aidraft.dto.response.AiDraftResponse;
import buncheoleasy.aidraft.infrastructure.AiDraftProperties;
import buncheoleasy.aidraft.infrastructure.AiWorkerClient;
import buncheoleasy.aidraft.infrastructure.AiWorkerDraft;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.dto.response.HostingEligibilityResponse;
import buncheoleasy.buncheol.dto.response.HostingEligibilityResponse.Reason;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.group.dto.response.GroupResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiDraftService 단위 테스트")
class AiDraftServiceTest {

  private static final Long USER_ID = 7L;
  private static final Long REQUEST_ID = 99L;
  private static final String LOCK_TOKEN = "lock-token";
  private static final String URL = "https://www.ktown4u.com/iteminfo?goods_no=1";
  private static final CreateAiDraftRequest URL_REQUEST = new CreateAiDraftRequest(URL, null);
  // 2026-10-22 14:00 KST
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-22T05:00:00Z"), ZoneOffset.UTC);
  private static final AiDraftDay TODAY = AiDraftDay.of(CLOCK.instant());
  private static final AiDraftCost COST =
      new AiDraftCost(new BigDecimal("0.0017"), 16200, 980, 2);
  private static final GroupResponse TXT =
      new GroupResponse(12L, "투모로우바이투게더", null, List.of("TOMORROW X TOGETHER"));

  @Mock private BuncheolService buncheolService;
  @Mock private AiDraftUsage usage;
  @Mock private AiDraftRequestRecorder recorder;
  @Mock private AiWorkerClient workerClient;
  @Mock private AiDraftGroupMatcher groupMatcher;
  @Mock private ApplicationEventPublisher eventPublisher;

  private AiDraftService service;

  @BeforeEach
  void setUp() {
    service = service(properties(true, 4));
  }

  private AiDraftService service(final AiDraftProperties properties) {
    return new AiDraftService(
        properties,
        buncheolService,
        usage,
        recorder,
        workerClient,
        groupMatcher,
        eventPublisher,
        CLOCK);
  }

  private static AiDraftProperties properties(final boolean enabled, final int maxConcurrent) {
    return new AiDraftProperties(
        enabled,
        "http://ai-worker:8000",
        "token",
        Duration.ofSeconds(2),
        Duration.ofSeconds(25),
        10,
        30,
        100,
        maxConcurrent);
  }

  private static AiWorkerDraft draft(
      final String title, final String description, final String purchaseSite) {
    return new AiWorkerDraft(
        title,
        description,
        purchaseSite,
        URL,
        "투모로우바이투게더 (TXT)",
        List.of("TOMORROW X TOGETHER", "TXT"),
        14100L,
        "KRW",
        "2026-11-03",
        List.of("iVBORw0KGgo=", "/9j/4AAQ"),
        1,
        COST);
  }

  /** 끝까지 통과하는 기본 흐름. 실패 경로 테스트는 필요한 한 군데만 덮어쓴다. */
  private void givenHappyPath() {
    lenient().when(buncheolService.getHostingEligibility(USER_ID)).thenReturn(HostingEligibilityResponse.allowed());
    lenient().when(usage.lock(eq(USER_ID), any(Duration.class))).thenReturn(Optional.of(LOCK_TOKEN));
    lenient().when(usage.addAttempt(USER_ID, TODAY)).thenReturn(1L);
    lenient().when(usage.addGeneration(USER_ID, TODAY)).thenReturn(1L);
    lenient().when(recorder.start(eq(USER_ID), any(AiDraftSource.class))).thenReturn(REQUEST_ID);
    lenient()
        .when(workerClient.createDraft(any(AiDraftSource.class)))
        .thenReturn(draft("제목", "본문", "케이타운포유"));
    lenient()
        .when(groupMatcher.match(any(), any()))
        .thenReturn(new GroupMatch(TXT, List.of("투모로우바이투게더 (TXT)", "TOMORROW X TOGETHER")));
    lenient().when(usage.addDailyTotal(TODAY)).thenReturn(OptionalLong.of(1L));
  }

  private void assertFailsWith(final CreateAiDraftRequest request, final ErrorCode errorCode) {
    assertThatThrownBy(() -> service.createDraft(USER_ID, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }

  @Nested
  @DisplayName("기능 스위치")
  class SwitchTest {

    @Test
    void 꺼져_있으면_AI_007_이고_아무것도_확인하지_않는다() {
      service = service(properties(false, 4));

      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_DISABLED);

      then(buncheolService).shouldHaveNoInteractions();
      then(usage).shouldHaveNoInteractions();
      then(workerClient).shouldHaveNoInteractions();
    }

    @Test
    void 꺼져_있으면_남은_횟수는_enabled_false_로_200() {
      // 화면이 AI 칸을 숨기는 신호다 — 409 로 주면 화면이 오류로 다룬다.
      service = service(properties(false, 4));

      AiDraftQuotaResponse quota = service.getQuota(USER_ID);

      assertThat(quota).isEqualTo(new AiDraftQuotaResponse(false, 10, 0));
      then(usage).shouldHaveNoInteractions();
    }
  }

  @Nested
  @DisplayName("남은 횟수 조회")
  class QuotaTest {

    @Test
    void 한도에서_쓴_횟수를_뺀다() {
      given(usage.countGenerations(USER_ID, TODAY)).willReturn(3L);

      assertThat(service.getQuota(USER_ID)).isEqualTo(new AiDraftQuotaResponse(true, 10, 7));
    }

    @Test
    void 넘친_증가가_있어도_0_아래로_내려가지_않는다() {
      given(usage.countGenerations(USER_ID, TODAY)).willReturn(13L);

      assertThat(service.getQuota(USER_ID).remainingToday()).isZero();
    }

    @Test
    void 저장소_장애는_그대로_AI_005() {
      given(usage.countGenerations(USER_ID, TODAY))
          .willThrow(new BusinessException(ErrorCode.AI_DRAFT_UNAVAILABLE));

      assertThatThrownBy(() -> service.getQuota(USER_ID))
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AI_DRAFT_UNAVAILABLE);
    }
  }

  @Nested
  @DisplayName("개최 자격")
  class EligibilityTest {

    @ParameterizedTest
    @EnumSource(Reason.class)
    void 자격이_없으면_개최_요청과_같은_오류를_던지고_잠금을_잡지_않는다(final Reason reason) {
      given(buncheolService.getHostingEligibility(USER_ID))
          .willReturn(HostingEligibilityResponse.blocked(reason));
      ErrorCode expected =
          switch (reason) {
            case PHONE_REQUIRED -> ErrorCode.USER_PROFILE_IS_NOT_COMPLETE;
            case AGE_UNVERIFIED -> ErrorCode.USER_AGE_NOT_VERIFIED;
            case NOT_ADULT -> ErrorCode.USER_NOT_ADULT;
            case LIMIT_EXCEEDED -> ErrorCode.BUNCHEOL_ACTIVE_HOST_LIMIT_EXCEEDED;
            case BANK_ACCOUNT_REQUIRED -> ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED;
          };

      assertFailsWith(URL_REQUEST, expected);

      then(usage).shouldHaveNoInteractions();
    }

    @Test
    void 입력_검사는_자격_다음이다() {
      // 자격 없는 사람에게 입력 오류부터 보여 주지 않는다.
      given(buncheolService.getHostingEligibility(USER_ID))
          .willReturn(HostingEligibilityResponse.blocked(Reason.NOT_ADULT));

      assertFailsWith(new CreateAiDraftRequest(null, null), ErrorCode.USER_NOT_ADULT);
    }
  }

  @Nested
  @DisplayName("입력·잠금")
  class InputAndLockTest {

    @Test
    void 입력이_틀리면_AI_001_이고_잠금도_횟수도_쓰지_않는다() {
      givenHappyPath();

      assertFailsWith(new CreateAiDraftRequest(URL, "글도 같이"), ErrorCode.AI_DRAFT_INVALID_INPUT);

      then(usage).shouldHaveNoInteractions();
    }

    @Test
    void 같은_사람이_이미_작성_중이면_AI_006_이고_횟수를_쓰지_않는다() {
      givenHappyPath();
      given(usage.lock(eq(USER_ID), any(Duration.class))).willReturn(Optional.empty());

      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_IN_PROGRESS);

      then(usage).should(never()).addAttempt(anyLong(), any());
      then(usage).should(never()).unlock(anyLong(), any());
    }

    @Test
    void 잠금_수명은_엔진_대기보다_길다() {
      givenHappyPath();

      service.createDraft(USER_ID, URL_REQUEST);

      then(usage).should().lock(USER_ID, Duration.ofSeconds(40));
    }

    @Test
    void 잠금은_성공해도_실패해도_자기_표로_푼다() {
      givenHappyPath();
      service.createDraft(USER_ID, URL_REQUEST);

      given(workerClient.createDraft(any()))
          .willThrow(new BusinessException(ErrorCode.AI_DRAFT_FETCH_FAILED));
      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_FETCH_FAILED);

      then(usage).should(times(2)).unlock(USER_ID, LOCK_TOKEN);
    }

    @Test
    void 저장소_장애로_세지_못하면_AI_005_로_막고_잠금은_푼다() {
      givenHappyPath();
      given(usage.addGeneration(USER_ID, TODAY))
          .willThrow(new BusinessException(ErrorCode.AI_DRAFT_UNAVAILABLE));

      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_UNAVAILABLE);

      then(workerClient).shouldHaveNoInteractions();
      then(usage).should(never()).refundGeneration(anyLong(), any());
      then(usage).should().unlock(USER_ID, LOCK_TOKEN);
    }
  }

  @Nested
  @DisplayName("하루 횟수")
  class DailyLimitTest {

    @Test
    void 열_번째는_통과하고_남은_횟수는_0() {
      givenHappyPath();
      given(usage.addGeneration(USER_ID, TODAY)).willReturn(10L);

      AiDraftResponse response = service.createDraft(USER_ID, URL_REQUEST);

      assertThat(response.remainingToday()).isZero();
    }

    @Test
    void 열한_번째는_AI_003_이고_엔진을_부르지_않고_돌려주지도_않는다() {
      givenHappyPath();
      given(usage.addGeneration(USER_ID, TODAY)).willReturn(11L);

      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_DAILY_LIMIT_EXCEEDED);

      then(workerClient).shouldHaveNoInteractions();
      then(usage).should(never()).refundGeneration(anyLong(), any());
    }

    @Test
    void 숨은_시도_상한을_넘으면_생성_횟수를_세지도_않고_AI_003() {
      givenHappyPath();
      given(usage.addAttempt(USER_ID, TODAY)).willReturn(31L);

      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_DAILY_LIMIT_EXCEEDED);

      then(usage).should(never()).addGeneration(anyLong(), any());
    }

    @Test
    void 시도_상한_30_번째는_통과한다() {
      givenHappyPath();
      given(usage.addAttempt(USER_ID, TODAY)).willReturn(30L);

      assertThat(service.createDraft(USER_ID, URL_REQUEST)).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
      // 10/22 23:59:59.999 KST — 아직 그날, 남은 1ms + 여유 1시간
      "2026-10-22T14:59:59.999Z, 2026-10-22, 3600001",
      // 10/23 00:00:00 KST — 새 날, 하루 + 여유 1시간
      "2026-10-22T15:00:00Z, 2026-10-23, 90000000"
    })
    void 한국_자정을_기준으로_날을_나누고_키_수명은_늘_양수다(
        final String now, final LocalDate expectedDate, final long expectedTtlMillis) {
      Clock clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC);
      service =
          new AiDraftService(
              properties(true, 4),
              buncheolService,
              usage,
              recorder,
              workerClient,
              groupMatcher,
              eventPublisher,
              clock);
      givenHappyPath();
      given(usage.addAttempt(eq(USER_ID), any())).willReturn(1L);
      given(usage.addGeneration(eq(USER_ID), any())).willReturn(1L);
      given(usage.addDailyTotal(any())).willReturn(OptionalLong.of(1L));

      service.createDraft(USER_ID, URL_REQUEST);

      ArgumentCaptor<AiDraftDay> day = ArgumentCaptor.forClass(AiDraftDay.class);
      then(usage).should().addGeneration(eq(USER_ID), day.capture());
      assertThat(day.getValue().date()).isEqualTo(expectedDate);
      assertThat(day.getValue().keyTtl().toMillis()).isPositive().isEqualTo(expectedTtlMillis);
    }
  }

  @Nested
  @DisplayName("엔진 실패와 횟수 돌려주기")
  class RefundTest {

    @Test
    void 판매_페이지를_읽지_못하면_AI_002_이고_생성_횟수를_돌려준다() {
      givenHappyPath();
      given(workerClient.createDraft(any()))
          .willThrow(new BusinessException(ErrorCode.AI_DRAFT_FETCH_FAILED));

      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_FETCH_FAILED);

      then(usage).should().refundGeneration(USER_ID, TODAY);
      then(usage).should(never()).addDailyTotal(any());
      then(recorder)
          .should()
          .fail(eq(REQUEST_ID), eq(ErrorCode.AI_DRAFT_FETCH_FAILED), any(Duration.class));
    }

    @Test
    void 엔진이_입력을_거절하거나_못_쓰는_상태면_돌려준다() {
      givenHappyPath();
      given(workerClient.createDraft(any()))
          .willThrow(new BusinessException(ErrorCode.AI_DRAFT_INVALID_INPUT))
          .willThrow(new BusinessException(ErrorCode.AI_DRAFT_UNAVAILABLE));

      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_INVALID_INPUT);
      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_UNAVAILABLE);

      then(usage).should(times(2)).refundGeneration(USER_ID, TODAY);
    }

    @Test
    void 생성_실패_AI_004_는_AI_를_이미_불렀을_수_있어_돌려주지_않고_합계에_넣는다() {
      givenHappyPath();
      given(workerClient.createDraft(any()))
          .willThrow(new BusinessException(ErrorCode.AI_DRAFT_GENERATION_FAILED));

      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_GENERATION_FAILED);

      then(usage).should(never()).refundGeneration(anyLong(), any());
      then(usage).should().addDailyTotal(TODAY);
      then(recorder)
          .should()
          .fail(eq(REQUEST_ID), eq(ErrorCode.AI_DRAFT_GENERATION_FAILED), any(Duration.class));
    }

    @Test
    void 기록을_남기지_못하면_엔진을_부르지_않고_돌려준다() {
      givenHappyPath();
      given(recorder.start(eq(USER_ID), any())).willThrow(new IllegalStateException("db down"));

      assertThatThrownBy(() -> service.createDraft(USER_ID, URL_REQUEST))
          .hasMessage("db down");

      then(workerClient).shouldHaveNoInteractions();
      then(usage).should().refundGeneration(USER_ID, TODAY);
    }
  }

  @Nested
  @DisplayName("서버 동시 상한")
  class ConcurrencyTest {

    @Test
    void 상한이_차면_기다리지_않고_AI_005_이고_돌려준다() throws Exception {
      service = service(properties(true, 1));
      givenHappyPath();
      Long otherUser = 8L;
      given(buncheolService.getHostingEligibility(otherUser))
          .willReturn(HostingEligibilityResponse.allowed());
      given(usage.lock(eq(otherUser), any(Duration.class))).willReturn(Optional.of("other"));
      given(usage.addAttempt(otherUser, TODAY)).willReturn(1L);
      given(usage.addGeneration(otherUser, TODAY)).willReturn(1L);

      CountDownLatch engineEntered = new CountDownLatch(1);
      CountDownLatch releaseEngine = new CountDownLatch(1);
      willAnswer(
              invocation -> {
                engineEntered.countDown();
                releaseEngine.await(5, TimeUnit.SECONDS);
                return draft("제목", "본문", "케이타운포유");
              })
          .given(workerClient)
          .createDraft(any());

      CompletableFuture<AiDraftResponse> first =
          CompletableFuture.supplyAsync(() -> service.createDraft(USER_ID, URL_REQUEST));
      assertThat(engineEntered.await(5, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> service.createDraft(otherUser, URL_REQUEST))
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AI_DRAFT_UNAVAILABLE);
      then(usage).should().refundGeneration(otherUser, TODAY);
      then(recorder).should(never()).start(eq(otherUser), any());

      releaseEngine.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS)).isNotNull();

      // 자리가 돌아왔으니 다음 요청은 엔진까지 간다.
      assertThat(service.createDraft(otherUser, URL_REQUEST)).isNotNull();
    }

    @Test
    void 엔진_실패에도_자리를_돌려준다() {
      service = service(properties(true, 1));
      givenHappyPath();
      given(workerClient.createDraft(any()))
          .willThrow(new BusinessException(ErrorCode.AI_DRAFT_GENERATION_FAILED))
          .willReturn(draft("제목", "본문", "케이타운포유"));

      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_GENERATION_FAILED);

      assertThat(service.createDraft(USER_ID, URL_REQUEST)).isNotNull();
    }
  }

  @Nested
  @DisplayName("성공 응답")
  class SuccessTest {

    @Test
    void 그룹_확정과_사진과_남은_횟수를_담고_비용을_기록한다() {
      givenHappyPath();
      given(usage.addGeneration(USER_ID, TODAY)).willReturn(3L);

      AiDraftResponse response = service.createDraft(USER_ID, URL_REQUEST);

      assertThat(response.title()).isEqualTo("제목");
      assertThat(response.description()).isEqualTo("본문");
      assertThat(response.purchaseSite()).isEqualTo("케이타운포유");
      assertThat(response.purchaseUrl()).isEqualTo(URL);
      assertThat(response.group()).isEqualTo(new AiDraftResponse.Group(12L, "투모로우바이투게더"));
      assertThat(response.groupKeywords())
          .containsExactly("투모로우바이투게더 (TXT)", "TOMORROW X TOGETHER");
      assertThat(response.listPrice()).isEqualTo(14100L);
      assertThat(response.priceCurrency()).isEqualTo("KRW");
      assertThat(response.releaseDate()).isEqualTo("2026-11-03");
      assertThat(response.images()).containsExactly("iVBORw0KGgo=", "/9j/4AAQ");
      assertThat(response.thumbnailIndex()).isEqualTo(1);
      assertThat(response.remainingToday()).isEqualTo(7);

      then(groupMatcher)
          .should()
          .match("투모로우바이투게더 (TXT)", List.of("TOMORROW X TOGETHER", "TXT"));
      then(recorder).should().succeed(eq(REQUEST_ID), eq(COST), eq(12L), any(Duration.class));
      then(usage).should(never()).refundGeneration(anyLong(), any());
      then(usage).should().addDailyTotal(TODAY);
    }

    @Test
    void 그룹을_확정하지_못하면_null_과_검색어만_준다() {
      givenHappyPath();
      given(groupMatcher.match(any(), any()))
          .willReturn(new GroupMatch(null, List.of("투모로우바이투게더 (TXT)")));

      AiDraftResponse response = service.createDraft(USER_ID, URL_REQUEST);

      assertThat(response.group()).isNull();
      assertThat(response.groupKeywords()).containsExactly("투모로우바이투게더 (TXT)");
      then(recorder).should().succeed(eq(REQUEST_ID), eq(COST), eq(null), any(Duration.class));
    }

    @Test
    void 제목_64자_본문_700자_구매처_20자에_맞춘다() {
      givenHappyPath();
      given(workerClient.createDraft(any()))
          .willReturn(draft("가".repeat(70), "나".repeat(800), "다".repeat(25)));

      AiDraftResponse response = service.createDraft(USER_ID, URL_REQUEST);

      assertThat(response.title()).hasSize(64);
      assertThat(response.description()).hasSize(700);
      assertThat(response.purchaseSite()).hasSize(20);
    }

    @Test
    void 제목을_자를_때_이모지를_반으로_자르지_않는다() {
      givenHappyPath();
      // 엔진은 이모지를 1글자로 세서 63자 + 💿 를 64자로 본다 — 여기서는 65칸이다.
      given(workerClient.createDraft(any()))
          .willReturn(draft("가".repeat(63) + "💿", "본문", "케이타운포유"));

      AiDraftResponse response = service.createDraft(USER_ID, URL_REQUEST);

      assertThat(response.title()).isEqualTo("가".repeat(63));
    }
  }

  @Nested
  @DisplayName("하루 합계 알림")
  class DailyTotalAlertTest {

    @Test
    void 합계가_정확히_기준에_닿는_순간_한_번_알린다() {
      givenHappyPath();
      given(usage.addDailyTotal(TODAY))
          .willReturn(OptionalLong.of(99L), OptionalLong.of(100L), OptionalLong.of(101L));

      service.createDraft(USER_ID, URL_REQUEST);
      service.createDraft(USER_ID, URL_REQUEST);
      service.createDraft(USER_ID, URL_REQUEST);

      then(eventPublisher)
          .should(times(1))
          .publishEvent(new AiDraftDailyTotalReachedEvent(TODAY.date(), 100L));
    }

    @Test
    void 생성_실패_AI_004_로_기준에_닿아도_알린다() {
      givenHappyPath();
      given(usage.addDailyTotal(TODAY)).willReturn(OptionalLong.of(100L));
      given(workerClient.createDraft(any()))
          .willThrow(new BusinessException(ErrorCode.AI_DRAFT_GENERATION_FAILED));

      assertFailsWith(URL_REQUEST, ErrorCode.AI_DRAFT_GENERATION_FAILED);

      then(eventPublisher)
          .should()
          .publishEvent(new AiDraftDailyTotalReachedEvent(TODAY.date(), 100L));
    }

    @Test
    void 합계를_세지_못해도_응답은_그대로다() {
      givenHappyPath();
      given(usage.addDailyTotal(TODAY)).willReturn(OptionalLong.empty());

      assertThat(service.createDraft(USER_ID, URL_REQUEST)).isNotNull();
      then(eventPublisher).shouldHaveNoInteractions();
    }
  }
}
