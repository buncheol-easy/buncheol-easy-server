package buncheoleasy.push.presentation;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.global.docs.DocsTestSupport;
import buncheoleasy.push.application.PushDeviceService;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("PushDeviceController 문서화 테스트")
class PushDeviceControllerDocsTest extends DocsTestSupport {

  // 예시 값(실제 기기 토큰 아님).
  private static final String TOKEN = "a1b2c3d4".repeat(8);

  @MockitoBean private PushDeviceService pushDeviceService;

  @Test
  void 앱_푸시_기기_등록() throws Exception {
    mockMvc
        .perform(
            put("/v1/push-devices")
                .with(userAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"token\":\"%s\",\"platform\":\"IOS\",\"environment\":\"PRODUCTION\"}"
                        .formatted(TOKEN)))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "push-devices-register",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("PushDevice")
                        .summary("앱 푸시 기기 등록")
                        .description(
                            "iOS 앱이 로그인·실행할 때 이 기기의 APNs 토큰을 등록한다. 토큰 기준 upsert — 같은 토큰이 이미 있으면 "
                                + "주인·환경을 지금 로그인한 계정으로 바꾼다(기기는 마지막에 로그인한 계정의 알림만 받는다). "
                                + "회원 한 명당 최근 10대까지 남기고 더 오래된 기기는 지운다. 수신함에 알림이 쌓일 때 같은 제목·본문으로 "
                                + "푸시가 가며, 페이로드의 linkPath 가 누르면 열 화면이다. 로그인 필수, 응답 본문 없음(204).")
                        .requestHeaders(userAuthorizationHeader())
                        .requestSchema(Schema.schema("RegisterPushDeviceRequest"))
                        .requestFields(
                            fieldWithPath("token")
                                .description("APNs 기기 토큰(hex 64~200자). 서버는 소문자로 저장한다"),
                            fieldWithPath("platform").description("기기 플랫폼. 지금은 `IOS` 만"),
                            fieldWithPath("environment")
                                .description(
                                    "토큰이 발급된 APNs 환경 — 개발 빌드면 `SANDBOX`, 스토어·TestFlight 빌드면 `PRODUCTION`"))
                        .build())));
  }

  @Test
  void 앱_푸시_기기_해제() throws Exception {
    mockMvc
        .perform(delete("/v1/push-devices/{token}", TOKEN).with(userAuth()))
        .andExpect(status().isNoContent())
        .andDo(
            document(
                "push-devices-unregister",
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("PushDevice")
                        .summary("앱 푸시 기기 해제")
                        .description(
                            "로그아웃·알림 끄기. 액세스 토큰을 버리기 전에 부른다. 본인 기기 토큰만 지운다 — 남의 토큰·없는 토큰도 "
                                + "204(존재 여부를 응답으로 흘리지 않는 멱등 삭제). 로그인 필수.")
                        .requestHeaders(userAuthorizationHeader())
                        .pathParameters(parameterWithName("token").description("등록했던 APNs 기기 토큰"))
                        .build())));
  }
}
