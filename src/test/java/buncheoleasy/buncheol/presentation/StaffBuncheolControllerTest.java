package buncheoleasy.buncheol.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.buncheol.application.BuncheolDetailQueryService;
import buncheoleasy.buncheol.application.BuncheolListQueryService;
import buncheoleasy.buncheol.application.BuncheolManagementQueryService;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.application.MyHostedBuncheolQueryService;
import buncheoleasy.buncheol.domain.FlowType;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 빈 교체 목록을 BuncheolControllerTest 와 같게 두어 테스트 컨텍스트를 함께 쓴다.
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("StaffBuncheolController 테스트")
class StaffBuncheolControllerTest {

  private static final Long HOST_ID = 1L;
  private static final String PATH = "/v1/staff/buncheols";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BuncheolService buncheolService;

  @MockitoBean private MyHostedBuncheolQueryService myHostedBuncheolQueryService;

  @MockitoBean private BuncheolListQueryService buncheolListQueryService;

  @MockitoBean private BuncheolDetailQueryService buncheolDetailQueryService;

  @MockitoBean private BuncheolManagementQueryService buncheolManagementQueryService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

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

  private static MockMultipartFile requestPart(final String json) {
    return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
  }

  private static MockMultipartFile imagePart() {
    return new MockMultipartFile(
        "images", "album-cover.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
  }

  private static String requestJson(final String opensAtLine, final String titleLine) {
    Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);
    return """
        {
          "groupId": 100,
          %s
          "purchaseSite": "공식 스토어",
          "deadline": "%s",
          %s
          "minHeadcount": 3,
          "gs25ShippingFee": 3000,
          "thumbnailIndex": 0,
          "buncheolMembers": [
            {"memberId": 200, "price": 50000},
            {"memberId": 201, "price": 0, "accessType": "CODE_ONLY"}
          ]
        }
        """
        .formatted(titleLine, deadline, opensAtLine);
  }

  @Test
  void 오픈_시각과_함께_개최하면_201과_분철_id_를_돌려준다() throws Exception {
    Instant opensAt = Instant.parse("2026-10-20T11:00:00Z");
    given(buncheolService.holdStaffBuncheol(eq(HOST_ID), any(), any())).willReturn(10L);

    mockMvc
        .perform(
            multipart(PATH)
                .file(
                    requestPart(
                        requestJson(
                            "\"opensAt\": \"" + opensAt + "\",", "\"title\": \"몬엑 분철\",")))
                .file(imagePart())
                .with(mockAuth()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.buncheolId").value(10));

    then(buncheolService)
        .should()
        .holdStaffBuncheol(
            eq(HOST_ID),
            argThat(
                request ->
                    opensAt.equals(request.opensAt()) && request.buncheolMembers().size() == 2),
            argThat(images -> images.size() == 1));
  }

  @Test
  void 오픈_시각_없이_개최하면_null_로_넘긴다() throws Exception {
    given(buncheolService.holdStaffBuncheol(eq(HOST_ID), any(), any())).willReturn(10L);

    mockMvc
        .perform(
            multipart(PATH)
                .file(requestPart(requestJson("", "\"title\": \"몬엑 분철\",")))
                .file(imagePart())
                .with(mockAuth()))
        .andExpect(status().isCreated());

    then(buncheolService)
        .should()
        .holdStaffBuncheol(eq(HOST_ID), argThat(request -> request.opensAt() == null), any());
  }

  @Test
  void 개최_방식_칸을_보내도_무시하고_개최한다() throws Exception {
    given(buncheolService.holdStaffBuncheol(eq(HOST_ID), any(), any())).willReturn(10L);

    mockMvc
        .perform(
            multipart(PATH)
                .file(requestPart(requestJson("\"flowType\": \"C2C\",", "\"title\": \"몬엑 분철\",")))
                .file(imagePart())
                .with(mockAuth()))
        .andExpect(status().isCreated());

    then(buncheolService)
        .should()
        .holdStaffBuncheol(
            eq(HOST_ID),
            argThat(request -> request.toParams().flowType() == FlowType.LEGACY),
            any());
  }

  @Test
  void 이미지_파트가_없으면_400_C001() throws Exception {
    mockMvc
        .perform(
            multipart(PATH)
                .file(requestPart(requestJson("", "\"title\": \"몬엑 분철\",")))
                .with(mockAuth()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

    then(buncheolService).should(never()).holdStaffBuncheol(any(), any(), any());
  }

  @Test
  void 제목이_없으면_회원_개최와_같은_400_C001() throws Exception {
    mockMvc
        .perform(
            multipart(PATH)
                .file(requestPart(requestJson("", "")))
                .file(imagePart())
                .with(mockAuth()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

    then(buncheolService).should(never()).holdStaffBuncheol(any(), any(), any());
  }

  @Test
  void 지난_오픈_시각은_C001_이_아니라_서비스의_BCH129_로_나간다() throws Exception {
    given(buncheolService.holdStaffBuncheol(eq(HOST_ID), any(), any()))
        .willThrow(new BusinessException(ErrorCode.BUNCHEOL_OPENS_AT_INVALID));

    mockMvc
        .perform(
            multipart(PATH)
                .file(
                    requestPart(
                        requestJson(
                            "\"opensAt\": \"2020-01-01T00:00:00Z\",", "\"title\": \"몬엑 분철\",")))
                .file(imagePart())
                .with(mockAuth()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.BUNCHEOL_OPENS_AT_INVALID.getCode()));
  }

  @Test
  void 운영진이_아니면_403_USR031() throws Exception {
    given(buncheolService.holdStaffBuncheol(eq(HOST_ID), any(), any()))
        .willThrow(new BusinessException(ErrorCode.USER_CANNOT_HOST));

    mockMvc
        .perform(
            multipart(PATH)
                .file(requestPart(requestJson("", "\"title\": \"몬엑 분철\",")))
                .file(imagePart())
                .with(mockAuth()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.USER_CANNOT_HOST.getCode()));
  }
}
