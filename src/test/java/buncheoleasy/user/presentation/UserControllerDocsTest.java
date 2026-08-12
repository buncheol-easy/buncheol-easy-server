package buncheoleasy.user.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.global.docs.DocsTestSupport;
import buncheoleasy.user.application.UserService;
import buncheoleasy.user.dto.response.NicknameDuplicateResponse;
import buncheoleasy.user.dto.response.ProfileStatusResponse;
import buncheoleasy.user.dto.response.UserProfileResponse;
import buncheoleasy.user.dto.response.UserProfileResponse.BankAccountInfo;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("UserController 문서화 테스트")
class UserControllerDocsTest extends DocsTestSupport {

  @MockitoBean private UserService userService;

  @Test
  void 회원_프로필_조회() throws Exception {
    // given
    given(userService.getUserProfile(USER_ID))
        .willReturn(
            UserProfileResponse.of(
                "KAKAO",
                "user@example.com",
                "에이지",
                "채아진",
                "01012345678",
                new BankAccountInfo("국민", "12345678901234", "채아진"),
                false));

    // when & then
    mockMvc
        .perform(get("/v1/users/me").with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "users-get-profile",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User")
                        .summary("회원 프로필 조회")
                        .description(
                            "로그인한 사용자의 프로필 정보를 반환한다. "
                                + "프로필 미완료 유저가 호출하면 403 `USR-018` (`USER_PROFILE_IS_NOT_COMPLETE`) 로 거부된다.")
                        .requestHeaders(userAuthorizationHeader())
                        .responseSchema(Schema.schema("UserProfileResponse"))
                        .responseFields(
                            fieldWithPath("provider").description("OAuth Provider (KAKAO)"),
                            fieldWithPath("email").description("이메일"),
                            fieldWithPath("nickname").description("닉네임"),
                            fieldWithPath("name")
                                .description("실명 (기존 회원은 미입력 시 null)")
                                .optional(),
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
        .perform(get("/v1/users/me/profile/status").with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "users-get-profile-status",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User")
                        .summary("프로필 완료 여부 조회")
                        .description("로그인 직후 클라이언트가 프로필 설정 완료 여부를 확인한다.")
                        .requestHeaders(userAuthorizationHeader())
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
                .with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "users-check-nickname-duplicate",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User")
                        .summary("닉네임 중복 조회")
                        .description("입력한 닉네임을 다른 유저가 이미 쓰고 있는지 확인한다. 본인 현재 닉네임은 중복으로 보지 않는다.")
                        .requestHeaders(userAuthorizationHeader())
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
                .with(userAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nickname\":\"새닉네임\",\"phoneNumber\":\"01098765432\","
                        + "\"name\":\"김실명\",\"marketingAgreed\":true}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "users-update-profile",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User")
                        .summary("회원 프로필(닉네임·휴대폰·실명·마케팅 동의) 등록/수정")
                        .description(
                            "닉네임·휴대폰 번호와 선택적으로 실명·마케팅 수신 동의 여부를 등록 또는 수정한다. "
                                + "미완료 유저가 호출하면 자동으로 프로필 완료 상태로 전이된다.")
                        .requestHeaders(userAuthorizationHeader())
                        .requestSchema(Schema.schema("UpdateUserProfileRequest"))
                        .requestFields(
                            fieldWithPath("nickname").description("닉네임 (1~20자, 한글/영문/숫자)"),
                            fieldWithPath("phoneNumber")
                                .description("휴대폰 번호 (01x로 시작하는 10~11자리 숫자)"),
                            fieldWithPath("name")
                                .optional()
                                .description("실명 (1~30자, 한글/영문 — 생략하면 기존 값 유지)"),
                            fieldWithPath("marketingAgreed")
                                .optional()
                                .description("마케팅 정보 수신 동의 여부 (생략하면 기존 동의 상태 유지)"))
                        .build())));
  }

  @Test
  void 정산_계좌_등록() throws Exception {
    // when & then
    mockMvc
        .perform(
            put("/v1/users/me/bank-account")
                .with(userAuth())
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
                        .description(
                            """
                            호스트가 정산받을 계좌 정보를 등록 또는 수정한다. C2C 분철에서는 이 계좌가 **참여자에게 그대로 노출되어 실제 송금 대상**이 되므로
                            계좌번호는 **하이픈을 제외한 숫자 8자리 이상**이어야 한다.

                            검증이 두 겹이라 오류 코드가 갈린다 — `@Pattern`·`@Size`(DTO) 위반은 `C-001`, 자릿수·형식(도메인) 위반은 `USR-*` 다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | DTO 검증 위반 (필수값 누락, 50자 초과 등) |
                            | 400 | `USR-023` (`USER_BANK_ACCOUNT_REQUIRED`) | 은행·계좌번호·예금주 중 공백 |
                            | 400 | `USR-024` (`USER_BANK_ACCOUNT_LENGTH_INVALID`) | 항목 길이가 50자 초과 |
                            | 400 | `USR-026` (`USER_BANK_ACCOUNT_FORMAT_INVALID`) | 계좌번호가 숫자·하이픈 형식이 아님 |
                            | 400 | `USR-034` (`USER_BANK_ACCOUNT_TOO_SHORT`) | 계좌번호가 하이픈 제외 8자리 미만 |

                            기존에 등록된 계좌에는 소급 적용하지 않는다 (신규 입력만 — docs/53 Q-02).
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .requestSchema(Schema.schema("BankAccountRequest"))
                        .requestFields(
                            fieldWithPath("bank").description("은행명 (1~50자)"),
                            fieldWithPath("account")
                                .description(
                                    "계좌번호 (숫자·하이픈, 1~50자). **하이픈을 제외한 숫자 8자리 이상** — 미만이면 `400 USR-034`"),
                            fieldWithPath("holder").description("예금주 (1~50자)"))
                        .build())));
  }

  @Test
  void 회원_탈퇴() throws Exception {
    // when & then
    mockMvc
        .perform(delete("/v1/users/me").with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "users-withdraw",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("User")
                        .summary("회원 탈퇴")
                        .description(
                            "로그인한 사용자를 탈퇴 처리한다. "
                                + "끝나지 않은 개최 분철(모집중이거나, 진행확정 후 배송이 끝나지 않은 참여가 남은 분철)이 있으면 "
                                + "409 `USR-028` (`USER_WITHDRAW_BLOCKED_BY_ACTIVE_BUNCHEOL`), "
                                + "끝나지 않은 참여(입금 확인 중이거나, 입금확인 후 배송이 끝나지 않았거나, 배송비 환급 신청 검수 대기 중)가 있으면 "
                                + "409 `USR-029` (`USER_WITHDRAW_BLOCKED_BY_ACTIVE_PARTICIPATION`) 로 거부된다. "
                                + "배송은 운송사 배송완료(DELIVERED)부터 끝난 것으로 본다.")
                        .requestHeaders(userAuthorizationHeader())
                        .build())));
  }
}
