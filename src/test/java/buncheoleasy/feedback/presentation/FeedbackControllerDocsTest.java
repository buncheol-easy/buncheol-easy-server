package buncheoleasy.feedback.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.feedback.application.FeedbackService;
import buncheoleasy.global.docs.DocsTestSupport;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("FeedbackController 문서화 테스트")
class FeedbackControllerDocsTest extends DocsTestSupport {

  @MockitoBean private FeedbackService feedbackService;

  @Test
  void 의견_보내기() throws Exception {
    mockMvc
        .perform(
            post("/v1/feedbacks")
                .with(userAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "content": "입금 계좌가 어디 있는지 못 찾겠어요",
                      "screenPath": "/profile/bids"
                    }"""))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "feedbacks-create",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Feedback")
                        .summary("의견 보내기")
                        .description(
                            """
                            사용자가 남긴 의견을 접수한다. **비로그인 호출 허용** — 로그인이 안 돼서 남기는
                            의견도 받기 위함이다. 접수된 의견은 **저장하지 않고 운영자 슬랙 채널로만 전달**되며,
                            사용자에게 답장하지 않는 단방향 수집이라 연락처는 받지 않는다.

                            도배 방지를 위해 제출 주체(로그인 회원은 회원 ID, 비로그인은 클라이언트 IP)별로
                            제한이 걸려 있고, 한도를 넘으면 `429 FDB-001` 을 반환한다.""")
                        .requestSchema(Schema.schema("CreateFeedbackRequest"))
                        .requestFields(
                            fieldWithPath("content").description("의견 본문 (필수, 최대 500자)"),
                            fieldWithPath("screenPath")
                                .optional()
                                .description(
                                    "의견을 남긴 화면의 in-app 경로 (선택, 최대 200자). "
                                        + "`/` 로 시작하는 상대 경로만 허용한다"))
                        .requestHeaders(
                            optionalUserAuthorizationHeader(
                                "로그인 상태면 슬랙 알림에 닉네임·회원 ID 가 함께 표시된다"))
                        .build())));
  }
}
