package buncheoleasy.buncheol.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.buncheol.application.participation.ParticipationBundleService;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * ⚠️ 이 파일이 없으면 신설 엔드포인트가 <b>OpenAPI 명세서에 아예 나타나지 않는다</b> — {@code openapi3 →
 * copyApiDocs → bootJar} 체인이 DocsTest 스니펫에서 명세를 만들어 앱이 서빙하기 때문이다.
 */
@DisplayName("ParticipationBundle 컨트롤러 문서화 테스트")
class ParticipationBundleControllerDocsTest extends DocsTestSupport {

  private static final Long BUNDLE_ID = 141L;

  @MockitoBean private ParticipationBundleService participationBundleService;

  @Test
  void 개최자_묶음_제외() throws Exception {
    given(participationBundleService.release(eq(USER_ID), eq(BUNDLE_ID)))
        .willReturn(List.of(232L, 233L));

    mockMvc
        .perform(
            post("/v1/participation-bundles/{bundleId}/release", BUNDLE_ID).with(userAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "participation-bundles-release",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("ParticipationBundle")
                        .summary("개최자 묶음 「제외」")
                        .description(
                            """
                            개최자가 **입금 기한이 지난** 묶음의 활성 슬롯을 전부 취소한다. C2C 는 기한이 지나도 자동
                            취소하지 않으므로 이것이 미입금자를 빼는 유일한 출구다.

                            **열리는 조건은 하나다 — 입금 기한이 정해졌고, 그 기한이 지났을 때.**

                            | 상태 | 제외 |
                            |------|------|
                            | 모집 중 (기한 없음) | ❌ |
                            | 입금 대기 · 「보냈어요」 — 기한 **전** | ❌ |
                            | 입금 대기 · 「보냈어요」 — 기한 **후** | ✅ |
                            | 입금확인됨 | ❌ |

                            모집 중을 막는 이유: 아직 확정도 안 된 참여자를 개최자가 임의로 자를 수 있는 도구가 되면
                            안 된다. 기한 전을 막는 이유: 이체가 늦게 찍혀 **정상 입금자를 빼는 사고**가 나면 복구
                            경로가 문의뿐이다.

                            응답의 `releasedParticipationIds` 는 **실제로 취소된** 슬롯이다 — 화면이 본 집합과
                            다를 수 있으므로(그 사이 참여자가 자발 취소하는 등) 사후 대조에 쓴다.

                            가부는 개최 관리 응답의 `participants[].releasability` 로 미리 알 수 있다(같은 판정).

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `BCH-114` (`BUNDLE_NOT_FOUND`) | 묶음 없음 |
                            | 403 | `BUNCHEOL_NO_PERMISSION` | 개최자가 아님 |
                            | 409 | `BCH-084` (`BUNCHEOL_FLOW_NOT_SUPPORTED`) | LEGACY 분철 |
                            | 409 | `BCH-111` (`BUNDLE_RELEASE_RECRUITING`) | 모집 중이라 불가 |
                            | 409 | `BCH-112` (`BUNDLE_RELEASE_BEFORE_DUE`) | 입금 기한 전 |
                            | 409 | `BCH-113` (`BUNDLE_RELEASE_HAS_CONFIRMED`) | 입금확인된 슬롯 있음 |
                            """)
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("bundleId").description("참여 묶음 ID"))
                        .responseFields(
                            fieldWithPath("bundleId")
                                .type(JsonFieldType.NUMBER)
                                .description("제외한 묶음 ID"),
                            fieldWithPath("releasedParticipationIds")
                                .type(JsonFieldType.ARRAY)
                                .description("실제로 취소된 참여 ID 목록"))
                        .responseSchema(Schema.schema("BundleReleaseResponse"))
                        .build())));
  }
}
