package buncheoleasy.admin.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.admin.application.AdminBuncheolCommandService;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("AdminBuncheolController 문서화 테스트")
class AdminBuncheolControllerDocsTest extends DocsTestSupport {

  @MockitoBean private AdminBuncheolCommandService adminBuncheolCommandService;

  @Test
  void 운영진_분철_파기() throws Exception {
    mockMvc
        .perform(post("/v1/admin/buncheols/{id}/cancel", 10L).with(adminAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "admin-buncheols-cancel",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Admin")
                        .summary("운영진 분철 파기")
                        .description(
                            """
                            입금 단계가 시작된 회원 개최 분철을 운영진이 접는다 (ROLE_ADMIN 전용). 개최자는 이 상태에서
                            직접 취소할 수 없고(`BCH-126`) 「운영진에게 알리기」를 거친다 — 운영자가 실제 입금과 환불 계획을
                            확인한 뒤 분철 번호로 부른다.

                            입금 확인 건수와 무관하게 분철을 숨기고(`HOST_CANCELLED`) 살아 있는 참여를 **입금 확인된 참여까지**
                            전원 취소한다(`BUNCHEOL_CANCELLED`). 빈 묶음은 닫히고 참여자에게 「분철 취소 안내」(사유 "개최자 취소")가
                            사람당 1통 간다. 환불은 개최자가 하고 운영자가 확인한다.

                            **진행 확정(`CONFIRMED`)된 회원 개최 분철도 운송장이 하나도 등록되기 전이면 파기한다.** 결과는 같고,
                            진행 확정 때 만들어진 배송 기록(모두 `SNAPSHOTTED`)도 함께 지운다. 운송장이 하나라도 있으면 발송이
                            시작된 것이라 `BCH-130` 으로 거절하고 아무것도 바꾸지 않는다.

                            **발생 가능한 에러**
                            | HTTP | 코드 | 의미 |
                            |------|------|------|
                            | 404 | `BCH-043` (`BUNCHEOL_NOT_FOUND`) | 존재하지 않는 분철 |
                            | 409 | `BCH-050` (`BUNCHEOL_CANCEL_NOT_ALLOWED`) | 입금 수집 중이거나 진행 확정된 회원 개최 분철이 아님(모집중·취소·운영진 분철) |
                            | 409 | `BCH-130` (`BUNCHEOL_CANCEL_HAS_TRACKING`) | 진행 확정 분철에 운송장이 등록된 배송이 있음 |
                            """)
                        .requestHeaders(adminAuthorizationHeader())
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .build())));

    then(adminBuncheolCommandService).should().cancel(10L);
  }
}
