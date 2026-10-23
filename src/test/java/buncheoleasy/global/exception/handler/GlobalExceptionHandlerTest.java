package buncheoleasy.global.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.inbox.application.BannerQueryService;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 처리기에 걸리지 않은 예외의 마지막 처리. 진짜 고장은 C-002 한국어 응답 + ERROR 1줄, Spring·Security 가 원래 4xx 등으로 내던
 * 예외는 상태가 그대로이고 ERROR 가 남지 않는지(Grafana 알림이 "ERROR" 글자를 센다) 고정한다. MockMvc 는 {@code /error} 포워드를
 * 흉내 내지 않으므로 4xx 는 상태 코드와 "C-002 아님"까지만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("GlobalExceptionHandler 테스트")
class GlobalExceptionHandlerTest {

  private static final String BANNERS = "/v1/banners";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BannerQueryService bannerQueryService;

  /*
  OutputCaptureExtension 은 클래스·메서드 단위 캡처를 겹쳐 쌓아 앞선 테스트의 출력까지 돌려준다.
  요청 직전에 표지 줄을 찍고 그 뒤만 본다.
   */
  private record Captured(ResultActions result, String logs) {

    long errorLineCount() {
      return logs.lines().filter(line -> line.contains("ERROR")).count();
    }
  }

  private Captured perform(final CapturedOutput output, final MockHttpServletRequestBuilder request)
      throws Exception {
    return perform(output, mockMvc, request);
  }

  private static Captured perform(
      final CapturedOutput output, final MockMvc mvc, final MockHttpServletRequestBuilder request)
      throws Exception {
    final String mark = "test-mark-" + UUID.randomUUID();
    System.out.println(mark);
    final ResultActions result = mvc.perform(request);
    final String all = output.getAll();
    return new Captured(result, all.substring(all.lastIndexOf(mark) + mark.length()));
  }

  /*
  다시 던질 때 같은 예외 인스턴스가 아니면(새 예외로 감싸면) 상태는 그대로인데
  「Failure in @ExceptionHandler」 WARN·스택이 새로 찍힌다.
   */
  private static void assertNoNewLogs(final Captured captured) {
    assertThat(captured.errorLineCount()).isZero();
    assertThat(captured.logs()).doesNotContain("Failure in @ExceptionHandler");
  }

  @Nested
  @DisplayName("처리되지 않은 예외")
  class Unexpected {

    @Test
    void C002_한국어_문장으로_응답하고_예외_메시지는_내보내지_않는다(final CapturedOutput output)
        throws Exception {
      given(bannerQueryService.getBanners()).willThrow(new IllegalStateException("내부 상세"));

      final Captured captured = perform(output, get(BANNERS));

      captured
          .result()
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.detail").value("서버 내부 오류가 발생했습니다."))
          .andExpect(jsonPath("$.code").value("C-002"))
          .andExpect(content().string(not(containsString("내부 상세"))))
          .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void 에러_로그_한_줄에_메서드와_경로와_스택을_남기고_쿼리스트링은_뺀다(final CapturedOutput output)
        throws Exception {
      given(bannerQueryService.getBanners()).willThrow(new IllegalStateException("내부 상세"));

      final Captured captured = perform(output, get(BANNERS).queryParam("probe", "query-marker"));

      assertThat(captured.errorLineCount()).isEqualTo(1);
      assertThat(captured.logs())
          .contains("처리되지 않은 예외 GET /v1/banners")
          .contains("java.lang.IllegalStateException: 내부 상세")
          .contains("\tat ")
          .doesNotContain("query-marker");
    }
  }

  @Nested
  @DisplayName("BusinessException")
  class Business {

    @Test
    void 코드가_4xx면_그대로_내려간다(final CapturedOutput output) throws Exception {
      given(bannerQueryService.getBanners())
          .willThrow(new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));

      final Captured captured = perform(output, get(BANNERS));

      captured
          .result()
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("BCH-043"));
      assertThat(captured.errorLineCount()).isZero();
    }

