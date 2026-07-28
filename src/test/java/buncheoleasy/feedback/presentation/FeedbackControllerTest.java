package buncheoleasy.feedback.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import buncheoleasy.auth.infrastructure.jwt.JwtTokenProvider;
import buncheoleasy.feedback.application.FeedbackService;
import buncheoleasy.feedback.dto.request.CreateFeedbackRequest;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("FeedbackController 테스트")
class FeedbackControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FeedbackService feedbackService;

  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @Test
  void 비로그인_의견은_204_로_접수된다() throws Exception {
    // 로그인이 안 돼서 남기는 의견이 가장 받고 싶은 종류라 인증 없이도 통과해야 한다.
    mockMvc
        .perform(
            post("/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"입금 계좌를 못 찾겠어요\",\"screenPath\":\"/profile/bids\"}"))
        .andExpect(status().isNoContent());

    ArgumentCaptor<CreateFeedbackRequest> captor =
        ArgumentCaptor.forClass(CreateFeedbackRequest.class);
    // 필터를 끈 테스트라 principal 이 없어 userId 는 null 로 들어온다(= 비로그인 경로).
    verify(feedbackService).submit(eq(null), any(), captor.capture());
    assertThat(captor.getValue().content()).isEqualTo("입금 계좌를 못 찾겠어요");
    assertThat(captor.getValue().screenPath()).isEqualTo("/profile/bids");
  }

  @Test
  void 화면_경로가_없어도_접수된다() throws Exception {
    mockMvc
        .perform(
            post("/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"그냥 좋아요\"}"))
        .andExpect(status().isNoContent());

    verify(feedbackService).submit(eq(null), any(), any());
  }

  @Test
  void 본문이_비어있으면_400_이고_서비스는_호출되지_않는다() throws Exception {
    mockMvc
        .perform(
            post("/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"   \"}"))
        .andExpect(status().isBadRequest());

    verify(feedbackService, never()).submit(any(), any(), any());
  }

  @Test
  void 본문이_500자를_넘으면_400_이다() throws Exception {
    String tooLong = "가".repeat(501);

    mockMvc
        .perform(
            post("/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"%s\"}".formatted(tooLong)))
        .andExpect(status().isBadRequest());

    verify(feedbackService, never()).submit(any(), any(), any());
  }

  @Test
  void 화면_경로에_외부_URL_을_넣으면_400_이다() throws Exception {
    // 슬랙 메시지에 외부 링크가 실리지 않도록 in-app 상대 경로만 허용한다.
    mockMvc
        .perform(
            post("/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"의견\",\"screenPath\":\"https://evil.example.com\"}"))
        .andExpect(status().isBadRequest());

    verify(feedbackService, never()).submit(any(), any(), any());
  }

  @Test
  void 도배_한도를_넘으면_429_FDB_001_을_반환한다() throws Exception {
    willThrow(new BusinessException(ErrorCode.FEEDBACK_RATE_LIMITED))
        .given(feedbackService)
        .submit(any(), any(), any());

    mockMvc
        .perform(
            post("/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"도배\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("FDB-001"));
  }
}
