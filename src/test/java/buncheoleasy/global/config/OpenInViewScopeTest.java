package buncheoleasy.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * 요청 내내 EntityManager 를 여는 인터셉터(OSIV)가 어느 경로에 걸리는지 고정한다. Boot 자동 등록을 끄고 WebConfig 가 다시 걸었으므로, 다른 API 는 지금처럼
 * 걸려 있어야 하고 AI 개최글만 빠져야 한다 — AI 는 엔진을 최대 25초 기다리는 동안 커넥션을 쥐면 안 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OSIV 적용 범위 테스트")
class OpenInViewScopeTest {

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  private boolean opensEntityManagerInView(final String method, final String path)
      throws Exception {
    return opensEntityManagerInView(method, path, null);
  }

  /** 본문 형식을 가려 받는 핸들러(JSON·multipart)는 Content-Type 이 맞아야 찾아진다. */
  private boolean opensEntityManagerInView(
      final String method, final String path, final String contentType) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setContentType(contentType);
    ServletRequestPathUtils.parseAndCache(request);
    HandlerExecutionChain chain = handlerMapping.getHandler(request);
    assertThat(chain).as("%s %s 에 핸들러가 있어야 한다", method, path).isNotNull();
    return chain.getInterceptorList().stream()
        .filter(WebRequestHandlerInterceptorAdapter.class::isInstance)
        .map(adapter -> ReflectionTestUtils.getField(adapter, "requestInterceptor"))
        .anyMatch(OpenEntityManagerInViewInterceptor.class::isInstance);
  }

  @Test
  void AI_개최글_두_경로에는_걸리지_않는다() throws Exception {
    assertThat(opensEntityManagerInView("POST", "/v1/ai/drafts", "application/json")).isFalse();
    assertThat(opensEntityManagerInView("GET", "/v1/ai/drafts/quota")).isFalse();
  }

  @Test
  void 다른_API_에는_지금처럼_걸린다() throws Exception {
    assertThat(opensEntityManagerInView("GET", "/v1/buncheols")).isTrue();
    assertThat(opensEntityManagerInView("GET", "/v1/buncheols/1")).isTrue();
    assertThat(opensEntityManagerInView("POST", "/v1/buncheols", "multipart/form-data; boundary=x"))
        .isTrue();
    assertThat(opensEntityManagerInView("GET", "/v1/groups")).isTrue();
    assertThat(opensEntityManagerInView("POST", "/v1/feedbacks", "application/json")).isTrue();
  }
}
