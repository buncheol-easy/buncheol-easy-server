package buncheoleasy.auth.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.TokenPair;
import buncheoleasy.auth.application.AppLoginResult;
import buncheoleasy.auth.application.AppLoginService;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("AppAuthController 문서화 테스트")
class AppAuthControllerDocsTest extends DocsTestSupport {

  private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

  @MockitoBean private AppLoginService appLoginService;

  private static FieldDescriptor[] loginResponseFields() {
    return new FieldDescriptor[] {
      fieldWithPath("accessToken").description("Access Token (앱은 메모리에만 둔다)"),
      fieldWithPath("refreshToken").description("앱 칸 Refresh Token (앱은 Keychain 에 둔다)"),
      fieldWithPath("signup").description("이번 로그인으로 계정을 새로 만들었으면 true")
    };
  }

  @Test
  void 앱_카카오_로그인_코드_교환() throws Exception {
    given(appLoginService.exchangeCode("one-time-code", VERIFIER))
        .willReturn(new AppLoginResult(new TokenPair("access-token", "app-refresh-token"), true));

    mockMvc
        .perform(
            post("/v1/auth/app/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "one-time-code",
                      "verifier": "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
                    }"""))
        .andExpect(status().isOk())
        .andDo(
            document(
                "auth-app-token",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Auth (App)")
                        .summary("앱 카카오 로그인 — 일회용 코드 교환")
                        .description(
                            """
                            앱이 인증 창으로 `GET /oauth2/authorization/kakao/app?challenge=<S256 challenge>`
                            를 열면, 로그인 뒤 서버가 앱 주소(`APP_LOGIN_CALLBACK_URL`, 예 `buncheoleasy://auth`)에
                            `?code=<code>`(새 가입이면 `&signup=1`, 실패면
                            `?error=email_required|cancelled|failed|invalid_request`)를 붙여 돌려보낸다.
                            앱 주소가 비어 있는 서버는 시작 요청을 `400 AUTH-013` 으로 거절한다(앱 로그인 꺼짐).
                            그 코드와 처음 만든 verifier 를 이 API 로 보내면 두 토큰을 본문으로 준다.

                            코드는 **120초 · 1회용**이다. 없음·만료·이미 사용은 `401 AUTH-007`,
                            verifier 가 challenge 와 맞지 않으면 `401 AUTH-008`(코드는 이미 소비됨).
                            토큰은 웹과 다른 앱 칸에 저장되므로 웹 로그인 상태에 영향을 주지 않는다.""")
                        .requestSchema(Schema.schema("AppTokenRequest"))
                        .requestFields(
                            fieldWithPath("code").description("앱 주소로 받은 일회용 코드"),
                            fieldWithPath("verifier")
                                .description(
                                    "PKCE verifier — RFC 7636 형식(43~128자, 영문·숫자·`-._~`). "
                                        + "challenge = BASE64URL(SHA256(verifier))"))
                        .responseSchema(Schema.schema("AppLoginResponse"))
                        .responseFields(loginResponseFields())
                        .build())));
  }

  @Test
  void 애플_로그인() throws Exception {
    given(
            appLoginService.loginWithApple(
                "apple-identity-token", "raw-nonce", "홍길동", "127.0.0.1"))
        .willReturn(new AppLoginResult(new TokenPair("access-token", "app-refresh-token"), true));

    mockMvc
        .perform(
            post("/v1/auth/app/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "identityToken": "apple-identity-token",
                      "rawNonce": "raw-nonce",
                      "fullName": "홍길동"
                    }"""))
        .andExpect(status().isOk())
        .andDo(
            document(
                "auth-app-apple",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Auth (App)")
                        .summary("애플 로그인")
                        .description(
                            """
                            기기의 애플 로그인 창에서 받은 신원 토큰으로 가입·로그인한다. 서버는 애플 공개키 서명,
                            발급자(`https://appleid.apple.com`), 대상 앱(허용 번들 ID), 만료, nonce
                            (토큰의 nonce = 소문자 hex SHA256(rawNonce))를 확인한다.

                            애플 가입자는 전화번호·연령대가 없어 가입 정보 입력 화면을 거치고, C2C 개최 자격은
                            생기지 않는다. 실패: 토큰 검증 실패 `401 AUTH-009`, 서버에 허용 앱이 설정되지 않음
                            `400 AUTH-010`, 애플 공개키 조회 실패 `503 AUTH-011`, 새 가입인데 이메일 없음
                            `401 AUTH-003`, 같은 IP 의 검증 실패가 한도(기본 10분에 10회)에 닿음 `429 AUTH-012`.""")
                        .requestSchema(Schema.schema("AppleLoginRequest"))
                        .requestFields(
                            fieldWithPath("identityToken").description("애플 신원 토큰(JWT)"),
                            fieldWithPath("rawNonce")
                                .description("애플 로그인 요청에 SHA256 해시로 넣었던 원래 nonce"),
                            fieldWithPath("fullName")
                                .optional()
                                .description(
                                    "애플이 첫 로그인 때만 주는 이름(선택). 새 가입에만 쓰고, 프로필 실명 규칙"
                                        + "(한글·영문 1~30자, 공백 없음)에 맞지 않으면 버린다"))
                        .responseSchema(Schema.schema("AppLoginResponse"))
                        .responseFields(loginResponseFields())
                        .build())));
  }

  @Test
  void 앱_토큰_재발급() throws Exception {
    given(appLoginService.reissueTokens("app-refresh-token"))
        .willReturn(new TokenPair("new-access-token", "new-app-refresh-token"));

    mockMvc
        .perform(
            post("/v1/auth/app/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "app-refresh-token"
                    }"""))
        .andExpect(status().isOk())
        .andDo(
            document(
                "auth-app-reissue",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Auth (App)")
                        .summary("앱 토큰 재발급")
                        .description(
                            "앱 칸 Refresh Token 으로 두 토큰을 새로 받는다(회전 — 이전 Refresh Token 은 무효). "
                                + "웹 Refresh Token 을 보내면 `401 AUTH-005`.")
                        .requestSchema(Schema.schema("AppReissueRequest"))
                        .requestFields(
                            fieldWithPath("refreshToken").description("앱 칸 Refresh Token"))
                        .responseSchema(Schema.schema("AppTokenResponse"))
                        .responseFields(
                            fieldWithPath("accessToken").description("새 Access Token"),
                            fieldWithPath("refreshToken").description("새 앱 칸 Refresh Token"))
                        .build())));
  }

  @Test
  void 앱_로그아웃() throws Exception {
    mockMvc
        .perform(post("/v1/auth/app/logout").with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "auth-app-logout",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Auth (App)")
                        .summary("앱 로그아웃")
                        .description("앱 칸 Refresh Token 만 무효화한다. 같은 회원의 웹 로그인은 유지된다.")
                        .requestHeaders(userAuthorizationHeader())
                        .build())));
  }
}
