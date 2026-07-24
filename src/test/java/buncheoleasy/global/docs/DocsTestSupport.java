package buncheoleasy.global.docs;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * API 문서화 테스트(restdocs-api-spec → OpenAPI → Scalar) 공통 지원.
 *
 * <p>컨벤션:
 *
 * <ul>
 *   <li>인증 필요한 요청은 {@code .with(userAuth())} (관리자는 {@code adminAuth()}) 하나로 호출한다 — Authorization 헤더
 *       프리필과 SecurityContext 인증이 함께 부착되며, 헤더 프리필 값이 Scalar 플레이그라운드의 예시 값이 된다. 문서화는 {@code
 *       requestHeaders(userAuthorizationHeader())} / {@code adminAuthorizationHeader()} 로 한다.
 *   <li>공개 API 는 인증을 붙이지 않는다. 로그인 여부에 따라 응답이 달라지는 공개 API 만 {@code .with(userAuth())} 로 로그인 예시를 만들고
 *       {@code optionalUserAuthorizationHeader()} 로 문서화한다.
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(RestDocumentationExtension.class)
public abstract class DocsTestSupport {

  protected static final Long USER_ID = 1L;
  protected static final Long ADMIN_ID = 1L;
  protected static final String AUTHORIZATION = HttpHeaders.AUTHORIZATION;
  protected static final String USER_TOKEN = "Bearer {accessToken}";
  protected static final String ADMIN_TOKEN = "Bearer {adminAccessToken}";

  private static final String OPTIONAL_AUTH_NOTE = "`" + USER_TOKEN + "` — 비로그인 호출이면 헤더 자체를 생략";

  protected MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean protected JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUpMockMvc(final RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  /** USER_ID 유저 로그인 — Authorization 헤더 프리필({@link #USER_TOKEN})과 인증 컨텍스트를 함께 부착한다. */
  protected RequestPostProcessor userAuth() {
    return authenticated(USER_ID, "ROLE_USER", USER_TOKEN);
  }

  /** ADMIN_ID 관리자 로그인 — Authorization 헤더 프리필({@link #ADMIN_TOKEN})과 인증 컨텍스트를 함께 부착한다. */
  protected RequestPostProcessor adminAuth() {
    return authenticated(ADMIN_ID, "ROLE_ADMIN", ADMIN_TOKEN);
  }

  private RequestPostProcessor authenticated(
      final Long principalId, final String role, final String token) {
    return request -> {
      request.addHeader(AUTHORIZATION, token);
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  principalId, null, List.of(new SimpleGrantedAuthority(role))));
      return request;
    };
  }

  protected HeaderDescriptor userAuthorizationHeader() {
    return headerWithName(AUTHORIZATION).description(USER_TOKEN);
  }

  protected HeaderDescriptor adminAuthorizationHeader() {
    return headerWithName(AUTHORIZATION).description(ADMIN_TOKEN);
  }

  /** 공개 API 지만 로그인 시 응답이 달라지는 엔드포인트의 선택적 Authorization 헤더 문서화. */
  protected HeaderDescriptor optionalUserAuthorizationHeader() {
    return headerWithName(AUTHORIZATION).description(OPTIONAL_AUTH_NOTE).optional();
  }

  /** {@link #optionalUserAuthorizationHeader()} 에 엔드포인트별 부가 설명을 덧붙인다. */
  protected HeaderDescriptor optionalUserAuthorizationHeader(final String note) {
    return headerWithName(AUTHORIZATION).description(OPTIONAL_AUTH_NOTE + ". " + note).optional();
  }
}