    @Test
    void 코드가_5xx여도_C002로_바뀌지_않는다(final CapturedOutput output) throws Exception {
      given(bannerQueryService.getBanners())
          .willThrow(new BusinessException(ErrorCode.S3_UPLOAD_FAILED));

      final Captured captured = perform(output, get(BANNERS));

      captured
          .result()
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("S3-001"));
      assertThat(captured.errorLineCount()).isZero();
    }

    @Test
    void 다른_예외에_감싸여도_원인의_코드로_응답한다(final CapturedOutput output) throws Exception {
      given(bannerQueryService.getBanners())
          .willThrow(
              new RuntimeException("감쌈", new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND)));

      final Captured captured = perform(output, get(BANNERS));

      captured
          .result()
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("BCH-043"));
      assertThat(captured.errorLineCount()).isZero();
    }

    @Test
    void 감싸인_ConstraintViolationException_은_C001로_응답한다(final CapturedOutput output)
        throws Exception {
      given(bannerQueryService.getBanners())
          .willThrow(new RuntimeException(new ConstraintViolationException(Set.of())));

      final Captured captured = perform(output, get(BANNERS));

      captured
          .result()
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("C-001"));
      assertThat(captured.errorLineCount()).isZero();
    }

    @Test
    void 다시_던질_예외에_감싸여도_원인_풀기가_먼저다(final CapturedOutput output) throws Exception {
      given(bannerQueryService.getBanners())
          .willThrow(
              new HttpMessageNotReadableException(
                  "JSON 변환 실패",
                  new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND),
                  new MockHttpInputMessage(new byte[0])));

      final Captured captured = perform(output, get(BANNERS));

      captured
          .result()
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("BCH-043"));
      assertThat(captured.errorLineCount()).isZero();
    }
  }

  @Nested
  @DisplayName("Spring 이 원래 처리하던 예외는 상태 그대로, ERROR 없음")
  class FrameworkHandled {

    private void assertKept(final Captured captured, final int expectedStatus) throws Exception {
      captured
          .result()
          .andExpect(status().is(expectedStatus))
          .andExpect(content().string(not(containsString("C-002"))));
      assertNoNewLogs(captured);
    }

    @Test
    void 잘못된_JSON은_400(final CapturedOutput output) throws Exception {
      final MockHttpServletRequestBuilder brokenJson =
          put("/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":");

      assertKept(perform(output, brokenJson), 400);
    }

    @Test
    void 없는_주소는_404(final CapturedOutput output) throws Exception {
      assertKept(perform(output, get("/v1/no-such-path")), 404);
    }

    @Test
    void 지원하지_않는_메서드는_405(final CapturedOutput output) throws Exception {
      assertKept(perform(output, delete(BANNERS)), 405);
    }

    @Test
    void 숫자가_아닌_경로_변수는_400(final CapturedOutput output) throws Exception {
      assertKept(perform(output, get("/v1/buncheols/abc")), 400);
    }

    @Test
    void 지원하지_않는_본문_형식은_415(final CapturedOutput output) throws Exception {
      assertKept(
          perform(output, put("/v1/users/me").contentType(MediaType.TEXT_PLAIN).content("x")),
          415);
    }

    @Test
    void 만들_수_없는_응답_형식을_요청하면_406(final CapturedOutput output) throws Exception {
      assertKept(perform(output, get(BANNERS).accept(MediaType.APPLICATION_XML)), 406);
    }

    @Test
    void 재발급_쿠키가_없으면_400(final CapturedOutput output) throws Exception {
      assertKept(perform(output, post("/v1/auth/reissue-token")), 400);
    }

    @Test
    void 필수_파라미터가_없으면_기존_처리기대로_C001(final CapturedOutput output) throws Exception {
      final Captured captured = perform(output, get("/v1/groups/members"));

      assertKept(captured, 400);
      captured.result().andExpect(jsonPath("$.code").value("C-001"));
    }

    @Test
    void 업로드_크기_초과는_413(final CapturedOutput output) throws Exception {
      given(bannerQueryService.getBanners())
          .willThrow(new MaxUploadSizeExceededException(5L * 1024 * 1024));

      assertKept(perform(output, get(BANNERS)), 413);
    }

    @Test
    void 클라이언트_연결_끊김은_에러_로그를_남기지_않는다(final CapturedOutput output) throws Exception {
      given(bannerQueryService.getBanners())
          .willAnswer(
              invocation -> {
                throw new IOException("Broken pipe");
              });

      final Captured captured = perform(output, get(BANNERS));

      captured.result().andExpect(content().string(not(containsString("C-002"))));
      assertNoNewLogs(captured);
    }
  }

  @Nested
  @DisplayName("Security 예외는 필터가 401·403 으로 바꾼다")
  class SecurityHandled {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private Captured performWithSecurity(
        final CapturedOutput output, final MockHttpServletRequestBuilder request) throws Exception {
      final MockMvc secured =
          MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
      return perform(
          output,
          secured,
          request.header(
              HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(1L)));
    }

    @Test
    void AccessDeniedException_은_403(final CapturedOutput output) throws Exception {
      given(bannerQueryService.getBanners()).willThrow(new AccessDeniedException("거부"));

      final Captured captured = performWithSecurity(output, get(BANNERS));

      captured
          .result()
          .andExpect(status().isForbidden())
          .andExpect(content().string(not(containsString("C-002"))));
      assertNoNewLogs(captured);
    }

    @Test
    void AuthenticationException_은_401(final CapturedOutput output) throws Exception {
      given(bannerQueryService.getBanners())
          .willThrow(new InsufficientAuthenticationException("인증 부족"));

      final Captured captured = performWithSecurity(output, get(BANNERS));

      captured
          .result()
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("AUTH-005"));
      assertNoNewLogs(captured);
    }
  }
}
