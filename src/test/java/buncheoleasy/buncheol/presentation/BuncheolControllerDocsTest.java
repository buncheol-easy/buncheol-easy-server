package buncheoleasy.buncheol.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.MyHostedBuncheolQueryService;
import buncheoleasy.buncheol.domain.BuncheolStatus;
import buncheoleasy.buncheol.dto.response.MyHostedBuncheolResponse;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("BuncheolController 문서화 테스트")
class BuncheolControllerDocsTest {

  private static final Long HOST_ID = 1L;

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @MockitoBean private BuncheolService buncheolService;

  @MockitoBean private MyHostedBuncheolQueryService myHostedBuncheolQueryService;

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
              new UsernamePasswordAuthenticationToken(HOST_ID, null, Collections.emptyList()));
      return request;
    };
  }

  private String holdRequestJson() {
    return """
        {
          "groupId": 100,
          "title": "뉴진스 1집 분철",
          "description": "공식 스토어 단독 구성",
          "purchaseSite": "공식 스토어",
          "deadline": "%s",
          "gs25ShippingFee": 3000,
          "cuShippingFee": null,
          "buncheolMembers": [
            {"memberId": 200, "bidMinPrice": 50000}
          ]
        }
        """
        .formatted(LocalDateTime.now().plusDays(7));
  }

  private String modifyRequestJson() {
    return """
        {
          "title": "뉴진스 1집 분철 (수정)",
          "description": "공식 스토어 단독 구성",
          "purchaseSite": "공식 스토어",
          "deadline": "%s",
          "gs25ShippingFee": 3500,
          "cuShippingFee": null,
          "keepImageIds": [1, 2],
          "buncheolMembers": [
            {"memberId": 200, "bidMinPrice": 60000}
          ]
        }
        """
        .formatted(LocalDateTime.now().plusDays(10));
  }

  @Test
  void 분철_개최() throws Exception {
    MockMultipartFile requestPart =
        new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, holdRequestJson().getBytes());

    mockMvc
        .perform(
            multipart("/v1/buncheols")
                .file(requestPart)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isCreated())
        .andDo(
            document(
                "buncheols-hold",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 개최")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .description(
                            """
                            multipart/form-data 요청.

                            **request 파트** (application/json, 필수)
                            ```json
                            {
                              "groupId": Long,                // 그룹 ID
                              "title": String,                // 1~200자
                              "description": String?,         // 선택, 300자 이하
                              "purchaseSite": String,         // 1~200자
                              "deadline": LocalDateTime,      // 미래 시점
                              "gs25ShippingFee": Integer?,    // 양수, gs25/cu 중 최소 1개 필수
                              "cuShippingFee": Integer?,      // 양수, gs25/cu 중 최소 1개 필수
                              "buncheolMembers": [
                                {
                                  "memberId": Long,
                                  "bidMinPrice": Long         // 양수
                                }
                              ]
                            }
                            ```

                            **images 파트** (선택): 이미지 파일 목록, **최대 5장**
                            """)
                        .build())));
  }

  @Test
  void 분철_수정() throws Exception {
    MockMultipartFile requestPart =
        new MockMultipartFile(
            "request", "", MediaType.APPLICATION_JSON_VALUE, modifyRequestJson().getBytes());

    MockMultipartHttpServletRequestBuilder builder =
        multipart("/v1/buncheols/{id}", 10L)
            .file(requestPart)
            .header("Authorization", "Bearer {accessToken}")
            .with(
                request -> {
                  request.setMethod("PUT");
                  return request;
                })
            .with(mockAuth());

    mockMvc
        .perform(builder)
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "buncheols-modify",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 수정")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .description(
                            """
                            multipart/form-data PUT.

                            **request 파트** (application/json, 필수)
                            ```json
                            {
                              "title": String,                // 1~200자
                              "description": String?,         // 선택, 300자 이하
                              "purchaseSite": String,         // 1~200자
                              "deadline": LocalDateTime,      // 미래 시점
                              "gs25ShippingFee": Integer?,    // 양수
                              "cuShippingFee": Integer?,      // 양수
                              "keepImageIds": [Long],         // 유지할 기존 이미지 ID (비어있으면 모두 제거)
                              "buncheolMembers": [
                                {
                                  "memberId": Long,
                                  "bidMinPrice": Long         // 양수
                                }
                              ]
                            }
                            ```

                            **images 파트** (선택): 새로 업로드할 이미지 파일 목록.
                            `keepImageIds.size + images.size` 가 **최대 5장** 이어야 함
                            """)
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .build())));
  }

  @Test
  void 분철_상태_진행() throws Exception {
    mockMvc
        .perform(
            patch("/v1/buncheols/{id}/status", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"GOODS_ORDERED\"}"))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "buncheols-advance-status",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 상태 진행")
                        .description("호스트가 분철 상태를 다음 단계로 전이시킨다.")
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .requestSchema(Schema.schema("BuncheolStatusRequest"))
                        .requestFields(
                            fieldWithPath("status")
                                .description(
                                    "전이할 상태. 호스트가 수동 전이 가능한 값: GOODS_ORDERED (CLOSED → GOODS_ORDERED), SELLER_SHIPPING (GOODS_ORDERED → SELLER_SHIPPING)"))
                        .build())));
  }

  @Test
  void 내_개최_분철_목록_조회() throws Exception {
    LocalDateTime deadline = LocalDateTime.of(2026, 6, 1, 12, 0);
    LocalDateTime createdAt = LocalDateTime.of(2026, 5, 1, 9, 0);
    MyHostedBuncheolResponse response =
        new MyHostedBuncheolResponse(
            10L, "뉴진스 1집 분철", "뉴진스", BuncheolStatus.RECRUITING, deadline, 5, 7L, createdAt);
    given(myHostedBuncheolQueryService.getMyHostedBuncheols(HOST_ID)).willReturn(List.of(response));

    mockMvc
        .perform(
            get("/v1/buncheols/me")
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isOk())
        .andDo(
            document(
                "buncheols-list-my-hosted",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("내가 개최한 분철 목록 조회")
                        .description("마이페이지에서 호스트 본인이 개최한 분철 목록을 최신 개최 순으로 조회한다.")
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .responseSchema(Schema.schema("MyHostedBuncheolListResponse"))
                        .responseFields(
                            fieldWithPath("[].buncheolId").description("분철 ID"),
                            fieldWithPath("[].title").description("분철 제목"),
                            fieldWithPath("[].groupName").description("대상 K-pop 그룹명"),
                            fieldWithPath("[].status")
                                .description("분철 진행 상태 (RECRUITING | CLOSED | ...)"),
                            fieldWithPath("[].deadline").description("분철 모집 마감일"),
                            fieldWithPath("[].memberSlotCount").description("분철에 포함된 멤버 슬롯 수"),
                            fieldWithPath("[].activeParticipationCount")
                                .description("활성 참여자 수 (ACTIVE_BID/AWAITING_PAYMENT/CONFIRMED)"),
                            fieldWithPath("[].createdAt").description("분철 개최 일시"))
                        .build())));
  }

  @Test
  void 분철_취소() throws Exception {
    mockMvc
        .perform(
            delete("/v1/buncheols/{id}", 10L)
                .header("Authorization", "Bearer {accessToken}")
                .with(mockAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "buncheols-cancel",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Buncheol")
                        .summary("분철 취소")
                        .description("호스트가 자신이 개최한 분철을 취소한다.")
                        .pathParameters(parameterWithName("id").description("분철 ID"))
                        .requestHeaders(
                            headerWithName("Authorization").description("Bearer {accessToken}"))
                        .build())));
  }
}
