package buncheoleasy.user.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.user.application.UserService;
import buncheoleasy.user.dto.response.NicknameDuplicateResponse;
import buncheoleasy.user.dto.response.ProfileStatusResponse;
import buncheoleasy.user.dto.response.UserProfileResponse;
import buncheoleasy.user.dto.response.UserProfileResponse.BankAccountInfo;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("UserController 문서화 테스트")
class UserControllerDocsTest {

  private static final Long USER_ID = 1L;

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private UserService userService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp(final RestDocumentationContextProvider restDocumentation) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private RequestPostProcessor mockAuth() {
    return (MockHttpServletRequest request) -> {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList()));
      return request;
    };
  }

  @Test
  void 회원_프로필_조회() throws Exception {
    // given
    given(userService.getUserProfile(USER_ID))
        .willReturn(
            UserProfileResponse.of(
                "KAKAO",
                "user@example.com",
                "에이지",
                "01012345678",
                new BankAccountInfo("국민", "12345678901234", "채아진"),
                false));

    // when & then
    mockMvc
        .perform(
            get("/v1/users/me").header("Authorization", "Bearer {accessToken}").with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "users-get-profile",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User")
                        .summary("회원 프로필 조회")
                        .description("로그인한 사용자의 프로필 정보를 반환한다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("UserProfileResponse"))
                        .responseFields(
                            fieldWithPath("provider").description("OAuth Provider (KAKAO)"),
                            fieldWithPath("email").description("이메일"),
                            fieldWithPath("nickname").description("닉네임"),
                            fieldWithPath("phoneNumber").description("휴대폰 번호").optional(),
                            fieldWithPath("bankAccount").description("정산 계좌 (없으면 null)").optional(),
                            fieldWithPath("bankAccount.bank").description("은행명").optional(),
                            fieldWithPath("bankAccount.account").description("계좌번호").optional(),
                            fieldWithPath("bankAccount.holder").description("예금주").optional(),
                            fieldWithPath("canHost")
                                .description("분철 개최 가능 여부 (개최 오픈 전엔 운영 지정 계정만 true)"))
                        .build())));
  }

  @Test
  void 프로필_완료_여부_조회() throws Exception {
    // given
    given(userService.getProfileStatus(USER_ID)).willReturn(ProfileStatusResponse.of(true));

    // when & then
    mockMvc
        .perform(
            get("/v1/users/me/profile/status")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "users-get-profile-status",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User")
                        .summary("프로필 완료 여부 조회")
                        .description("로그인 직후 클라이언트가 프로필 설정 완료 여부를 확인한다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("ProfileStatusResponse"))
                        .responseFields(
                            fieldWithPath("profileCompleted").description("프로필 설정 완료 여부"))
                        .build())));
  }

  @Test
  void 닉네임_중복_조회() throws Exception {
    // given
    given(userService.checkNicknameDuplicate(USER_ID, "새닉네임"))
        .willReturn(NicknameDuplicateResponse.of(false));

    // when & then
    mockMvc
        .perform(
            get("/v1/users/nickname/duplicate")
                .param("nickname", "새닉네임")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "users-check-nickname-duplicate",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User")
                        .summary("닉네임 중복 조회")
                        .description("입력한 닉네임을 다른 유저가 이미 쓰고 있는지 확인한다. 본인 현재 닉네임은 중복으로 보지 않는다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .queryParameters(
                            parameterWithName("nickname").description("확인할 닉네임 (1~20자, 한글/영문/숫자)"))
                        .responseSchema(Schema.schema("NicknameDuplicateResponse"))
                        .responseFields(
                            fieldWithPath("duplicated").description("중복 여부 (true=이미 사용 중)"))
                        .build())));
  }

  @Test
  void 회원_프로필_수정() throws Exception {
    // when & then
    mockMvc
        .perform(
            put("/v1/users/me")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"새닉네임\",\"phoneNumber\":\"01098765432\"}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "users-update-profile",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User")
                        .summary("회원 프로필 등록/수정")
                        .description("닉네임/휴대폰 번호를 등록 또는 수정한다. 미완료 유저가 호출하면 자동으로 프로필 완료 상태로 전이된다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestSchema(Schema.schema("UpdateUserProfileRequest"))
                        .requestFields(
                            fieldWithPath("nickname").description("닉네임 (1~20자, 한글/영문/숫자)"),
                            fieldWithPath("phoneNumber")
                                .description("휴대폰 번호 (01x로 시작하는 10~11자리 숫자)"))
                        .build())));
  }

  @Test
  void 정산_계좌_등록() throws Exception {
    // when & then
    mockMvc
        .perform(
            put("/v1/users/me/bank-account")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bank\":\"국민\",\"account\":\"12345678901234\",\"holder\":\"채아진\"}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "users-update-bank-account",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User")
                        .summary("정산 계좌 등록/수정")
                        .description("호스트가 정산받을 계좌 정보를 등록 또는 수정한다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestSchema(Schema.schema("BankAccountRequest"))
                        .requestFields(
                            fieldWithPath("bank").description("은행명 (1~50자)"),
                            fieldWithPath("account").description("계좌번호 (숫자·하이픈, 1~50자)"),
                            fieldWithPath("holder").description("예금주 (1~50자)"))
                        .build())));
  }

  @Test
  void 회원_탈퇴() throws Exception {
    // when & then
    mockMvc
        .perform(
            delete("/v1/users/me").header("Authorization", "Bearer {accessToken}").with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "users-withdraw",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User")
                        .summary("회원 탈퇴")
                        .description("로그인한 사용자를 탈퇴 처리한다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }
}
