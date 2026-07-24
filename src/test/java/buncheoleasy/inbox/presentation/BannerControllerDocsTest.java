package buncheoleasy.inbox.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.global.docs.DocsTestSupport;
import buncheoleasy.inbox.application.BannerQueryService;
import buncheoleasy.inbox.dto.response.BannerResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("BannerController 문서화 테스트")
class BannerControllerDocsTest extends DocsTestSupport {

  @MockitoBean private BannerQueryService bannerQueryService;

  @Test
  void 홈_배너_목록_조회() throws Exception {
    // given
    given(bannerQueryService.getBanners())
        .willReturn(
            List.of(
                new BannerResponse(
                    8L, "여름 이벤트", "https://cdn.example.com/notices/8/banner/summer.jpg")));

    // when & then
    mockMvc
        .perform(get("/v1/banners"))
        .andExpect(status().isOk())
        .andDo(
            document(
                "banners-list",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Banner")
                        .summary("홈 배너 목록 조회")
                        .description(
                            """
                            홈 화면 배너 목록을 조회한다. **비로그인 호출 허용**. 배너가 등록된 공지를
                            개수 제한 없이 최신순(id DESC)으로 모두 반환한다. 각 배너의 `noticeId` 로
                            연결된 공지 상세(`GET /v1/inbox/{noticeId}`)로 이동할 수 있다.""")
                        .responseSchema(Schema.schema("BannerListResponse"))
                        .responseFields(
                            fieldWithPath("[].noticeId")
                                .description("연결된 공지 ID (상세 이동: GET /v1/inbox/{noticeId})"),
                            fieldWithPath("[].bannerTitle").description("배너 제목"),
                            fieldWithPath("[].bannerImageUrl").description("배너 이미지 URL"))
                        .build())));
  }
}
