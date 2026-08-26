package buncheoleasy.admin.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.admin.application.AdminParticipationCodeService;
import buncheoleasy.admin.dto.request.AdminParticipationCodeIssueRequest;
import buncheoleasy.admin.dto.response.AdminBuncheolSlotResponse;
import buncheoleasy.admin.dto.response.AdminParticipationCodeResponse;
import buncheoleasy.buncheol.domain.member.SlotAccessType;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("AdminParticipationCodeController 문서화 테스트")
class AdminParticipationCodeControllerDocsTest extends DocsTestSupport {

  @MockitoBean private AdminParticipationCodeService adminParticipationCodeService;

  private static final Instant ISSUED_AT = Instant.parse("2026-08-25T02:00:00Z");
  private static final Instant EXPIRES_AT = Instant.parse("2026-08-27T02:00:00Z");

  private AdminParticipationCodeResponse code() {
    return new AdminParticipationCodeResponse(
        7L,
        "ABCD2345",
        10L,
        101L,
        "정원",
        "@supporter",
        AdminParticipationCodeResponse.Status.ACTIVE,
        ISSUED_AT,
        EXPIRES_AT,
        "8월 25일(화) 11:00",
        "8월 27일(목) 11:00",
        null,
        null,
        null);
  }

  @Test
  void 관리자_분철_슬롯_목록_조회() throws Exception {
    given(adminParticipationCodeService.getSlots(10L))
        .willReturn(
            List.of(
                new AdminBuncheolSlotResponse(
                    101L, "정원", 0L, SlotAccessType.CODE_ONLY, false, code()),
                new AdminBuncheolSlotResponse(
                    102L, "제이", 20_700L, SlotAccessType.OPEN, true, null)));

    mockMvc
        .perform(get("/v1/admin/buncheols/{buncheolId}/slots", 10L).with(adminAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "admin-buncheol-slots",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 분철 슬롯 목록 조회")
                        .description(
                            """
                            코드 발급 화면용 슬롯 목록을 등록 순으로 조회한다 (ROLE_ADMIN 전용).

                            각 슬롯의 접근 정책(`accessType`)과 가장 최근에 발급한 미사용 코드(`activeCode`)를 함께 내려준다.
                            `activeCode` 는 **만료된 코드도 포함**한다 — "발급했는데 안 썼다" 가 차순위 재발급 판단의 근거이기 때문이다.
                            사용·폐기된 코드는 제외된다.""")
                        .requestHeaders(adminAuthorizationHeader())
                        .pathParameters(parameterWithName("buncheolId").description("분철 ID"))
                        .responseSchema(Schema.schema("AdminBuncheolSlotListResponse"))
                        .responseFields(
                            fieldWithPath("[].buncheolMemberId").description("멤버 슬롯 ID (코드 발급 대상)"),
                            fieldWithPath("[].memberName").description("멤버 이름").optional(),
                            fieldWithPath("[].price").description("슬롯 금액 (원). 코드 참여 슬롯은 0"),
                            fieldWithPath("[].accessType")
                                .description("슬롯 접근 정책 (OPEN=선착순 | CODE_ONLY=코드 참여)"),
                            fieldWithPath("[].taken").description("활성 참여가 점유 중인지"),
                            fieldWithPath("[].activeCode")
                                .description("가장 최근 미사용 코드(만료 포함). 없으면 null")
                                .optional(),
                            fieldWithPath("[].activeCode.codeId").description("코드 ID").optional(),
                            fieldWithPath("[].activeCode.code").description("코드 문자열").optional(),
                            fieldWithPath("[].activeCode.buncheolId").description("분철 ID").optional(),
                            fieldWithPath("[].activeCode.buncheolMemberId")
                                .description("바인딩된 멤버 슬롯 ID")
                                .optional(),
                            fieldWithPath("[].activeCode.memberName")
                                .description("멤버 이름")
                                .optional(),
                            fieldWithPath("[].activeCode.issuedTo")
                                .description("코드를 보낸 계정")
                                .optional(),
                            fieldWithPath("[].activeCode.status")
                                .description("ACTIVE | EXPIRED | USED | REVOKED")
                                .optional(),
                            fieldWithPath("[].activeCode.issuedAt").description("발급 시각").optional(),
                            fieldWithPath("[].activeCode.expiresAt").description("만료 시각").optional(),
                            fieldWithPath("[].activeCode.issuedAtText")
                                .description("발급 시각 KST 표기 (DM 문안 복붙용)")
                                .optional(),
                            fieldWithPath("[].activeCode.expiresAtText")
                                .description("만료 시각 KST 표기 (DM 문안 복붙용)")
                                .optional(),
                            fieldWithPath("[].activeCode.usedAt").description("사용 시각").optional(),
                            fieldWithPath("[].activeCode.usedParticipationId")
                                .description("이 코드로 생성된 참여 ID")
                                .optional(),
                            fieldWithPath("[].activeCode.revokedAt").description("폐기 시각").optional())
                        .build())));
  }

  @Test
  void 관리자_참여_코드_발급_이력_조회() throws Exception {
    given(adminParticipationCodeService.getCodes(10L)).willReturn(List.of(code()));

    mockMvc
        .perform(
            get("/v1/admin/buncheols/{buncheolId}/participation-codes", 10L).with(adminAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "admin-participation-codes-list",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 참여 코드 발급 이력 조회")
                        .description(
                            """
                            분철의 코드 발급 이력 전체를 최신순으로 조회한다 (ROLE_ADMIN 전용).
                            폐기·사용된 코드도 포함한다 — 차순위 재발급 경위를 추적하는 것이 목적이다.""")
                        .requestHeaders(adminAuthorizationHeader())
                        .pathParameters(parameterWithName("buncheolId").description("분철 ID"))
                        .responseSchema(Schema.schema("AdminParticipationCodeListResponse"))
                        .responseFields(
                            fieldWithPath("[].codeId").description("코드 ID (폐기 시 사용)"),
                            fieldWithPath("[].code").description("코드 문자열 (8자, Crockford base32)"),
                            fieldWithPath("[].buncheolId").description("분철 ID"),
                            fieldWithPath("[].buncheolMemberId")
                                .description("바인딩된 멤버 슬롯 ID")
                                .optional(),
                            fieldWithPath("[].memberName").description("멤버 이름").optional(),
                            fieldWithPath("[].issuedTo")
                                .description("코드를 보낸 계정 (예: `X_handle`·`N_blogid`)")
                                .optional(),
                            fieldWithPath("[].status")
                                .description("ACTIVE | EXPIRED | USED | REVOKED (저장 컬럼 조합의 파생값)"),
                            fieldWithPath("[].issuedAt").description("발급 시각"),
                            fieldWithPath("[].expiresAt").description("만료 시각"),
                            fieldWithPath("[].issuedAtText").description("발급 시각 KST 표기"),
                            fieldWithPath("[].expiresAtText").description("만료 시각 KST 표기"),
                            fieldWithPath("[].usedAt").description("사용 시각. 미사용이면 null").optional(),
                            fieldWithPath("[].usedParticipationId")
                                .description("이 코드로 생성된 참여 ID")
                                .optional(),
                            fieldWithPath("[].revokedAt")
                                .description("폐기 시각. 미폐기면 null")
                                .optional())
                        .build())));
  }

  @Test
  void 관리자_참여_코드_발급() throws Exception {
    given(adminParticipationCodeService.issue(eq(10L), any())).willReturn(code());

    mockMvc
        .perform(
            post("/v1/admin/buncheols/{buncheolId}/participation-codes", 10L)
                .with(adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"buncheolMemberId\": 101, \"issuedTo\": \"@supporter\","
                        + " \"validHours\": 48, \"reissue\": false}"))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "admin-participation-codes-issue",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 참여 코드 발급")
                        .description(
                            """
                            코드 참여 슬롯(`CODE_ONLY`)에 1회용 참여 코드를 발급한다 (ROLE_ADMIN 전용).

                            `reissue=true` 면 슬롯에 남은 코드를 모두 폐기한 뒤 새로 발급한다(차순위 재발급).
                            기본값(false)에서 **아직 쓸 수 있는** 코드가 있으면 거부한다 — 두 사람에게 동시에 유효한
                            코드가 나가는 이중 배정을 기본 동작에서 막는다.

                            만료된 코드는 발급을 막지 않는다(이미 쓸 수 없으므로 두 코드가 동시에 유효해지지 않는다).
                            다만 이력 위생을 위해 `reissue=true` 로 함께 닫는 것을 권한다.

                            | 상태 | 코드 | 의미 |
                            |------|------|------|
                            | 400 | `C-001` (`INVALID_INPUT_VALUE`) | `buncheolMemberId` 누락 등 요청 검증 실패 |
                            | 400 | `BCH-104` (`PARTICIPATION_CODE_EXPIRY_INVALID`) | 산정된 유효기한이 현재 시각 이전 |
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 분철 없음 |
                            | 404 | `BCH-061` (`PARTICIPATION_MEMBER_NOT_FOUND`) | 해당 분철에 그 멤버 슬롯이 없음 |
                            | 409 | `BCH-102` (`PARTICIPATION_CODE_SLOT_ALREADY_ISSUED`) | 아직 쓸 수 있는 코드가 있는데 `reissue=false` |
                            | 409 | `BCH-103` (`PARTICIPATION_CODE_SLOT_NOT_CODE_ONLY`) | 선착순 슬롯에 발급 시도 |
                            | 409 | `BCH-108` (`PARTICIPATION_CODE_SLOT_TAKEN`) | 이미 참여가 확정된 슬롯 (발급해도 쓸 수 없다) |
                            """)
                        .requestHeaders(adminAuthorizationHeader())
                        .pathParameters(parameterWithName("buncheolId").description("분철 ID"))
                        .requestSchema(Schema.schema("AdminParticipationCodeIssueRequest"))
                        .requestFields(
                            fieldWithPath("buncheolMemberId")
                                .description("코드를 바인딩할 멤버 슬롯 ID (해당 슬롯이 CODE_ONLY 여야 한다)"),
                            fieldWithPath("issuedTo")
                                .description(
                                    "코드를 보낸 계정 (50자 이내, 예: `X_handle`·`N_blogid`)."
                                        + " 운영 메모이며 인증에는 쓰지 않는다")
                                .optional(),
                            fieldWithPath("validHours")
                                .description("유효기간(시간). 생략 시 48시간, 최대 720시간")
                                .optional(),
                            fieldWithPath("reissue")
                                .description("재발급 여부. true 면 이전 코드를 폐기한 뒤 발급")
                                .optional())
                        .responseSchema(Schema.schema("AdminParticipationCodeResponse"))
                        .responseFields(
                            fieldWithPath("codeId").description("코드 ID"),
                            fieldWithPath("code").description("발급된 코드 문자열 (DM 으로 전달)"),
                            fieldWithPath("buncheolId").description("분철 ID"),
                            fieldWithPath("buncheolMemberId").description("바인딩된 멤버 슬롯 ID").optional(),
                            fieldWithPath("memberName").description("멤버 이름").optional(),
                            fieldWithPath("issuedTo").description("코드를 보낸 계정").optional(),
                            fieldWithPath("status").description("ACTIVE | EXPIRED | USED | REVOKED"),
                            fieldWithPath("issuedAt").description("발급 시각"),
                            fieldWithPath("expiresAt").description("만료 시각"),
                            fieldWithPath("issuedAtText").description("발급 시각 KST 표기 (DM 문안 복붙용)"),
                            fieldWithPath("expiresAtText").description("만료 시각 KST 표기 (DM 문안 복붙용)"),
                            fieldWithPath("usedAt").description("사용 시각. 발급 직후엔 null").optional(),
                            fieldWithPath("usedParticipationId")
                                .description("이 코드로 생성된 참여 ID")
                                .optional(),
                            fieldWithPath("revokedAt").description("폐기 시각").optional())
                        .build())));

    ArgumentCaptor<AdminParticipationCodeIssueRequest> captor =
        ArgumentCaptor.forClass(AdminParticipationCodeIssueRequest.class);
    then(adminParticipationCodeService).should().issue(eq(10L), captor.capture());
    assertThat(captor.getValue().buncheolMemberId()).isEqualTo(101L);
    assertThat(captor.getValue().reissue()).isFalse();
  }

  @Test
  void 관리자_슬롯_접근_정책_전환() throws Exception {
    mockMvc
        .perform(
            patch(
                    "/v1/admin/buncheols/{buncheolId}/slots/{buncheolMemberId}",
                    10L,
                    101L)
                .with(adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessType\": \"CODE_ONLY\"}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "admin-buncheol-slot-access-type",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 슬롯 접근 정책 전환")
                        .description(
                            """
                            멤버 슬롯을 선착순(`OPEN`) ↔ 코드 참여(`CODE_ONLY`)로 전환한다 (ROLE_ADMIN 전용).

                            개최 폼은 전 슬롯을 선착순으로 만들고 배정 슬롯 지정은 이 API 로 한다 — 일반 유저
                            개최 화면에 운영 전용 옵션을 노출하지 않기 위함이다.

                            **활성 참여가 있는 슬롯은 바꿀 수 없다.** 판정과 전이를 한 UPDATE 로 원자화해,
                            정책을 바꾸는 사이에 들어온 선착순 참여가 코드 슬롯에 남지 않게 한다.

                            | 상태 | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 분철 없음 |
                            | 409 | `BCH-103` (`PARTICIPATION_CODE_SLOT_NOT_CODE_ONLY`) | C2C 분철을 코드 참여로 전환 시도 |
                            | 409 | `BCH-107` (`BUNCHEOL_MEMBER_ACCESS_TYPE_CHANGE_NOT_ALLOWED`) | 참여자가 있는 슬롯이거나 슬롯 없음 |
                            | 409 | `BCH-109` (`PARTICIPATION_CODE_SLOT_NOT_FREE`) | 유료 슬롯을 코드 참여로 전환 시도 (코드 참여는 0원 전제) |
                            """)
                        .requestHeaders(adminAuthorizationHeader())
                        .pathParameters(
                            parameterWithName("buncheolId").description("분철 ID"),
                            parameterWithName("buncheolMemberId").description("멤버 슬롯 ID"))
                        .requestSchema(Schema.schema("AdminSlotAccessTypeRequest"))
                        .requestFields(
                            fieldWithPath("accessType")
                                .description("OPEN=선착순 | CODE_ONLY=참여 코드 보유자 전용"))
                        .build())));

    then(adminParticipationCodeService)
        .should()
        .changeSlotAccessType(10L, 101L, SlotAccessType.CODE_ONLY);
  }

  @Test
  void 관리자_참여_코드_폐기() throws Exception {
    mockMvc
        .perform(delete("/v1/admin/participation-codes/{codeId}", 7L).with(adminAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "admin-participation-codes-revoke",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("관리자 참여 코드 폐기")
                        .description(
                            """
                            코드를 즉시 무효화한다 (ROLE_ADMIN 전용). 유출 신고를 받았을 때처럼 재발급 없이
                            닫아야 하는 경우에 쓴다. 이미 사용된 코드는 폐기할 수 없다 — 참여가 이미 생성됐으므로
                            폐기로 되돌려지지 않는다.

                            | 상태 | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `BCH-101` (`PARTICIPATION_CODE_NOT_FOUND`) | 코드 없음 |
                            | 409 | `BCH-106` (`PARTICIPATION_CODE_REVOKE_NOT_ALLOWED`) | 이미 사용되었거나 폐기됨 |
                            """)
                        .requestHeaders(adminAuthorizationHeader())
                        .pathParameters(parameterWithName("codeId").description("코드 ID"))
                        .build())));

    then(adminParticipationCodeService).should().revoke(7L);
  }
}
